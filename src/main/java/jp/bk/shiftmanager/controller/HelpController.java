package jp.bk.shiftmanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 使い方（ヘルプ画面）。本文はテンプレートに直接書いているため、業務処理は呼ばない */
@Controller
public class HelpController {

    @GetMapping("/help")
    public String staff() {
        return "help/staff";
    }

    /** 管理者向け。/admin/** のため管理者のみ表示できる */
    @GetMapping("/admin/help")
    public String admin() {
        return "help/admin";
    }
}
