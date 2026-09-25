package jp.bk.shiftmanager.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.TestData;
import jp.bk.shiftmanager.entity.User;
import org.junit.jupiter.api.Test;

class LoginTest extends IntegrationTestBase {

    @Test
    void 未ログインでトップにアクセスするとログイン画面へリダイレクトされる() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void ログイン画面は未ログインでも表示できる() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void 正しいIDとパスワードでログインできる() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/login").param("loginId", "taro").param("password", TestData.PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("taro"));
    }

    @Test
    void ログインIDは大文字や前後の空白があってもログインできる() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/login").param("loginId", " Taro ").param("password", TestData.PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("taro"));
    }

    @Test
    void パスワードが違うとログイン画面にエラー付きで戻る() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/login").param("loginId", "taro").param("password", "wrong-pass").with(csrf()))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void 無効化されたスタッフはログインできない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        data.disable(taro);

        mvc.perform(post("/login").param("loginId", "taro").param("password", TestData.PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void ログイン状態の保持を選ぶと10日間有効なCookieが発行される() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/login").param("loginId", "taro").param("password", TestData.PASSWORD)
                        .param("remember-me", "on").with(csrf()))
                .andExpect(cookie().maxAge("remember-me", 864000));
    }

    @Test
    void 保持Cookieがあればセッションなしでもアクセスできるが無効化後は使えない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        Cookie rememberMe = mvc.perform(post("/login").param("loginId", "taro")
                        .param("password", TestData.PASSWORD).param("remember-me", "on").with(csrf()))
                .andReturn().getResponse().getCookie("remember-me");

        mvc.perform(get("/").cookie(rememberMe)).andExpect(status().isOk());

        data.disable(taro);
        mvc.perform(get("/").cookie(rememberMe))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 一般スタッフは管理画面にアクセスできない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(get("/admin/staff").with(user(data.login(taro))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ログアウトするとログイン画面へ戻る() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/logout").with(user(data.login(taro))).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }
}
