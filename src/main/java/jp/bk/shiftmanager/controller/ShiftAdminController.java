package jp.bk.shiftmanager.controller;

import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.ShiftDayForm;
import jp.bk.shiftmanager.service.ShiftService;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 確定シフトの転記・公開（管理者） */
@Controller
@RequestMapping("/admin/shifts")
@RequiredArgsConstructor
public class ShiftAdminController {

    private static final String VIEW = "admin/shifts/day";
    private static final String REDIRECT_DAY = "redirect:/admin/shifts";

    private final ShiftService shiftService;

    @GetMapping
    public String show(@RequestParam(required = false) String date, Model model) {
        model.addAttribute("view", shiftService.getDay(shiftService.resolveDate(date)));
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
        return VIEW;
    }

    @PostMapping
    public String save(@ModelAttribute ShiftDayForm form, Model model, RedirectAttributes redirectAttributes) {
        try {
            shiftService.saveDay(form);
        } catch (BusinessException e) {
            // 1日分の入力を消さないよう、リダイレクトせずに表示し直す
            model.addAttribute("view", shiftService.getDay(shiftService.resolveDate(form.getDate()), form));
            model.addAttribute("error", e.getMessage());
            model.addAttribute("timeOptions", TimeSlots.OPTIONS);
            return VIEW;
        }
        redirectAttributes.addFlashAttribute("message", "登録しました");
        redirectAttributes.addAttribute("date", form.getDate());
        return REDIRECT_DAY;
    }
}
