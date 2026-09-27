package jp.bk.shiftmanager.entity;

/** 公開後の変更の種別（shift_changes.change_type） */
public enum ShiftChangeType {
    /** 追加 */
    ADDED,
    /** 時刻またはポジションの変更 */
    UPDATED,
    /** 取り消し */
    CANCELLED
}
