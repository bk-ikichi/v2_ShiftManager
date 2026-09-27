package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** トップ画面の「変更あり」1件 */
@Data
public class ChangeNotice {
    private Long id;
    private LocalDate date;
    /** 例：10/2（金） */
    private String dateLabel;
    /** 追加・変更・取り消し */
    private String typeLabel;
    /** その日のシフトが取り消されたか */
    private boolean cancelled;
    /** 例：09:00〜17:00（取り消しならnull） */
    private String timeLabel;
    /** 取り消しならnull */
    private String positionName;
}
