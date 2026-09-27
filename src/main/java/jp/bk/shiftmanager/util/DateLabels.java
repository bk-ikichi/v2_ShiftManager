package jp.bk.shiftmanager.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 画面表示用の日付の書式 */
public final class DateLabels {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M/d", Locale.JAPANESE);
    private static final DateTimeFormatter MONTH_DAY_WEEK = DateTimeFormatter.ofPattern("M/d（E）", Locale.JAPANESE);
    private static final DateTimeFormatter DAY_WEEK = DateTimeFormatter.ofPattern("d（E）", Locale.JAPANESE);

    private DateLabels() {
    }

    /** 例：10/3 */
    public static String monthDay(LocalDate date) {
        return date.format(MONTH_DAY);
    }

    /** 例：10/3（土） */
    public static String monthDayWeek(LocalDate date) {
        return date.format(MONTH_DAY_WEEK);
    }

    /** 例：3（土） */
    public static String dayWeek(LocalDate date) {
        return date.format(DAY_WEEK);
    }
}
