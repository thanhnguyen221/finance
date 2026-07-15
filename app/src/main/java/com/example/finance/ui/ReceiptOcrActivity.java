package com.example.finance.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;
import com.example.finance.util.DateUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.TextRecognizer;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptOcrActivity extends AppCompatActivity {

    private PreviewView previewView;
    private Button btnCapture, btnRetake, btnSave;
    private ProgressBar progress;
    private TextView tvOcr, tvImgPath;
    private RadioGroup rgType;
    private EditText etAmount, etDate, etMerchant;
    private AutoCompleteTextView etCategory;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    private DatabaseHelper db;
    private int userId;

    private Uri savedImageUri;
    private Parsed parsed = new Parsed();

    /// Xử lý kết quả khi người dùng cấp (hoặc từ chối) quyền Camera
    private final ActivityResultLauncher<String> reqCam = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) startCamera();
                else {
                    Toast.makeText(this, getString(R.string.ocr_need_camera), Toast.LENGTH_LONG).show();
                    finish();
                }
            }
    );

    /// Hàm chính: Khởi tạo UI, DB, Session, Executor, và kiểm tra/xin quyền Camera
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_ocr);

        db = new DatabaseHelper(this);
        userId = new SessionManager(this).getUserId();
        if (userId <= 0) { finish(); return; }

        // Bind views
        previewView = findViewById(R.id.previewView);
        btnCapture  = findViewById(R.id.btnCapture);
        btnRetake   = findViewById(R.id.btnRetake);
        btnSave     = findViewById(R.id.btnSave);
        progress    = findViewById(R.id.progress);
        tvOcr       = findViewById(R.id.tvOcr);
        tvImgPath   = findViewById(R.id.tvImgPath);

        rgType      = findViewById(R.id.rgType);
        etAmount    = findViewById(R.id.etAmount);
        etDate      = findViewById(R.id.etDate);
        etMerchant  = findViewById(R.id.etMerchant);
        etCategory  = findViewById(R.id.etCategory);

        rgType.check(R.id.rbExpense);
        etDate.setText(DateUtils.fmt(LocalDate.now()));

        bindCategorySuggestions();

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Quyền
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            reqCam.launch(Manifest.permission.CAMERA);
        }

        // Clicks
        btnCapture.setOnClickListener(v -> capturePhoto());
        btnRetake.setOnClickListener(v -> resetCapture());
        btnSave.setOnClickListener(v -> saveAll());
    }

    /// Gọi khi quay lại app, đảm bảo camera xoay đúng hướng màn hình
    @Override protected void onResume() {
        super.onResume();
        if (imageCapture != null && previewView != null) {
            imageCapture.setTargetRotation(previewView.getDisplay().getRotation());
        }
    }
