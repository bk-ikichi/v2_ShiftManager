package jp.bk.shiftmanager.form;

import lombok.Data;

/** 申請画面の1日分の入力。検証はServiceで行う */
@Data
public class RequestDayForm {
    /** yyyy-MM-dd */
    private String date;
    /** HH:mm。空なら申請なし */
    private String startTime;
    private String endTime;
    private String note;
}
