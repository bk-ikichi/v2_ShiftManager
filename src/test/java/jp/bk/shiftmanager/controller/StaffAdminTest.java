package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.UserMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class StaffAdminTest extends IntegrationTestBase {

    @Autowired
    UserMapper userMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    User bossUser;
    LoginUser boss;

    @BeforeEach
    void setUp() {
        bossUser = data.user("boss", "店長", true);
        boss = data.login(bossUser);
    }

    @Test
    void 一覧に名前とポジションが表示される() throws Exception {
        Position kitchen = data.position("キッチン", 1);
        User taro = data.user("taro", "山田太郎", false);
        data.assignPosition(taro, kitchen);

        mvc.perform(get("/admin/staff").with(user(boss)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("山田太郎")))
                .andExpect(content().string(Matchers.containsString("キッチン")));
    }

    @Test
    void 登録したスタッフは初期パスワードでログインでき初回に変更が必要になる() throws Exception {
        Position kitchen = data.position("キッチン", 1);

        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "hanako").param("name", "佐藤花子")
                        .param("positionId", kitchen.getId().toString())
                        .param("password", "initpass1"))
                .andExpect(redirectedUrl("/admin/staff"));

        User hanako = userMapper.findByLoginId("hanako");
        assertThat(hanako.getName()).isEqualTo("佐藤花子");
        assertThat(hanako.getPositionId()).isEqualTo(kitchen.getId());
        assertThat(hanako.isAdmin()).isFalse();
        assertThat(hanako.isEnabled()).isTrue();
        assertThat(hanako.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches("initpass1", hanako.getPasswordHash())).isTrue();
    }

    @Test
    void ログインIDは小文字化され前後の空白が除かれる() throws Exception {
        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", " Hanako ").param("name", "佐藤花子").param("password", "initpass1"))
                .andExpect(redirectedUrl("/admin/staff"));

        assertThat(userMapper.findByLoginId("hanako")).isNotNull();
    }

    @Test
    void 大文字小文字違いを含め既存と同じログインIDは登録できない() throws Exception {
        data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "TARO").param("name", "別の太郎").param("password", "initpass1"))
                .andExpect(view().name("admin/staff/new"))
                .andExpect(model().attributeHasErrors("form"));

        assertThat(userMapper.count()).isEqualTo(2);
    }

    @Test
    void ログインIDの形式が不正だと登録できない() throws Exception {
        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "た ろう").param("name", "山田太郎").param("password", "initpass1"))
                .andExpect(view().name("admin/staff/new"))
                .andExpect(model().attributeHasFieldErrors("form", "loginId"));
    }

    @Test
    void 全角を含む初期パスワードは登録できない() throws Exception {
        mvc.perform(post("/admin/staff").with(user(boss)).with(csrf())
                        .param("loginId", "hanako").param("name", "佐藤花子").param("password", "パスワード１２３"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/staff/new"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));
    }

    @Test
    void 名前_ログインID_ポジション_管理者権限を編集できる() throws Exception {
        Position counter = data.position("カウンター", 2);
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff/{id}", taro.getId()).with(user(boss)).with(csrf())
                        .param("loginId", "taro2").param("name", "山田太郎（社員）")
                        .param("positionId", counter.getId().toString()).param("admin", "true"))
                .andExpect(redirectedUrl("/admin/staff"));

        User updated = userMapper.findById(taro.getId());
        assertThat(updated.getLoginId()).isEqualTo("taro2");
        assertThat(updated.getName()).isEqualTo("山田太郎（社員）");
        assertThat(updated.getPositionId()).isEqualTo(counter.getId());
        assertThat(updated.isAdmin()).isTrue();
    }

    @Test
    void 自分の管理者権限は外せない() throws Exception {
        mvc.perform(post("/admin/staff/{id}", bossUser.getId()).with(user(boss)).with(csrf())
                        .param("loginId", "boss").param("name", "店長").param("_admin", "on"))
                .andExpect(view().name("admin/staff/edit"))
                .andExpect(model().attributeHasErrors("form"));

        assertThat(userMapper.findById(bossUser.getId()).isAdmin()).isTrue();
    }

    @Test
    void パスワードをリセットすると仮パスワードが有効になり次回変更が必要になる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff/{id}/password", taro.getId()).with(user(boss)).with(csrf())
                        .param("tempPassword", "temppass1"))
                .andExpect(redirectedUrl("/admin/staff/" + taro.getId() + "/edit"));

        User updated = userMapper.findById(taro.getId());
        assertThat(passwordEncoder.matches("temppass1", updated.getPasswordHash())).isTrue();
        assertThat(updated.isMustChangePassword()).isTrue();
    }

    @Test
    void 仮パスワードが短いとリセットされない() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff/{id}/password", taro.getId()).with(user(boss)).with(csrf())
                        .param("tempPassword", "short"))
                .andExpect(flash().attributeExists("error"));

        assertThat(userMapper.findById(taro.getId()).isMustChangePassword()).isFalse();
    }

    @Test
    void 無効化と有効化ができる() throws Exception {
        User taro = data.user("taro", "山田太郎", false);

        mvc.perform(post("/admin/staff/{id}/enabled", taro.getId()).with(user(boss)).with(csrf())
                        .param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/staff"));
        assertThat(userMapper.findById(taro.getId()).isEnabled()).isFalse();

        mvc.perform(post("/admin/staff/{id}/enabled", taro.getId()).with(user(boss)).with(csrf())
                        .param("enabled", "true"))
                .andExpect(redirectedUrl("/admin/staff"));
        assertThat(userMapper.findById(taro.getId()).isEnabled()).isTrue();
    }

    @Test
    void 自分自身は無効化できない() throws Exception {
        mvc.perform(post("/admin/staff/{id}/enabled", bossUser.getId()).with(user(boss)).with(csrf())
                        .param("enabled", "false"))
                .andExpect(flash().attribute("error", "自分自身は無効化できません"));

        assertThat(userMapper.findById(bossUser.getId()).isEnabled()).isTrue();
    }

    @Test
    void 一般スタッフはスタッフ管理を使えない() throws Exception {
        LoginUser staff = data.login(data.user("taro", "山田太郎", false));

        mvc.perform(get("/admin/staff").with(user(staff))).andExpect(status().isForbidden());
    }
}
