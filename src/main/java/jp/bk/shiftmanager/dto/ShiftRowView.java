package jp.bk.shiftmanager.dto;

import lombok.Data;

/** 転記画面の1行 */
@Data
public class ShiftRowView {
    /** フォームの添字（rows[index]）。画面全体で連番 */
    private int index;
    /** 名前未選択ならnullまたは空文字 */
    private String userId;
    /** HH:mm */
    private String startTime;
    private String endTime;
    /** 申請IN・OUT。名前未選択なら空文字、申請がなければ --:-- */
    private String requestStart;
    private String requestEnd;
    /** 申請の備考（なければnull） */
    private String requestNote;
    /** 申請との差分の警告（なければnull） */
    private String warning;
    /** バーの色のクラス（名前・IN・OUTのどれかが未選択、またはIN ≥ OUTならnull） */
    private String barClass;
    /** バーの左端と幅（バーを出さない行はnull） */
    private String barStyle;
}
