package jp.bk.shiftmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 転記画面に埋め込むその日の申請（名前を選んだときの表示と「希望シフトを反映する」に使う） */
@Data
@AllArgsConstructor
public class ShiftRequestInfo {
    private String userId;
    /** 反映できなかったときの表示に使う */
    private String name;
    /** 初期ポジション（未設定ならnull）。反映するときの入れ先 */
    private String positionId;
    /** HH:mm */
    private String start;
    private String end;
    /** 備考（なければnull） */
    private String note;
}
