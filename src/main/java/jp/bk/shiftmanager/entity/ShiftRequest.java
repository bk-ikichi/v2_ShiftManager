package jp.bk.shiftmanager.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/** シフト希望の申請（1人1日1件） */
@Data
public class ShiftRequest {
    private Long id;
    private Long userId;
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 備考（なければnull） */
    private String note;
}
