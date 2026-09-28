package jp.bk.shiftmanager.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StaffInitialPasswordTest {

    @Test
    void ルールに合う値を保持する() {
        assertThat(new StaffInitialPassword("testinit1").value()).isEqualTo("testinit1");
    }

    @Test
    void 短い値は起動時にエラーにする() {
        assertThatThrownBy(() -> new StaffInitialPassword("short12"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STAFF_INITIAL_PASSWORD");
    }

    @Test
    void 全角を含む値は起動時にエラーにする() {
        assertThatThrownBy(() -> new StaffInitialPassword("パスワード１２３"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 空の値は起動時にエラーにする() {
        assertThatThrownBy(() -> new StaffInitialPassword(""))
                .isInstanceOf(IllegalStateException.class);
    }
}
