package jp.bk.shiftmanager.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** 申請のサイクル（毎月1日〜10日・11日〜20日・21日〜月末） */
public record Cycle(LocalDate start, LocalDate end) {

    /** 指定日を含むサイクル */
    public static Cycle of(LocalDate date) {
        int day = date.getDayOfMonth();
        if (day <= 10) {
            return new Cycle(date.withDayOfMonth(1), date.withDayOfMonth(10));
        }
        if (day <= 20) {
            return new Cycle(date.withDayOfMonth(11), date.withDayOfMonth(20));
        }
        return new Cycle(date.withDayOfMonth(21), date.with(TemporalAdjusters.lastDayOfMonth()));
    }

    /** その月の3つのサイクル */
    public static List<Cycle> ofMonth(YearMonth month) {
        return List.of(of(month.atDay(1)), of(month.atDay(11)), of(month.atDay(21)));
    }

    public Cycle next() {
        return of(end.plusDays(1));
    }

    public Cycle previous() {
        return of(start.minusDays(1));
    }

    /** 締切日（サイクル開始日の daysBefore 日前）。この日まではスタッフが編集できる */
    public LocalDate deadline(int daysBefore) {
        return start.minusDays(daysBefore);
    }

    /** スタッフが編集できるか（今日が締切日以前か） */
    public boolean isOpen(LocalDate today, int daysBefore) {
        return !today.isAfter(deadline(daysBefore));
    }

    /** 開始日から終了日までの日付 */
    public List<LocalDate> dates() {
        return start.datesUntil(end.plusDays(1)).toList();
    }

    /** 例：10/11〜10/20 */
    public String label() {
        return DateLabels.monthDay(start) + "〜" + DateLabels.monthDay(end);
    }
}
