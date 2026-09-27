package jp.bk.shiftmanager.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 日別シフト一覧の1日分 */
@Data
public class DailyShiftView {
    private LocalDate date;
    /** 例：10/2（金） */
    private String dateLabel;
    private LocalDate previousDate;
    private String previousLabel;
    /** 前の日へ移動できるか（スタッフは1週間より前の日へ移動できない） */
    private boolean previousVisible;
    private LocalDate nextDate;
    private String nextLabel;
    /** 一覧を表示できない理由、または出勤者がいない旨（一覧を表示するときはnull） */
    private String message;
    /** ポジションの表示順。出勤者のいないポジションは含めない */
    private List<DailyShiftGroup> groups = new ArrayList<>();
    /** 管理者なら転記画面へのリンクを出す */
    private boolean admin;
}
