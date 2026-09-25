package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jp.bk.shiftmanager.util.LoginIds;
import lombok.Data;

@Data
public class StaffEditForm {

    @NotBlank(message = "ログインIDを入力してください")
    @Pattern(regexp = "[a-z0-9._-]{3,50}", message = "ログインIDは半角英数字と . _ - の3〜50文字で入力してください")
    private String loginId;

    @NotBlank(message = "名前を入力してください")
    @Size(max = 50, message = "名前は50文字以内で入力してください")
    private String name;

    /** 初期ポジション（未設定はnull） */
    private Long positionId;

    private boolean admin;

    public void setLoginId(String loginId) {
        this.loginId = LoginIds.normalize(loginId);
    }

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }
}
