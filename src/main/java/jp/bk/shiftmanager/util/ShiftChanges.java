package jp.bk.shiftmanager.util;

import java.util.Objects;
import java.util.Optional;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.ShiftChangeType;

/** 公開済みの日の登録前後のシフトから、変更の種別を決める */
public final class ShiftChanges {

    private ShiftChanges() {
    }

    /**
     * 前なし・後あり → ADDED、前あり・後なし → CANCELLED、
     * IN・OUT・ポジションのどれかが違う → UPDATED、差分なし → 空
     */
    public static Optional<ShiftChangeType> detect(Shift before, Shift after) {
        if (before == null && after == null) {
            return Optional.empty();
        }
        if (before == null) {
            return Optional.of(ShiftChangeType.ADDED);
        }
        if (after == null) {
            return Optional.of(ShiftChangeType.CANCELLED);
        }
        boolean same = Objects.equals(before.getPositionId(), after.getPositionId())
                && Objects.equals(before.getStartTime(), after.getStartTime())
                && Objects.equals(before.getEndTime(), after.getEndTime());
        return same ? Optional.empty() : Optional.of(ShiftChangeType.UPDATED);
    }
}
