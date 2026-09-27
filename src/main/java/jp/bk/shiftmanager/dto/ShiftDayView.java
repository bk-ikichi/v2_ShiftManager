package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 転記画面の1日分 */
@Data
public class ShiftDayView {
    private LocalDate date;
    /** 例：10/2（金） */
    private String dateLabel;
    private LocalDate previousDate;
    private String previousLabel;
    private LocalDate nextDate;
    private String nextLabel;
    private boolean published;
    /** ポジションの表示順 */
    private List<ShiftGroupView> groups;
    /** その日の申請（名前を選んだときの表示に使う） */
    private List<ShiftRequestInfo> requests;
    /** 「+ 追加する」で追加する行の最初の添字 */
    private int nextIndex;
    /** 「○日〜○日まで公開」の選択肢（表示中の日を含むサイクルと前後1サイクル） */
    private List<DateOption> rangeOptions;
    /** 期間の初期値（表示中の日を含むサイクルの初日〜末日） */
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
}
