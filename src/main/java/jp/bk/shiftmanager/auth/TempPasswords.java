package jp.bk.shiftmanager.auth;

import java.security.SecureRandom;

/** 管理者がリセットしたときの仮パスワードを生成する。口頭やLINEで伝えやすいよう英小文字と数字だけにする */
public final class TempPasswords {

    /** 読み間違えやすい 0 o 1 l i を除いた文字 */
    private static final String CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TempPasswords() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
