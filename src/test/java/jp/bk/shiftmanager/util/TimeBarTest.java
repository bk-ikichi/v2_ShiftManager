package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeBarTest {

    private static TimeBar bar(String start, String end) {
        return TimeBar.of(LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void 左端と幅を8時から23時を100パーセントとして計算する() {
        assertThat(bar("09:00", "17:00").style()).isEqualTo("left:6.6667%;width:53.3333%");
    }

    @Test
    void 端から端までの勤務は左端0_幅100で時刻はバーの中() {
        TimeBar bar = bar("08:00", "23:00");
        assertThat(bar.style()).isEqualTo("left:0.0000%;width:100.0000%");
        assertThat(bar.labelInside()).isTrue();
        assertThat(bar.labelStyle()).isNull();
    }

    @Test
    void 勤務が5時間ちょうどならバーの中_5時間未満ならバーの外() {
        assertThat(bar("10:00", "15:00").label()).isEqualTo(TimeBar.Label.INSIDE);
        assertThat(bar("10:00", "14:30").label()).isEqualTo(TimeBar.Label.RIGHT);
    }

    @Test
    void 外に出すときはOUTが19時以前なら右_19時より後なら左() {
        TimeBar right = bar("17:00", "19:00");
        assertThat(right.label()).isEqualTo(TimeBar.Label.RIGHT);
        // バーの右端の位置から書き始める
        assertThat(right.labelStyle()).isEqualTo("left:73.3333%");

        TimeBar left = bar("17:30", "19:30");
        assertThat(left.label()).isEqualTo(TimeBar.Label.LEFT);
        // バーの左端の位置で書き終える（右からの距離で指定する）
        assertThat(left.labelStyle()).isEqualTo("right:36.6667%");
    }

    @Test
    void 時刻がないかINがOUT以降ならバーを作らない() {
        assertThat(TimeBar.of(null, LocalTime.of(17, 0))).isNull();
        assertThat(TimeBar.of(LocalTime.of(9, 0), null)).isNull();
        assertThat(bar("12:00", "12:00")).isNull();
        assertThat(bar("13:00", "12:00")).isNull();
    }
}
