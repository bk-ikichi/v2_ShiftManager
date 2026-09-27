package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestCycleView;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.CycleUnavailableMapper;
import jp.bk.shiftmanager.mapper.ShiftRequestMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 「この期間は出勤できない」とパターン選択（今日は2026-09-25、締切はサイクル開始日の5日前） */
class RequestUnavailableTest extends IntegrationTestBase {

    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT11 = LocalDate.of(2026, 10, 11);
    private static final LocalDate OCT12 = LocalDate.of(2026, 10, 12);
    private static final LocalDate OCT31 = LocalDate.of(2026, 10, 31);

    @Autowired
    CycleUnavailableMapper cycleUnavailableMapper;

    @Autowired
    ShiftRequestMapper shiftRequestMapper;

    User taro;
    LoginUser me;

    @BeforeEach
    void setUp() {
        taro = data.user("taro", "山田太郎", false);
        me = data.login(taro);
    }

    @Test
    void チェックすると登録されその期間の申請は削除される() throws Exception {
        data.request(taro, OCT12, "09:00", "17:00", null);

        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("unavailableCycles", "2026-10-11")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", ""))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT31)).containsExactly(OCT11);
        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT1, OCT31))
                .extracting(ShiftRequest::getWorkDate).containsExactly(OCT1);
    }

    @Test
    void チェックを外すと解除される() throws Exception {
        data.unavailable(taro, OCT11);

        mvc.perform(post("/requests").with(user(me)).with(csrf()).param("month", "2026-10"))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT31)).isEmpty();
    }

    @Test
    void チェックした期間に入力があると何も保存されない() throws Exception {
        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("unavailableCycles", "2026-10-11")
                        .param("days[0].date", "2026-10-12").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error",
                        "10/11〜10/20は「この期間は出勤できない」にチェックがあるため、申請を入力できません"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT31)).isEmpty();
        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT1, OCT31)).isEmpty();
    }

    @Test
    void エラーで表示し直すときもチェックが残る() throws Exception {
        MvcResult result = mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("unavailableCycles", "2026-10-21")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "07:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", ""))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(view(result).getCycles().get(2).isUnavailable()).isTrue();
    }

    @Test
    void 締切済みの期間のチェックは他の保存で消えない() throws Exception {
        data.today(LocalDate.of(2026, 9, 27));
        data.unavailable(taro, OCT1);

        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("days[0].date", "2026-10-12").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", ""))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, OCT31)).containsExactly(OCT1);
    }

    @Test
    void 締切済みや不正な期間はチェックできない() throws Exception {
        data.today(LocalDate.of(2026, 9, 27));

        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10").param("unavailableCycles", "2026-10-01"))
                .andExpect(model().attribute("error",
                        "10/1〜10/10は締切を過ぎたため変更できません。変更は管理者に伝えてください"));
        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10").param("unavailableCycles", "2026-10-05"))
                .andExpect(model().attribute("error", "不正な日付です"));
        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10").param("unavailableCycles", "2026-11-01"))
                .andExpect(model().attribute("error", "不正な日付です"));

        assertThat(cycleUnavailableMapper.findStarts(taro.getId(), OCT1, LocalDate.of(2026, 11, 30))).isEmpty();
    }

    @Test
    void 画面にチェック状態と自分のパターンが表示される() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        data.unavailable(taro, OCT11);
        data.pattern(taro, "朝", "08:00", "13:00");
        data.pattern(hanako, "他人用", "17:00", "22:00");

        MvcResult result = mvc.perform(get("/requests").param("month", "2026-10").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("data-start=\"08:00\"")))
                .andExpect(content().string(Matchers.containsString("この期間は出勤できない")))
                .andExpect(content().string(Matchers.containsString("/js/requests.js")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("他人用"))))
                .andReturn();

        assertThat(view(result).getCycles()).extracting(RequestCycleView::isUnavailable)
                .containsExactly(false, true, false);
    }

    private static RequestMonthView view(MvcResult result) {
        return (RequestMonthView) result.getModelAndView().getModel().get("view");
    }
}
