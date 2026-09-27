package jp.bk.shiftmanager.dto;

import java.util.List;
import lombok.Data;

/** 転記画面のポジション1つ分 */
@Data
public class ShiftGroupView {
    private long positionId;
    private String positionName;
    /** そのポジションを初期ポジションとするスタッフ（名前の順） */
    private List<ShiftCandidate> primaryCandidates;
    /** その他のスタッフ（ポジションの表示順、未設定は最後 → 名前の順） */
    private List<ShiftCandidate> otherCandidates;
    private List<ShiftRowView> rows;
}
