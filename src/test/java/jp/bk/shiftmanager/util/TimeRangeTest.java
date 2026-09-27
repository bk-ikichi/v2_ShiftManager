package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import jp.bk.shiftmanager.exception.BusinessException;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

    @Test
    void INとOUTを変換できる() {
        TimeRange range = TimeRange.parse("09:00", "17:30");
        assertThat(range.start()).isEqualTo(LocalTime.of(9, 0));
        assertThat(range.end()).isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    void 片方または両方が空ならエラー() {
        for (String[] pair : new String[][] {{"09:00", ""}, {"", "17:00"}, {"", ""}, {null, null}}) {
            assertThatThrownBy(() -> TimeRange.parse(pair[0], pair[1]))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("INとOUTを選択してください");
        }
    }

    @Test
    void OUTがIN以前ならエラー() {
        for (String[] pair : new String[][] {{"12:00", "12:00"}, {"13:00", "12:00"}}) {
            assertThatThrownBy(() -> TimeRange.parse(pair[0], pair[1]))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("OUTはINより後の時刻にしてください");
        }
    }

    @Test
    void 選択肢にない時刻はエラー() {
        assertThatThrownBy(() -> TimeRange.parse("07:30", "12:00"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("時刻は8:00〜23:00の30分刻みで選択してください");
    }
}
