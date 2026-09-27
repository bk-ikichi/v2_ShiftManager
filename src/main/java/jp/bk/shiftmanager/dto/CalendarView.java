package jp.bk.shiftmanager.dto;

import java.time.YearMonth;
import java.util.List;
import lombok.Data;

/** トップ画面の月カレンダー（日曜始まり） */
@Data
public class CalendarView {
    /** 例：2026年9月 */
    private String monthLabel;
    /** 週ごとの7マス */
    private List<List<CalendarDay>> weeks;
    private YearMonth previousMonth;
    /** 前の月へ移動できるか（スタッフは前の月がすべて7日前より前なら移動できない） */
    private boolean previousVisible;
    private YearMonth nextMonth;
}
