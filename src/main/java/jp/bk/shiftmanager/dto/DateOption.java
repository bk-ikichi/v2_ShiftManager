package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 日付のプルダウンの選択肢 */
@Data
@AllArgsConstructor
public class DateOption {
    private LocalDate value;
    /** 例：10/1（木） */
    private String label;
}
