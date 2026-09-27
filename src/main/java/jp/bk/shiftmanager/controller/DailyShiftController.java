package jp.bk.shiftmanager.controller;

import java.time.LocalDate;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.service.DailyShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 公開済みシフトの日別一覧（スタッフ・管理者） */
@Controller
@RequestMapping("/shifts")
@RequiredArgsConstructor
public class DailyShiftController {

    private final DailyShiftService dailyShiftService;

    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String date,
            Model model) {
        LocalDate target = dailyShiftService.resolveDate(date);
        model.addAttribute("view", dailyShiftService.getDay(target, me.getId(), me.isAdmin()));
        return "shifts/day";
    }
}
