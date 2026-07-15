package com.example.finance.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public class DateUtils {
    // Định dạng chuẩn yyyy-MM-dd
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Format LocalDate -> "yyyy-MM-dd" */
    public static String fmt(LocalDate d) { //Chuyển một đối tượng "Lịch" (LocalDate) thành một chuỗi String ("2025-10-21") để bạn có thể lưu vào database.
        return d.format(DF);
    }

    /** Parse "yyyy-MM-dd" -> LocalDate */
    public static LocalDate parse(String s) {
        return LocalDate.parse(s, DF);
    }

    /** Ngày đầu tháng của ngày cho trước */
    public static LocalDate firstDayOfMonth(LocalDate d) {
        return d.withDayOfMonth(1);
    }

    /** Ngày cuối tháng của ngày cho trước */
    public static LocalDate lastDayOfMonth(LocalDate d) {
        return d.withDayOfMonth(d.lengthOfMonth());
    }

    /** Thứ Hai (Monday) của tuần chứa ngày d – theo chuẩn ISO (tuần bắt đầu từ Thứ Hai) */
    public static LocalDate mondayOfWeek(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Chủ Nhật (Sunday) của tuần chứa ngày d – theo chuẩn ISO */
    public static LocalDate sundayOfWeek(LocalDate d) {
        return d.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }
}
