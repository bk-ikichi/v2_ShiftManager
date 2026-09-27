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

import java.time.LocalDate;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.DateOption;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.PublishedDateMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 公開（今日は2026-09-25） */
class ShiftPublishTest extends IntegrationTestBase {

    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate OCT3 = LocalDate.of(2026, 10, 3);
    private static final LocalDate OCT4 = LocalDate.of(2026, 10, 4);
    private static final LocalDate OCT5 = LocalDate.of(2026, 10, 5);

    @Autowired
    PublishedDateMapper publishedDateMapper;

    LoginUser admin;
    Position kitchen;
    User taro;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
        kitchen = data.position("キッチン", 1);
        taro = data.user("taro", "山田太郎", false);
    }

    @Test
    void この日を公開すると公開済みになる() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        mvc.perform(post("/admin/shifts/publish").with(user(admin)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                .andExpect(flash().attribute("message", "公開しました"));

        assertThat(publishedDateMapper.exists(OCT2)).isTrue();
        assertThat(publishedDateMapper.exists(OCT1)).isFalse();
    }

    @Test
    void シフトが登録されていない日は公開できない() throws Exception {
        mvc.perform(post("/admin/shifts/publish").with(user(admin)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                .andExpect(flash().attribute("error", "シフトが登録されていないため公開できません"));

        assertThat(publishedDateMapper.exists(OCT2)).isFalse();
    }

    @Test
    void 公開済みの日をもう一度公開してもエラーにならない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        mvc.perform(post("/admin/shifts/publish").with(user(admin)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(flash().attribute("message", "公開しました"));

        assertThat(publishedDateMapper.exists(OCT2)).isTrue();
    }

    @Test
    void 期間を指定するとシフトがある日だけ公開し公開しなかった日を知らせる() throws Exception {
        data.shift(taro, kitchen, OCT1, "09:00", "17:00");
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(taro, kitchen, OCT5, "09:00", "17:00");

        mvc.perform(post("/admin/shifts/publish-range").with(user(admin)).with(csrf())
                        .param("date", "2026-10-02").param("from", "2026-10-01").param("to", "2026-10-05"))
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                .andExpect(flash().attribute("message",
                        "10/1〜10/5を公開しました（シフトが登録されていないため公開しなかった日：10/3、10/4）"));

        assertThat(publishedDateMapper.exists(OCT1)).isTrue();
        assertThat(publishedDateMapper.exists(OCT2)).isTrue();
        assertThat(publishedDateMapper.exists(OCT3)).isFalse();
        assertThat(publishedDateMapper.exists(OCT4)).isFalse();
        assertThat(publishedDateMapper.exists(OCT5)).isTrue();
    }

    @Test
    void 期間内のすべての日にシフトがあれば公開しなかった日は出さない() throws Exception {
        data.shift(taro, kitchen, OCT1, "09:00", "17:00");
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        mvc.perform(post("/admin/shifts/publish-range").with(user(admin)).with(csrf())
                        .param("date", "2026-10-01").param("from", "2026-10-01").param("to", "2026-10-02"))
                .andExpect(flash().attribute("message", "10/1〜10/2を公開しました"));
    }

    @Test
    void 不正な期間は何も公開しない() throws Exception {
        data.shift(taro, kitchen, OCT1, "09:00", "17:00");
        data.shift(taro, kitchen, OCT5, "09:00", "17:00");

        String[][] cases = {
                {"2026-10-05", "2026-10-01", "終了日は開始日以降の日付を選択してください"},
                {"2026-10-01", "2026-11-01", "一度に公開できるのは31日分までです"},
                {"abc", "2026-10-05", "不正な日付です"},
                {"2026-10-01", "", "不正な日付です"},
                {"2026-10-02", "2026-10-04", "期間内にシフトが登録されている日がないため公開できません"},
        };
        for (String[] c : cases) {
            mvc.perform(post("/admin/shifts/publish-range").with(user(admin)).with(csrf())
                            .param("date", "2026-10-02").param("from", c[0]).param("to", c[1]))
                    .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                    .andExpect(flash().attribute("error", c[2]));
        }

        assertThat(publishedDateMapper.exists(OCT1)).isFalse();
        assertThat(publishedDateMapper.exists(OCT5)).isFalse();
    }

    @Test
    void 期間の初期値は表示中の日のサイクルで選択肢は前後1サイクルずつ() throws Exception {
        ShiftDayView view = view("2026-10-15");
        assertThat(view.getRangeStart()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(view.getRangeEnd()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(view.getRangeOptions()).hasSize(31);
        assertThat(view.getRangeOptions().get(0).getValue()).isEqualTo(OCT1);
        assertThat(view.getRangeOptions().get(0).getLabel()).isEqualTo("10/1（木）");
        assertThat(view.getRangeOptions()).extracting(DateOption::getValue).last()
                .isEqualTo(LocalDate.of(2026, 10, 31));

        // 年をまたぐ
        ShiftDayView december = view("2026-12-25");
        assertThat(december.getRangeStart()).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(december.getRangeEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(december.getRangeOptions().get(0).getValue()).isEqualTo(LocalDate.of(2026, 12, 11));
        assertThat(december.getRangeOptions()).extracting(DateOption::getValue).last()
                .isEqualTo(LocalDate.of(2027, 1, 10));
    }

    @Test
    void 公開済みの日にはこの日を公開ボタンを出さない() throws Exception {
        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("この日を公開")))
                .andExpect(content().string(Matchers.containsString("まで公開")));

        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);
        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.not(Matchers.containsString("この日を公開"))))
                .andExpect(content().string(Matchers.containsString("まで公開")));
    }

    @Test
    void 一般スタッフは公開できない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        LoginUser staff = data.login(taro);

        mvc.perform(post("/admin/shifts/publish").with(user(staff)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/shifts/publish-range").with(user(staff)).with(csrf())
                        .param("date", "2026-10-02").param("from", "2026-10-01").param("to", "2026-10-05"))
                .andExpect(status().isForbidden());

        assertThat(publishedDateMapper.exists(OCT2)).isFalse();
    }

    private ShiftDayView view(String date) throws Exception {
        MvcResult result = mvc.perform(get("/admin/shifts").param("date", date).with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        return (ShiftDayView) result.getModelAndView().getModel().get("view");
    }
}
