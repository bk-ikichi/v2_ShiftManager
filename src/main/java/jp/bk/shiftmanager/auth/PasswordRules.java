package jp.bk.shiftmanager.auth;

/** パスワードの入力ルール（BCryptの72バイト上限に収めるため半角のみ） */
public final class PasswordRules {

    public static final String REGEXP = "[\\x21-\\x7E]{8,72}";
    public static final String MESSAGE = "パスワードは半角英数字記号で8〜72文字にしてください";

    private PasswordRules() {
    }
}
