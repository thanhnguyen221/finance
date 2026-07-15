package com.example.finance.db;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.database.Cursor;

import com.example.finance.util.PasswordUtil;
import com.example.finance.util.DateUtils;

import java.time.LocalDate;



public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "finance.db";
    public static final int DB_VERSION = 8;


    public DatabaseHelper(Context ctx) { super(ctx, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys=ON");
        //Giống như onConfigure, đây là một cách khác để đảm bảo "Luật Khóa Ngoại" được bật.

        // === Core tables ===
        //Nó dùng lệnh db.execSQL(...) (thực thi lệnh SQL) để tạo từng bảng một
        // Người Dùng
        db.execSQL("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT NOT NULL UNIQUE," +
                "password_hash TEXT NOT NULL," +
                "email TEXT," +
                "phone TEXT," +
                "avatar_uri TEXT," +
                "created_at TEXT DEFAULT (datetime('now'))" +
                ")");

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone ON users(phone)");


        //Lưu các danh mục như "Ăn uống", "Lương", "Đi lại".
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "type TEXT CHECK(type IN ('INCOME','EXPENSE')) NOT NULL," +


                "is_default INTEGER DEFAULT 0," +
                "UNIQUE(user_id, name, type)," +
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")");

                // ngân sách
        db.execSQL("CREATE TABLE IF NOT EXISTS budgets (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "period_type TEXT CHECK(period_type IN ('DAY','WEEK','MONTH','YEAR')) NOT NULL," +



                "period_start TEXT NOT NULL," +
                "period_end TEXT NOT NULL," +

                "planned_amount INTEGER NOT NULL," +
                "note TEXT," +
                "UNIQUE(user_id, period_type, period_start)," +
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")");


        db.execSQL("CREATE TABLE IF NOT EXISTS recurring_incomes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "category_id INTEGER NOT NULL," +
                "amount INTEGER NOT NULL," +
                "frequency TEXT CHECK(frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY')) NOT NULL," +


                "day_of_month INTEGER," +
                "day_of_week INTEGER," +
                "start_date TEXT NOT NULL," +
                "end_date TEXT," +
                "next_run TEXT NOT NULL," +
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE," +
                "FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE" +
                ")");


        db.execSQL("CREATE TABLE IF NOT EXISTS receipts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "image_uri TEXT NOT NULL," +

                "image_hash TEXT," +
                "ocr_text TEXT," +
                "ocr_json TEXT," +
                "extracted_amount INTEGER," +
                "extracted_date TEXT," +
                "vendor TEXT," +
                "currency TEXT DEFAULT 'VND'," +
                "status TEXT CHECK(status IN ('NEW','OCR_DONE','PARSED','LINKED','ERROR')) NOT NULL DEFAULT 'NEW'," +
                "error_msg TEXT," +
                "created_at INTEGER DEFAULT (strftime('%s','now'))," +
                "linked_txn_id INTEGER," +
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")");



        // transactions + cột mới cho OCR/NLP
        db.execSQL("CREATE TABLE IF NOT EXISTS transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +

                //Giao dịch này thuộc danh mục nào? (Ví dụ: ID=5, ứng với "Ăn uống")
                "category_id INTEGER NOT NULL," +

                //Đây là khoản Thu (INCOME) hay Chi (EXPENSE)? Bắt buộc phải là 1 trong 2.
                "type TEXT CHECK(type IN ('INCOME','EXPENSE')) NOT NULL," +

                //Số tiền của giao dịch (ví dụ: 50000, đơn vị tính bằng currency).
                "amount INTEGER NOT NULL," +

                //Giao dịch xảy ra vào ngày nào? (Lưu dạng text, ví dụ: "2025-10-21").
                "txn_date TEXT NOT NULL," +

                //Ghi chú của người dùng (ví dụ: "Ăn trưa với sếp").
                "note TEXT," +

                "recurring_id INTEGER REFERENCES recurring_incomes(id) ON DELETE SET NULL," +
                "is_auto INTEGER NOT NULL DEFAULT 0," +
                "is_void INTEGER NOT NULL DEFAULT 0," +
                "void_reason TEXT," +
                "reconciled INTEGER NOT NULL DEFAULT 0," +
                "merchant TEXT," +
                "input_text TEXT," +
                "currency TEXT DEFAULT 'VND'," +
                "receipt_id INTEGER REFERENCES receipts(id) ON DELETE SET NULL," +

                "auto_source TEXT," +
                "auto_conf REAL DEFAULT 0," +
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE," +
                "FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS app_prefs (" +
                "user_id INTEGER PRIMARY KEY," +
                "global_budget_amount INTEGER NOT NULL DEFAULT 0," +
                "note TEXT," +
                "updated_at INTEGER DEFAULT (strftime('%s','now'))," +
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")");

        // Rules & suggestions cho NLP/learning
        db.execSQL("CREATE TABLE IF NOT EXISTS auto_rules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "kind TEXT CHECK(kind IN ('MERCHANT','KEYWORD','REGEX')) NOT NULL," +
                "pattern TEXT NOT NULL," +
                "category_id INTEGER NOT NULL," +
                "confidence_boost REAL DEFAULT 0.0," +
                "hit_count INTEGER DEFAULT 0," +
                "last_hit INTEGER," +
                "enabled INTEGER DEFAULT 1," +
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE," +
                "FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE," +
                "UNIQUE(user_id, kind, pattern)" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS txn_auto_suggestions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "txn_id INTEGER NOT NULL REFERENCES transactions(id) ON DELETE CASCADE," +
                "suggested_category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL," +
                "suggested_merchant TEXT," +
                "suggested_date TEXT," +
                "suggested_amount INTEGER," +
                "source TEXT CHECK(source IN ('OCR','NLP','RULE')) NOT NULL," +
                "confidence REAL NOT NULL," +
                "created_at INTEGER DEFAULT (strftime('%s','now'))," +
                "accepted INTEGER DEFAULT NULL" +
                ")");

        // Indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_txn_user_date ON transactions(user_id, txn_date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_txn_recurring ON transactions(recurring_id, txn_date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_txn_user_merchant ON transactions(user_id, merchant)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cat_user ON categories(user_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_budget_user ON budgets(user_id, period_type, period_start)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_budget_user_end ON budgets(user_id, period_type, period_end)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_receipt_user_status ON receipts(user_id, status)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sugg_txn ON txn_auto_suggestions(txn_id)");
    }



    // "Luật Ràng Buộc Khóa Ngoại".
    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);

    }

    // khởi tạo
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS app_prefs (" +
                    "user_id INTEGER PRIMARY KEY," +
                    "global_budget_amount INTEGER NOT NULL DEFAULT 0," +
                    "note TEXT," +
                    "updated_at INTEGER DEFAULT (strftime('%s','now'))," +
                    "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE" +
                    ")");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE users ADD COLUMN email TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN phone TEXT");
            db.execSQL("ALTER TABLE users ADD COLUMN avatar_uri TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone ON users(phone)");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN recurring_id INTEGER REFERENCES recurring_incomes(id) ON DELETE SET NULL");
            db.execSQL("ALTER TABLE transactions ADD COLUMN is_auto INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE transactions ADD COLUMN is_void INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE transactions ADD COLUMN void_reason TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN reconciled INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 5) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_txn_recurring ON transactions(recurring_id, txn_date)");
        }
        if (oldVersion < 8) {
            db.execSQL("CREATE TABLE IF NOT EXISTS receipts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id INTEGER NOT NULL," +
                    "image_uri TEXT NOT NULL," +
                    "image_hash TEXT," +
                    "ocr_text TEXT," +
                    "ocr_json TEXT," +
                    "extracted_amount INTEGER," +
                    "extracted_date TEXT," +
                    "vendor TEXT," +
                    "currency TEXT DEFAULT 'VND'," +
                    "status TEXT CHECK(status IN ('NEW','OCR_DONE','PARSED','LINKED','ERROR')) NOT NULL DEFAULT 'NEW'," +
                    "error_msg TEXT," +
                    "created_at INTEGER DEFAULT (strftime('%s','now'))," +
                    "linked_txn_id INTEGER," +
                    "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE" +
                    ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_receipt_user_status ON receipts(user_id, status)");
        }
        if (oldVersion < 9) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN merchant TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN input_text TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT DEFAULT 'VND'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN receipt_id INTEGER REFERENCES receipts(id) ON DELETE SET NULL");
            db.execSQL("ALTER TABLE transactions ADD COLUMN auto_source TEXT");
            db.execSQL("ALTER TABLE transactions ADD COLUMN auto_conf REAL DEFAULT 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_txn_user_merchant ON transactions(user_id, merchant)");
        }
        if (oldVersion < 10) {
            db.execSQL("CREATE TABLE IF NOT EXISTS auto_rules (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id INTEGER NOT NULL," +
                    "kind TEXT CHECK(kind IN ('MERCHANT','KEYWORD','REGEX')) NOT NULL," +
                    "pattern TEXT NOT NULL," +
                    "category_id INTEGER NOT NULL," +
                    "confidence_boost REAL DEFAULT 0.0," +
                    "hit_count INTEGER DEFAULT 0," +
                    "last_hit INTEGER," +
                    "enabled INTEGER DEFAULT 1," +
                    "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE CASCADE," +
                    "UNIQUE(user_id, kind, pattern)" +
                    ")");
            db.execSQL("CREATE TABLE IF NOT EXISTS txn_auto_suggestions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "txn_id INTEGER NOT NULL REFERENCES transactions(id) ON DELETE CASCADE," +
                    "suggested_category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL," +
                    "suggested_merchant TEXT," +
                    "suggested_date TEXT," +
                    "suggested_amount INTEGER," +
                    "source TEXT CHECK(source IN ('OCR','NLP','RULE')) NOT NULL," +
                    "confidence REAL NOT NULL," +
                    "created_at INTEGER DEFAULT (strftime('%s','now'))," +
                    "accepted INTEGER DEFAULT NULL" +
                    ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sugg_txn ON txn_auto_suggestions(txn_id)");
        }
    }

    // ===================== Users =====================
    public int createUser(String username, char[] password) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("username", username);
        cv.put("password_hash", PasswordUtil.hash(password));
        long id = db.insert("users", null, cv);
        return (int) id;
    }


    //xac thuc dang nhap
    public int getUserIdIfValid(String username, char[] password) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, password_hash FROM users WHERE username = ?", new String[]{username});
        try {
            if (c.moveToFirst()) {
                int id = c.getInt(0);
                String hash = c.getString(1);
                return PasswordUtil.verify(password, hash) ? id : -1;
            }
            return -1;
        } finally { c.close(); }
    }

    // kiem tra username ton tai chua
    public boolean usernameExists(String username) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT 1 FROM users WHERE username = ?", new String[]{username});
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    // ===================== User Profile (email/phone/avatar) =====================

    // cap nhap email,sdt
    public int updateUserContact(int userId, String email, String phone) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("email", (email == null || email.isEmpty()) ? null : email);
        cv.put("phone", (phone == null || phone.isEmpty()) ? null : phone);
        return db.update("users", cv, "id=?", new String[]{ String.valueOf(userId) });
    }

    //Lưu URI/đường dẫn avatar.
    public int setUserAvatar(int userId, String uriOrPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("avatar_uri", (uriOrPath == null || uriOrPath.isEmpty()) ? null : uriOrPath);
        return db.update("users", cv, "id=?", new String[]{ String.valueOf(userId) });
    }

    //Xóa avatar (đặt NULL).
    public int clearUserAvatar(int userId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.putNull("avatar_uri");
        return db.update("users", cv, "id=?", new String[]{ String.valueOf(userId) });
    }

    //Lấy avatar_uri của user.
    public String getUserAvatar(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT avatar_uri FROM users WHERE id=?",
                new String[]{ String.valueOf(userId) });
        try { return c.moveToFirst() ? c.getString(0) : null; }
        finally { c.close(); }
    }

    //Lấy hồ sơ rút gọn của user cho UI.
    public Cursor getUserProfile(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery(
                "SELECT username, email, phone, avatar_uri FROM users WHERE id=? LIMIT 1",
                new String[]{ String.valueOf(userId) }
        );
    }


    //kiemtra email ton tai chua
    public boolean emailExists(String email, Integer exceptUserId) {
        if (email == null || email.isEmpty()) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = (exceptUserId == null)
                ? db.rawQuery("SELECT 1 FROM users WHERE email=? LIMIT 1", new String[]{ email })
                : db.rawQuery("SELECT 1 FROM users WHERE email=? AND id<>? LIMIT 1",
                new String[]{ email, String.valueOf(exceptUserId) });
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    //Kiểm tra phone đã tồn tại
    public boolean phoneExists(String phone, Integer exceptUserId) {
        if (phone == null || phone.isEmpty()) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = (exceptUserId == null)
                ? db.rawQuery("SELECT 1 FROM users WHERE phone=? LIMIT 1", new String[]{ phone })
                : db.rawQuery("SELECT 1 FROM users WHERE phone=? AND id<>? LIMIT 1",
                new String[]{ phone, String.valueOf(exceptUserId) });
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    // ===================== Categories =====================
    /**
     * Không seed mặc định để tránh hard-code tên danh mục. Nếu cần, gọi API riêng ở UI.
     */
    public void seedDefaultCategories(int userId) { /* intentionally empty */ }

    //Thêm category mới (INCOME/EXPENSE).
    public long insertCategory(int userId, String name, String type, boolean isDefault) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("user_id", userId);
        cv.put("name", name);
        cv.put("type", type);
        cv.put("is_default", isDefault ? 1 : 0);
        return db.insertWithOnConflict("categories", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }


    // Sửa tên/type category theo id.
    public int updateCategory(int id, String name, String type) throws android.database.sqlite.SQLiteConstraintException {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("type", type);
        return db.update("categories", cv, "id=?", new String[]{ String.valueOf(id) });
    }

    //xoá theo id
    public int deleteCategory(int id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("categories", "id=?", new String[]{ String.valueOf(id) });
    }

    //Lấy danh sách category theo type cho user.
    public Cursor getCategories(int userId, String type) {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery("SELECT id, name FROM categories WHERE user_id=? AND type=? ORDER BY name",
                new String[]{String.valueOf(userId), type});
    }

    // ===================== Budgets (monthly – tương thích cũ) =====================

    //Tạo/sửa ngân sách theo THÁNG
    public long upsertMonthlyBudget(int userId, int year, int month, int plannedAmount, String note) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        String s = DateUtils.fmt(start), e = DateUtils.fmt(end);
        SQLiteDatabase db = getWritableDatabase();

        Cursor c = db.rawQuery("SELECT id FROM budgets WHERE user_id=? AND period_type='MONTH' AND period_start=?",
                new String[]{String.valueOf(userId), s});
        try {
            if (c.moveToFirst()) {
                int id = c.getInt(0);
                ContentValues cv = new ContentValues();
                cv.put("planned_amount", plannedAmount);
                cv.put("period_end", e);
                cv.put("note", note);
                db.update("budgets", cv, "id=?", new String[]{String.valueOf(id)});
                return id;
            } else {
                ContentValues cv = new ContentValues();
                cv.put("user_id", userId);
                cv.put("period_type", "MONTH");
                cv.put("period_start", s);
                cv.put("period_end", e);
                cv.put("planned_amount", plannedAmount);
                cv.put("note", note);
                return db.insert("budgets", null, cv);
            }
        } finally { c.close(); }
    }

    //Lấy planned_amount của ngân sách THÁNG chứa anyDayOfMonth.
    public int getMonthlyPlanned(int userId, LocalDate anyDayOfMonth) {
        String start = DateUtils.fmt(anyDayOfMonth.withDayOfMonth(1));
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT planned_amount FROM budgets WHERE user_id=? AND period_type='MONTH' AND period_start=?",
                new String[]{String.valueOf(userId), start});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    // ===================== Global Budget =====================
    //Thiết lập "Global Budget" (ngân sách toàn cục fallback)
    public void setGlobalBudget(int userId, int amount, String note) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("user_id", userId);
        cv.put("global_budget_amount", Math.max(0, amount));
        cv.put("note", note);
        cv.put("updated_at", System.currentTimeMillis() / 1000);
        db.insertWithOnConflict("app_prefs", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    //Lấy Global Budget hiện tại.
    public int getGlobalBudget(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT global_budget_amount FROM app_prefs WHERE user_id=?",
                new String[]{ String.valueOf(userId) });
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { if (c != null) c.close(); }
    }

    //Xóa (đặt 0) Global Budget.
    public int clearGlobalBudget(int userId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("global_budget_amount", 0);
        cv.put("updated_at", System.currentTimeMillis() / 1000);
        return db.update("app_prefs", cv, "user_id=?", new String[]{ String.valueOf(userId) });
    }

    // ===================== Budgets by range =====================

    //SUY DIỄN loại kỳ ngân sách dựa vào start/end (DAY/WEEK/MONTH/YEAR).
    private String inferPeriodType(String start, String end) {
        LocalDate s = DateUtils.parse(start);
        LocalDate e = DateUtils.parse(end);
        if (s.equals(e)) return "DAY";
        if (s.getDayOfWeek().getValue() == 1 && e.equals(s.plusDays(6))) return "WEEK"; // Mon..Sun
        if (s.getDayOfMonth() == 1 && e.equals(s.withDayOfMonth(s.lengthOfMonth()))) return "MONTH";
        if (s.getDayOfYear() == 1 && e.getDayOfYear() == e.lengthOfYear() && s.getYear() == e.getYear()) return "YEAR";
        return "MONTH";
    }

    public void upsertBudgetRange(int userId, String startDate, String endDate, int plannedAmount, String note) {
        setGlobalBudget(userId, plannedAmount, note);
    }

    public int deleteBudgetRange(int userId, String startDate, String endDate) {
        return clearGlobalBudget(userId);
    }

    //Upsert ngân sách theo NGÀY
    public long upsertDayBudget(int userId, LocalDate day, int amount, String note) {
        return upsertGenericBudget(userId, "DAY", day, day, amount, note);
    }

    //Upsert ngân sách theo TUẦN (Mon..Sun).
    public long upsertWeekBudget(int userId, LocalDate monday, int amount, String note) {
        LocalDate sunday = monday.plusDays(6);
        return upsertGenericBudget(userId, "WEEK", monday, sunday, amount, note);
    }

    //Upsert ngân sách theo NĂM.
    public long upsertYearBudget(int userId, int year, int amount, String note) {
        LocalDate s = LocalDate.of(year, 1, 1);
        LocalDate e = LocalDate.of(year, 12, 31);
        return upsertGenericBudget(userId, "YEAR", s, e, amount, note);
    }

    //Upsert generic cho mọi period_type (DAY/WEEK/MONTH/YEAR).
    private long upsertGenericBudget(int userId, String type, LocalDate s, LocalDate e, int amount, String note) {
        SQLiteDatabase db = getWritableDatabase();
        String start = DateUtils.fmt(s), end = DateUtils.fmt(e);
        Cursor c = db.rawQuery("SELECT id FROM budgets WHERE user_id=? AND period_type=? AND period_start=?",
                new String[]{ String.valueOf(userId), type, start });
        try {
            ContentValues cv = new ContentValues();
            cv.put("user_id", userId);
            cv.put("period_type", type);
            cv.put("period_start", start);
            cv.put("period_end", end);
            cv.put("planned_amount", Math.max(0, amount));
            cv.put("note", note);
            if (c.moveToFirst()) {
                int id = c.getInt(0);
                db.update("budgets", cv, "id=?", new String[]{ String.valueOf(id) });
                return id;
            } else {
                return db.insert("budgets", null, cv);
            }
        } finally { c.close(); }
    }

    //Xóa ngân sách đúng kỳ & đúng ngày bắt đầu.
    public int deleteBudgetExact(int userId, String type, LocalDate start) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("budgets",
                "user_id=? AND period_type=? AND period_start=?",
                new String[]{ String.valueOf(userId), type, DateUtils.fmt(start) });
    }

    //Tổng ngân sách phân bổ theo từng ngày trong khoảng [startDate..endDate].
    public int getBudgetForRange(int userId, String startDate, String endDate) {
        LocalDate s = DateUtils.parse(startDate);
        LocalDate e = DateUtils.parse(endDate);
        long total = 0;
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            total += plannedForDay(userId, d);
            if (total > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    //Tính planned cho MỘT NGÀY
    private int plannedForDay(int userId, LocalDate d) {
        Integer day = findExactBudget(userId, "DAY", d, d);
        if (day != null) return day;

        LocalDate monday = DateUtils.mondayOfWeek(d);
        LocalDate sunday = DateUtils.sundayOfWeek(d);
        Integer week = findExactBudget(userId, "WEEK", monday, sunday);
        if (week != null) return divideEven(week, 7, d.getDayOfWeek().getValue());

        LocalDate mStart = d.withDayOfMonth(1);
        LocalDate mEnd   = d.withDayOfMonth(d.lengthOfMonth());
        Integer month = findExactBudget(userId, "MONTH", mStart, mEnd);
        if (month != null) return divideEven(month, d.lengthOfMonth(), d.getDayOfMonth());

        LocalDate yStart = LocalDate.of(d.getYear(), 1, 1);
        LocalDate yEnd   = LocalDate.of(d.getYear(), 12, 31);
        Integer year = findExactBudget(userId, "YEAR", yStart, yEnd);
        if (year != null) return divideEven(year, yStart.lengthOfYear(), d.getDayOfYear());

        int global = getGlobalBudget(userId);
        if (global > 0) return divideEven(global, d.lengthOfMonth(), d.getDayOfMonth());

        return 0;
    }

    //Tìm chính xác 1 bản ghi ngân sách theo type và [s..e].
    private Integer findExactBudget(int userId, String type, LocalDate s, LocalDate e) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT planned_amount FROM budgets WHERE user_id=? AND period_type=? AND period_start=? AND period_end=? LIMIT 1",
                new String[]{ String.valueOf(userId), type, DateUtils.fmt(s), DateUtils.fmt(e) });
        try {
            if (c.moveToFirst()) return c.getInt(0);
            return null;
        } finally { c.close(); }
    }

    //Chia đều amount cho n phần, cộng 1 cho các ngày đầu đến khi hết remainder.
    private int divideEven(int amount, int n, int index1Based) {
        if (n <= 0) return amount;
        int base = amount / n;
        int rem  = amount % n;
        return base + (index1Based <= rem ? 1 : 0);
    }

    // ===================== Transactions =====================
    //Thêm giao dịch thủ công (manual transaction).
    public long insertTransaction(int userId, int categoryId, String type, int amount, String date, String note) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("user_id", userId);
        cv.put("category_id", categoryId);
        cv.put("type", type);
        cv.put("amount", amount);
        cv.put("txn_date", date);
        cv.put("note", note);
        cv.put("is_auto", 0);
        cv.put("is_void", 0);
        cv.put("reconciled", 0);
        return db.insert("transactions", null, cv);
    }

    //Cập nhật giao dịch thủ công theo id.
    public int updateTransaction(int id, int categoryId, int amount, String date, String note) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("category_id", categoryId);
        cv.put("amount", amount);
        cv.put("txn_date", date);
        cv.put("note", note);
        return db.update("transactions", cv, "id=?", new String[]{String.valueOf(id)});
    }

    //Xóa giao dịch theo id.
    public int deleteTransaction(int id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("transactions", "id=?", new String[]{ String.valueOf(id) });
    }


    //Liệt kê giao dịch theo type trong khoảng ngày [start..end], bỏ qua is_void=1.
    public Cursor listTransactions(int userId, String type, String startDate, String endDate) {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery("SELECT t.id, t.amount, t.txn_date, t.note, c.name " +
                        "FROM transactions t JOIN categories c ON c.id=t.category_id " +
                        "WHERE t.user_id=? AND t.type=? AND t.is_void=0 AND t.txn_date BETWEEN ? AND ? " +
                        "ORDER BY t.txn_date DESC, t.id DESC",
                new String[]{String.valueOf(userId), type, startDate, endDate});
    }

    //Lấy 1 giao dịch theo id (phục vụ màn hình edit/view).
    public Cursor getTransactionById(int id) {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery(
                "SELECT category_id, amount, txn_date, note, type FROM transactions WHERE id=?",
                new String[]{ String.valueOf(id) }
        );
    }

    //Tính tổng amount theo type trong khoảng ngày, bỏ qua is_void=1.
    public int sumByTypeInRange(int userId, String type, String startDate, String endDate) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT IFNULL(SUM(amount),0) FROM transactions " +
                        "WHERE user_id=? AND type=? AND is_void=0 AND txn_date BETWEEN ? AND ?",
                new String[]{String.valueOf(userId), type, startDate, endDate});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    // ===================== Recurring incomes =====================

    //Tạo rule THU NHẬP ĐỊNH KỲ kiểu MONTHLY (dayOfMonth cố định).
    public long addRecurringIncomeMonthly(int userId, int categoryId, int amount, int dayOfMonth, String startDate) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("user_id", userId);
        cv.put("category_id", categoryId);
        cv.put("amount", amount);
        cv.put("frequency", "MONTHLY");
        cv.put("day_of_month", dayOfMonth);
        cv.put("start_date", startDate);
        cv.put("end_date", (String) null);
        cv.put("next_run", startDate);
        return db.insert("recurring_incomes", null, cv);
    }

    public Cursor listRecurringIncomes(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery(
                "SELECT r.id, r.category_id, c.name, r.amount, r.day_of_month, r.start_date " +
                        "FROM recurring_incomes r JOIN categories c ON c.id = r.category_id " +
                        "WHERE r.user_id=? ORDER BY r.day_of_month, r.id",
                new String[]{ String.valueOf(userId) }
        );
    }

    //Liệt kê các khoản thu định kỳ của user (join tên category).
    public int updateRecurringIncome(int id, int categoryId, int amount, int dayOfMonth) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("category_id", categoryId);
        cv.put("amount", amount);
        cv.put("day_of_month", dayOfMonth);
        return db.update("recurring_incomes", cv, "id=?", new String[]{ String.valueOf(id) });
    }

    /**
     * Nếu startDate > hôm nay => void các giao dịch auto của rule từ hôm nay trở đi.
     * Luôn đặt next_run = startDate.
     */
    //Cập nhật rule định kỳ (không đụng start_date/next_run).
    public int updateRecurringIncome(int id, int categoryId, int amount, int dayOfMonth, String startDate) {
        SQLiteDatabase db = getWritableDatabase();
        LocalDate today = LocalDate.now();
        LocalDate newStart = DateUtils.parse(startDate);
        if (newStart.isAfter(today)) {
            voidAutoTransactionsFromDate(id, today, "Rule updated: start_date moved to future");
        }
        ContentValues cv = new ContentValues();
        cv.put("category_id", categoryId);
        cv.put("amount", amount);
        cv.put("day_of_month", dayOfMonth);
        cv.put("start_date", startDate);
        cv.put("next_run", startDate);
        return db.update("recurring_incomes", cv, "id=?", new String[]{ String.valueOf(id) });
    }

    /**
     * Xóa rule: void các phát sinh tự động tương lai rồi xóa rule.
     */
    //Cập nhật rule định kỳ KÈM startDate mới.
    public int deleteRecurringIncome(int id) {
        LocalDate today = LocalDate.now();
        voidAutoTransactionsFromDate(id, today, "Rule deleted: void future occurrences");
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("recurring_incomes", "id=?", new String[]{ String.valueOf(id) });
    }

    /**
     * Materialize các khoản thu định kỳ tới hôm nay, tránh trùng theo (recurring_id, txn_date).
     */
    public void processRecurringIncomes(int userId, LocalDate today) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id, category_id, amount, frequency, day_of_month, day_of_week, start_date, end_date, next_run " +
                "FROM recurring_incomes WHERE user_id=?", new String[]{String.valueOf(userId)});
        try {
            db.beginTransaction();
            while (c.moveToNext()) {
                int id = c.getInt(0);
                int catId = c.getInt(1);
                int amount = c.getInt(2);
                String freq = c.getString(3);
                Integer dom = c.isNull(4) ? null : c.getInt(4);
                Integer dow = c.isNull(5) ? null : c.getInt(5);
                String endStr = c.getString(7);
                LocalDate end = (endStr == null) ? null : DateUtils.parse(endStr);
                LocalDate next = DateUtils.parse(c.getString(8));

                while ((next.isBefore(today) || next.equals(today)) && (end == null || !next.isAfter(end))) {
                    if (!hasAnyTxnForRecurringOnDate(id, next)) {
                        ContentValues cv = new ContentValues();
                        cv.put("user_id", userId);
                        cv.put("category_id", catId);
                        cv.put("type", "INCOME");
                        cv.put("amount", amount);
                        cv.put("txn_date", DateUtils.fmt(next));
                        cv.put("note", "Thu nhập định kỳ");
                        cv.put("recurring_id", id);
                        cv.put("is_auto", 1);
                        cv.put("is_void", 0);
                        cv.put("reconciled", 0);
                        db.insert("transactions", null, cv);
                    }
                    next = nextDate(next, freq, dom, dow);
                }
                ContentValues up = new ContentValues();
                up.put("next_run", DateUtils.fmt(next));
                db.update("recurring_incomes", up, "id=?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            if (db.inTransaction()) db.endTransaction();
            c.close();
        }
    }

    // ===== Helpers for recurring =====

    private boolean hasAnyTxnForRecurringOnDate(int recurringId, LocalDate date) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT 1 FROM transactions WHERE recurring_id=? AND txn_date=? LIMIT 1",
                new String[]{ String.valueOf(recurringId), DateUtils.fmt(date) }
        );
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    private int voidAutoTransactionsFromDate(int recurringId, LocalDate fromInclusive, String reason) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_void", 1);
        cv.put("void_reason", reason);
        return db.update("transactions", cv,
                "recurring_id=? AND is_auto=1 AND reconciled=0 AND is_void=0 AND txn_date>=?",
                new String[]{ String.valueOf(recurringId), DateUtils.fmt(fromInclusive) });
    }

    private LocalDate nextDate(LocalDate current, String freq, Integer dom, Integer dow) {
        switch (freq) {
            case "DAILY": return current.plusDays(1);
            case "WEEKLY": return current.plusWeeks(1); // có thể dùng dow để ghim về một thứ cụ thể
            case "MONTHLY": {
                int day = (dom != null) ? dom : current.getDayOfMonth();
                LocalDate next = current.plusMonths(1);
                int last = next.lengthOfMonth();
                int safe = Math.min(day, last);
                return LocalDate.of(next.getYear(), next.getMonth(), safe);
            }
            case "YEARLY": return current.plusYears(1);
            default: return current.plusMonths(1);
        }
    }
}
