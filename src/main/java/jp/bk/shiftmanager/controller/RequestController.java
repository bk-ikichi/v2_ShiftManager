package jp.bk.shiftmanager.controller;

import java.time.YearMonth;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestMonthForm;
import jp.bk.shiftmanager.service.RequestService;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** シフト希望の申請（スタッフ本人） */
@Controller
@RequestMapping("/requests")
@RequiredArgsConstructor
public class RequestController {

    private static final String VIEW = "requests/month";

    private final RequestService requestService;

    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month,
            Model model) {
        YearMonth target = requestService.resolveMonth(month);
        model.addAttribute("view", requestService.getMonth(me.getId(), target));
        addOptions(model);
        return VIEW;
    }

    @PostMapping
    public String save(@AuthenticationPrincipal LoginUser me, @ModelAttribute RequestMonthForm form, Model model,
            RedirectAttributes redirectAttributes) {
        YearMonth target = requestService.resolveMonth(form.getMonth());
        try {
            requestService.saveMonth(me.getId(), form);
        } catch (BusinessException e) {
            // 1か月分の入力を消さないよう、リダイレクトせずに表示し直す
            RequestMonthView view = requestService.getMonth(me.getId(), target);
            view.applyInput(form);
            model.addAttribute("view", view);
            model.addAttribute("error", e.getMessage());
            addOptions(model);
            return VIEW;
        }
        redirectAttributes.addFlashAttribute("message", "登録しました");
        redirectAttributes.addAttribute("month", target.toString());
        return "redirect:/requests";
    }

    private void addOptions(Model model) {
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
    }
}
