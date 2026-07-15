package com.example.finance.util;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;

public class PasswordUtil {
    private static final int ITERATIONS = 65536;

    private static final int KEY_LENGTH = 256;

    public static String hash(char[] password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            byte[] hash = pbkdf2(password, salt); //Gọi hàm băm (xem bên dưới) để tạo ra hash từ mật khẩu và salt.
            return encode(salt) + ":" + encode(hash);
        } catch (Exception e) { throw new RuntimeException(e); }
    }


    //Phương thức này kiểm tra xem mật khẩu (password)
    public static boolean verify(char[] password, String stored) {
        try {
            String[] parts = stored.split(":");//lay du lieu da luu


            byte[] salt = decode(parts[0]);//giai ma
            byte[] expected = decode(parts[1]);
            byte[] actual = pbkdf2(password, salt);
            if (actual.length != expected.length) return false;
            int res = 0;
            for (int i = 0; i < actual.length; i++) res |= actual[i] ^ expected[i];
            return res == 0;

        } catch (Exception e) { return false; }
    }


    //hàm lõi thực hiện thuật toán băm PBKDF2 (Password-Based Key Derivation Function 2)

    private static byte[] pbkdf2(char[] password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);

        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        return skf.generateSecret(spec).getEncoded();

    }


    private static String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    //encode: Chuyển byte[] (dữ liệu nhị phân) → String (dạng Base64).

    private static byte[] decode(String s) { return Base64.getDecoder().decode(s); }

}


