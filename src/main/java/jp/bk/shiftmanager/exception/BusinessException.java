package jp.bk.shiftmanager.exception;

/** 業務ルール違反。メッセージはそのまま画面に表示する */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
