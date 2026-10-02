package jp.bk.shiftmanager.controller;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.RequestEditForm;
import jp.bk.shiftmanager.service.AdminRequestService;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 申請の閲覧・代理編集（管理者） */
@Controller
@RequestMapping("/admin/requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private static final String REDIRECT_TABLE = "redirect:/admin/requests";

    private final AdminRequestService adminRequestService;

    @GetMapping
    public String table(@RequestParam(required = false) String date, Model model) {
        model.addAttribute("view", adminRequestService.getTable(adminRequestService.resolveCycle(date)));
        return "admin/requests/table";
    }

    /** 申請一覧のドラッグで変えたスタッフの並び順を保存する（画面の fetch から呼ぶ） */
    @PostMapping("/order")
    @ResponseBody
    public ResponseEntity<String> saveOrder(@RequestParam List<Long> userIds) {
        try {
            adminRequestService.saveOrder(userIds);
            return ResponseEntity.ok().build();
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/edit")
    public String edit(@RequestParam long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("view", adminRequestService.getForEdit(userId, date));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return REDIRECT_TABLE;
        }
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
        return "admin/requests/edit";
    }

    @PostMapping("/edit")
    public String save(@ModelAttribute RequestEditForm form, RedirectAttributes redirectAttributes) {
        try {
            boolean unavailableRemoved = adminRequestService.save(form);
            redirectAttributes.addFlashAttribute("message",
                    unavailableRemoved ? "保存しました（「この期間は出勤できない」のチェックを外しました）" : "保存しました");
            redirectAttributes.addAttribute("date", form.getDate());
            return REDIRECT_TABLE;
        } catch (BusinessException e) {
            // 入力は3項目のみのため、編集画面へ戻して選び直してもらう
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addAttribute("userId", form.getUserId());
            redirectAttributes.addAttribute("date", form.getDate());
            return "redirect:/admin/requests/edit";
        }
    }

    @PostMapping("/delete")
    public String delete(@RequestParam long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            RedirectAttributes redirectAttributes) {
        try {
            adminRequestService.delete(userId, date);
            redirectAttributes.addFlashAttribute("message", "削除しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        redirectAttributes.addAttribute("date", date.toString());
        return REDIRECT_TABLE;
    }
}
