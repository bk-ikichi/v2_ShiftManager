package jp.bk.shiftmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** バーの色の凡例1つ分 */
@Data
@AllArgsConstructor
public class LegendItem {
    /** ポジション名、または「社員」 */
    private String label;
    private String barClass;
}
