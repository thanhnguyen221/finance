package com.example.finance.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;
import com.example.finance.util.DateUtils;
import com.example.finance.util.FormatUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Dùng chung layout activity_transaction_list:
 * - RadioGroup: Tất cả / Chi tiêu / Thu nhập
 * - Điều hướng ngày: prev/next (dịch theo đúng độ dài range)
 * - Tổng thu/chi cho range hiện chọn
 * - Click item mở màn sửa đúng loại
 */
public class IncomeListActivity extends Activity {

    private static final int REQ_EDIT = 1002;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DatabaseHelper db;
    private SessionManager session;

    // Top bar
    private TextView tvTitle;
    private ImageButton btnTopBack, btnFilter;

    // Header chọn loại + ngày
    private RadioGroup rgType;
    private RadioButton rbAll, rbExpense, rbIncome;
    private ImageButton btnPrevDay, btnNextDay;
    private TextView tvDayTitle, tvDayIncome, tvDayExpense;

    // List + action
    private ListView lv;
    private Button btnAdd, btnBack;

    // Data
    private final ArrayList<Row> rowsData = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private String startDate; // yyyy-MM-dd
    private String endDate;   // yyyy-MM-dd
    private LocalDate pendingStartForRange = null;

    // "ALL" | "EXPENSE" | "INCOME"
    private String selectedType = "INCOME"; // mặc định tab Thu nhập

    public static void launch(Activity from) {
        from.startActivity(new Intent(from, IncomeListActivity.class));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_list);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        // Top bar
        tvTitle    = findViewById(R.id.tvTitle);
        btnTopBack = findViewById(R.id.btnBack);
        btnFilter  = findViewById(R.id.btnFilter);

        // Header
        rgType      = findViewById(R.id.rgType);
        rbAll       = findViewById(R.id.rbAll);
        rbExpense   = findViewById(R.id.rbExpense);
        rbIncome    = findViewById(R.id.rbIncome);
        btnPrevDay  = findViewById(R.id.btnPrevDay);
        btnNextDay  = findViewById(R.id.btnNextDay);
        tvDayTitle  = findViewById(R.id.tvDayTitle);
        tvDayIncome = findViewById(R.id.tvDayIncome);
        tvDayExpense= findViewById(R.id.tvDayExpense);

        // Content
        lv     = findViewById(R.id.lvTransactions);
        btnAdd = findViewById(R.id.btnAddTransaction);
        btnBack= findViewById(R.id.btnBackHomeList);
        TextView emptyView = findViewById(R.id.emptyView);
        if (lv != null && emptyView != null) lv.setEmptyView(emptyView);

        // Mặc định: NGÀY hôm nay + loại Thu nhập
        setDay(LocalDate.now());
        if (rgType != null) rgType.check(R.id.rbIncome);
        selectedType = "INCOME";
        updateTitlesAndTotals();

        // Actions
        btnAdd.setOnClickListener(v -> {
            if ("EXPENSE".equals(selectedType)) {
                startActivityForResult(new Intent(this, ExpenseEditActivity.class), REQ_EDIT);
            } else {
                startActivityForResult(new Intent(this, IncomeEditActivity.class), REQ_EDIT);
            }
        });

