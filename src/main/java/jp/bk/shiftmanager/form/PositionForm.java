package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.bk.shiftmanager.util.BarColor;
import lombok.Data;

@Data
public class PositionForm {

    @NotBlank(message = "名前を入力してください")
    @Size(max = 30, message = "名前は30文字以内で入力してください")
    private String name;

    @NotNull(message = "表示順を入力してください")
    @Min(value = 0, message = "表示順は0〜999で入力してください")
    @Max(value = 999, message = "表示順は0〜999で入力してください")
    private Integer displayOrder;

    private boolean hidden;

    /** BarColor のキー。選択肢にない値は PositionService で入力エラーにする */
    private String color = BarColor.DEFAULT_KEY;

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
