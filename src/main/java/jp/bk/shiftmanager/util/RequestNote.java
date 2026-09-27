package jp.bk.shiftmanager.util;

import jp.bk.shiftmanager.exception.BusinessException;

/** 申請の備考 */
public final class RequestNote {

    public static final int MAX_LENGTH = 200;

    private RequestNote() {
    }

    /** 前後の空白を除き、空ならnull。200文字（DBの文字数）を超えたら入力エラー */
    public static String normalize(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String stripped = note.strip();
        if (stripped.codePointCount(0, stripped.length()) > MAX_LENGTH) {
            throw new BusinessException("備考は200文字以内で入力してください");
        }
        return stripped;
    }
}
