package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jp.bk.shiftmanager.exception.BusinessException;
import org.junit.jupiter.api.Test;

class RequestNoteTest {

    @Test
    void 前後の空白を除き空ならnullにする() {
        assertThat(RequestNote.normalize(" 午前のみ ")).isEqualTo("午前のみ");
        assertThat(RequestNote.normalize("")).isNull();
        assertThat(RequestNote.normalize("  ")).isNull();
        assertThat(RequestNote.normalize(null)).isNull();
    }

    @Test
    void 備考は200文字まで() {
        assertThat(RequestNote.normalize("あ".repeat(200))).hasSize(200);
        assertThatThrownBy(() -> RequestNote.normalize("あ".repeat(201)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("備考は200文字以内で入力してください");
    }
}
