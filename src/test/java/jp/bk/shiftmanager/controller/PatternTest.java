package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.ShiftPattern;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.ShiftPatternMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PatternTest extends IntegrationTestBase {

    @Autowired
    ShiftPatternMapper shiftPatternMapper;

    User taro;
    LoginUser me;

    @BeforeEach
    void setUp() {
        taro = data.user("taro", "山田太郎", false);
        me = data.login(taro);
    }

    @Test
    void マイページにパターンとパスワード変更へのリンクがある() throws Exception {
        mvc.perform(get("/mypage").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/mypage/patterns\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/password\"")));
    }

    @Test
    void ヘッダーからマイページへ行ける() throws Exception {
        mvc.perform(get("/").with(user(me)))
                .andExpect(content().string(Matchers.containsString("href=\"/mypage\"")));
    }

    @Test
    void パターンを追加できる() throws Exception {
        mvc.perform(post("/mypage/patterns").with(user(me)).with(csrf())
                        .param("name", " 朝 ").param("startTime", "08:00").param("endTime", "13:00"))
                .andExpect(redirectedUrl("/mypage/patterns"));

        List<ShiftPattern> patterns = shiftPatternMapper.findByUserId(taro.getId());
        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0).getName()).isEqualTo("朝");
        assertThat(patterns.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(patterns.get(0).getEndTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void 自分のパターンだけが一覧に表示される() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        data.pattern(taro, "朝", "08:00", "13:00");
        data.pattern(hanako, "他人用", "17:00", "22:00");

        mvc.perform(get("/mypage/patterns").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("朝")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("他人用"))));
    }

    @Test
    void 時刻が不正なパターンは追加されない() throws Exception {
        String[][] cases = {{"07:30", "12:00"}, {"08:15", "12:00"}, {"22:30", "23:30"}, {"abc", "12:00"},
                {"12:00", "12:00"}, {"13:00", "12:00"}, {"", "12:00"}};
        for (String[] c : cases) {
            mvc.perform(post("/mypage/patterns").with(user(me)).with(csrf())
                            .param("name", "朝").param("startTime", c[0]).param("endTime", c[1]))
                    .andExpect(redirectedUrl("/mypage/patterns"))
                    .andExpect(flash().attributeExists("error"));
        }
        assertThat(shiftPatternMapper.findByUserId(taro.getId())).isEmpty();
    }

    @Test
    void 名前が空または21文字以上のパターンは追加されない() throws Exception {
        for (String name : new String[] {" ", "あ".repeat(21)}) {
            mvc.perform(post("/mypage/patterns").with(user(me)).with(csrf())
                            .param("name", name).param("startTime", "08:00").param("endTime", "13:00"))
                    .andExpect(flash().attributeExists("error"));
        }
        assertThat(shiftPatternMapper.findByUserId(taro.getId())).isEmpty();
    }

    @Test
    void パターンを編集できる() throws Exception {
        ShiftPattern pattern = data.pattern(taro, "朝", "08:00", "13:00");

        mvc.perform(post("/mypage/patterns/{id}", pattern.getId()).with(user(me)).with(csrf())
                        .param("name", "早朝").param("startTime", "08:00").param("endTime", "12:00"))
                .andExpect(redirectedUrl("/mypage/patterns"))
                .andExpect(flash().attribute("message", "保存しました"));

        ShiftPattern saved = shiftPatternMapper.findById(pattern.getId());
        assertThat(saved.getName()).isEqualTo("早朝");
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void パターンを削除できる() throws Exception {
        ShiftPattern pattern = data.pattern(taro, "朝", "08:00", "13:00");

        mvc.perform(post("/mypage/patterns/{id}/delete", pattern.getId()).with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/mypage/patterns"));

        assertThat(shiftPatternMapper.findById(pattern.getId())).isNull();
    }

    @Test
    void 他人のパターンは編集も削除もできない() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        ShiftPattern others = data.pattern(hanako, "夕方", "17:00", "22:00");

        mvc.perform(post("/mypage/patterns/{id}", others.getId()).with(user(me)).with(csrf())
                        .param("name", "乗っ取り").param("startTime", "08:00").param("endTime", "12:00"))
                .andExpect(flash().attribute("error", "パターンが見つかりません"));
        mvc.perform(post("/mypage/patterns/{id}/delete", others.getId()).with(user(me)).with(csrf()))
                .andExpect(flash().attribute("error", "パターンが見つかりません"));

        ShiftPattern saved = shiftPatternMapper.findById(others.getId());
        assertThat(saved.getName()).isEqualTo("夕方");
    }
}
