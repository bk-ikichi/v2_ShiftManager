package jp.bk.shiftmanager.util;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Locale;

/**
 * シフトのバーの位置。8:00〜23:00を100%とする（転記画面の shifts.js も同じ式で計算する）。
 * style はバーの左端と幅、labelStyle は時刻をバーの外に出すときの位置（中に書くときはnull）
 */
public record TimeBar(String style, Label label, String labelStyle) {

    /** 時刻を書く位置 */
    public enum Label { INSIDE, RIGHT, LEFT }

    private static final double SPAN_MINUTES = Duration.between(TimeSlots.FIRST, TimeSlots.LAST).toMinutes();
    /** これより短い勤務は、スマホでは時刻がバーに収まらないため外に出す */
    private static final long INSIDE_MIN_MINUTES = 5 * 60;
    /** OUTがこれより後なら、右側に時刻を書く余白がないため左に出す */
    private static final LocalTime RIGHT_LABEL_LAST = LocalTime.of(19, 0);

    /** どちらかがnull、またはINがOUT以降ならnull */
    public static TimeBar of(LocalTime start, LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            return null;
        }
        double left = percent(Duration.between(TimeSlots.FIRST, start).toMinutes());
        double width = percent(Duration.between(start, end).toMinutes());
        String style = "left:" + format(left) + "%;width:" + format(width) + "%";
        if (Duration.between(start, end).toMinutes() >= INSIDE_MIN_MINUTES) {
            return new TimeBar(style, Label.INSIDE, null);
        }
        if (!end.isAfter(RIGHT_LABEL_LAST)) {
            return new TimeBar(style, Label.RIGHT, "left:" + format(left + width) + "%");
        }
        return new TimeBar(style, Label.LEFT, "right:" + format(100 - left) + "%");
    }

    public boolean labelInside() {
        return label == Label.INSIDE;
    }

    private static double percent(long minutes) {
        return minutes * 100 / SPAN_MINUTES;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
