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
import java.time.LocalTime;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestEditView;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.CycleUnavailableMapper;
import jp.bk.shiftmanager.mapper.ShiftRequestMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 管理者による申請の代理編集（今日は2026-09-25） */
class AdminRequestEditTest extends IntegrationTestBase {

    private static final LocalDate SEP22 = LocalDate.of(2026, 9, 22);
    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    @Autowired
    ShiftRequestMapper shiftRequestMapper;

    @Autowired
    CycleUnavailableMapper cycleUnavailableMapper;

    LoginUser admin;
    User taro;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
        taro = data.user("taro", "山田太郎", false);
    }

    @Test
    void 一覧のマスから編集画面へ行ける() throws Exception {
        mvc.perform(get("/admin/requests").param("date", "2026-10-01").with(user(admin)))
                .andExpect(content().string(Matchers.containsString(
                        "/admin/requests/edit?userId=" + taro.getId() + "&amp;date=2026-10-02")));
    }

    @Test
    void 編集画面に現在の申請が表示される() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "遅れるかも");

        MvcResult result = mvc.perform(get("/admin/requests/edit")
                        .param("userId", taro.getId().toString()).param("date", "2026-10-02").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("山田太郎")))
                .andReturn();

        RequestEditView view = (RequestEditView) result.getModelAndView().getModel().get("view");
        assertThat(view.isExists()).isTrue();
        assertThat(view.getStartTime()).isEqualTo("09:00");
        assertThat(view.getNote()).isEqualTo("遅れるかも");
        assertThat(view.getDateLabel()).isEqualTo("10/2（金）");
    }

    @Test
    void 締切後の日も代理で登録でき一覧へ戻る() throws Exception {
        mvc.perform(post("/admin/requests/edit").with(user(admin)).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-09-22")
                        .param("startTime", "10:00").param("endTime", "18:00").param("note", " 電話で連絡あり "))
                .andExpect(redirectedUrl("/admin/requests?date=2026-09-22"))
                .andExpect(flash().attribute("message", "保存しました"));

        List<ShiftRequest> saved = shiftRequestMapper.findByUserAndPeriod(taro.getId(), SEP22, SEP22);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(saved.get(0).getNote()).isEqualTo("電話で連絡あり");
    }

    @Test
    void 既存の申請を上書きできる() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "メモ");

        mvc.perform(post("/admin/requests/edit").with(user(admin)).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-10-02")
                        .param("startTime", "12:00").param("endTime", "20:00").param("note", ""))
                .andExpect(redirectedUrl("/admin/requests?date=2026-10-02"));

        ShiftRequest saved = shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT2, OCT2).get(0);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(saved.getNote()).isNull();
    }

    @Test
    void 出勤できない期間に登録するとチェックが外れる() throws Exception {
        data.unavailable(taro, OCT1);

        mvc.perform(post("/admin/requests/edit").with(user(admin)).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-10-02")
                        .param("startTime", "09:00").param("endTime", "17:00").param("note", ""))
                .andExpect(flash().attribute("message", "保存しました（「この期間は出勤できない」のチェックを外しました）"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT1)).isEmpty();
        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT2, OCT2)).hasSize(1);
    }

    @Test
    void 不正な時刻は保存されず編集画面へ戻る() throws Exception {
        for (String[] c : new String[][] {{"07:30", "12:00"}, {"13:00", "12:00"}, {"", "12:00"}}) {
            mvc.perform(post("/admin/requests/edit").with(user(admin)).with(csrf())
                            .param("userId", taro.getId().toString()).param("date", "2026-10-02")
                            .param("startTime", c[0]).param("endTime", c[1]).param("note", ""))
                    .andExpect(redirectedUrl("/admin/requests/edit?userId=" + taro.getId() + "&date=2026-10-02"))
                    .andExpect(flash().attributeExists("error"));
        }
        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT2, OCT2)).isEmpty();
    }

    @Test
    void 申請を削除できる() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", null);

        mvc.perform(post("/admin/requests/delete").with(user(admin)).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-10-02"))
                .andExpect(redirectedUrl("/admin/requests?date=2026-10-02"))
                .andExpect(flash().attribute("message", "削除しました"));

        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT2, OCT2)).isEmpty();
    }

    @Test
    void 存在しないスタッフは編集できない() throws Exception {
        mvc.perform(get("/admin/requests/edit").param("userId", "99999").param("date", "2026-10-02")
                        .with(user(admin)))
                .andExpect(redirectedUrl("/admin/requests"))
                .andExpect(flash().attribute("error", "スタッフが見つかりません"));
    }

    @Test
    void 一般スタッフは代理編集できない() throws Exception {
        mvc.perform(post("/admin/requests/edit").with(user(data.login(taro))).with(csrf())
                        .param("userId", taro.getId().toString()).param("date", "2026-09-22")
                        .param("startTime", "10:00").param("endTime", "18:00").param("note", ""))
                .andExpect(status().isForbidden());

        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), SEP22, SEP22)).isEmpty();
    }
}
