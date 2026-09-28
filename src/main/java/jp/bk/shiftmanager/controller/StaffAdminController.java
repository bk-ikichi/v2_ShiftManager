package jp.bk.shiftmanager.controller;

import jakarta.validation.Valid;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.StaffCreateForm;
import jp.bk.shiftmanager.form.StaffEditForm;
import jp.bk.shiftmanager.service.PositionService;
import jp.bk.shiftmanager.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/staff")
@RequiredArgsConstructor
public class StaffAdminController {

    private final StaffService staffService;
    private final PositionService positionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("staff", staffService.findAll());
        return "admin/staff/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new StaffCreateForm());
        model.addAttribute("positions", positionService.findAll());
        return "admin/staff/new";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") StaffCreateForm form, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                staffService.create(form);
                redirectAttributes.addFlashAttribute("message",
                        form.getName() + "さんを登録しました。ログインIDと初期パスワードを本人に伝えてください");
                return "redirect:/admin/staff";
            } catch (BusinessException e) {
                bindingResult.reject("business", e.getMessage());
            }
        }
        model.addAttribute("positions", positionService.findAll());
        return "admin/staff/new";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("id", id);
        model.addAttribute("form", staffService.editForm(id));
        model.addAttribute("positions", positionService.findAll());
        return "admin/staff/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @AuthenticationPrincipal LoginUser me,
            @Valid @ModelAttribute("form") StaffEditForm form, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                staffService.update(id, form, me.getId());
                redirectAttributes.addFlashAttribute("message", "保存しました");
                return "redirect:/admin/staff";
            } catch (BusinessException e) {
                bindingResult.reject("business", e.getMessage());
            }
        }
        model.addAttribute("id", id);
        model.addAttribute("positions", positionService.findAll());
        return "admin/staff/edit";
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            String tempPassword = staffService.resetPassword(id);
            redirectAttributes.addFlashAttribute("tempPassword", tempPassword);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff/" + id + "/edit";
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@PathVariable long id, @RequestParam boolean enabled,
            @AuthenticationPrincipal LoginUser me, RedirectAttributes redirectAttributes) {
        try {
            staffService.setEnabled(id, enabled, me.getId());
            redirectAttributes.addFlashAttribute("message", enabled ? "有効にしました" : "無効にしました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff";
    }
}
