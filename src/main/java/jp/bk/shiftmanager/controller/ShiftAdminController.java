package jp.bk.shiftmanager.controller;

import jp.bk.shiftmanager.service.ShiftService;
import jp.bk.shiftmanager.util.TimeSlots;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 確定シフトの転記・公開（管理者） */
@Controller
@RequestMapping("/admin/shifts")
@RequiredArgsConstructor
public class ShiftAdminController {

    private static final String VIEW = "admin/shifts/day";

    private final ShiftService shiftService;

    @GetMapping
    public String show(@RequestParam(required = false) String date, Model model) {
        model.addAttribute("view", shiftService.getDay(shiftService.resolveDate(date)));
        model.addAttribute("timeOptions", TimeSlots.OPTIONS);
        return VIEW;
    }
}
