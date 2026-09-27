package jp.bk.shiftmanager.util;

import java.time.LocalTime;

/** 確定シフトの時刻と申請の差分の警告（警告があっても登録はできる） */
public final class ShiftWarnings {

    public static final String NO_REQUEST = "申請がありません";
    public static final String OUT_OF_REQUEST = "申請の時間外です";

    private ShiftWarnings() {
    }

    /**
     * 申請がなければ NO_REQUEST、IN・OUTが申請の時間帯からはみ出していれば OUT_OF_REQUEST、それ以外はnull。
     * 時刻が未選択（null）の側は判定しない。画面の shifts.js も同じ判定をする
     */
    public static String of(LocalTime start, LocalTime end, TimeRange request) {
        if (request == null) {
            return NO_REQUEST;
        }
        if (start != null && start.isBefore(request.start())) {
            return OUT_OF_REQUEST;
        }
        if (end != null && end.isAfter(request.end())) {
            return OUT_OF_REQUEST;
        }
        return null;
    }
}
