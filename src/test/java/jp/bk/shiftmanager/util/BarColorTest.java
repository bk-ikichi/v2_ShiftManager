package jp.bk.shiftmanager.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BarColorTest {

    @Test
    void キーから色を引ける() {
        assertThat(BarColor.fromKey("sky")).contains(BarColor.SKY);
        assertThat(BarColor.SKY.getLabel()).isEqualTo("水色");
        assertThat(BarColor.SKY.getBarClass()).isEqualTo("bg-sky-300 text-sky-950");
        assertThat(BarColor.GRAY.getBarClass()).isEqualTo("bg-stone-300 text-stone-950");
    }

    @Test
    void 選択肢にないキーは空_社員の緑も選択肢にない() {
        assertThat(BarColor.fromKey("green")).isEmpty();
        assertThat(BarColor.fromKey("")).isEmpty();
        assertThat(BarColor.fromKey(null)).isEmpty();
        assertThat(BarColor.fromKey("SKY")).isEmpty();
    }

    @Test
    void 未知のキーはグレーとして扱う() {
        assertThat(BarColor.ofKey("green")).isEqualTo(BarColor.GRAY);
        assertThat(BarColor.ofKey(null)).isEqualTo(BarColor.GRAY);
    }

    @Test
    void 管理者なら社員の緑_そうでなければポジションの色() {
        assertThat(BarColor.barClass("pink", true)).isEqualTo("bg-green-400 text-green-950");
        assertThat(BarColor.barClass("pink", false)).isEqualTo("bg-pink-300 text-pink-950");
        assertThat(BarColor.barClass("unknown", false)).isEqualTo("bg-stone-300 text-stone-950");
    }
}
