package jp.bk.shiftmanager.util;

import java.time.LocalDate;
import java.time.YearMonth;

/** シフトを閲覧できる範囲（日別一覧・トップ画面のカレンダーで共通） */
public final class ViewRange {

    /** カレンダーで表示できる先の月数（今月を含めず、今月9月なら11月まで） */
    public static final int FUTURE_MONTHS = 2;

    private ViewRange() {
    }

    /** スタッフが閲覧できる最も古い日（前月1日）。管理者は制限しない */
    public static LocalDate staffOldest(LocalDate today) {
        return YearMonth.from(today).minusMonths(1).atDay(1);
    }
}
