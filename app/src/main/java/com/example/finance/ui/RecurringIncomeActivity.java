package com.example.finance.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;
import com.example.finance.util.DateUtils;
import com.example.finance.util.FormatUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.time.LocalDate;
import java.util.ArrayList;

public class RecurringIncomeActivity extends Activity {
    private DatabaseHelper db;
    private SessionManager session;
    private Spinner spCategory;
    private EditText etAmount, etStartDate;
    private Button btnAdd, btnBackHome, btnToggleList;
    private ImageButton btnPickStartDate;
    private ListView lv;

    private final ArrayList<Integer> catIds = new ArrayList<>();

    // dữ liệu list
    private final ArrayList<Integer> recIds = new ArrayList<>();
    private final ArrayList<Integer> recCatIds = new ArrayList<>();
    private final ArrayList<Integer> recAmounts = new ArrayList<>();
    private final ArrayList<Integer> recDays = new ArrayList<>();
    private final ArrayList<String> recCatNames = new ArrayList<>();
    private final ArrayList<String> recStarts = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;

    private boolean listExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recurring_income);
        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        spCategory      = findViewById(R.id.spIncomeCategory);
        etAmount        = findViewById(R.id.etIncomeAmount);
        etStartDate     = findViewById(R.id.etStartDate);
        btnAdd          = findViewById(R.id.btnAddRecurring);
        btnBackHome     = findViewById(R.id.btnBackHome);
        btnToggleList   = findViewById(R.id.btnToggleList);
        btnPickStartDate= findViewById(R.id.btnPickStartDate);
        lv              = findViewById(R.id.lvRecurring);

        // mask tiền & mặc định ngày bắt đầu = hôm nay nếu trống
        FormatUtils.applyMoneyMask(etAmount);
        if (etStartDate.getText().toString().trim().isEmpty()) {
            etStartDate.setText(DateUtils.fmt(LocalDate.now()));
        }

        // spinner danh mục thu
        int uid = session.getUserId();
        loadIncomeCategories(uid);

        // Pick start date khi nhấn field hoặc icon
        View.OnClickListener pickListener = v -> pickDateInto(etStartDate);
        if (btnPickStartDate != null) btnPickStartDate.setOnClickListener(pickListener);
        etStartDate.setOnClickListener(pickListener);

        // Thêm mới rule
        btnAdd.setOnClickListener(v -> {
            try {
                String raw = etAmount.getText().toString().trim();
                int amount = FormatUtils.parseMoney(raw);
                String start = etStartDate.getText().toString().trim();
                if (start.isEmpty()) start = DateUtils.fmt(LocalDate.now());

                LocalDate startLd;
                try { startLd = DateUtils.parse(start); }
                catch (Exception ex) {
                    Toast.makeText(this, getString(R.string.msg_invalid_date), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (amount <= 0) throw new NumberFormatException();

                int pos = spCategory.getSelectedItemPosition();
                if (pos < 0 || pos >= catIds.size()) {
                    Toast.makeText(this, getString(R.string.msg_category_conflict), Toast.LENGTH_SHORT).show();
                    return;
                }
                int catId  = catIds.get(pos);

                // Lấy day_of_month trực tiếp từ ngày bắt đầu
                int dom = startLd.getDayOfMonth();

                db.addRecurringIncomeMonthly(uid, catId, amount, dom, start);
                Toast.makeText(this, getString(R.string.msg_added_recurring), Toast.LENGTH_SHORT).show();

                etAmount.setText("");
                loadList(true);
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.msg_enter_valid_number), Toast.LENGTH_SHORT).show();
            }
        });

        // Trở về
        btnBackHome.setOnClickListener(v -> goHome());

        // Toggle xổ/thu danh sách
        btnToggleList.setOnClickListener(v -> {
            listExpanded = !listExpanded;
            lv.setVisibility(listExpanded ? View.VISIBLE : View.GONE);
            btnToggleList.setText(listExpanded ? R.string.btn_collapse_list : R.string.btn_expand_list);
        });

        // Click item -> BottomSheet sửa / xoá
        lv.setOnItemClickListener((parent, view, position, id) -> {
            showEditBottomSheet(
                    recIds.get(position),
                    recCatIds.get(position),
                    recCatNames.get(position),
                    recAmounts.get(position),
                    recDays.get(position),
                    recStarts.get(position)
            );
        });

        // load dữ liệu lần đầu, mặc định THU GỌN
        lv.setVisibility(View.GONE);
        btnToggleList.setText(R.string.btn_expand_list);
        loadList(false);
    }

    private void loadIncomeCategories(int uid) {
        catIds.clear();
        ArrayList<String> names = new ArrayList<>();
        Cursor c = db.getCategories(uid, "INCOME");
        try {
            while (c.moveToNext()) {
                catIds.add(c.getInt(0));
                names.add(c.getString(1));
            }
        } finally { c.close(); }

        // nếu chưa có danh mục thu -> seed mặc định rồi load lại
        if (names.isEmpty()) {
            db.seedDefaultCategories(uid);
            names.clear(); catIds.clear();
            Cursor c2 = db.getCategories(uid, "INCOME");
            try {
                while (c2.moveToNext()) {
                    catIds.add(c2.getInt(0));
                    names.add(c2.getString(1));
                }
            } finally { c2.close(); }
        }

        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(ad);
    }

    /** Hiển thị DatePicker và set yyyy-MM-dd vào EditText target */
    private void pickDateInto(EditText target) {
        LocalDate base;
        String cur = target.getText().toString().trim();
        try { base = cur.isEmpty() ? LocalDate.now() : DateUtils.parse(cur); }
        catch (Exception e) { base = LocalDate.now(); }

        DatePickerDialog dlg = new DatePickerDialog(
                this,
                (picker, y, m, d) -> target.setText(DateUtils.fmt(LocalDate.of(y, m + 1, d))),
                base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth()
        );
        dlg.show();
    }

    /** load list; optionally expand after load */
    private void loadList(boolean expandAfterLoad) {
        recIds.clear(); recCatIds.clear(); recCatNames.clear(); recAmounts.clear(); recDays.clear(); recStarts.clear();
        int uid = session.getUserId();
        Cursor c = db.listRecurringIncomes(uid);
        ArrayList<String> rows = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                int id     = c.getInt(0);
                int catId  = c.getInt(1);
                String cat = c.getString(2);
                int amount = c.getInt(3);
                int dom    = c.getInt(4);
                String start = c.getString(5);

                recIds.add(id);
                recCatIds.add(catId);
                recCatNames.add(cat);
                recAmounts.add(amount);
                recDays.add(dom);
                recStarts.add(start);

                rows.add(cat + " • ngày " + dom + " • " + FormatUtils.money(amount) + " • từ " + start);
            }
        } finally { c.close(); }

        if (listAdapter == null) {
            listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
            lv.setAdapter(listAdapter);
        } else {
            listAdapter.clear();
            listAdapter.addAll(rows);
            listAdapter.notifyDataSetChanged();
        }

        if (expandAfterLoad) {
            listExpanded = true;
            lv.setVisibility(View.VISIBLE);
            btnToggleList.setText(R.string.btn_collapse_list);
        }
    }

    /** BottomSheet: sửa / xoá 1 rule định kỳ */
    private void showEditBottomSheet(int recId, int catId, String catName, int amount, int dom, String startDate) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_recurring_edit, null);
        sheet.setContentView(view);

        Spinner sp       = view.findViewById(R.id.spEditCategory);
        EditText etAmt   = view.findViewById(R.id.etEditAmount);
        EditText etStart = view.findViewById(R.id.etEditStart);
        Button btnSave   = view.findViewById(R.id.btnDialogSave);
        Button btnDelete = view.findViewById(R.id.btnDialogDelete);
        Button btnCancel = view.findViewById(R.id.btnDialogCancel);

        // bind spinner
        ArrayAdapter<String> cad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, getSpinnerNames());
        cad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(cad);
        int idx = findIndexById(catIds, catId);
        if (idx >= 0) sp.setSelection(idx);

        // bind amount
        etAmt.setText(FormatUtils.money(amount));
        etAmt.setSelection(etAmt.getText().length());
        FormatUtils.applyMoneyMask(etAmt);

        // bind start
        etStart.setText(startDate);
        etStart.setOnClickListener(v -> pickDateInto(etStart));

        // SAVE
        btnSave.setOnClickListener(v -> {
            int pos = sp.getSelectedItemPosition();
            if (pos < 0 || pos >= catIds.size()) {
                Toast.makeText(this, getString(R.string.msg_category_conflict), Toast.LENGTH_SHORT).show();
                return;
            }
            int newCatId  = catIds.get(pos);
            int newAmount = FormatUtils.parseMoney(etAmt.getText().toString().trim());

            String newStart = etStart.getText().toString().trim();
            LocalDate newStartLd;
            try { newStartLd = DateUtils.parse(newStart); }
            catch (Exception e) {
                Toast.makeText(this, getString(R.string.msg_invalid_date), Toast.LENGTH_SHORT).show();
                return;
            }
            if (newAmount <= 0) {
                Toast.makeText(this, getString(R.string.msg_enter_valid_number), Toast.LENGTH_SHORT).show();
                return;
            }

            // day_of_month lấy từ start
            int newDom = newStartLd.getDayOfMonth();

            db.updateRecurringIncome(recId, newCatId, newAmount, newDom, newStart);

            // Nếu đẩy start > hôm nay, DB đã void auto-future -> báo cho user biết
            if (newStartLd.isAfter(LocalDate.now())) {
                Toast.makeText(this,
                        "Đã cập nhật. Các khoản tự sinh từ hôm nay trở đi của quy tắc này đã được hủy.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.msg_updated), Toast.LENGTH_SHORT).show();
            }

            sheet.dismiss();
            loadList(false);
        });

        // DELETE (void future auto rồi xóa rule)
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Xóa quy tắc thu định kỳ?")
                    .setMessage("Các khoản thu TỰ SINH từ hôm nay trở đi theo quy tắc này sẽ bị hủy và biến mất khỏi danh sách thu. Bạn có chắc muốn xóa?")
                    .setPositiveButton(R.string.btn_delete_txn, (d, w) -> {
                        db.deleteRecurringIncome(recId);
                        Toast.makeText(this, getString(R.string.msg_deleted), Toast.LENGTH_SHORT).show();
                        sheet.dismiss();
                        loadList(false);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        // CANCEL
        btnCancel.setOnClickListener(v -> sheet.dismiss());

        sheet.show();
    }

    private ArrayList<String> getSpinnerNames() {
        ArrayList<String> names = new ArrayList<>();
        if (spCategory.getAdapter() != null) {
            for (int i = 0; i < spCategory.getAdapter().getCount(); i++) {
                Object item = spCategory.getAdapter().getItem(i);
                names.add(item != null ? item.toString() : "");
            }
        }
        return names;
    }

    private int findIndexById(ArrayList<Integer> ids, int id) {
        for (int i = 0; i < ids.size(); i++) if (ids.get(i) == id) return i;
        return -1;
    }

    private void goHome() {
        Intent home = new Intent(this, DashboardActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }
}
