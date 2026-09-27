package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/** 本人の公開済みシフト（トップ画面の取得用） */
@Data
public class MyShiftRow {
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String positionName;
}
