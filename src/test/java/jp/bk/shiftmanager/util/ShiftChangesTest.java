package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import org.junit.jupiter.api.Test;

class ShiftChangesTest {

    @Test
    void 前がなく後があれば追加() {
        assertThat(ShiftChanges.detect(null, shift(1L, "09:00", "17:00"))).contains(ShiftChangeType.ADDED);
    }

    @Test
    void 前があり後がなければ取り消し() {
        assertThat(ShiftChanges.detect(shift(1L, "09:00", "17:00"), null)).contains(ShiftChangeType.CANCELLED);
    }

    @Test
    void 時刻かポジションが違えば変更() {
        Shift before = shift(1L, "09:00", "17:00");
        assertThat(ShiftChanges.detect(before, shift(1L, "10:00", "17:00"))).contains(ShiftChangeType.UPDATED);
        assertThat(ShiftChanges.detect(before, shift(1L, "09:00", "16:00"))).contains(ShiftChangeType.UPDATED);
        assertThat(ShiftChanges.detect(before, shift(2L, "09:00", "17:00"))).contains(ShiftChangeType.UPDATED);
    }

    @Test
    void 差分がなければ記録しない() {
        Shift before = shift(1L, "09:00", "17:00");
        before.setId(10L);
        // 登録し直すとIDは変わるが、内容が同じなら変更ではない
        Shift after = shift(1L, "09:00", "17:00");
        after.setId(20L);
        assertThat(ShiftChanges.detect(before, after)).isEmpty();
        assertThat(ShiftChanges.detect(null, null)).isEmpty();
    }

    private static Shift shift(Long positionId, String start, String end) {
        Shift shift = new Shift();
        shift.setPositionId(positionId);
        shift.setStartTime(LocalTime.parse(start));
        shift.setEndTime(LocalTime.parse(end));
        return shift;
    }
}
