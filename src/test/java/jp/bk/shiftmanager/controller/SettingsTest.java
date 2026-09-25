package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.mapper.AppSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SettingsTest extends IntegrationTestBase {

    @Autowired
    AppSettingMapper appSettingMapper;

    LoginUser admin;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
    }

    @Test
    void 設定画面を表示できる() throws Exception {
        mvc.perform(get("/admin/settings").with(user(admin))).andExpect(status().isOk());
    }

    @Test
    void 締切日数を変更できる() throws Exception {
        mvc.perform(post("/admin/settings").with(user(admin)).with(csrf()).param("deadlineDaysBefore", "7"))
                .andExpect(redirectedUrl("/admin/settings"));

        assertThat(appSettingMapper.getDeadlineDaysBefore()).isEqualTo(7);
    }

    @Test
    void 範囲外や数値以外の締切日数は保存されない() throws Exception {
        for (String invalid : new String[] {"-1", "31", "abc", ""}) {
            mvc.perform(post("/admin/settings").with(user(admin)).with(csrf()).param("deadlineDaysBefore", invalid))
                    .andExpect(redirectedUrl("/admin/settings"))
                    .andExpect(flash().attribute("error", "締切日数は0〜30の整数で入力してください"));
        }
        assertThat(appSettingMapper.getDeadlineDaysBefore()).isEqualTo(5);
    }

    @Test
    void 一般スタッフは設定を変更できない() throws Exception {
        LoginUser staff = data.login(data.user("taro", "山田太郎", false));

        mvc.perform(post("/admin/settings").with(user(staff)).with(csrf()).param("deadlineDaysBefore", "7"))
                .andExpect(status().isForbidden());
    }
}
