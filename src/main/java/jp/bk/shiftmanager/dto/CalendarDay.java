package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** トップ画面のカレンダーの1マス */
@Data
public class CalendarDay {
    private LocalDate date;
    /** 日（例：25） */
    private int day;
    /** 表示している月の日か（前後の月の日は空欄にする） */
    private boolean inMonth;
    private boolean today;
    /** 日別一覧へ移動できるか（公開済みの日。スタッフは前月1日以降のみ） */
    private boolean linkable;
    /** 本人のIN（例：11:00）。本人のシフトがない・移動できない日はnull */
    private String startLabel;
    /** 本人の未確認の変更がある日か */
    private boolean changed;
}
