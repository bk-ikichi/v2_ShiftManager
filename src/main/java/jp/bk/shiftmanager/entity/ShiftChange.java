package jp.bk.shiftmanager.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/** 公開後の変更マーク（「変更あり」）。スタッフが確認済みにするまで表示する */
@Data
public class ShiftChange {
    private Long id;
    private Long userId;
    private LocalDate workDate;
    private ShiftChangeType changeType;
    /** 確認済みにした日時（未確認ならnull） */
    private OffsetDateTime acknowledgedAt;
}
