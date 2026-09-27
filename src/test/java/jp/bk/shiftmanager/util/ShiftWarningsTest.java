package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ShiftWarningsTest {

    private static final TimeRange REQUEST = new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0));

    @Test
    void 申請がなければ警告する() {
        assertThat(ShiftWarnings.of(time("09:00"), time("17:00"), null)).isEqualTo("申請がありません");
        assertThat(ShiftWarnings.of(null, null, null)).isEqualTo("申請がありません");
    }

    @Test
    void 申請の時間帯に収まっていれば警告しない() {
        assertThat(ShiftWarnings.of(time("09:00"), time("17:00"), REQUEST)).isNull();
        assertThat(ShiftWarnings.of(time("10:00"), time("15:00"), REQUEST)).isNull();
    }

    @Test
    void 申請の時間帯からはみ出していれば警告する() {
        assertThat(ShiftWarnings.of(time("08:30"), time("17:00"), REQUEST)).isEqualTo("申請の時間外です");
        assertThat(ShiftWarnings.of(time("09:00"), time("17:30"), REQUEST)).isEqualTo("申請の時間外です");
    }

    @Test
    void 時刻が未選択の側は判定しない() {
        assertThat(ShiftWarnings.of(null, null, REQUEST)).isNull();
        assertThat(ShiftWarnings.of(time("08:00"), null, REQUEST)).isEqualTo("申請の時間外です");
        assertThat(ShiftWarnings.of(null, time("16:00"), REQUEST)).isNull();
    }

    private static LocalTime time(String text) {
        return LocalTime.parse(text);
    }
}
