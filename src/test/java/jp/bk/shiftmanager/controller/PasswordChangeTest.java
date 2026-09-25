package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.http.Cookie;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.TestData;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.UserMapper;
import jp.bk.shiftmanager.service.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordChangeTest extends IntegrationTestBase {

    @Autowired
    UserMapper userMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PasswordService passwordService;

    @Test
    void パスワード変更が必要なユーザーは他の画面に行くと変更画面へ飛ばされる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        data.requirePasswordChange(taro);

        mvc.perform(get("/").with(user(data.login(taro))))
                .andExpect(redirectedUrl("/password"));
        mvc.perform(get("/password").with(user(data.login(taro))))
                .andExpect(status().isOk());
    }

    @Test
    void パスワードを変更すると変更必須が解除され新しいパスワードが有効になる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        data.requirePasswordChange(taro);

        mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                        .param("currentPassword", TestData.PASSWORD)
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass123"))
                .andExpect(redirectedUrl("/"));

        User updated = userMapper.findById(taro.getId());
        assertThat(passwordEncoder.matches("newpass123", updated.getPasswordHash())).isTrue();
        assertThat(updated.isMustChangePassword()).isFalse();
    }

    @Test
    void 現在のパスワードが違うと変更できない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                        .param("currentPassword", "wrong-pass")
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass123"))
                .andExpect(view().name("password"))
                .andExpect(model().attributeHasErrors("form"));

        assertThat(passwordEncoder.matches(TestData.PASSWORD,
                userMapper.findById(taro.getId()).getPasswordHash())).isTrue();
    }

    @Test
    void 確認用パスワードが一致しないと変更できない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                        .param("currentPassword", TestData.PASSWORD)
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass999"))
                .andExpect(view().name("password"))
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));
    }

    @Test
    void 現在と同じパスワードには変更できない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                        .param("currentPassword", TestData.PASSWORD)
                        .param("newPassword", TestData.PASSWORD)
                        .param("confirmPassword", TestData.PASSWORD))
                .andExpect(view().name("password"))
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    void 短すぎる_全角を含む_73文字以上のパスワードは入力エラーになる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        for (String invalid : new String[] {"short1", "パスワード１２３４５", "a".repeat(73)}) {
            mvc.perform(post("/password").with(user(data.login(taro))).with(csrf())
                            .param("currentPassword", TestData.PASSWORD)
                            .param("newPassword", invalid)
                            .param("confirmPassword", invalid))
                    .andExpect(status().isOk())
                    .andExpect(view().name("password"))
                    .andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        }
    }

    @Test
    void パスワード変更後は別端末の古い保持Cookieが使えなくなる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);
        Cookie rememberMe = mvc.perform(post("/login").param("loginId", "taro")
                        .param("password", TestData.PASSWORD).param("remember-me", "on").with(csrf()))
                .andReturn().getResponse().getCookie("remember-me");

        passwordService.change(taro.getId(), TestData.PASSWORD, "newpass123");

        mvc.perform(get("/").cookie(rememberMe))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
