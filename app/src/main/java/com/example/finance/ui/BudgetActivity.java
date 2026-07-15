package com.example.finance.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.content.Intent;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;
import com.example.finance.util.DateUtils;
import com.example.finance.util.FormatUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class BudgetActivity extends Activity {
    private DatabaseHelper db;
    private SessionManager session;
    private EditText etPlanned;
    private RadioGroup rg;
    private TextView tvInfo, tvRangeInfoBudget;

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        int uid = session.getUserId();
        if (uid <= 0) { finish(); return; } // đảm bảo đã đăng nhập

        etPlanned        = findViewById(R.id.etPlannedAmount);
        tvInfo           = findViewById(R.id.tvBudgetInfo);
        tvRangeInfoBudget= findViewById(R.id.tvRangeInfoBudget);
        Button btnSave   = findViewById(R.id.btnSaveBudget);
        Button btnBack   = findViewById(R.id.btnBackHome);
        rg               = findViewById(R.id.rgRange);

        // Mô tả chung
        tvInfo.setText(getString(R.string.budget_hint_title));
        FormatUtils.applyMoneyMask(etPlanned);

        // Lần đầu theo kỳ mặc định (tháng) trong XML
        updateRangeInfo();
        loadExistingBudget();

        // Đổi kỳ -> cập nhật mô tả + nạp lại số
        rg.setOnCheckedChangeListener((g, id) -> {
            etPlanned.setError(null);
            updateRangeInfo();
            loadExistingBudget();
        });

        btnSave.setOnClickListener(v -> {
            int userId = session.getUserId();
            LocalDate[] range = resolveRange();
            String raw = etPlanned.getText().toString().trim();
            int amount = FormatUtils.parseMoney(raw);
            int checked = rg.getCheckedRadioButtonId();

            // Nếu rỗng hoặc <= 0: xoá override đúng kỳ (coi như bỏ thiết lập kỳ đó)
            if (raw.isEmpty() || amount <= 0) {
                if (checked == R.id.rbDay) {
                    db.deleteBudgetExact(userId, "DAY", range[0]);
                } else if (checked == R.id.rbWeek) {
                    db.deleteBudgetExact(userId, "WEEK", DateUtils.mondayOfWeek(range[0]));
                } else if (checked == R.id.rbYear) {
                    db.deleteBudgetExact(userId, "YEAR", LocalDate.of(range[0].getYear(), 1, 1));
                } else {
                    db.deleteBudgetExact(userId, "MONTH", range[0].withDayOfMonth(1));
                }
                Toast.makeText(this, getString(R.string.msg_budget_deleted), Toast.LENGTH_SHORT).show();
                goHome();
                return;
            }

            // Upsert đúng kỳ người dùng chọn
            if (checked == R.id.rbDay) {
                db.upsertDayBudget(userId, range[0], amount, "Ngân sách ngày");
            } else if (checked == R.id.rbWeek) {
                db.upsertWeekBudget(userId, DateUtils.mondayOfWeek(range[0]), amount, "Ngân sách tuần");
            } else if (checked == R.id.rbYear) {
                db.upsertYearBudget(userId, range[0].getYear(), amount, "Ngân sách năm");
            } else {
                db.upsertMonthlyBudget(userId, range[0].getYear(), range[0].getMonthValue(), amount, "Ngân sách tháng");
            }

            Toast.makeText(this, getString(R.string.msg_budget_saved), Toast.LENGTH_SHORT).show();
            goHome();
        });

        btnBack.setOnClickListener(v -> goHome());
    }

    /** Hiển thị số đang áp dụng cho range (tính theo ưu tiên DAY>WEEK>MONTH>YEAR>GLOBAL) */
    private void loadExistingBudget() {
        int uid = session.getUserId();
        LocalDate[] range = resolveRange();
        int plannedForRange = db.getBudgetForRange(uid, DateUtils.fmt(range[0]), DateUtils.fmt(range[1]));
        if (plannedForRange > 0) {
            String txt = FormatUtils.money(plannedForRange);
            etPlanned.setText(txt);
            etPlanned.setSelection(txt.length());
        } else {
            etPlanned.setText("");
        }
    }

    /** Tính start/end theo radio đang chọn */
    private LocalDate[] resolveRange() {
        LocalDate today = LocalDate.now();
        int checked = rg.getCheckedRadioButtonId();
        LocalDate start, end;
        if (checked == R.id.rbDay) {
            start = today; end = today;
        } else if (checked == R.id.rbWeek) {
            start = DateUtils.mondayOfWeek(today);
            end   = DateUtils.sundayOfWeek(today);
        } else if (checked == R.id.rbYear) {
            start = LocalDate.of(today.getYear(), 1, 1);
            end   = LocalDate.of(today.getYear(), 12, 31);
        } else {
            start = DateUtils.firstDayOfMonth(today);
            end   = DateUtils.lastDayOfMonth(today);
        }
        return new LocalDate[]{start, end};
    }

    /** Cập nhật mô tả khoảng: ngày mấy; tuần/tháng từ ngày nào–ngày nào; năm con gì */
    private void updateRangeInfo() {
        LocalDate today = LocalDate.now();
        LocalDate[] range = resolveRange();
        int checked = rg.getCheckedRadioButtonId();

        if (checked == R.id.rbDay) {
            tvRangeInfoBudget.setText("Hôm nay: " + DMY.format(today) + " • Ngày " + today.getDayOfMonth());
        } else if (checked == R.id.rbWeek) {
            tvRangeInfoBudget.setText("Tuần này: " + DMY.format(range[0]) + " – " + DMY.format(range[1]));
        } else if (checked == R.id.rbYear) {
            int year = today.getYear();
            String zodiacVi = zodiacVN(year);
            String zodiacEn = zodiacEN(year);
            tvRangeInfoBudget.setText("Năm " + year + " • Năm con " + zodiacVi + " (" + zodiacEn + ")");
        } else {
            int days = range[0].lengthOfMonth();
            tvRangeInfoBudget.setText(String.format("Tháng %d/%d • %d ngày • %s – %s",
                    range[0].getMonthValue(), range[0].getYear(), days,
                    DMY.format(range[0]), DMY.format(range[1])));
        }
    }

    private String zodiacVN(int year) {
        String[] vn = {"Tý","Sửu","Dần","Mão","Thìn","Tỵ","Ngọ","Mùi","Thân","Dậu","Tuất","Hợi"};
        int idx = Math.floorMod(year - 4, 12); // 1900 -> Tý
        return vn[idx];
    }

    private String zodiacEN(int year) {
        String[] en = {"Rat","Ox","Tiger","Cat","Dragon","Snake","Horse","Goat","Monkey","Rooster","Dog","Pig"};
        int idx = Math.floorMod(year - 4, 12);
        return en[idx];
    }

    private void goHome() {
        startActivity(new Intent(this, DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
