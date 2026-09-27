package jp.bk.shiftmanager.form;

import lombok.Data;

/** 転記画面の1行の入力。IDも文字列で受け取り、検証はServiceで行う */
@Data
public class ShiftRowForm {
    private String positionId;
    /** 空なら名前未選択 */
    private String userId;
    /** HH:mm */
    private String startTime;
    private String endTime;
}
