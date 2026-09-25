package jp.bk.shiftmanager.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jp.bk.shiftmanager.auth.PasswordRules;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StaffCreateForm extends StaffEditForm {

    /** 初期パスワード（本人に口頭やLINEで伝える） */
    @NotBlank(message = "初期パスワードを入力してください")
    @Pattern(regexp = PasswordRules.REGEXP, message = PasswordRules.MESSAGE)
    private String password;
}
