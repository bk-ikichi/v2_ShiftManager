package jp.bk.shiftmanager.form;

import lombok.Data;

/** 管理者による申請の代理編集。検証はServiceで行う */
@Data
public class RequestEditForm {
    private Long userId;
    /** yyyy-MM-dd */
    private String date;
    /** HH:mm */
    private String startTime;
    private String endTime;
    private String note;
}
