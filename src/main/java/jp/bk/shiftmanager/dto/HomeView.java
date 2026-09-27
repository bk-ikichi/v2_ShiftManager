package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** スタッフのトップ画面 */
@Data
public class HomeView {
    /** 未確認の「変更あり」（日付順） */
    private List<ChangeNotice> changes;
}
