package jp.bk.shiftmanager.controller;

import jakarta.validation.Valid;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PositionForm;
import jp.bk.shiftmanager.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/positions")
@RequiredArgsConstructor
public class PositionAdminController {

    private static final String REDIRECT = "redirect:/admin/positions";

    private final PositionService positionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("positions", positionService.findAll());
        return "admin/positions";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute PositionForm form, BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            positionService.create(form);
            redirectAttributes.addFlashAttribute("message", "追加しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute PositionForm form,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            positionService.update(id, form);
            redirectAttributes.addFlashAttribute("message", "保存しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            positionService.delete(id);
            redirectAttributes.addFlashAttribute("message", "削除しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }
}
