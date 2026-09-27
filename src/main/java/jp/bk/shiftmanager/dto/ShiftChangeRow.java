package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import lombok.Data;

/** 未確認の変更と、その日の現在のシフト（シフトがなければ時刻・ポジションはnull） */
@Data
public class ShiftChangeRow {
    private Long id;
    private LocalDate workDate;
    private ShiftChangeType changeType;
    private LocalTime startTime;
    private LocalTime endTime;
    private String positionName;
}
