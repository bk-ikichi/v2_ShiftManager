package jp.bk.shiftmanager.entity;

import java.time.LocalTime;
import lombok.Data;

/** スタッフ個人の申請パターン（本人のみ使用） */
@Data
public class ShiftPattern {
    private Long id;
    private Long userId;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
}
