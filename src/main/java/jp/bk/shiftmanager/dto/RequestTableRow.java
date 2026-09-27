package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** 申請一覧の1スタッフ分 */
@Data
public class RequestTableRow {
    private long userId;
    private String name;
    private String positionName;
    /** サイクル内に1日以上の申請がある、または「この期間は出勤できない」 */
    private boolean submitted;
    private boolean unavailable;
    private List<RequestCell> cells;
}
