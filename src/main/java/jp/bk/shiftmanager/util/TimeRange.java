package jp.bk.shiftmanager.util;

import java.time.LocalTime;
import jp.bk.shiftmanager.exception.BusinessException;

/** IN・OUTの組（8:00〜23:00の30分刻み、IN < OUT） */
public record TimeRange(LocalTime start, LocalTime end) {

    /** 画面入力の文字列を検証して変換する */
    public static TimeRange parse(String start, String end) {
        LocalTime startTime = TimeSlots.parse(start);
        LocalTime endTime = TimeSlots.parse(end);
        if (startTime == null || endTime == null) {
            throw new BusinessException("INとOUTを選択してください");
        }
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException("OUTはINより後の時刻にしてください");
        }
        return new TimeRange(startTime, endTime);
    }
}
