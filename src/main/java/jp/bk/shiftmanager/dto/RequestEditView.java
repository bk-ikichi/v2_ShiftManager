package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** 代理編集画面の表示内容 */
@Data
public class RequestEditView {
    private long userId;
    private String userName;
    private LocalDate date;
    /** 例：10/2（金） */
    private String dateLabel;
    /** 申請が登録済みか（削除ボタンの表示に使う） */
    private boolean exists;
    private String startTime;
    private String endTime;
    private String note;
}
