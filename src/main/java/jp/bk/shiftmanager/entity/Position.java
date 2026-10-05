package jp.bk.shiftmanager.entity;

import jp.bk.shiftmanager.util.BarColor;
import lombok.Data;

/** ポジション（転記画面・日別一覧のグループ分けに使う） */
@Data
public class Position {
    private Long id;
    private String name;
    /** 上からの並び順（昇順） */
    private int displayOrder;
    /** 使用中で削除できないポジションを選択肢から外す */
    private boolean hidden;
    /** バーの色（BarColor のキー） */
    private String color = BarColor.DEFAULT_KEY;
}
