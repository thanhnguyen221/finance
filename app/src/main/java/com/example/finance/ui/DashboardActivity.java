package com.example.finance.ui;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;
import com.example.finance.util.DateUtils;
import com.example.finance.util.FormatUtils;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

public class DashboardActivity extends AppCompatActivity {
    private static final String CH_BUDGET = "budget_alerts";
    private static final int REQ_POST_NOTIF = 9001;

    private DatabaseHelper db;
    private SessionManager session;

    private ImageView ivAvatar;

    private TextView tvGreeting, tvMonthPlanned, tvMonthSpent, tvMonthRemain, tvMonthProgress, tvTodayIncome, tvTodayExpense;
    private TextView tvRangeInfo, tvChartSubtitle, tvChartEmpty;

    // Today list
    private TextView tvTodayHeader, tvTodayEmpty;
    private ViewGroup todayListContainer;

    private ProgressBar pb;
    private RadioGroup rgRange;
    private PieChart pie;

    // Dock zoom
    private View dockContent;
    private android.widget.HorizontalScrollView dockScroll;
    private ScaleGestureDetector scaleDetector;
    private float dockScale = 1f;

    // FABs
    private FloatingActionButton btnBudget, btnExpenses, btnIncomes, btnRecurring, btnCategories, btnStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        int uid = session.getUserId();
        if (uid <= 0) { finish(); return; }

        // Materialize recurring tới hôm nay
        db.processRecurringIncomes(uid, LocalDate.now());

        // Bind views
        ivAvatar        = findViewById(R.id.ivAvatar);
        tvGreeting      = findViewById(R.id.tvGreeting);
        tvMonthPlanned  = findViewById(R.id.tvMonthPlanned);
        tvMonthSpent    = findViewById(R.id.tvMonthSpent);
        tvMonthRemain   = findViewById(R.id.tvMonthRemain);
        tvMonthProgress = findViewById(R.id.tvMonthProgress);
        tvTodayIncome   = findViewById(R.id.tvTodayIncome);
        tvTodayExpense  = findViewById(R.id.tvTodayExpense);
        tvRangeInfo     = findViewById(R.id.tvRangeInfo);
        tvChartSubtitle = findViewById(R.id.tvChartSubtitle);
        tvChartEmpty    = findViewById(R.id.tvChartEmpty);
        pb              = findViewById(R.id.pbMonthProgress);
        rgRange         = findViewById(R.id.rgBudgetRange);
        pie             = findViewById(R.id.pieBudget);

        tvTodayHeader       = findViewById(R.id.tvTodayHeader);
        todayListContainer  = findViewById(R.id.todayListContainer);
        tvTodayEmpty        = findViewById(R.id.tvTodayEmpty);

        dockScroll  = findViewById(R.id.dockScroll);
        dockContent = findViewById(R.id.dockContent);

        btnBudget     = findViewById(R.id.btnBudget);
        btnExpenses   = findViewById(R.id.btnExpenses);
        btnIncomes    = findViewById(R.id.btnIncomes);
        btnRecurring  = findViewById(R.id.btnRecurring);
        btnCategories = findViewById(R.id.btnCategories);
        btnStats      = findViewById(R.id.btnStats);

        // Điều hướng chính
        btnBudget.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        btnStats.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
        btnCategories.setOnClickListener(v -> startActivity(new Intent(this, CategoryActivity.class)));
        btnRecurring.setOnClickListener(v -> startActivity(new Intent(this, RecurringIncomeActivity.class)));

        // Chi / Thu mở chooser (Danh sách / Thêm nhanh / Quét hoá đơn)
        btnExpenses.setOnClickListener(v -> showActionsDialog("EXPENSE"));
        btnIncomes.setOnClickListener(v -> showActionsDialog("INCOME"));

