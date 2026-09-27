package jp.bk.shiftmanager.dto;

import java.time.YearMonth;
import lombok.Data;

/** トップ画面の申請締切の案内 */
@Data
public class DeadlineNotice {
    /** 例：10/1〜10/10 */
    private String cycleLabel;
    /** 例：9/26（土） */
    private String deadlineLabel;
    private boolean submitted;
    /** 申請画面で開く月（サイクルの開始日の月） */
    private YearMonth month;
}
