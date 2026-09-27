package jp.bk.shiftmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 転記画面の名前の候補 */
@Data
@AllArgsConstructor
public class ShiftCandidate {
    /** 画面の選択値と比べるため文字列にする */
    private String userId;
    private String name;
}
