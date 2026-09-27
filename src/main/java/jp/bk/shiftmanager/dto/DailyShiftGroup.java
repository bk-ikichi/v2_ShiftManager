package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** 日別シフト一覧のポジション1つ分 */
@Data
public class DailyShiftGroup {
    private String positionName;
    /** INの早い順 */
    private List<DailyShiftRow> rows;
}
