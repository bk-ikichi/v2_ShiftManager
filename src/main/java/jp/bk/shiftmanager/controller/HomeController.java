package jp.bk.shiftmanager.controller;

import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** スタッフのトップ画面（管理者も出勤者として同じ画面を使う） */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /** トップ画面。month（yyyy-MM）でカレンダーの月を指定する */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String month,
            Model model) {
        model.addAttribute("view", homeService.getHome(me.getId(), me.isAdmin(), month));
        return "home";
    }

    /** 「変更あり」を確認済みにする */
    @PostMapping("/changes/acknowledge")
    public String acknowledge(@AuthenticationPrincipal LoginUser me, @RequestParam(required = false) String id) {
        homeService.acknowledge(me.getId(), id);
        return "redirect:/";
    }
}