        btnBack.setOnClickListener(v -> {
            Intent home = new Intent(this, DashboardActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            finish();
        });

        if (btnTopBack != null) btnTopBack.setOnClickListener(v -> finish());
        if (btnFilter != null) btnFilter.setOnClickListener(v -> showFilterMenu(btnFilter));

        if (rgType != null) {
            rgType.setOnCheckedChangeListener((g, id) -> {
                if (id == R.id.rbExpense) selectedType = "EXPENSE";
                else if (id == R.id.rbIncome) selectedType = "INCOME";
                else selectedType = "ALL";
                updateTitlesAndTotals();
                reload();
            });
        }

        if (btnPrevDay != null) btnPrevDay.setOnClickListener(v -> {
            shiftRange(-1);
            updateTitlesAndTotals();
            reload();
        });

        if (btnNextDay != null) btnNextDay.setOnClickListener(v -> {
            shiftRange(+1);
            updateTitlesAndTotals();
            reload();
        });

        // sửa
        lv.setOnItemClickListener((p, v, pos, id) -> {
            if (pos < 0 || pos >= rowsData.size()) return;
            Row row = rowsData.get(pos);
            if ("INCOME".equals(row.type)) {
                Intent i = new Intent(this, IncomeEditActivity.class);
                i.putExtra(IncomeEditActivity.EXTRA_TXN_ID, row.id);
                startActivityForResult(i, REQ_EDIT);
            } else {
                Intent i = new Intent(this, ExpenseEditActivity.class);
                i.putExtra(ExpenseEditActivity.EXTRA_TXN_ID, row.id);
                startActivityForResult(i, REQ_EDIT);
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        int uid = session.getUserId();
        if (uid > 0) db.processRecurringIncomes(uid, LocalDate.now());
        reload();
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT && resultCode == RESULT_OK && data != null) {
            String saved = data.getStringExtra("saved_date");
            if (saved != null) {
                LocalDate d = DateUtils.parse(saved);
                if (saved.compareTo(startDate) < 0 || saved.compareTo(endDate) > 0 || !startDate.equals(endDate)) {
                    setDay(d);
                    Toast.makeText(this, "Chuyển tới ngày: " + DMY.format(d), Toast.LENGTH_SHORT).show();
                }
            }
            updateTitlesAndTotals();
            reload();
        }
    }

    // ================= Range helpers =================

    private void setDay(LocalDate d) {
        startDate = DateUtils.fmt(d);
        endDate   = DateUtils.fmt(d);
    }

    private void setWeekOf(LocalDate d) {
        LocalDate s = DateUtils.mondayOfWeek(d);
        LocalDate e = DateUtils.sundayOfWeek(d);
        startDate = DateUtils.fmt(s);
        endDate   = DateUtils.fmt(e);
    }

    private void setMonthOf(LocalDate d) {
        startDate = DateUtils.fmt(DateUtils.firstDayOfMonth(d));
        endDate   = DateUtils.fmt(DateUtils.lastDayOfMonth(d));
    }

    private void setYearOf(int year) {
        LocalDate s = LocalDate.of(year, 1, 1);
        LocalDate e = LocalDate.of(year, 12, 31);
        startDate = DateUtils.fmt(s);
        endDate   = DateUtils.fmt(e);
    }

    /** Dịch range theo số ngày (nếu đang là 1 ngày thì +-1; nếu là khoảng thì dịch cả khoảng). */
    private void shiftRange(int step) {
        LocalDate s = DateUtils.parse(startDate);
        LocalDate e = DateUtils.parse(endDate);
        int days = (int) (e.toEpochDay() - s.toEpochDay()) + 1;
        s = s.plusDays(step * (long) days);
        e = e.plusDays(step * (long) days);
        startDate = DateUtils.fmt(s);
        endDate   = DateUtils.fmt(e);
    }

    // ================= Header =================

    private void updateTitlesAndTotals() {
        String header;
        switch (selectedType) {
            case "EXPENSE": header = "Chi tiêu"; break;
            case "INCOME":  header = "Thu nhập"; break;
            default:        header = "Giao dịch";
        }

        LocalDate s = DateUtils.parse(startDate);
        LocalDate e = DateUtils.parse(endDate);
        String rangeLabel = s.equals(e)
                ? (DMY.format(s) + " • " + weekdayVN(s))
                : (DMY.format(s) + " – " + DMY.format(e));

        if (tvTitle != null) tvTitle.setText(header + " • " + rangeLabel);
        if (tvDayTitle != null) tvDayTitle.setText(rangeLabel);

        int uid = session.getUserId();
        if (uid > 0) {
            int income  = db.sumByTypeInRange(uid, "INCOME", startDate, endDate);
            int expense = db.sumByTypeInRange(uid, "EXPENSE", startDate, endDate);
            if (tvDayIncome != null)  tvDayIncome.setText(FormatUtils.money(income));
            if (tvDayExpense != null) tvDayExpense.setText(FormatUtils.money(expense));
        }
    }

    private String weekdayVN(LocalDate d) {
        switch (d.getDayOfWeek()) {
            case MONDAY: return "Thứ hai";
            case TUESDAY: return "Thứ ba";
            case WEDNESDAY: return "Thứ tư";
            case THURSDAY: return "Thứ năm";
            case FRIDAY: return "Thứ sáu";
            case SATURDAY: return "Thứ bảy";
            default: return "Chủ nhật";
        }
    }

    // ================= Filter menu =================

    private void showFilterMenu(ImageButton anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Hôm nay");
        menu.getMenu().add(0, 2, 1, "Tuần này");
        menu.getMenu().add(0, 3, 2, "Tháng này");
        menu.getMenu().add(0, 4, 3, "Năm nay");
        menu.getMenu().add(0, 5, 4, "Chọn ngày…");
        menu.getMenu().add(0, 6, 5, "Chọn khoảng…");

        menu.setOnMenuItemClickListener((MenuItem item) -> {
            LocalDate today = LocalDate.now();
            switch (item.getItemId()) {
                case 1: setDay(today); break;
                case 2: setWeekOf(today); break;
                case 3: setMonthOf(today); break;
                case 4: setYearOf(today.getYear()); break;
                case 5: pickSingleDay(today); return true;
                case 6: pickRange(today); return true;
            }
            updateTitlesAndTotals();
            reload();
            return true;
        });
        menu.show();
    }

    private void pickSingleDay(LocalDate base) {
        DatePickerDialog dlg = new DatePickerDialog(
                this,
                (vp, y, m, d) -> {
                    LocalDate picked = LocalDate.of(y, m + 1, d);
                    setDay(picked);
                    updateTitlesAndTotals();
                    reload();
                },
                base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth()
        );
        dlg.show();
    }

    private void pickRange(LocalDate base) {
        pendingStartForRange = null;
        DatePickerDialog startDlg = new DatePickerDialog(
                this,
                (vp, y, m, d) -> {
                    pendingStartForRange = LocalDate.of(y, m + 1, d);
                    DatePickerDialog endDlg = new DatePickerDialog(
                            this,
                            (vp2, y2, m2, d2) -> {
                                LocalDate end = LocalDate.of(y2, m2 + 1, d2);
                                LocalDate s = (pendingStartForRange == null) ? end : pendingStartForRange;
                                if (end.isBefore(s)) {
                                    LocalDate tmp = s; s = end; end = tmp;
                                }
                                startDate = DateUtils.fmt(s);
                                endDate   = DateUtils.fmt(end);
                                updateTitlesAndTotals();
                                reload();
                            },
                            pendingStartForRange.getYear(),
                            pendingStartForRange.getMonthValue() - 1,
                            pendingStartForRange.getDayOfMonth()
                    );
                    endDlg.setTitle("Chọn ngày kết thúc");
                    endDlg.show();
                },
                base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth()
        );
        startDlg.setTitle("Chọn ngày bắt đầu");
        startDlg.show();
    }

    // ================= Load data =================

    private void reload() {
        int uid = session.getUserId();
        if (uid <= 0) { finish(); return; }

        rowsData.clear();
        ArrayList<String> display = new ArrayList<>();

        if ("INCOME".equals(selectedType)) {
            Cursor c = db.listTransactions(uid, "INCOME", startDate, endDate);
            try {
                while (c.moveToNext()) rowsData.add(Row.fromCursor(c, "INCOME"));
            } finally { c.close(); }
        } else if ("EXPENSE".equals(selectedType)) {
            Cursor c = db.listTransactions(uid, "EXPENSE", startDate, endDate);
            try {
                while (c.moveToNext()) rowsData.add(Row.fromCursor(c, "EXPENSE"));
            } finally { c.close(); }
        } else { // ALL
            Cursor c1 = db.listTransactions(uid, "INCOME", startDate, endDate);
            try { while (c1.moveToNext()) rowsData.add(Row.fromCursor(c1, "INCOME")); }
            finally { c1.close(); }
            Cursor c2 = db.listTransactions(uid, "EXPENSE", startDate, endDate);
            try { while (c2.moveToNext()) rowsData.add(Row.fromCursor(c2, "EXPENSE")); }
            finally { c2.close(); }
            Collections.sort(rowsData, new Comparator<Row>() {
                @Override public int compare(Row a, Row b) {
                    int cmp = b.date.compareTo(a.date);
                    if (cmp != 0) return cmp;
                    return Integer.compare(b.id, a.id);
                }
            });
        }

        for (Row r : rowsData) {
            String money = FormatUtils.money(r.amount);
            if ("INCOME".equals(r.type)) money = "+" + money;
            String line = r.date + " • " + r.category + " • " + money;
            if (r.note != null && !r.note.isEmpty()) line += "\n" + r.note;
            display.add(line);
        }

        if (adapter == null) {
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, display);
            lv.setAdapter(adapter);
        } else {
            adapter.clear();
            adapter.addAll(display);
            adapter.notifyDataSetChanged();
        }
    }

    // ================= helper model =================
    private static class Row {
        int id;
        String type;   // INCOME | EXPENSE
        String date;   // yyyy-MM-dd
        int amount;
        String note;
        String category;

        static Row fromCursor(Cursor c, String type) {
            Row r = new Row();
            r.id = c.getInt(0);
            r.amount = c.getInt(1);
            r.date = c.getString(2);
            r.note = c.getString(3);
            r.category = c.getString(4);
            r.type = type;
            return r;
        }
    }
}
