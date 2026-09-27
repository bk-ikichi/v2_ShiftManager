package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** トップ画面の次回の出勤 */
@Data
public class MyShiftView {
    private LocalDate date;
    /** 例：9/25（金） */
    private String dateLabel;
    /** 例：09:00〜17:00 */
    private String timeLabel;
    private String positionName;
    /** 今日のシフトか */
    private boolean today;
}
