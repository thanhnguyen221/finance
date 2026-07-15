package com.example.finance.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;
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
 * Màn danh sách giao dịch theo layout activity_transaction_list:
 * - RadioGroup lọc loại: Tất cả / Chi tiêu / Thu nhập
 * - Điều hướng ngày: prev / title / next (nếu range > 1 ngày thì dịch theo đúng số ngày)
 * - Tổng thu/chi cho range hiện chọn
 * - Click item mở màn sửa tương ứng (chi hoặc thu)
 */
public class ExpenseListActivity extends Activity {

    private static final int REQ_EDIT = 1001;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DatabaseHelper db;
    private SessionManager session;

    // Top bar
    private ImageButton btnTopBack, btnFilter;
    private TextView tvTitle;

    // Header chọn loại + điều hướng ngày
    private RadioGroup rgType;
    private RadioButton rbAll, rbExpense, rbIncome;
    private ImageButton btnPrevDay, btnNextDay;
    private TextView tvDayTitle, tvDayIncome, tvDayExpense;

    // List / action buttons
    private ListView lv;
    private Button btnAdd, btnBackBottom;

    // Data
    private final ArrayList<Row> rowsData = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    // range hiện tại
    private String startDate; // yyyy-MM-dd
    private String endDate;   // yyyy-MM-dd

    // giữ tạm khi chọn khoảng ngày
    private LocalDate pendingStartForRange = null;

    // loại lọc: "ALL" | "EXPENSE" | "INCOME"
    private String selectedType = "EXPENSE"; // mặc định theo tên Activity

    public static void launch(Activity from, String typeIgnore) {
        from.startActivity(new Intent(from, ExpenseListActivity.class));
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

        // Header controls
        rgType     = findViewById(R.id.rgType);
        rbAll      = findViewById(R.id.rbAll);
        rbExpense  = findViewById(R.id.rbExpense);
        rbIncome   = findViewById(R.id.rbIncome);
        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        tvDayTitle = findViewById(R.id.tvDayTitle);
        tvDayIncome= findViewById(R.id.tvDayIncome);
        tvDayExpense=findViewById(R.id.tvDayExpense);

        // Content
        lv            = findViewById(R.id.lvTransactions);
        btnAdd        = findViewById(R.id.btnAddTransaction);
        btnBackBottom = findViewById(R.id.btnBackHomeList);
        TextView emptyView = findViewById(R.id.emptyView);
        if (lv != null && emptyView != null) lv.setEmptyView(emptyView);

        // Range mặc định: NGÀY hôm nay (để khớp UI mới)
        setDay(LocalDate.now());
        // Loại mặc định: chi tiêu
        if (rgType != null) rgType.check(R.id.rbExpense);
        selectedType = "EXPENSE";

        updateTitlesAndTotals();

        // Sự kiện
        btnAdd.setOnClickListener(v -> {
            if ("INCOME".equals(selectedType)) {
                startActivityForResult(new Intent(this, IncomeEditActivity.class), REQ_EDIT);
            } else { // EXPENSE hoặc ALL (ưu tiên chi)
                startActivityForResult(new Intent(this, ExpenseEditActivity.class), REQ_EDIT);
            }
        });

        if (btnTopBack != null) btnTopBack.setOnClickListener(v -> finish());

        btnBackBottom.setOnClickListener(v -> {
            Intent home = new Intent(this, DashboardActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            finish();
        });

        if (btnFilter != null) btnFilter.setOnClickListener(v -> showFilterMenu(btnFilter));

        if (rgType != null) {
            rgType.setOnCheckedChangeListener((g, id) -> {
                if (id == R.id.rbIncome) selectedType = "INCOME";
                else if (id == R.id.rbExpense) selectedType = "EXPENSE";
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

        // Sửa item
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
        reload();
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT && resultCode == RESULT_OK && data != null) {
            String saved = data.getStringExtra("saved_date");
            if (saved != null) {
                // Nếu giao dịch nằm ngoài range -> nhảy tới NGÀY chứa giao dịch đó
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

    /** Dịch range theo số ngày step; nếu range là 1 ngày -> +-1; nếu là khoảng N ngày -> dịch cả N ngày */
    private void shiftRange(int step) {
        LocalDate s = DateUtils.parse(startDate);
        LocalDate e = DateUtils.parse(endDate);
        int days = (int) (e.toEpochDay() - s.toEpochDay()) + 1;
        s = s.plusDays(step * (long)days);
        e = e.plusDays(step * (long)days);
        startDate = DateUtils.fmt(s);
        endDate   = DateUtils.fmt(e);
    }

    // ================= Title / header =================

    private void updateTitlesAndTotals() {
        // Top title theo loại
        String header;
        switch (selectedType) {
            case "INCOME": header = "Thu nhập"; break;
            case "EXPENSE": header = "Chi tiêu"; break;
            default: header = "Giao dịch";
        }

        LocalDate s = DateUtils.parse(startDate);
        LocalDate e = DateUtils.parse(endDate);
        String rangeLabel = s.equals(e)
                ? (DMY.format(s) + " • " + weekdayVN(s))
                : (DMY.format(s) + " – " + DMY.format(e));
        if (tvTitle != null) tvTitle.setText(header + " • " + rangeLabel);
        if (tvDayTitle != null) tvDayTitle.setText(rangeLabel);

        // Tổng
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

        if ("EXPENSE".equals(selectedType)) {
            Cursor c = db.listTransactions(uid, "EXPENSE", startDate, endDate);
            try {
                while (c.moveToNext()) {
                    Row r = Row.fromCursor(c, "EXPENSE");
                    rowsData.add(r);
                }
            } finally { c.close(); }
        } else if ("INCOME".equals(selectedType)) {
            Cursor c = db.listTransactions(uid, "INCOME", startDate, endDate);
            try {
                while (c.moveToNext()) {
                    Row r = Row.fromCursor(c, "INCOME");
                    rowsData.add(r);
                }
            } finally { c.close(); }
        } else { // ALL
            Cursor c1 = db.listTransactions(uid, "EXPENSE", startDate, endDate);
            try {
                while (c1.moveToNext()) rowsData.add(Row.fromCursor(c1, "EXPENSE"));
            } finally { c1.close(); }
            Cursor c2 = db.listTransactions(uid, "INCOME", startDate, endDate);
            try {
                while (c2.moveToNext()) rowsData.add(Row.fromCursor(c2, "INCOME"));
            } finally { c2.close(); }
            // sắp xếp theo (date desc, id desc)
            Collections.sort(rowsData, new Comparator<Row>() {
                @Override public int compare(Row a, Row b) {
                    int cmp = b.date.compareTo(a.date); // yyyy-MM-dd so lexicographic ok
                    if (cmp != 0) return cmp;
                    return Integer.compare(b.id, a.id);
                }
            });
        }

        // render text
        for (Row r : rowsData) {
            String money = FormatUtils.money(r.amount);
            // Thu thêm dấu + để phân biệt
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

    // ================= model helper =================
    private static class Row {
        int id;
        String type;     // INCOME | EXPENSE
        String date;     // yyyy-MM-dd
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
