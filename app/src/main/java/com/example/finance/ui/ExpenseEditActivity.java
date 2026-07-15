package com.example.finance.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.*;
import android.database.Cursor;
import android.content.Intent;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;
import com.example.finance.util.DateUtils;
import com.example.finance.util.FormatUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;

public class ExpenseEditActivity extends Activity {

    public static final String EXTRA_TXN_ID = "txn_id";

    private DatabaseHelper db;
    private SessionManager session;

    private Spinner spCategory;
    private EditText etAmount, etDate, etNote;
    private Button btnSave, btnDelete, btnBack;

    private final ArrayList<Integer> catIds = new ArrayList<>();
    private int editingId = -1; // -1 = tạo mới

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_edit);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        spCategory = findViewById(R.id.spCategory);
        etAmount   = findViewById(R.id.etAmount);
        etDate     = findViewById(R.id.etDate);
        etNote     = findViewById(R.id.etNote);
        btnSave    = findViewById(R.id.btnSaveTxn);
        btnDelete  = findViewById(R.id.btnDeleteTxn);
        btnBack    = findViewById(R.id.btnBackHome);

        FormatUtils.applyMoneyMask(etAmount);

        // Date default = hôm nay + DatePicker
        etDate.setText(DateUtils.fmt(LocalDate.now()));
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> showDatePicker());

        // Tải danh mục CHI
        loadCategories("EXPENSE");

        // Nếu có id => chế độ sửa
        editingId = getIntent().getIntExtra(EXTRA_TXN_ID, -1);
        if (editingId > 0) {
            fillEditingData(editingId);
            btnDelete.setEnabled(true);
        } else {
            btnDelete.setEnabled(false);
        }

        btnSave.setOnClickListener(v -> onSave());
        btnDelete.setOnClickListener(v -> onDelete());
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadCategories(String type) {
        catIds.clear();
        ArrayList<String> names = new ArrayList<>();
        int uid = session.getUserId();
        Cursor c = db.getCategories(uid, type);
        try {
            while (c.moveToNext()) {
                catIds.add(c.getInt(0));
                names.add(c.getString(1));
            }
        } finally { c.close(); }
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(ad);
    }

    private void fillEditingData(int id) {
        Cursor c = db.getTransactionById(id);
        try {
            if (c.moveToFirst()) {
                int catId = c.getInt(0);
                int amount = c.getInt(1);
                String date = c.getString(2);
                String note = c.getString(3);
                String type = c.getString(4); // "EXPENSE" | "INCOME"

                // đảm bảo đang ở màn CHI
                if (!"EXPENSE".equals(type)) {
                    Toast.makeText(this, "Bản ghi không phải CHI", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                selectSpinnerByCatId(catId);
                etAmount.setText(FormatUtils.money(amount));
                etDate.setText(date);
                etNote.setText(note == null ? "" : note);
            }
        } finally { c.close(); }
    }

    private void selectSpinnerByCatId(int catId) {
        for (int i = 0; i < catIds.size(); i++) {
            if (catIds.get(i) == catId) {
                spCategory.setSelection(i);
                break;
            }
        }
    }

    private void onSave() {
        int uid = session.getUserId();
        if (uid <= 0) { finish(); return; }

        String amountStr = etAmount.getText().toString().trim();
        int amount = FormatUtils.parseMoney(amountStr);
        if (amount <= 0) {
            Toast.makeText(this, getString(R.string.msg_enter_valid_number), Toast.LENGTH_SHORT).show();
            return;
        }

        String date = etDate.getText().toString().trim();
        try {
            DateUtils.parse(date); // validate định dạng yyyy-MM-dd
        } catch (Exception e) {
            Toast.makeText(this, "Ngày không hợp lệ (yyyy-MM-dd)", Toast.LENGTH_SHORT).show();
            return;
        }

        int catId = catIds.isEmpty() ? -1 : catIds.get(spCategory.getSelectedItemPosition());
        String note = etNote.getText().toString().trim();

        if (editingId > 0) {
            db.updateTransaction(editingId, catId, amount, date, note);
            Toast.makeText(this, getString(R.string.msg_updated), Toast.LENGTH_SHORT).show();
        } else {
            db.insertTransaction(uid, catId, "EXPENSE", amount, date, note);
            Toast.makeText(this, getString(R.string.msg_added_expense), Toast.LENGTH_SHORT).show();
        }

        // Trả saved_date về List để nó tự điều chỉnh bộ lọc nếu cần
        Intent data = new Intent();
        data.putExtra("saved_date", date);
        setResult(RESULT_OK, data);
        finish();
    }

    private void onDelete() {
        if (editingId <= 0) return;
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.msg_deleted) + "?")
                .setPositiveButton(getString(R.string.btn_delete_txn), (d, w) -> {
                    db.deleteTransaction(editingId);
                    Toast.makeText(this, getString(R.string.msg_deleted), Toast.LENGTH_SHORT).show();
                    Intent data = new Intent();
                    data.putExtra("deleted", true);
                    setResult(RESULT_OK, data);
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDatePicker() {
        String cur = etDate.getText().toString().trim();
        LocalDate ld = LocalDate.now();
        try { ld = DateUtils.parse(cur); } catch (Exception ignore) {}

        Calendar cal = Calendar.getInstance();
        cal.set(ld.getYear(), ld.getMonthValue()-1, ld.getDayOfMonth());

        DatePickerDialog dlg = new DatePickerDialog(this,
                (view, y, m, d) -> etDate.setText(String.format("%04d-%02d-%02d", y, m+1, d)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }
}
