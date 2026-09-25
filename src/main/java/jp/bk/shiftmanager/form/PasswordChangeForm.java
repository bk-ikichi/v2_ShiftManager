package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jp.bk.shiftmanager.auth.PasswordRules;
import lombok.Data;

@Data
public class PasswordChangeForm {

    @NotBlank(message = "現在のパスワードを入力してください")
    private String currentPassword;

    @NotBlank(message = "新しいパスワードを入力してください")
    @Pattern(regexp = PasswordRules.REGEXP, message = PasswordRules.MESSAGE)
    private String newPassword;

    @NotBlank(message = "確認用パスワードを入力してください")
    private String confirmPassword;
}
