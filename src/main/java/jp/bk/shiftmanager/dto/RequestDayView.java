package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.Data;

/** 申請画面の1日（1行） */
@Data
public class RequestDayView {
    private LocalDate date;
    /** 例：3（土） */
    private String label;
    /** フォームの添字。締切済みの日はnull（入力欄を出さず、送信もしない） */
    private Integer index;
    /** HH:mm。申請がなければnull */
    private String startTime;
    private String endTime;
    private String note;
}
