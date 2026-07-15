package com.example.finance.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;
import com.example.finance.util.DateUtils;
import com.example.finance.util.FormatUtils;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import android.database.Cursor;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.time.LocalDate;
import java.util.*;
import java.util.Locale;

public class StatsActivity extends Activity {
    private DatabaseHelper db;
    private SessionManager session;

    // ChipGroup thay cho RadioGroup
    private ChipGroup cgRange;
    private LinearLayout boxCustom;
    private Button btnStart, btnEnd;
    private FloatingActionButton fabBack; // FAB về trang chính
    private CheckBox cbUseIncomeBaseline;

    private TextView tvRangeInfo;
    private TextView tvPlanned, tvInc, tvExp, tvBaseline, tvRemainPlan, tvRemainInc, tvUsagePlan, tvUsageInc, tvBal;
    private BarChart chart;

    private RecyclerView rvActivity;
    private TextView tvRangeEmpty;
    private RangeAdapter rangeAdapter;

    private LocalDate customStart, customEnd;

    static class NoScrollLinearLayoutManager extends LinearLayoutManager {
        NoScrollLinearLayoutManager(Activity a) {
            super(a, VERTICAL, false);
            setAutoMeasureEnabled(true);
        }
        @Override public boolean canScrollVertically() { return false; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        cgRange      = findViewById(R.id.cgRange);
        boxCustom    = findViewById(R.id.boxCustom);
        btnStart     = findViewById(R.id.btnStart);
        btnEnd       = findViewById(R.id.btnEnd);
        fabBack      = findViewById(R.id.fabBackHome); // FAB mới
        cbUseIncomeBaseline = findViewById(R.id.cbUseIncomeBaseline);

        tvRangeInfo  = findViewById(R.id.tvRangeInfo);
        tvPlanned    = findViewById(R.id.tvPlanned);
        tvInc        = findViewById(R.id.tvIncomeTotal);
        tvExp        = findViewById(R.id.tvExpenseTotal);
        tvBal        = findViewById(R.id.tvBalance);
        tvBaseline   = findViewById(R.id.tvBaseline);
        tvRemainPlan = findViewById(R.id.tvRemainPlanned);
        tvRemainInc  = findViewById(R.id.tvRemainIncome);
        tvUsagePlan  = findViewById(R.id.tvUsagePlanned);
        tvUsageInc   = findViewById(R.id.tvUsageIncome);
        chart        = findViewById(R.id.barChart);

        rvActivity   = findViewById(R.id.rvActivity);
        tvRangeEmpty = findViewById(R.id.tvRangeEmpty);

        rvActivity.setLayoutManager(new NoScrollLinearLayoutManager(this));
        rvActivity.setNestedScrollingEnabled(false);
        rvActivity.setHasFixedSize(false);
        rvActivity.setItemAnimator(null);

        rangeAdapter = new RangeAdapter();
        rvActivity.setAdapter(rangeAdapter);

        // FAB về trang chính
        fabBack.setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });

        // init custom range = tháng hiện tại
        LocalDate today = LocalDate.now();
        customStart = DateUtils.firstDayOfMonth(today);
        customEnd   = DateUtils.lastDayOfMonth(today);
        updateCustomButtons();

        btnStart.setOnClickListener(v -> pickDate(true));
        btnEnd.setOnClickListener(v -> pickDate(false));

        // chọn mặc định: Month
        cgRange.check(R.id.chipMonth);
        cgRange.setSingleSelection(true);
        cgRange.setOnCheckedStateChangeListener((group, checkedIds) -> {
            boolean showCustom = checkedIds.contains(R.id.chipCustom);
            boxCustom.setVisibility(showCustom ? View.VISIBLE : View.GONE);
            refresh();
        });

        cbUseIncomeBaseline.setOnCheckedChangeListener((buttonView, isChecked) -> refresh());

