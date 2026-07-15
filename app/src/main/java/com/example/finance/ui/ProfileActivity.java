package com.example.finance.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Patterns;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ProfileActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private SessionManager session;

    private ImageView ivAvatarBig;
    private EditText etEmail, etPhone;

    private ActivityResultLauncher<Intent> pickFromGallery;              // Hướng 1: Gallery
    private ActivityResultLauncher<String[]> pickFromFilesOpenDocument;  // Hướng 2: Files/SAF

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_profile);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        // Toolbar + mũi tên back
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_profile);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ivAvatarBig = findViewById(R.id.ivAvatarBig);
        etEmail     = findViewById(R.id.etEmail);
        etPhone     = findViewById(R.id.etPhone);
        Button btnPickGallery = findViewById(R.id.btnPickGallery);
        Button btnPickFiles   = findViewById(R.id.btnPickFiles);
        Button btnSave        = findViewById(R.id.btnSave);

        // Load hồ sơ hiện tại
        try (Cursor c = db.getUserProfile(session.getUserId())) {
            if (c != null && c.moveToFirst()) {
                String avatar = c.getString(3); // avatar_uri
                applyAvatar(avatar);
                etEmail.setText(c.getString(1)); // email
                etPhone.setText(c.getString(2)); // phone
            }
        }

        // === HƯỚNG 1: Gallery (ảnh nằm trong Thư viện / Pictures) ===
        pickFromGallery = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            String saved = copyAvatarToInternal(uri);
                            if (saved != null) {
                                db.setUserAvatar(session.getUserId(), saved);
                                applyAvatar(saved);
                            }
                        }
                    }
                });

        btnPickGallery.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            pickFromGallery.launch(Intent.createChooser(intent, getString(R.string.pick_image_title)));
        });

        // === HƯỚNG 2: Files/SAF (mở bộ chọn tệp, chọn ảnh từ Downloads/Drive, v.v.) ===
        pickFromFilesOpenDocument = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        // Có thể xin persistable permission nếu bạn KHÔNG copy về bộ nhớ app.
                        // Ở đây mình copy về internal storage nên không cần giữ permission.
                        String saved = copyAvatarToInternal(uri);
                        if (saved != null) {
                            db.setUserAvatar(session.getUserId(), saved);
                            applyAvatar(saved);
                        }
                    }
                });

        btnPickFiles.setOnClickListener(v -> {
            // Cho phép chọn ảnh từ mọi provider (Downloads, Drive…)
            pickFromFilesOpenDocument.launch(new String[]{"image/*"});
        });

        // Lưu email/phone
        btnSave.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError(getString(R.string.err_email_invalid)); return;
            }
            if (!phone.isEmpty() && !phone.matches("[0-9+()\\-\\s]{6,}")) {
                etPhone.setError(getString(R.string.err_phone_invalid)); return;
            }
            if (db.emailExists(email, session.getUserId())) {
                etEmail.setError(getString(R.string.err_email_taken)); return;
            }
            if (db.phoneExists(phone, session.getUserId())) {
                etPhone.setError(getString(R.string.err_phone_taken)); return;
            }

            db.updateUserContact(session.getUserId(), email, phone);
            finish(); // quay lại Dashboard
        });
    }

    // Mũi tên back trên ActionBar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void applyAvatar(String path) {
        if (path == null || path.isEmpty()) {
            ivAvatarBig.setImageResource(R.drawable.ic_user_avatar_default);
            return;
        }
        File f = new File(path);
        if (f.exists()) ivAvatarBig.setImageURI(Uri.fromFile(f));
        else ivAvatarBig.setImageResource(R.drawable.ic_user_avatar_default);
    }

    private String copyAvatarToInternal(Uri src) {
        try (InputStream in = getContentResolver().openInputStream(src)) {
            if (in == null) return null;
            File dir = new File(getFilesDir(), "avatars");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "avatar_" + session.getUserId() + ".jpg");
            try (OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            return out.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
