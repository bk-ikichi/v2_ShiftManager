package jp.bk.shiftmanager.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.TestData;
import jp.bk.shiftmanager.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** 実際のログインで作ったセッションを使い、管理者の操作がログイン中のセッションに反映されることを確かめる */
class UserStateCheckFilterTest extends IntegrationTestBase {

    LoginUser boss;

    @BeforeEach
    void setUp() {
        boss = data.login(data.user("boss", "店長", true));
    }

    @Test
    void 無効化されたスタッフのログイン中セッションはログイン画面へ戻される() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        MockHttpSession session = login("taro");
        mvc.perform(get("/").session(session)).andExpect(status().isOk());

        mvc.perform(post("/admin/staff/{id}/enabled", taro.getId()).with(user(boss)).with(csrf())
                        .param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/staff"));

        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 仮パスワードにリセットされたスタッフのログイン中セッションはログイン画面へ戻される() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        MockHttpSession session = login("taro");
        mvc.perform(get("/").session(session)).andExpect(status().isOk());

        mvc.perform(post("/admin/staff/{id}/password", taro.getId()).with(user(boss)).with(csrf())
                        .param("tempPassword", "temppass1"))
                .andExpect(redirectedUrl("/admin/staff/" + taro.getId() + "/edit"));

        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 別の端末でパスワードを変更すると他のセッションは戻され変更した端末は使い続けられる() throws Exception {
        data.user("taro", "山田太郎", false);
        MockHttpSession pc = login("taro");
        MockHttpSession phone = login("taro");

        mvc.perform(post("/password").session(phone).with(csrf())
                        .param("currentPassword", TestData.PASSWORD)
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass123"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/").session(phone)).andExpect(status().isOk());
        mvc.perform(get("/").session(pc)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 管理者権限を外されると次のリクエストから管理画面を使えない() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", true);
        MockHttpSession session = login("hanako");
        mvc.perform(get("/admin/staff").session(session)).andExpect(status().isOk());

        // adminを送らない＝チェックを外した状態
        mvc.perform(post("/admin/staff/{id}", hanako.getId()).with(user(boss)).with(csrf())
                        .param("loginId", "hanako").param("name", "佐藤花子"))
                .andExpect(redirectedUrl("/admin/staff"));

        mvc.perform(get("/admin/staff").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
    }

    @Test
    void 管理者権限を付けられると再ログインなしで管理画面を使える() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        MockHttpSession session = login("taro");
        mvc.perform(get("/admin/staff").session(session)).andExpect(status().isForbidden());

        mvc.perform(post("/admin/staff/{id}", taro.getId()).with(user(boss)).with(csrf())
                        .param("loginId", "taro").param("name", "山田太郎").param("admin", "true"))
                .andExpect(redirectedUrl("/admin/staff"));

        mvc.perform(get("/admin/staff").session(session)).andExpect(status().isOk());
    }

    /** ログイン画面から実際にログインし、そのセッションを返す */
    private MockHttpSession login(String loginId) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").param("loginId", loginId)
                        .param("password", TestData.PASSWORD).with(csrf()))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);
    }
}
