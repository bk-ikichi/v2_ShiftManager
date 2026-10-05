package jp.bk.shiftmanager.util;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * シフトのバーの色。ポジションに選べる色と、社員（管理者）の色を持つ。
 * Tailwindはクラス名を文字列連結で組み立てるとCSSに含めないため、クラス名は省略せずに書く
 * （このファイルは app.css の @source でスキャン対象にしている）
 */
@Getter
@RequiredArgsConstructor
public enum BarColor {
    SKY("sky", "水色", "bg-sky-300 text-sky-950"),
    PINK("pink", "ピンク", "bg-pink-300 text-pink-950"),
    RED("red", "赤", "bg-red-300 text-red-950"),
    ORANGE("orange", "オレンジ", "bg-orange-300 text-orange-950"),
    YELLOW("yellow", "黄", "bg-yellow-300 text-yellow-950"),
    PURPLE("purple", "紫", "bg-purple-300 text-purple-950"),
    BLUE("blue", "青", "bg-blue-300 text-blue-950"),
    GRAY("gray", "グレー", "bg-stone-300 text-stone-950");

    /** ポジションの色の初期値 */
    public static final String DEFAULT_KEY = "gray";
    /** 社員（管理者）の凡例の名前 */
    public static final String EMPLOYEE_LABEL = "社員";
    /** 社員（管理者）のバーの色。ポジションの選択肢には入れない */
    public static final String EMPLOYEE_CLASS = "bg-green-400 text-green-950";

    /** DBに保存する値 */
    private final String key;
    /** 画面の表示名 */
    private final String label;
    /** バーに付けるTailwindのクラス */
    private final String barClass;

    /** 選択肢にないキー・nullなら空 */
    public static Optional<BarColor> fromKey(String key) {
        return Arrays.stream(values()).filter(color -> color.key.equals(key)).findFirst();
    }

    /** 表示用。選択肢にないキーはグレーとして扱う */
    public static BarColor ofKey(String key) {
        return fromKey(key).orElse(GRAY);
    }

    /** バーの色：管理者なら社員の緑、そうでなければポジションの色 */
    public static String barClass(String positionColor, boolean admin) {
        return admin ? EMPLOYEE_CLASS : ofKey(positionColor).getBarClass();
    }
}
