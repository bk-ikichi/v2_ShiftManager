package jp.bk.shiftmanager.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TempPasswordsTest {

    @Test
    void 英小文字と数字の8文字で紛らわしい文字を含まない() {
        for (int i = 0; i < 200; i++) {
            String password = TempPasswords.generate();
            assertThat(password).matches("[a-z2-9]{8}");
            assertThat(password).doesNotContain("0", "o", "1", "l", "i");
            assertThat(password).matches(PasswordRules.REGEXP);
        }
    }

    @Test
    void 繰り返し生成すると異なる値になる() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            passwords.add(TempPasswords.generate());
        }
        assertThat(passwords).hasSize(50);
    }
}
