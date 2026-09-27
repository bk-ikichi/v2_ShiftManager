package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PatternForm {

    @NotBlank(message = "名前を入力してください")
    @Size(max = 20, message = "名前は20文字以内で入力してください")
    private String name;

    /** HH:mm。検証はServiceで行う */
    private String startTime;

    private String endTime;

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
