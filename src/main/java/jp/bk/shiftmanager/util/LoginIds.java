package jp.bk.shiftmanager.util;

import java.util.Locale;

/** ログインIDの正規化 */
public final class LoginIds {

    private LoginIds() {
    }

    /** 前後の空白を除去し小文字にする。スマホの自動大文字化・末尾スペース対策 */
    public static String normalize(String loginId) {
        return loginId == null ? null : loginId.strip().toLowerCase(Locale.ROOT);
    }
}
