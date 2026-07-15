package com.example.finance.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.text.NumberFormat;
import java.util.Locale;

public class FormatUtils {
    private static final Locale VI = new Locale("vi", "VN");
    private static final NumberFormat NF = NumberFormat.getInstance(VI);

    /** Hiển thị tiền: 3000000 -> "3.000.000" */
    public static String money(int vnd) {
        return NF.format(vnd);
    }
    //Đây là "phiên dịch viên" Số → Chữ.

    /** Parse từ chuỗi có chấm/thứ khác -> số int */
    public static int parseMoney(String s) {
        if (s == null) return 0;
        String digits = s.replaceAll("[^\\d]", "");
        if (digits.isEmpty()) return 0;
        try {
            long val = Long.parseLong(digits);
            if (val > Integer.MAX_VALUE) val = Integer.MAX_VALUE;
            return (int) val;
        } catch (NumberFormatException e) {
            return 0;
        }
    }






    /** Gắn mask nhập tiền cho EditText (tự chèn dấu chấm khi gõ) */

    public static void applyMoneyMask(EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            boolean selfChange = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override public void afterTextChanged(Editable s) {
                if (selfChange) return;
                selfChange = true;

                String digits = s.toString().replaceAll("[^\\d]", "");
                if (digits.isEmpty()) {
                    et.setText("");
                } else {
                    int value = parseMoney(digits);
                    String formatted = money(value);
                    et.setText(formatted);
                    et.setSelection(formatted.length()); // đưa con trỏ về cuối
                }

                selfChange = false;
            }
        });
    }
}
