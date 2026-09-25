package jp.bk.shiftmanager.controller;

import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingService settingService;

    @GetMapping
    public String show(Model model) {
        model.addAttribute("deadlineDaysBefore", settingService.getDeadlineDaysBefore());
        return "admin/settings";
    }

    @PostMapping
    public String update(@RequestParam String deadlineDaysBefore, RedirectAttributes redirectAttributes) {
        try {
            settingService.updateDeadlineDaysBefore(deadlineDaysBefore);
            redirectAttributes.addFlashAttribute("message", "保存しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }
}
