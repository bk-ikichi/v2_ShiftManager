package jp.bk.shiftmanager.controller;

import jakarta.validation.Valid;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.form.PatternForm;
import jp.bk.shiftmanager.service.PatternService;
import jp.bk.shiftmanager.util.TimeSlots;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/mypage/patterns")
@RequiredArgsConstructor
public class PatternController {

    private static final String REDIRECT = "redirect:/mypage/patterns";

    private final PatternService patternService;

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser me, Model model) {
        model.addAttribute("patterns", patternService.findMine(me.getId()));
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
        return "mypage/patterns";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal LoginUser me, @Valid @ModelAttribute PatternForm form,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            patternService.create(me.getId(), form);
            redirectAttributes.addFlashAttribute("message", "追加しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
            @Valid @ModelAttribute PatternForm form, BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return REDIRECT;
        }
        try {
            patternService.update(me.getId(), id, form);
            redirectAttributes.addFlashAttribute("message", "保存しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal LoginUser me, @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        try {
            patternService.delete(me.getId(), id);
            redirectAttributes.addFlashAttribute("message", "削除しました");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }
}
