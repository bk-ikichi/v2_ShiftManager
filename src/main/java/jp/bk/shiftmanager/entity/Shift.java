package jp.bk.shiftmanager.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/** 確定シフト（1人1日1件）。どのポジション枠に入れたかを持つ */
@Data
public class Shift {
    private Long id;
    private LocalDate workDate;
    private Long userId;
    private Long positionId;
    private LocalTime startTime;
    private LocalTime endTime;
}
