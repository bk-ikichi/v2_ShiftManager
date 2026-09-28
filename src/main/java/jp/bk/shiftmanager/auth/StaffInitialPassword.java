package jp.bk.shiftmanager.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** スタッフ登録で初期パスワードを省略したときに使う共通の固定値。ルールに合わなければ起動を止める */
@Component
public class StaffInitialPassword {

    private final String value;

    public StaffInitialPassword(@Value("${app.staff-initial-password}") String value) {
        if (value == null || !value.matches(PasswordRules.REGEXP)) {
            throw new IllegalStateException(
                    "環境変数 STAFF_INITIAL_PASSWORD がパスワードのルールに合いません：" + PasswordRules.MESSAGE);
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
