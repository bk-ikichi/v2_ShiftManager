package jp.bk.shiftmanager.dto;

import java.util.List;
import jp.bk.shiftmanager.util.Cycle;
import lombok.Data;

/** 申請画面の1サイクル分 */
@Data
public class RequestCycleView {
    private Cycle cycle;
    /** 例：10/11〜10/20 */
    private String label;
    /** 例：10/6（火） */
    private String deadlineLabel;
    /** 締切前でスタッフが編集できるか */
    private boolean open;
    private List<RequestDayView> days;
}
