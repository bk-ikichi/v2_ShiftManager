package jp.bk.shiftmanager.controller;

import jp.bk.shiftmanager.service.AdminRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 申請の閲覧・代理編集（管理者） */
@Controller
@RequestMapping("/admin/requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private final AdminRequestService adminRequestService;

    @GetMapping
    public String table(@RequestParam(required = false) String date, Model model) {
        model.addAttribute("view", adminRequestService.getTable(adminRequestService.resolveCycle(date)));
        return "admin/requests/table";
    }
}