        setupChart();
        refresh();
    }

    private void pickDate(boolean isStart) {
        LocalDate d = isStart ? customStart : customEnd;
        DatePickerDialog dlg = new DatePickerDialog(
                this,
                (view, y, m, day) -> {
                    LocalDate picked = LocalDate.of(y, m + 1, day);
                    if (isStart) {
                        customStart = picked;
                        if (customEnd.isBefore(customStart)) customEnd = customStart;
                    } else {
                        customEnd = picked;
                        if (customStart.isAfter(customEnd)) customStart = customEnd;
                    }
                    updateCustomButtons();
                    refresh();
                },
                d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()
        );
        dlg.show();
    }

    private void updateCustomButtons() {
        btnStart.setText(getString(R.string.pick_start) + ": " + DateUtils.fmt(customStart));
        btnEnd.setText(getString(R.string.pick_end) + ": " + DateUtils.fmt(customEnd));
    }

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);

        // Trục X: nhãn từ strings
        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setGranularity(1f);
        x.setValueFormatter(new ValueFormatter() {
            @Override public String getAxisLabel(float value, AxisBase axis) {
                if (value == 0f) return getString(R.string.chart_label_income);   // "Thu"
                if (value == 1f) return getString(R.string.chart_label_expense);  // "Chi"
                return "";
            }
        });

        // Trục Y trái: grid dashed ngang
        chart.getAxisRight().setEnabled(false);
        com.github.mikephil.charting.components.YAxis y = chart.getAxisLeft();
        y.setAxisMinimum(0f);
        y.setDrawGridLines(true);
        y.enableGridDashedLine(8f, 8f, 0f);
        y.setGridColor(ContextCompat.getColor(this, R.color.chart_grid));
        y.setGridLineWidth(1f);

        chart.setExtraOffsets(8, 8, 8, 8);
        chart.setDrawGridBackground(false);
        chart.setDrawBarShadow(false);
        chart.setFitBars(true);
    }

    private int getCheckedRangeId() {
        List<Integer> ids = cgRange.getCheckedChipIds();
        return ids.isEmpty() ? R.id.chipMonth : ids.get(0);
    }

    private void refresh() {
        int uid = session.getUserId();
        if (uid <= 0) return;

        LocalDate today = LocalDate.now();
        LocalDate start, end;

        int checked = getCheckedRangeId();
        if (checked == R.id.chipDay) {
            start = today; end = today;
        } else if (checked == R.id.chipWeek) {
            start = DateUtils.mondayOfWeek(today); end = DateUtils.sundayOfWeek(today);
        } else if (checked == R.id.chipYear) {
            start = LocalDate.of(today.getYear(), 1, 1);
            end   = LocalDate.of(today.getYear(), 12, 31);
        } else if (checked == R.id.chipCustom) {
            start = customStart; end = customEnd;
        } else { // Month (default)
            start = DateUtils.firstDayOfMonth(today);
            end   = DateUtils.lastDayOfMonth(today);
        }

        String s = DateUtils.fmt(start), e = DateUtils.fmt(end);

        int planned = db.getBudgetForRange(uid, s, e);
        int income  = db.sumByTypeInRange(uid, "INCOME", s, e);
        int expense = db.sumByTypeInRange(uid, "EXPENSE", s, e);
        int balance = income - expense;

        boolean useIncomeBaseline = cbUseIncomeBaseline.isChecked();
        int baseline = useIncomeBaseline ? Math.max(planned, income) : planned;

        int remainVsPlanned = planned - expense;
        int remainVsIncome  = income  - expense;

        double usageVsPlanned = planned > 0 ? (expense * 100.0) / planned : 0.0;
        double usageVsIncome  = income  > 0 ? (expense * 100.0) / income  : 0.0;

        tvRangeInfo.setText(makeRangeInfo(checked, start, end));
        tvPlanned.setText(FormatUtils.money(planned));
        tvInc.setText(FormatUtils.money(income));
        tvExp.setText(FormatUtils.money(expense));
        tvBal.setText(FormatUtils.money(balance));
        tvBaseline.setText(FormatUtils.money(baseline));
        tvRemainPlan.setText(FormatUtils.money(remainVsPlanned));
        tvRemainInc.setText(FormatUtils.money(remainVsIncome));
        tvUsagePlan.setText(String.format(Locale.getDefault(), "%.1f%%", clampPercent(usageVsPlanned)));
        tvUsageInc.setText(String.format(Locale.getDefault(), "%.1f%%", clampPercent(usageVsIncome)));

        // Biểu đồ: 2 cột Thu/Chi
        ArrayList<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, income));   // Thu
        entries.add(new BarEntry(1f, expense));  // Chi

        BarDataSet set = new BarDataSet(entries, getString(R.string.chart_title_income_expense));
        set.setColors(
                ContextCompat.getColor(this, R.color.income_green), // Thu = xanh
                ContextCompat.getColor(this, R.color.expense_red)   // Chi = đỏ
        );
        set.setValueTextColor(Color.DKGRAY);
        set.setValueTextSize(12f);

        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        chart.setData(data);
        chart.animateY(500);
        chart.invalidate();

        loadRangeActivities(uid, s, e);
    }

    private String makeRangeInfo(int checkedId, LocalDate start, LocalDate end) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        String s = DateUtils.fmt(start), e = DateUtils.fmt(end);
        if (checkedId == R.id.chipDay)    return getString(R.string.range_day, s);
        if (checkedId == R.id.chipWeek)   return getString(R.string.range_week, s, e, days);
        if (checkedId == R.id.chipYear)   return getString(R.string.range_year, start.getYear(), "", days, s, e);
        if (checkedId == R.id.chipCustom) return getString(R.string.range_week, s, e, days);
        return getString(R.string.range_month, start.getMonthValue(), start.getYear(), start.lengthOfMonth(), s, e);
    }

    private double clampPercent(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        if (v < 0) return 0.0;
        if (v > 1000) return 1000;
        return v;
    }

    private void loadRangeActivities(int uid, String startDate, String endDate) {
        List<Txn> incomes  = readTxns(uid, "INCOME",  startDate, endDate);
        List<Txn> expenses = readTxns(uid, "EXPENSE", startDate, endDate);

        Map<String, List<Txn>> byDate = new HashMap<>();
        for (Txn t : incomes)  byDate.computeIfAbsent(t.date, k -> new ArrayList<>()).add(t);
        for (Txn t : expenses) byDate.computeIfAbsent(t.date, k -> new ArrayList<>()).add(t);

        List<String> dates = new ArrayList<>(byDate.keySet());
        Collections.sort(dates, Collections.reverseOrder());

        List<RangeItem> items = new ArrayList<>();
        for (String d : dates) {
            items.add(RangeItem.header(d));
            List<Txn> list = byDate.get(d);
            Collections.sort(list, (a, b) -> {
                if (a.type.equals(b.type)) return 0;
                return "EXPENSE".equals(a.type) ? -1 : 1;
            });
            for (Txn t : list) items.add(RangeItem.row(t));
        }

        rangeAdapter.setItems(items);

        if (items.isEmpty()) {
            tvRangeEmpty.setVisibility(View.VISIBLE);
            rvActivity.setVisibility(View.GONE);
        } else {
            tvRangeEmpty.setVisibility(View.GONE);
            rvActivity.setVisibility(View.VISIBLE);
        }
    }

    private List<Txn> readTxns(int uid, String type, String s, String e) {
        List<Txn> out = new ArrayList<>();
        try (Cursor c = db.listTransactions(uid, type, s, e)) {
            while (c.moveToNext()) {
                int amount   = c.getInt(1);
                String date  = c.getString(2);
                String note  = c.getString(3);
                String cat   = c.getString(4);
                out.add(new Txn(type, date, cat, amount, note));
            }
        }
        return out;
    }

    static class Txn {
        final String type; // INCOME/EXPENSE
        final String date; // yyyy-MM-dd
        final String category;
        final int amount;
        final String note;
        Txn(String type, String date, String category, int amount, String note) {
            this.type = type; this.date = date; this.category = category; this.amount = amount;
            this.note = note == null ? "" : note;
        }
    }

    static class RangeItem {
        static final int HEADER = 0, ROW = 1;
        final int kind;
        final String date;
        final Txn txn;
        private RangeItem(int kind, String date, Txn txn) { this.kind = kind; this.date = date; this.txn = txn; }
        static RangeItem header(String date) { return new RangeItem(HEADER, date, null); }
        static RangeItem row(Txn t) { return new RangeItem(ROW, null, t); }
    }

    static class RangeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<RangeItem> items = new ArrayList<>();
        void setItems(List<RangeItem> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }
        @Override public int getItemViewType(int position) { return items.get(position).kind; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == RangeItem.HEADER) {
                View v = inflater.inflate(R.layout.item_txn_header, parent, false);
                return new VHHeader(v);
            } else {
                View v = inflater.inflate(R.layout.item_txn_row, parent, false);
                return new VHRow(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RangeItem it = items.get(position);
            if (holder instanceof VHHeader) {
                ((VHHeader) holder).tv.setText(it.date);
            } else if (holder instanceof VHRow) {
                Txn t = it.txn;
                VHRow h = (VHRow) holder;
                h.tvCat.setText(t.category);
                h.tvNote.setText(t.note.isEmpty() ? h.itemView.getContext().getString(R.string.no_note) : t.note);

                // Thu = xanh, Chi = đỏ
                if ("INCOME".equals(t.type)) {
                    h.tvAmount.setText("+" + FormatUtils.money(t.amount));
                    h.tvAmount.setTextColor(0xFF2E7D32);
                    h.dot.setBackgroundColor(0xFF2E7D32);
                } else {
                    h.tvAmount.setText("-" + FormatUtils.money(t.amount));
                    h.tvAmount.setTextColor(0xFFC62828);
                    h.dot.setBackgroundColor(0xFFC62828);
                }
            }
        }

        @Override public int getItemCount() { return items.size(); }

        static class VHHeader extends RecyclerView.ViewHolder {
            final TextView tv;
            VHHeader(@NonNull View v) { super(v); tv = v.findViewById(R.id.tvHeader); }
        }
        static class VHRow extends RecyclerView.ViewHolder {
            final View dot; final TextView tvCat, tvNote, tvAmount;
            VHRow(@NonNull View v) {
                super(v);
                dot = v.findViewById(R.id.dot);
                tvCat = v.findViewById(R.id.tvCat);
                tvNote = v.findViewById(R.id.tvNote);
                tvAmount = v.findViewById(R.id.tvAmount);
            }
        }
    }
}
