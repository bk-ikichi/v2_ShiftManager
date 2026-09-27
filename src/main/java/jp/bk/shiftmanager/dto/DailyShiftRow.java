package jp.bk.shiftmanager.dto;

import lombok.Data;

/** 日別シフト一覧の1人分 */
@Data
public class DailyShiftRow {
    private String name;
    /** 例：09:00〜17:00 */
    private String timeLabel;
    /** 閲覧している本人のシフトか */
    private boolean mine;
}
