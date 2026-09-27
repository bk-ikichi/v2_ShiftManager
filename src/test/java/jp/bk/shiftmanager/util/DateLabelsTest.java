package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateLabelsTest {

    private static final LocalDate OCT3 = LocalDate.of(2026, 10, 3);

    @Test
    void 画面表示用の日付() {
        assertThat(DateLabels.monthDay(OCT3)).isEqualTo("10/3");
        assertThat(DateLabels.monthDayWeek(OCT3)).isEqualTo("10/3（土）");
        assertThat(DateLabels.dayWeek(OCT3)).isEqualTo("3（土）");
    }
}
