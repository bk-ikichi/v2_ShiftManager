package jp.bk.shiftmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 転記画面に埋め込むその日の申請（名前を選んだときの表示に使う） */
@Data
@AllArgsConstructor
public class ShiftRequestInfo {
    private String userId;
    /** HH:mm */
    private String start;
    private String end;
    /** 備考（なければnull） */
    private String note;
}
