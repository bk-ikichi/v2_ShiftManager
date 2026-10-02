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
    /** バーの色のクラス */
    private String barClass;
    /** バーの左端と幅（例：left:6.6667%;width:53.3333%） */
    private String barStyle;
    /** 時刻をバーの中に書くか（短い勤務はバーの外に書く） */
    private boolean labelInside;
    /** 時刻をバーの外に書くときの位置（中に書くときはnull） */
    private String labelStyle;
}
