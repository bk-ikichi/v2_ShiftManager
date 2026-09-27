package jp.bk.shiftmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TestClockTest extends IntegrationTestBase {

    @Autowired
    Clock clock;

    @Test
    void アプリの今日はテストから変更できる() {
        assertThat(LocalDate.now(clock)).isEqualTo(TestClock.DEFAULT_TODAY);

        data.today(LocalDate.of(2026, 10, 7));

        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 7));
    }
}
