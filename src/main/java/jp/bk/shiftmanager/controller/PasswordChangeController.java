package jp.bk.shiftmanager.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PasswordChangeForm;
import jp.bk.shiftmanager.service.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PasswordChangeController {

    private final PasswordService passwordService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping("/password")
    public String form(Model model) {
        model.addAttribute("form", new PasswordChangeForm());
        return "password";
    }

    @PostMapping("/password")
    public String change(@AuthenticationPrincipal LoginUser me,
            @Valid @ModelAttribute("form") PasswordChangeForm form, BindingResult bindingResult,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (!Objects.equals(form.getNewPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "確認用パスワードが一致しません");
        }
        if (bindingResult.hasErrors()) {
            return "password";
        }
        String newHash;
        try {
            newHash = passwordService.change(me.getId(), form.getCurrentPassword(), form.getNewPassword());
        } catch (BusinessException e) {
            bindingResult.reject("business", e.getMessage());
            return "password";
        }
        // セッション上のログイン情報を更新し、変更必須を解除する
        LoginUser updated = me.withPasswordChanged(newHash);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        redirectAttributes.addFlashAttribute("message", "パスワードを変更しました");
        return "redirect:/";
    }
}
