package jp.bk.shiftmanager.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.ShiftDayForm;
import jp.bk.shiftmanager.service.PublishService;
import jp.bk.shiftmanager.service.ShiftService;
import jp.bk.shiftmanager.util.DateLabels;
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
    private final PublishService publishService;

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

    @PostMapping("/publish")
    public String publishDay(@RequestParam(required = false) String date, RedirectAttributes redirectAttributes) {
        try {
            publishService.publishDay(date);
            redirectAttributes.addFlashAttribute("message", "公開しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        redirectAttributes.addAttribute("date", shiftService.resolveDate(date).toString());
        return REDIRECT_DAY;
    }

    /** date は戻り先（表示中の日） */
    @PostMapping("/publish-range")
    public String publishRange(@RequestParam(required = false) String date,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            RedirectAttributes redirectAttributes) {
        try {
            List<LocalDate> skipped = publishService.publishRange(from, to);
            String message = DateLabels.monthDay(LocalDate.parse(from)) + "〜"
                    + DateLabels.monthDay(LocalDate.parse(to)) + "を公開しました";
            if (!skipped.isEmpty()) {
                message += "（シフトが登録されていないため公開しなかった日："
                        + skipped.stream().map(DateLabels::monthDay).collect(Collectors.joining("、")) + "）";
            }
            redirectAttributes.addFlashAttribute("message", message);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        redirectAttributes.addAttribute("date", shiftService.resolveDate(date).toString());
        return REDIRECT_DAY;
    }
}
