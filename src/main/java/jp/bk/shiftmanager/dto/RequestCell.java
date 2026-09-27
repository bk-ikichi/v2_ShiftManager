package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 申請一覧の1マス（申請がなければ時刻・備考はnull） */
@Data
@AllArgsConstructor
public class RequestCell {
    private LocalDate date;
    private String startTime;
    private String endTime;
    private String note;
}
