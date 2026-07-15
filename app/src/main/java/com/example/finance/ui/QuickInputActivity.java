package com.example.finance.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;
import com.example.finance.util.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuickInputActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private int userId;

    private EditText etInput, etAmount, etDate, etMerchant;
    private AutoCompleteTextView etCategory;
    private RadioGroup rgType;
    private TextView tvHint;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_input);

        db = new DatabaseHelper(this);
        userId = new SessionManager(this).getUserId();
        if (userId <= 0) { finish(); return; }

        etInput    = findViewById(R.id.etInput);
        etAmount   = findViewById(R.id.etAmount);
        etDate     = findViewById(R.id.etDate);
        etMerchant = findViewById(R.id.etMerchant);
        etCategory = findViewById(R.id.etCategory);
        rgType     = findViewById(R.id.rgType);
        tvHint     = findViewById(R.id.tvHint);

        // default
        etDate.setText(DateUtils.fmt(LocalDate.now()));
        rgType.check(R.id.rbExpense);


        bindCategorySuggestions();

        Button btnParse = findViewById(R.id.btnParse);
        Button btnSave  = findViewById(R.id.btnSave);

        String initType = getIntent().getStringExtra("type");
        if ("INCOME".equals(initType)) rgType.check(R.id.rbIncome);

        btnParse.setOnClickListener(v -> parseAndFill());
        btnSave.setOnClickListener(v -> save());

        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { tvHint.setText(""); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    //tai dnah muc tu csdl
    private void bindCategorySuggestions() {
        ArrayList<String> catNames = new ArrayList<>();
        try (android.database.Cursor c1 = db.getCategories(userId, "EXPENSE")) {
            while (c1.moveToNext()) catNames.add(c1.getString(1));
        }
        try (android.database.Cursor c2 = db.getCategories(userId, "INCOME")) {
            while (c2.moveToNext()) catNames.add(c2.getString(1));
        }
        ArrayAdapter<String> adp = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, catNames);
        etCategory.setAdapter(adp);
        etCategory.setThreshold(1);
    }

    //dien thong tin dien vao text
    private void parseAndFill() {
        String text = etInput.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.quick_input_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        Parsed r = Parsed.fromText(text);
        if (r.amount != null) etAmount.setText(String.valueOf(r.amount));
        if (r.dateIso != null) etDate.setText(r.dateIso);
        if (r.merchant != null) etMerchant.setText(r.merchant);


        if (r.isIncome != null) {
            if (r.isIncome) rgType.check(R.id.rbIncome);
            else rgType.check(R.id.rbExpense);
        }

        // gợi ý danh mục từ text với list user
        if (etCategory.getAdapter() != null) {
            String best = bestCategory(normalize(text), getAllSuggestions());
            if (best != null) etCategory.setText(best, false);
        }

        tvHint.setText(getString(R.string.quick_input_parsed));
    }

    private void save() {
        String amountStr = etAmount.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        String merchant = etMerchant.getText().toString().trim();
        String catName = etCategory.getText().toString().trim();
        String type = (rgType.getCheckedRadioButtonId() == R.id.rbIncome) ? "INCOME" : "EXPENSE";
        String rawInput = etInput.getText().toString();

        if (amountStr.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, getString(R.string.need_amount_date), Toast.LENGTH_SHORT).show();
            return;
        }
        int amount = Integer.parseInt(amountStr);

        int catId = ensureCategory(userId, catName, type);
        long id = db.insertTransaction(userId, catId, type, amount, date, merchant);

        // enrich transaction
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put("merchant", merchant.isEmpty() ? null : merchant);
        cv.put("input_text", rawInput);
        cv.put("auto_source", "NLP");
        cv.put("auto_conf", 0.8f);
        db.getWritableDatabase().update("transactions", cv, "id=?", new String[]{ String.valueOf(id) });

        Toast.makeText(this, getString(R.string.quick_input_saved_with_id, id), Toast.LENGTH_LONG).show();
        finish();
    }

    //ql
    private int ensureCategory(int userId, String name, String type) {
        if (name == null || name.trim().isEmpty()) {
            int any = findAnyCategoryId(userId, type);
            if (any > 0) return any;
            long newId = db.insertCategory(userId, type.equals("INCOME") ? getString(R.string.title_incomes) : getString(R.string.title_transactions), type, false);
            return (int)newId;
        }
        int found = findCategoryId(userId, name, type);
        if (found != -1) return found;
        long newId = db.insertCategory(userId, name, type, false);
        return (int)newId;
    }

    //ql
    private int findCategoryId(int userId, String name, String type) {
        try (android.database.Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id FROM categories WHERE user_id=? AND type=? AND name=? LIMIT 1",
                new String[]{String.valueOf(userId), type, name})) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return -1;
    }

    //ql
    private int findAnyCategoryId(int userId, String type) {
        try (android.database.Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id FROM categories WHERE user_id=? AND type=? ORDER BY is_default DESC, id LIMIT 1",
                new String[]{String.valueOf(userId), type})) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return -1;
    }

    // ===== Helpers: simple VN parser =====
    private static class Parsed {
        Integer amount;
        String dateIso;
        String merchant;
        Boolean isIncome;

        static final Pattern MONEY = Pattern.compile("(?i)(^|\\s)([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+(?:[.,][0-9]+)?)\\s*(k|nghìn|ngan|ngàn|tr|trieu|triệu|vnd|vnđ|đ)?\\b");
        static final Pattern DATE_1 = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b");
        static final Pattern TODAY = Pattern.compile("(?i)\\b(hom nay|hôm nay|today)\\b");
        static final Pattern YESTERDAY = Pattern.compile("(?i)\\b(hom qua|hôm qua|yesterday)\\b");
        static final Pattern AT_MERCHANT = Pattern.compile("(?i)(?:\\b(tai|tại|o|ở|@)\\s+)([\\u00C0-\\u1EF9A-Za-z0-9 .,'-]{2,})");

        static Parsed fromText(String input) {
            Parsed r = new Parsed();
            if (input == null) return r;
            String text = input.trim();

            // amount
            Integer amount = null; double best = -1;
            Matcher m = MONEY.matcher(text);
            while (m.find()) {
                String num = m.group(2);
                String unit = m.group(3);
                double v = parseNumber(num);
                if (unit != null) {
                    String u = normalize(unit);
                    if (u.startsWith("k") || u.startsWith("ngh") || u.startsWith("ngan") || u.startsWith("ngàn")) v *= 1000;
                    else if (u.startsWith("tr")) v *= 1_000_000;
                }
                if (v > best) { best = v; amount = (int)Math.round(v); }
            }
            r.amount = amount;

            // date
            r.dateIso = extractDateIso(text);

            // merchant
            Matcher mm = AT_MERCHANT.matcher(text);
            if (mm.find()) {
                String name = mm.group(2).trim();
                name = name.replaceAll("[|\\-–].*$", "").trim();
                r.merchant = name;
            }

            String low = normalize(text);
            if (low.contains("thu ") || low.contains("thu nhap") || low.contains("thu nhập")
                    || low.contains("nhan duoc") || low.contains("nhận được") || low.contains("refund")) {
                r.isIncome = true;
            } else if (low.contains("chi ") || low.contains("mua ") || low.contains("tra ") || low.contains("trả ")) {
                r.isIncome = false;
            }
            return r;
        }

        private static String extractDateIso(String text) {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (TODAY.matcher(text).find()) return today.toString();
            if (YESTERDAY.matcher(text).find()) return today.minusDays(1).toString();
            Matcher m = DATE_1.matcher(text);
            if (m.find()) {
                int d = Integer.parseInt(m.group(1));
                int mn = Integer.parseInt(m.group(2));
                int y = (m.group(3) == null) ? today.getYear() : parseYear(m.group(3));
                return String.format(Locale.US, "%04d-%02d-%02d", y, mn, d);
            }
            return today.toString();
        }

        private static int parseYear(String s) { int y = Integer.parseInt(s); return (y < 100) ? (2000 + y) : y; }
        private static double parseNumber(String s) {
            String clean = s.replace(".", "").replace(",", "");
            try { return Double.parseDouble(clean); }
            catch (Exception e) { try { return Double.parseDouble(s.replace(',', '.')); } catch (Exception ex) { return 0; } }
        }
        private static String normalize(String s) {
            String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            return n.toLowerCase(Locale.ROOT);
        }
    }

    private ArrayList<String> getAllSuggestions() {
        ArrayList<String> list = new ArrayList<>();
        ArrayAdapter<?> ad = (ArrayAdapter<?>) etCategory.getAdapter();
        if (ad != null) for (int i = 0; i < ad.getCount(); i++) list.add(ad.getItem(i).toString());
        return list;
    }

    //tìm danh mục phù hợp nhất
    private static String bestCategory(String normalizedText, ArrayList<String> catNames) {
        double bestScore = 0; String best = null;
        java.util.HashSet<String> tokens = new java.util.HashSet<>(java.util.Arrays.asList(normalize(normalizedText).split("\\s+")));
        for (String c : catNames) {
            String nc = normalize(c);
            java.util.HashSet<String> ctoks = new java.util.HashSet<>(java.util.Arrays.asList(nc.split("\\s+")));
            if (ctoks.isEmpty()) continue;
            int inter = 0; for (String t : ctoks) if (tokens.contains(t)) inter++;
            double score = inter / (double) ctoks.size();
            if (normalize(normalizedText).contains(nc)) score += 0.25;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    //phuong thuc chuan hoa chuỗi văn bản
    private static String normalize(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT);
    }
}