//// Gọi khi đóng màn hình, tắt dịch vụ camera (cameraExecutor) để tiết kiệm pin/RAM
    @Override protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }

    // ===== UI helpers =====
    /// Tải tất cả tên danh mục (Thu/Chi) từ DB gán vào ô `etCategory` để gợi ý
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
/// (Nút "Chụp Lại") - Xóa sạch dữ liệu trên form, reset trạng thái, và bật lại camera
    private void resetCapture() {
        savedImageUri = null;
        tvImgPath.setText("");
        tvOcr.setText("");
        etAmount.setText("");
        etMerchant.setText("");
        etCategory.setText("");
        etDate.setText(DateUtils.fmt(LocalDate.now()));
        btnSave.setEnabled(false);
        btnRetake.setVisibility(View.GONE);
        btnCapture.setEnabled(true);
        startCamera();
    }

    // ===== CameraX =====
    /// Khởi động CameraX, thiết lập `Preview` (hiển thị) và `ImageCapture` (chụp ảnh)
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> provider = ProcessCameraProvider.getInstance(this);
        provider.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = provider.get();

                Preview preview = new Preview.Builder()
                        .setTargetResolution(new Size(1080, 1920))
                        .build();

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);

                // cập nhật rotation theo màn hình hiện tại
                if (imageCapture != null && previewView != null) {
                    imageCapture.setTargetRotation(previewView.getDisplay().getRotation());
                }
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.ocr_camera_init_failed), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private File createImageFile() {
        File dir = new File(getFilesDir(), "receipts");
        if (!dir.exists()) dir.mkdirs();
        String name = "rcp_" + System.currentTimeMillis() + ".jpg";
        return new File(dir, name);
    }

    private void capturePhoto() {
        if (imageCapture == null) return;
        File file = createImageFile();
        ImageCapture.OutputFileOptions opts =
                new ImageCapture.OutputFileOptions.Builder(file).build();

        progress.setVisibility(View.VISIBLE);
        btnCapture.setEnabled(false);

        imageCapture.takePicture(opts, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    savedImageUri = Uri.fromFile(file);
                    tvImgPath.setText(file.getAbsolutePath());
                    btnRetake.setVisibility(View.VISIBLE);
                    analyzeImage(file);
                });
            }
            @Override public void onError(ImageCaptureException exception) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnCapture.setEnabled(true);
                    Toast.makeText(ReceiptOcrActivity.this, getString(R.string.ocr_capture_error, exception.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ===== OCR =====
    private void analyzeImage(File file) {
        try {
            InputImage image = InputImage.fromFilePath(this, Uri.fromFile(file));
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            progress.setVisibility(View.VISIBLE);
            recognizer.process(image)
                    .addOnSuccessListener(this::onOcrSuccess)
                    .addOnFailureListener(e -> {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(this, getString(R.string.ocr_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            progress.setVisibility(View.GONE);
            Toast.makeText(this, getString(R.string.ocr_image_read_failed), Toast.LENGTH_LONG).show();
        }
    }

    private void onOcrSuccess(Text text) {
        progress.setVisibility(View.GONE);


        StringBuilder sb = new StringBuilder();
        for (Text.TextBlock b : text.getTextBlocks()) {
            String line = b.getText();
            if (line != null) {
                sb.append(line).append("\n");
            }
        }
        String raw = sb.toString().trim();
        tvOcr.setText(raw);

        parsed = Parsed.fromReceipt(raw);

        if (parsed.amount != null) etAmount.setText(String.valueOf(parsed.amount));
        if (parsed.dateIso != null) etDate.setText(parsed.dateIso);
        if (parsed.merchant != null) etMerchant.setText(parsed.merchant);

        if (parsed.isIncome != null && parsed.isIncome) rgType.check(R.id.rbIncome);
        else rgType.check(R.id.rbExpense);

        // gợi ý danh mục theo merchant + toàn văn
        suggestCategory(parsed.merchant, raw);

        btnSave.setEnabled(true);
    }

    private void suggestCategory(@Nullable String merchant, String fullText) {
        ArrayList<String> cats = getAllCategoryNames();
        String base = (merchant != null && !merchant.trim().isEmpty())
                ? merchant + " " + fullText
                : fullText;
        String best = bestCategory(normalize(base), cats);
        if (best != null) etCategory.setText(best, false);
    }

    private ArrayList<String> getAllCategoryNames() {
        ArrayList<String> catNames = new ArrayList<>();
        try (android.database.Cursor c1 = db.getCategories(userId, "EXPENSE")) {
            while (c1.moveToNext()) catNames.add(c1.getString(1));
        }
        try (android.database.Cursor c2 = db.getCategories(userId, "INCOME")) {
            while (c2.moveToNext()) catNames.add(c2.getString(1));
        }
        return catNames;
    }

    // ===== Save =====
    private void saveAll() {
        if (savedImageUri == null) {
            Toast.makeText(this, getString(R.string.ocr_no_image), Toast.LENGTH_SHORT).show();
            return;
        }
        String amountStr = etAmount.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        String merchant = etMerchant.getText().toString().trim();
        String catName = etCategory.getText().toString().trim();
        String type = (rgType.getCheckedRadioButtonId() == R.id.rbIncome) ? "INCOME" : "EXPENSE";

        if (amountStr.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, getString(R.string.need_amount_date), Toast.LENGTH_SHORT).show();
            return;
        }
        int amount = Integer.parseInt(amountStr);

        // 1) Insert receipt
        android.content.ContentValues rv = new android.content.ContentValues();
        rv.put("user_id", userId);
        rv.put("image_uri", new File(savedImageUri.getPath()).getAbsolutePath());
        rv.put("ocr_text", tvOcr.getText().toString());
        rv.put("extracted_amount", amount);
        rv.put("extracted_date", date);
        rv.put("vendor", merchant.isEmpty() ? null : merchant);
        rv.put("currency", "VND");
        rv.put("status", "PARSED");
        long receiptId = db.getWritableDatabase().insert("receipts", null, rv);

        // 2) Ensure category + insert transaction
        int catId = ensureCategory(userId, catName, type);
        long txnId = db.insertTransaction(userId, catId, type, amount, date, merchant);

        // 3) Link_TXN metadata
        android.content.ContentValues tv = new android.content.ContentValues();
        tv.put("merchant", merchant.isEmpty() ? null : merchant);
        tv.put("input_text", "[OCR] " + (tvOcr.getText() == null ? "" : tvOcr.getText().toString()));
        tv.put("auto_source", "OCR");
        tv.put("auto_conf", parsed.confidence);
        tv.put("receipt_id", receiptId);
        db.getWritableDatabase().update("transactions", tv, "id=?", new String[]{ String.valueOf(txnId) });

        android.content.ContentValues rv2 = new android.content.ContentValues();
        rv2.put("status", "LINKED");
        rv2.put("linked_txn_id", txnId);
        db.getWritableDatabase().update("receipts", rv2, "id=?", new String[]{ String.valueOf(receiptId) });

        Toast.makeText(this, getString(R.string.ocr_saved_both_with_id, receiptId, txnId), Toast.LENGTH_LONG).show();
        finish();
    }

    private int ensureCategory(int userId, String name, String type) {
        if (name == null || name.trim().isEmpty()) {
            int any = findAnyCategoryId(userId, type);
            if (any > 0) return any;
            long newId = db.insertCategory(userId,
                    type.equals("INCOME") ? getString(R.string.title_incomes) : getString(R.string.title_transactions),
                    type, false);
            return (int)newId;
        }
        int found = findCategoryId(userId, name, type);
        if (found != -1) return found;
        long newId = db.insertCategory(userId, name, type, false);
        return (int)newId;
    }
    private int findCategoryId(int userId, String name, String type) {
        try (android.database.Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id FROM categories WHERE user_id=? AND type=? AND name=? LIMIT 1",
                new String[]{String.valueOf(userId), type, name})) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return -1;
    }
    private int findAnyCategoryId(int userId, String type) {
        try (android.database.Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id FROM categories WHERE user_id=? AND type=? ORDER BY is_default DESC, id LIMIT 1",
                new String[]{String.valueOf(userId), type})) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return -1;
    }

    // ===== Heuristics Parser =====
    private static class Parsed {
        Integer amount;
        String dateIso;
        String merchant;
        Boolean isIncome;
        float confidence = 0.80f; // populate when we hit keyword lines

        // Regex
        static final Pattern MONEY_NUM = Pattern.compile(
                "(?<!\\d)(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d+)(?:[.,](\\d{2}))?\\s*(₫|đ|vnd|vnđ)?",
                Pattern.CASE_INSENSITIVE);
        static final Pattern UNIT = Pattern.compile("(?i)\\b(k|nghin|nghìn|ngan|ngàn|tr|trieu|triệu|ty|tỷ)\\b");
        static final Pattern DATE_SLASH = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b");
        static final Pattern DATE_ISO = Pattern.compile("\\b(20\\d{2})-(\\d{1,2})-(\\d{1,2})\\b");
        static final Pattern DATE_TEXT = Pattern.compile("(?i)ng[aà]y\\s*(\\d{1,2})\\s*th[aá]ng\\s*(\\d{1,2})\\s*n[aă]m\\s*(\\d{2,4})");
        static final Pattern KEY_INCOME = Pattern.compile("(?i)(refund|ho[aà]n ti[eê]n|cashback|tr[aả] l[ạ]i)");
        static final Pattern KEY_AMOUNT = Pattern.compile("(?i)(t[oô]ng|c[ộo]ng|th[àa]nh ti[eê]n|total|amount|payable|ph[ả]i tr[ả])");
        static final Pattern PHONE_LIKE = Pattern.compile("\\b\\d{9,11}\\b"); // để loại số điện thoại/MST

        static Parsed fromReceipt(String text) {
            Parsed r = new Parsed();
            if (text == null) return r;

            String normAll = normalize(text);
            String[] lines = text.split("\\r?\\n");
            String[] nlines = normAll.split("\\r?\\n");

            // 1) Amount – ưu tiên dòng có KEY_AMOUNT
            int bestAmt = 0;
            double bestScore = -1;
            for (int i = 0; i < nlines.length; i++) {
                String ln = nlines[i];
                double score = KEY_AMOUNT.matcher(ln).find() ? 2.0 : 0.0;
                if (PHONE_LIKE.matcher(ln).find()) continue;

                Matcher m = MONEY_NUM.matcher(ln);
                while (m.find()) {
                    String num = m.group(1);
                    String cents = m.group(2);
                    String cur = m.group(3);
                    int v = (int)Math.round(parseNumber(num, cents, null));

                    // Nếu có đơn vị sau đó ở bản gốc -> nhân theo k/tr/triệu
                    Matcher u = UNIT.matcher(ln);
                    if (u.find()) {
                        v = (int) scaleByUnit(v, u.group(1));
                    }
                    // Ưu tiên có currency
                    if (cur != null && !cur.isEmpty()) score += 0.5;
                    // Ưu tiên số lớn hơn (nhưng không quá lệch)
                    double candidateScore = score + Math.log10(Math.max(1, v));
                    if (candidateScore > bestScore && v > bestAmt) {
                        bestScore = candidateScore;
                        bestAmt = v;
                    }
                }
            }
            // fallback: quét toàn bộ nếu chưa thấy
            if (bestAmt == 0) {
                Matcher m = MONEY_NUM.matcher(normAll);
                while (m.find()) {
                    String num = m.group(1);
                    String cents = m.group(2);
                    int v = (int)Math.round(parseNumber(num, cents, null));
                    if (v > bestAmt && v < 2_000_000_000) bestAmt = v;
                }
            }
            r.amount = (bestAmt > 0) ? bestAmt : null;
            if (bestScore > 0) r.confidence = 0.9f;

            // 2) Date – hôm nay / hôm qua / dd/MM / ISO / “ngày ... tháng ... năm ...”
            r.dateIso = extractDateIso(normAll);

            // 3) Income hint
            r.isIncome = KEY_INCOME.matcher(normAll).find();

            // 4) Merchant – lấy dòng đầu hợp lệ (không phải tiêu đề/địa chỉ/MST)
            r.merchant = guessMerchant(lines);

            return r;
        }

        private static String guessMerchant(String[] rawLines) {
            String[] bad = {"hoa don", "hoadon", "bien nhan", "receipt", "invoice", "vat", "mst", "ma so thue",
                    "dia chi", "address", "ma don", "order", "sdt", "phone"};
            for (int i = 0; i < Math.min(rawLines.length, 6); i++) {
                String line = rawLines[i].trim();
                String n = normalize(line);
                if (line.isEmpty()) continue;
                if (n.length() < 3) continue;
                boolean hasBad = false;
                for (String b : bad) if (n.contains(b)) { hasBad = true; break; }
                if (hasBad) continue;
                // tránh dòng toàn số / có số dài như phone/MST
                if (PHONE_LIKE.matcher(n).find()) continue;
                // độ alpha (chữ) phải cao
                int alpha = 0;
                for (char c : line.toCharArray()) if (Character.isLetter(c)) alpha++;
                if (alpha < Math.min(6, line.length()/2)) continue;
                // cắt phần đuôi nhiễu
                line = line.replaceAll("[|\\-–•·].*$", "").trim();
                return line;
            }
            return null;
        }

        private static String extractDateIso(String text) {
            LocalDate today = LocalDate.now();
            if (text.contains("hom nay") || text.contains("hôm nay") || text.contains("today")) return today.toString();
            if (text.contains("hom qua") || text.contains("hôm qua") || text.contains("yesterday")) return today.minusDays(1).toString();

            Matcher iso = DATE_ISO.matcher(text);
            if (iso.find()) {
                int y = Integer.parseInt(iso.group(1));
                int m = Integer.parseInt(iso.group(2));
                int d = Integer.parseInt(iso.group(3));
                return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
            }

            Matcher dmy = DATE_SLASH.matcher(text);
            if (dmy.find()) {
                int d = Integer.parseInt(dmy.group(1));
                int m = Integer.parseInt(dmy.group(2));
                int y = (dmy.group(3) == null) ? today.getYear() : parseYear(dmy.group(3));
                return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
            }

            Matcher txt = DATE_TEXT.matcher(text);
            if (txt.find()) {
                int d = Integer.parseInt(txt.group(1));
                int m = Integer.parseInt(txt.group(2));
                int y = parseYear(txt.group(3));
                return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
            }

            return today.toString();
        }

        private static int parseYear(String s) {
            int y = Integer.parseInt(s);
            return (y < 100) ? (2000 + y) : y;
        }

        private static double parseNumber(String num, String cents, @Nullable String unit) {
            // xoá phân tách nghìn . , và khoảng trắng
            String clean = num.replaceAll("[.,\\s]", "");
            double v;
            try { v = Double.parseDouble(clean); }
            catch (Exception e) { v = 0; }
            if (cents != null && cents.length() == 2) v = v; // đã có lẻ, nhưng VNĐ thường không dùng
            if (unit != null) v = scaleByUnit(v, unit);
            return v;
        }

        private static double scaleByUnit(double v, String unit) {
            String u = normalize(unit);
            if (u.startsWith("k") || u.startsWith("ngh") || u.startsWith("ngan") || u.startsWith("ngàn")) return v * 1_000d;
            if (u.startsWith("tr")) return v * 1_000_000d;
            if (u.startsWith("ty") || u.startsWith("tỷ")) return v * 1_000_000_000d;
            return v;
        }
    }

    private static String normalize(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    private static String bestCategory(String normalizedText, ArrayList<String> catNames) {
        double bestScore = 0; String best = null;
        HashSet<String> tokens = new HashSet<>(Arrays.asList(normalize(normalizedText).split("\\s+")));
        for (String c : catNames) {
            String nc = normalize(c);
            HashSet<String> ctoks = new HashSet<>(Arrays.asList(nc.split("\\s+")));
            if (ctoks.isEmpty()) continue;
            int inter = 0; for (String t : ctoks) if (tokens.contains(t)) inter++;
            double score = inter / (double) ctoks.size();
            if (normalize(normalizedText).contains(nc)) score += 0.25;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best;
    }
}
