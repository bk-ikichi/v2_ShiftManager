package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import jp.bk.shiftmanager.exception.BusinessException;
import org.junit.jupiter.api.Test;

class TimeSlotsTest {

    @Test
    void 選択肢は8時から23時まで30分刻み() {
        assertThat(TimeSlots.OPTIONS).hasSize(31)
                .startsWith("08:00", "08:30")
                .endsWith("22:30", "23:00");
    }

    @Test
    void 選択肢の時刻を変換できる() {
        assertThat(TimeSlots.parse("08:00")).isEqualTo(LocalTime.of(8, 0));
        assertThat(TimeSlots.parse(" 23:00 ")).isEqualTo(LocalTime.of(23, 0));
        assertThat(TimeSlots.parse("")).isNull();
        assertThat(TimeSlots.parse("  ")).isNull();
        assertThat(TimeSlots.parse(null)).isNull();
        assertThat(TimeSlots.format(LocalTime.of(9, 30))).isEqualTo("09:30");
        assertThat(TimeSlots.format(null)).isNull();
    }

    @Test
    void 選択肢にない時刻は入力エラー() {
        for (String invalid : new String[] {"07:30", "23:30", "08:15", "8:00", "abc", "25:00"}) {
            assertThatThrownBy(() -> TimeSlots.parse(invalid))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("時刻は8:00〜23:00の30分刻みで選択してください");
        }
    }
}
