package jp.bk.shiftmanager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import jp.bk.shiftmanager.config.ClockConfig;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

/** テスト用の時計。今日の日付をテストから変更できる（初期値は2026-09-25の12:00） */
@TestComponent
@Primary
public class TestClock extends Clock {

    public static final LocalDate DEFAULT_TODAY = LocalDate.of(2026, 9, 25);

    private volatile Instant instant;

    public TestClock() {
        setToday(DEFAULT_TODAY);
    }

    public void setToday(LocalDate today) {
        instant = today.atTime(12, 0).atZone(ClockConfig.ZONE).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return ClockConfig.ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
