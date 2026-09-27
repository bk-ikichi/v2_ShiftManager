package jp.bk.shiftmanager.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 「今日」の基準となる時計。テストでは差し替える */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
