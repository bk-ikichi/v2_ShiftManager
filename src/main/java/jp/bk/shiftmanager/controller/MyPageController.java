package jp.bk.shiftmanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** マイページ（本人用の設定画面への入口） */
@Controller
public class MyPageController {

    @GetMapping("/mypage")
    public String index() {
        return "mypage/index";
    }
}
