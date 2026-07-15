package com.example.finance.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;

import com.example.finance.R;
import com.example.finance.SessionManager;
import com.example.finance.db.DatabaseHelper;

public class RegisterActivity extends Activity {
    private EditText etUser, etPass;
    private DatabaseHelper db;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        etUser = findViewById(R.id.etRegUsername);
        etPass = findViewById(R.id.etRegPassword);
        Button btnCreate = findViewById(R.id.btnCreateAccount);

        btnCreate.setOnClickListener(v -> {
            String u = etUser.getText().toString().trim();
            String p = etPass.getText().toString();

            if (u.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, getString(R.string.msg_register_fill), Toast.LENGTH_SHORT).show();
                return;
            }
            if (db.usernameExists(u)) {
                Toast.makeText(this, getString(R.string.msg_register_exists), Toast.LENGTH_SHORT).show();
                return;
            }
            int uid = db.createUser(u, p.toCharArray());
            if (uid > 0) {
                db.seedDefaultCategories(uid);
                session.saveSession(uid, u);
                Toast.makeText(this, getString(R.string.msg_register_success), Toast.LENGTH_SHORT).show();

                startActivity(new Intent(this, LoginActivity.class));
                finish();
            } else {
                Toast.makeText(this, getString(R.string.msg_register_fail), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
