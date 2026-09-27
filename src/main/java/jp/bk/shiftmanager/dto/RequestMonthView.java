package jp.bk.shiftmanager.dto;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.bk.shiftmanager.form.RequestDayForm;
import jp.bk.shiftmanager.form.RequestMonthForm;
import lombok.Data;

/** 申請画面の1か月分 */
@Data
public class RequestMonthView {
    private YearMonth month;
    /** 申請できる月（切り替えタブ） */
    private List<YearMonth> months;
    private List<RequestCycleView> cycles;

    /** 保存できなかった入力を画面に戻す（締切済みのサイクルには戻さない） */
    public void applyInput(RequestMonthForm form) {
        Map<String, RequestDayForm> inputs = new HashMap<>();
        for (RequestDayForm input : form.getDays()) {
            if (input != null && input.getDate() != null) {
                inputs.put(input.getDate(), input);
            }
        }
        Set<String> checked = new HashSet<>(form.getUnavailableCycles());
        for (RequestCycleView cycle : cycles) {
            if (!cycle.isOpen()) {
                continue;
            }
            cycle.setUnavailable(checked.contains(cycle.getCycle().start().toString()));
            for (RequestDayView day : cycle.getDays()) {
                RequestDayForm input = inputs.get(day.getDate().toString());
                if (input != null) {
                    day.setStartTime(input.getStartTime());
                    day.setEndTime(input.getEndTime());
                    day.setNote(input.getNote());
                }
            }
        }
    }
}
