package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.util.Cycle;
import lombok.Data;

/** 申請一覧（1サイクル分の日付 × スタッフ） */
@Data
public class RequestTableView {
    private Cycle cycle;
    /** 例：10/1〜10/10 */
    private String label;
    /** 例：9/26（土） */
    private String deadlineLabel;
    private LocalDate previousStart;
    private LocalDate nextStart;
    /** 列見出し（例：1（木）） */
    private List<String> dateLabels;
    private List<RequestTableRow> rows;
}
