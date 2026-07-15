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

public class LoginActivity extends Activity {
    private EditText etUser, etPass;
    private DatabaseHelper db;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        etUser = findViewById(R.id.etUsername);
        etPass = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnGoRegister);

        btnLogin.setOnClickListener(v -> {
            String u = etUser.getText().toString().trim();
            String p = etPass.getText().toString();
            int uid = db.getUserIdIfValid(u, p.toCharArray());
            if (uid > 0) {
                session.saveSession(uid, u);
                Toast.makeText(this, getString(R.string.msg_login_success), Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, DashboardActivity.class));
                finish();
            } else {
                Toast.makeText(this, getString(R.string.msg_login_wrong), Toast.LENGTH_LONG).show();
            }
        });

        btnRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }
}
