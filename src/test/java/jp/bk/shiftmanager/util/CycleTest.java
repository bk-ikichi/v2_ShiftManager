package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CycleTest {

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    void 日付からサイクルを求める() {
        assertThat(Cycle.of(d(2026, 10, 1))).isEqualTo(new Cycle(d(2026, 10, 1), d(2026, 10, 10)));
        assertThat(Cycle.of(d(2026, 10, 10))).isEqualTo(new Cycle(d(2026, 10, 1), d(2026, 10, 10)));
        assertThat(Cycle.of(d(2026, 10, 11))).isEqualTo(new Cycle(d(2026, 10, 11), d(2026, 10, 20)));
        assertThat(Cycle.of(d(2026, 10, 20))).isEqualTo(new Cycle(d(2026, 10, 11), d(2026, 10, 20)));
        assertThat(Cycle.of(d(2026, 10, 21))).isEqualTo(new Cycle(d(2026, 10, 21), d(2026, 10, 31)));
        assertThat(Cycle.of(d(2026, 9, 30))).isEqualTo(new Cycle(d(2026, 9, 21), d(2026, 9, 30)));
    }

    @Test
    void 月末のサイクルは月の日数で長さが変わる() {
        assertThat(Cycle.of(d(2027, 2, 21)).dates()).hasSize(8);
        assertThat(Cycle.of(d(2028, 2, 25)).end()).isEqualTo(d(2028, 2, 29));
        assertThat(Cycle.of(d(2028, 2, 25)).dates()).hasSize(9);
        assertThat(Cycle.of(d(2026, 9, 21)).dates()).hasSize(10);
        assertThat(Cycle.of(d(2026, 10, 21)).dates()).hasSize(11)
                .startsWith(d(2026, 10, 21)).endsWith(d(2026, 10, 31));
    }

    @Test
    void 月のサイクルは3つ() {
        assertThat(Cycle.ofMonth(YearMonth.of(2027, 2))).extracting(Cycle::start)
                .containsExactly(d(2027, 2, 1), d(2027, 2, 11), d(2027, 2, 21));
        assertThat(Cycle.ofMonth(YearMonth.of(2027, 2)).get(2).end()).isEqualTo(d(2027, 2, 28));
    }

    @Test
    void 前後のサイクルは月や年をまたぐ() {
        assertThat(Cycle.of(d(2026, 9, 25)).next()).isEqualTo(new Cycle(d(2026, 10, 1), d(2026, 10, 10)));
        assertThat(Cycle.of(d(2026, 10, 1)).previous()).isEqualTo(new Cycle(d(2026, 9, 21), d(2026, 9, 30)));
        assertThat(Cycle.of(d(2026, 12, 25)).next().start()).isEqualTo(d(2027, 1, 1));
        assertThat(Cycle.of(d(2027, 1, 5)).previous().start()).isEqualTo(d(2026, 12, 21));
    }

    @Test
    void 締切日はサイクル開始日の設定日数前() {
        assertThat(Cycle.of(d(2026, 10, 11)).deadline(5)).isEqualTo(d(2026, 10, 6));
        assertThat(Cycle.of(d(2026, 10, 1)).deadline(5)).isEqualTo(d(2026, 9, 26));
        assertThat(Cycle.of(d(2026, 10, 1)).deadline(0)).isEqualTo(d(2026, 10, 1));
    }

    @Test
    void 締切日の当日までは編集できる() {
        Cycle cycle = Cycle.of(d(2026, 10, 11));
        assertThat(cycle.isOpen(d(2026, 10, 6), 5)).isTrue();
        assertThat(cycle.isOpen(d(2026, 10, 7), 5)).isFalse();
        assertThat(cycle.isOpen(d(2026, 10, 11), 0)).isTrue();
        assertThat(cycle.isOpen(d(2026, 10, 12), 0)).isFalse();
    }

    @Test
    void 表示用の期間() {
        assertThat(Cycle.of(d(2026, 10, 15)).label()).isEqualTo("10/11〜10/20");
    }
}
