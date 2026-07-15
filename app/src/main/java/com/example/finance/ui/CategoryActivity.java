package com.example.finance.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;

import java.util.ArrayList;

public class CategoryActivity extends Activity {
    private DatabaseHelper db;
    private SessionManager session;
    private Spinner spType;
    private EditText etName;
    private Button btnAdd;
    private ListView lv;

    // lưu song song id & tên để sửa/xoá
    private final ArrayList<Integer> catIds = new ArrayList<>();
    private final ArrayList<String>  catNames = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        spType  = findViewById(R.id.spType);
        etName  = findViewById(R.id.etCategoryName);
        btnAdd  = findViewById(R.id.btnAddCategory);
        lv      = findViewById(R.id.lvCategories);

        // Empty view (nhớ có TextView id=emptyCategories trong activity_category.xml)
        TextView empty = findViewById(R.id.emptyCategories);
        if (empty != null) lv.setEmptyView(empty);

        Button btnBack = findViewById(R.id.btnBackHome);
        if (btnBack != null) btnBack.setOnClickListener(v -> goHome());

        // Spinner loại: Thu / Chi
        spType.setAdapter(buildTypeAdapter());

        // đổi loại -> reload
        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { loadList(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // thêm mới
        btnAdd.setOnClickListener(v -> {
            int uid = session.getUserId();
            if (uid <= 0) { finish(); return; }

            String name = etName.getText().toString().trim().replaceAll("\\s+", " ");
            String type = currentType();
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.msg_register_fill), Toast.LENGTH_SHORT).show();
                return;
            }
            // insertWithOnConflict IGNORE -> -1 nếu trùng (UNIQUE)
            long rowId = db.insertCategory(uid, name, type, false);
            if (rowId == -1) {
                Toast.makeText(this, getString(R.string.msg_category_conflict), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.msg_saved), Toast.LENGTH_SHORT).show();
                etName.setText("");
            }
            loadList();
        });

        // click 1 item -> Sửa / Xoá (mở dialog đẹp)
        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= catIds.size()) return;
            int catId = catIds.get(position);
            String oldName = catNames.get(position);
            showEditDialog(catId, oldName, currentType());
        });
    }

    @Override protected void onResume() {
        super.onResume();
        loadList();
    }

    // ===== Helpers =====
    private String currentType() {
        return spType.getSelectedItemPosition() == 0 ? "INCOME" : "EXPENSE";
    }

    private ArrayAdapter<String> buildTypeAdapter() {
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.opt_income), getString(R.string.opt_expense)}
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return typeAdapter;
    }

    private void loadList() {
        int uid = session.getUserId();
        if (uid <= 0) { finish(); return; }
        String type = currentType();

        catIds.clear();
        catNames.clear();
        ArrayList<String> rows = new ArrayList<>();

        Cursor c = db.getCategories(uid, type);
        try {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String name = c.getString(1);
                catIds.add(id);
                catNames.add(name);
                rows.add(name);
            }
        } finally { if (c != null) c.close(); }

        if (adapter == null) {
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
            lv.setAdapter(adapter);
        } else {
            adapter.clear();
            adapter.addAll(rows);
            adapter.notifyDataSetChanged();
        }
    }

    private void showEditDialog(int catId, String oldName, String currentType) {
        // Inflate layout dialog custom
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_category, null);
        Spinner sp = view.findViewById(R.id.spTypeDialog);
        EditText et = view.findViewById(R.id.etCategoryNameDialog);
        Button btnSave = view.findViewById(R.id.btnSaveDialog);
        Button btnDelete = view.findViewById(R.id.btnDeleteDialog);
        Button btnCancel = view.findViewById(R.id.btnCancelDialog);

        // bind dữ liệu cũ
        sp.setAdapter(buildTypeAdapter());
        sp.setSelection("INCOME".equals(currentType) ? 0 : 1);
        et.setHint(getString(R.string.hint_category_name));
        et.setText(oldName);
        if (et.getText() != null) et.setSelection(et.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();
        if (dialog.getWindow() != null) {
            // để thấy bo góc của bg_card_round
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Lưu
        btnSave.setOnClickListener(v -> {
            String newName = et.getText().toString().trim().replaceAll("\\s+", " ");
            String newType = (sp.getSelectedItemPosition() == 0) ? "INCOME" : "EXPENSE";
            if (newName.isEmpty()) {
                Toast.makeText(this, getString(R.string.msg_register_fill), Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int rows = db.updateCategory(catId, newName, newType);
                if (rows > 0) {
                    Toast.makeText(this, getString(R.string.msg_saved), Toast.LENGTH_SHORT).show();
                    // nếu đổi loại -> chuyển tab spinner chính
                    if (!newType.equals(currentType)) {
                        spType.setSelection("INCOME".equals(newType) ? 0 : 1);
                    } else {
                        loadList();
                    }
                } else {
                    loadList();
                }
                dialog.dismiss();
            } catch (SQLiteConstraintException ex) {
                // UNIQUE(user_id, name, type)
                Toast.makeText(this, getString(R.string.msg_category_conflict), Toast.LENGTH_LONG).show();
            }
        });

        // Xóa (xác nhận)
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.msg_delete_category_warning))
                    .setPositiveButton(getString(R.string.btn_delete_txn), (d2, w2) -> {
                        db.deleteCategory(catId);
                        Toast.makeText(this, getString(R.string.msg_deleted), Toast.LENGTH_SHORT).show();
                        loadList();
                        dialog.dismiss();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        // Huỷ
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void goHome() {
        Intent home = new Intent(this, DashboardActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }
}
