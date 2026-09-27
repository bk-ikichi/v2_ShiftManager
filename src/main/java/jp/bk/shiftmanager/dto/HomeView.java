package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** スタッフのトップ画面 */
@Data
public class HomeView {
    /** 未確認の「変更あり」（日付順） */
    private List<ChangeNotice> changes;
    /** 今日以降で最も早い公開済みシフト（来月末まで。なければnull） */
    private MyShiftView nextShift;
    /** 月カレンダー */
    private CalendarView calendar;
    /** 締切前で最も近いサイクルの案内 */
    private DeadlineNotice deadline;
    /** 例：9月 */
    private String thisMonthLabel;
    /** 例：13時間30分 */
    private String thisMonthHours;
    private String nextMonthLabel;
    private String nextMonthHours;
}