        // Greeting + avatar
        if (tvGreeting != null) {
            String name = "";
            try {
                Object val = SessionManager.class.getMethod("getUsername").invoke(session);
                if (val != null) name = val.toString();
            } catch (Exception ignored) {}
            tvGreeting.setText(getString(R.string.greeting_hello, name));
        }
        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
            loadAvatarIfAny();
        }

        // Pie config
        if (pie != null) {
            pie.getDescription().setEnabled(false);
            pie.setUsePercentValues(true);
            pie.setDrawHoleEnabled(true);
            pie.setHoleRadius(45f);
            pie.setTransparentCircleRadius(50f);
            Legend l = pie.getLegend();
            l.setEnabled(true);
            l.setWordWrapEnabled(true);
        }

        // Notif channel + quyền
        createNotificationChannel();
        ensurePostNotifPermission();

        // Range mặc định + listener
        if (rgRange != null && rgRange.getCheckedRadioButtonId() == -1) rgRange.check(R.id.rbMonth);
        if (rgRange != null) rgRange.setOnCheckedChangeListener((g, id) -> refresh());

        // Pinch-to-zoom cho dock
        setupDockZoom();

        refresh();
    }

    private void showActionsDialog(String type) {
        // 3 lựa chọn đơn giản, không đụng string resources của bạn
        String title = "EXPENSE".equals(type) ? "Chi" : "Thu";
        CharSequence[] items = new CharSequence[]{"Danh sách", "Thêm nhanh", "Quét hoá đơn"};
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        if ("EXPENSE".equals(type)) {
                            ExpenseListActivity.launch(this, "EXPENSE");
                        } else {
                            IncomeListActivity.launch(this);
                        }
                    } else if (which == 1) {
                        // Sẽ tạo file QuickInputActivity ở bước sau
                        Intent i = new Intent(this, QuickInputActivity.class);
                        i.putExtra("type", type);
                        startActivity(i);
                    } else if (which == 2) {
                        // Sẽ tạo file ReceiptOcrActivity ở bước sau
                        Intent i = new Intent(this, ReceiptOcrActivity.class);
                        i.putExtra("type", type);
                        startActivity(i);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override protected void onResume() {
        super.onResume();
        loadAvatarIfAny();
        refresh();
    }

    // ===== Pinch zoom cho dock =====
    private void setupDockZoom() {
        if (dockScroll == null || dockContent == null) return;
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                dockScale = Math.max(0.85f, Math.min(1.30f, dockScale * detector.getScaleFactor()));
                dockContent.setScaleX(dockScale);
                dockContent.setScaleY(dockScale);
                return true;
            }
        });
        dockScroll.setOnTouchListener((v, e) -> { scaleDetector.onTouchEvent(e); return false; });
    }

    private void refresh() {
        int uid = session.getUserId();
        if (uid <= 0) return;

        LocalDate today = LocalDate.now();
        LocalDate start, end;

        int checked = (rgRange != null) ? rgRange.getCheckedRadioButtonId() : R.id.rbMonth;
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

        if (tvRangeInfo != null) tvRangeInfo.setText(makeRangeInfo(checked, start, end));

        String s = DateUtils.fmt(start), e = DateUtils.fmt(end);
        int planned = db.getBudgetForRange(uid, s, e);
        int spent   = db.sumByTypeInRange(uid, "EXPENSE", s, e);
        int remain  = planned - spent;

        int progress = (planned > 0) ? (int)Math.round(Math.max(0, Math.min(100, (spent * 100.0) / planned))) : 0;

        tvMonthPlanned.setText(FormatUtils.money(planned));
        tvMonthSpent.setText(FormatUtils.money(spent));
        tvMonthRemain.setText(FormatUtils.money(remain));
        tvMonthProgress.setText(progress + "%");
        if (pb != null) pb.setProgress(Math.min(progress, 100));

        String t = DateUtils.fmt(today);
        int todayIncome  = db.sumByTypeInRange(uid, "INCOME", t, t);
        int todayExpense = db.sumByTypeInRange(uid, "EXPENSE", t, t);
        tvTodayIncome.setText(FormatUtils.money(todayIncome));
        tvTodayExpense.setText(FormatUtils.money(todayExpense));

        if (tvChartSubtitle != null) tvChartSubtitle.setText(tvRangeInfo != null ? tvRangeInfo.getText() : "");
        boolean overspent = planned > 0 && spent > planned;
        bindPie(planned, spent, progress, overspent);
        if (overspent) {
            int over = spent - planned;
            sendBudgetOverNotification(tvChartSubtitle != null ? tvChartSubtitle.getText().toString() : "", planned, spent, over);
        }

        if (tvTodayHeader != null) tvTodayHeader.setText(getString(R.string.section_today_with_date, t));
        loadTodayActivities(uid, t);
    }

    private void bindPie(int planned, int spent, int progress, boolean overspent) {
        if (pie == null) return;
        if (planned <= 0) {
            pie.clear();
            if (tvChartEmpty != null) tvChartEmpty.setVisibility(View.VISIBLE);
            pie.setVisibility(View.GONE);
            return;
        }
        ArrayList<PieEntry> entries = new ArrayList<>();
        PieDataSet dataSet;

        if (!overspent) {
            int remain = Math.max(planned - spent, 0);
            entries.add(new PieEntry(spent, getString(R.string.label_month_spent)));
            entries.add(new PieEntry(remain, getString(R.string.label_month_remain)));
            dataSet = new PieDataSet(entries, "");
            dataSet.setColors(
                    ContextCompat.getColor(this, R.color.expense_red),
                    ContextCompat.getColor(this, R.color.primary_green)
            );
            pie.setCenterText(getString(R.string.label_month_progress) + " " + progress + "%");
            pie.setCenterTextColor(ContextCompat.getColor(this, R.color.text_primary));
        } else {
            int inLimit = planned;
            int over    = spent - planned;
            entries.add(new PieEntry(inLimit, getString(R.string.label_spent_in_limit)));
            entries.add(new PieEntry(over, getString(R.string.label_over_budget)));
            dataSet = new PieDataSet(entries, "");
            dataSet.setColors(
                    ContextCompat.getColor(this, R.color.primary_green),
                    ContextCompat.getColor(this, R.color.expense_red)
            );
            pie.setCenterText(getString(R.string.center_over_budget, FormatUtils.money(over)));
            pie.setCenterTextColor(ContextCompat.getColor(this, R.color.expense_red));
        }

        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(6f);

        PieData data = new PieData(dataSet);
        data.setDrawValues(true);
        data.setValueTextSize(12f);

        pie.setData(data);
        pie.highlightValues(null);
        pie.invalidate();

        if (tvChartEmpty != null) tvChartEmpty.setVisibility(View.GONE);
        pie.setVisibility(View.VISIBLE);
    }

    private String makeRangeInfo(int checkedId, LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        String startStr = DateUtils.fmt(start);
        String endStr   = DateUtils.fmt(end);
        if (checkedId == R.id.rbDay)   return getString(R.string.range_day, startStr);
        if (checkedId == R.id.rbWeek)  return getString(R.string.range_week, startStr, endStr, days);
        if (checkedId == R.id.rbYear)  return getString(R.string.range_year, start.getYear(), "", days, startStr, endStr);
        int n = start.lengthOfMonth();
        return getString(R.string.range_month, start.getMonthValue(), start.getYear(), n, startStr, endStr);
    }

    // ===== Today activities =====
    private static class TxnRow {
        final String type, category, note; final int amount;
        TxnRow(String type, String category, String note, int amount) {
            this.type = type; this.category = category; this.note = note == null ? "" : note; this.amount = amount;
        }
    }

    private void loadTodayActivities(int uid, String day) {
        if (todayListContainer == null || tvTodayEmpty == null) return;

        ArrayList<TxnRow> list = new ArrayList<>();
        try (Cursor ci = db.listTransactions(uid, "INCOME", day, day)) {
            while (ci.moveToNext()) list.add(new TxnRow("INCOME", ci.getString(4), ci.getString(3), ci.getInt(1)));
        }
        try (Cursor ce = db.listTransactions(uid, "EXPENSE", day, day)) {
            while (ce.moveToNext()) list.add(new TxnRow("EXPENSE", ce.getString(4), ce.getString(3), ce.getInt(1)));
        }

        list.sort((a, b) -> a.type.equals(b.type) ? 0 : ("EXPENSE".equals(a.type) ? -1 : 1));

        todayListContainer.removeAllViews();
        if (list.isEmpty()) { tvTodayEmpty.setVisibility(View.VISIBLE); return; }
        tvTodayEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (TxnRow t : list) {
            View row = inflater.inflate(R.layout.item_txn_row, todayListContainer, false);
            View dot = row.findViewById(R.id.dot);
            TextView tvCat = row.findViewById(R.id.tvCat);
            TextView tvNote = row.findViewById(R.id.tvNote);
            TextView tvAmount = row.findViewById(R.id.tvAmount);

            tvCat.setText(t.category);
            tvNote.setText(t.note.isEmpty() ? getString(R.string.no_note) : t.note);

            if ("INCOME".equals(t.type)) {
                tvAmount.setText("+" + FormatUtils.money(t.amount));
                int green = ContextCompat.getColor(this, R.color.income_green);
                tvAmount.setTextColor(green);
                if (dot != null) dot.setBackgroundColor(green);
            } else {
                tvAmount.setText("-" + FormatUtils.money(t.amount));
                int red = ContextCompat.getColor(this, R.color.expense_red);
                tvAmount.setTextColor(red);
                if (dot != null) dot.setBackgroundColor(red);
            }
            todayListContainer.addView(row);
        }
    }

    // ===== Notifications =====
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_budget_name);
            String desc = getString(R.string.channel_budget_desc);
            NotificationChannel channel = new NotificationChannel(CH_BUDGET, name, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(desc);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void ensurePostNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIF);
        }
    }

    private void sendBudgetOverNotification(String subtitle, int planned, int spent, int over) {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        String body = getString(
                R.string.notif_over_body,
                FormatUtils.money(spent),
                FormatUtils.money(planned),
                FormatUtils.money(over)
        );
        if (subtitle != null && !subtitle.isEmpty()) {
            body = body + " • " + getString(R.string.notif_over_range_suffix, subtitle);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CH_BUDGET)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notif_over_title))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(("over:" + subtitle).hashCode(), builder.build());
    }

    // ===== Avatar helpers =====
    private void loadAvatarIfAny() {
        if (ivAvatar == null) return;
        String p = db.getUserAvatar(session.getUserId());
        if (p != null && !p.isEmpty()) applyAvatar(p);
        else ivAvatar.setImageResource(R.drawable.ic_user_avatar_default);
    }

    private void applyAvatar(String path) {
        if (ivAvatar == null) return;
        File f = new File(path);
        if (f.exists()) ivAvatar.setImageURI(Uri.fromFile(f));
        else ivAvatar.setImageResource(R.drawable.ic_user_avatar_default);
    }

    private String copyAvatarToInternal(Uri src) {
        try (InputStream in = getContentResolver().openInputStream(src)) {
            if (in == null) return null;
            File dir = new File(getFilesDir(), "avatars");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "avatar_" + session.getUserId() + ".jpg");
            try (OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            return out.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
