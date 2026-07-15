package com.example.finance;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "finance_prefs";
    //Đây là tên của file "sổ tay" mà Android sẽ dùng để lưu thông tin.
    private static final String KEY_USER_ID = "current_user_id";
    private static final String KEY_USERNAME = "current_username";


    private final SharedPreferences prefs;

    //(Hàm khởi tạo)
    //Khi bạn tạo một SessionManager, nó sẽ dùng Context (quyền truy cập hệ thống) để đi "mở" hoặc
    public SessionManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    //Đây là hàm được gọi khi người dùng đăng nhập thành công.
    public void saveSession(int userId, String username) {
        prefs.edit().putInt(KEY_USER_ID, userId).putString(KEY_USERNAME, username).apply();
    }



    //Đây là hàm được gọi ở mọi nơi trong ứng dụng khi cần biết "Ai đang dùng app vậy?".
    public int getUserId() { return prefs.getInt(KEY_USER_ID, -1); }


    //Công dụng: Tương tự, dùng để lấy tên người dùng (ví dụ: để hiển thị "Xin chào, Anh!").
    public String getUsername() { return prefs.getString(KEY_USERNAME, null); }

    //Đây chính là hàm "Đăng xuất" (Logout).
    public void clear() { prefs.edit().clear().apply(); }
}
