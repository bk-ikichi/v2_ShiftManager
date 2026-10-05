package jp.bk.shiftmanager.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.User;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HelpTest extends IntegrationTestBase {

    User hanako;
    LoginUser staff;
    LoginUser admin;

    @BeforeEach
    void setUp() {
        hanako = data.user("hanako", "山田花子", false);
        staff = data.login(hanako);
        admin = data.login(data.user("boss", "店長", true));
    }

    @Test
    void スタッフ向けの使い方を表示できる() throws Exception {
        mvc.perform(get("/help").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("使い方（スタッフ向け）")))
                .andExpect(content().string(Matchers.containsString("href=\"/manual/staff.pdf\"")))
                // スタッフには管理者向けへのリンクを出さない
                .andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/admin/help\""))));
    }

    @Test
    void 管理者にはスタッフ向けの使い方に管理者向けへのリンクが出る() throws Exception {
        mvc.perform(get("/help").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/admin/help\"")));
    }

    @Test
    void 管理者向けの使い方は管理者だけが表示できる() throws Exception {
        mvc.perform(get("/admin/help").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("使い方（管理者向け）")))
                .andExpect(content().string(Matchers.containsString("href=\"/admin/manual/admin.pdf\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/help\"")));
        mvc.perform(get("/admin/help").with(user(staff))).andExpect(status().isForbidden());
    }

    @Test
    void ヘッダーに使い方のリンクがある() throws Exception {
        mvc.perform(get("/mypage").with(user(staff)))
                .andExpect(content().string(Matchers.containsString("href=\"/help\"")));
    }

    @Test
    void 未ログインではログイン画面へ移動する() throws Exception {
        mvc.perform(get("/help")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void パスワード変更が必要な人はパスワード変更画面へ移動する() throws Exception {
        data.requirePasswordChange(hanako);
        mvc.perform(get("/help").with(user(data.login(hanako))))
                .andExpect(redirectedUrl("/password"));
    }
}
