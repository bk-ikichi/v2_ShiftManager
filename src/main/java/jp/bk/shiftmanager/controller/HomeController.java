package jp.bk.shiftmanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** トップ画面（Plan 4でスタッフのトップ画面に置き換える） */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
