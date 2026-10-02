package jp.bk.shiftmanager.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Stream;
import jp.bk.shiftmanager.exception.BusinessException;

/** シフトの時刻の選択肢（8:00〜23:00、30分刻み） */
public final class TimeSlots {

    public static final LocalTime FIRST = LocalTime.of(8, 0);
    public static final LocalTime LAST = LocalTime.of(23, 0);

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    /** Excelで数値として扱えるよう、コロンと先頭のゼロを付けない */
    private static final DateTimeFormatter COMPACT_FORMAT = DateTimeFormatter.ofPattern("Hmm");

    /** 選択肢の時刻（31個） */
    public static final List<LocalTime> ALL =
            Stream.iterate(FIRST, t -> !t.isAfter(LAST), t -> t.plusMinutes(30)).toList();

    /** プルダウンに表示する文字列（例：08:00） */
    public static final List<String> OPTIONS = ALL.stream().map(TimeSlots::format).toList();

    private TimeSlots() {
    }

    /** 画面入力（HH:mm）を時刻に変換する。空ならnull。選択肢にない時刻は入力エラー */
    public static LocalTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            LocalTime time = LocalTime.parse(text.strip(), FORMAT);
            if (ALL.contains(time)) {
                return time;
            }
        } catch (DateTimeParseException e) {
            // 下で入力エラーにする
        }
        throw new BusinessException("時刻は8:00〜23:00の30分刻みで選択してください");
    }

    /** 例：08:00。nullならnull */
    public static String format(LocalTime time) {
        return time == null ? null : time.format(FORMAT);
    }

    /** 例：800、1230。nullならnull */
    public static String formatCompact(LocalTime time) {
        return time == null ? null : time.format(COMPACT_FORMAT);
    }

    /** 例：09:00〜17:00 */
    public static String formatRange(LocalTime start, LocalTime end) {
        return format(start) + "〜" + format(end);
    }
}
