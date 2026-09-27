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
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestDayView;
import jp.bk.shiftmanager.dto.RequestMonthView;
import jp.bk.shiftmanager.entity.ShiftRequest;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.AppSettingMapper;
import jp.bk.shiftmanager.mapper.ShiftRequestMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 申請画面（今日は2026-09-25（金）、締切はサイクル開始日の5日前） */
class RequestTest extends IntegrationTestBase {

    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate OCT12 = LocalDate.of(2026, 10, 12);
    private static final String OCT1_CLOSED = "10/1〜10/10は締切を過ぎたため変更できません。変更は管理者に伝えてください";

    @Autowired
    ShiftRequestMapper shiftRequestMapper;

    @Autowired
    AppSettingMapper appSettingMapper;

    User taro;
    LoginUser me;

    @BeforeEach
    void setUp() {
        taro = data.user("taro", "山田太郎", false);
        me = data.login(taro);
    }

    @Test
    void 申請画面に月のサイクルと締切が表示される() throws Exception {
        mvc.perform(get("/requests").param("month", "2026-10").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("10/1〜10/10")))
                .andExpect(content().string(Matchers.containsString("締切 9/26（土）")))
                .andExpect(content().string(Matchers.containsString("10/21〜10/31")))
                .andExpect(content().string(Matchers.containsString("登録する")));
    }

    @Test
    void 申請できるのは今月から翌々月まで() throws Exception {
        MvcResult result = mvc.perform(get("/requests").param("month", "2026-12").with(user(me))).andReturn();
        assertThat(view(result).getMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(view(result).getMonths())
                .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11));

        MvcResult invalid = mvc.perform(get("/requests").param("month", "abc").with(user(me))).andReturn();
        assertThat(view(invalid).getMonth()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void 締切済みのサイクルは入力欄を出さず申請内容だけ表示する() throws Exception {
        data.request(taro, LocalDate.of(2026, 9, 22), "09:00", "17:00", null);

        MvcResult result = mvc.perform(get("/requests").with(user(me)))
                .andExpect(content().string(Matchers.containsString("締切済み")))
                .andReturn();

        RequestDayView day = day(view(result), LocalDate.of(2026, 9, 22));
        assertThat(day.getIndex()).isNull();
        assertThat(day.getStartTime()).isEqualTo("09:00");
    }

    @Test
    void 一括で登録できる() throws Exception {
        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", " 午前だけでも可 ")
                        .param("days[1].date", "2026-10-02").param("days[1].startTime", "")
                        .param("days[1].endTime", "").param("days[1].note", "")
                        .param("days[2].date", "2026-10-12").param("days[2].startTime", "10:00")
                        .param("days[2].endTime", "15:00").param("days[2].note", ""))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        List<ShiftRequest> saved = requests();
        assertThat(saved).extracting(ShiftRequest::getWorkDate).containsExactly(OCT1, OCT12);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.get(0).getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(saved.get(0).getNote()).isEqualTo("午前だけでも可");
        assertThat(saved.get(1).getNote()).isNull();
    }

    @Test
    void 空欄にした日は削除され時刻は上書きされる() throws Exception {
        data.request(taro, OCT1, "09:00", "17:00", null);
        data.request(taro, OCT2, "09:00", "17:00", "メモ");

        mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "")
                        .param("days[0].endTime", "").param("days[0].note", "")
                        .param("days[1].date", "2026-10-02").param("days[1].startTime", "12:00")
                        .param("days[1].endTime", "20:00").param("days[1].note", ""))
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        List<ShiftRequest> saved = requests();
        assertThat(saved).extracting(ShiftRequest::getWorkDate).containsExactly(OCT2);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(saved.get(0).getEndTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(saved.get(0).getNote()).isNull();
    }

    @Test
    void 送信されなかった日の申請は変わらない() throws Exception {
        data.request(taro, OCT12, "09:00", "17:00", null);

        postOneDay("2026-10", "2026-10-01", "09:00", "17:00", "")
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(requests()).extracting(ShiftRequest::getWorkDate).containsExactly(OCT1, OCT12);
    }

    @Test
    void 締切日の当日までは登録できる() throws Exception {
        data.today(LocalDate.of(2026, 9, 26));

        postOneDay("2026-10", "2026-10-01", "09:00", "17:00", "")
                .andExpect(redirectedUrl("/requests?month=2026-10"));

        assertThat(requests()).hasSize(1);
    }

    @Test
    void 締切を過ぎたサイクルは保存されず入力は画面に残る() throws Exception {
        data.today(LocalDate.of(2026, 9, 27));

        MvcResult result = mvc.perform(post("/requests").with(user(me)).with(csrf())
                        .param("month", "2026-10")
                        .param("days[0].date", "2026-10-01").param("days[0].startTime", "09:00")
                        .param("days[0].endTime", "17:00").param("days[0].note", "")
                        .param("days[1].date", "2026-10-12").param("days[1].startTime", "10:00")
                        .param("days[1].endTime", "15:00").param("days[1].note", "昼まで"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", OCT1_CLOSED))
                .andReturn();

        assertThat(requests()).isEmpty();
        RequestDayView day = day(view(result), OCT12);
        assertThat(day.getStartTime()).isEqualTo("10:00");
        assertThat(day.getEndTime()).isEqualTo("15:00");
        assertThat(day.getNote()).isEqualTo("昼まで");
        assertThat(day(view(result), OCT1).getIndex()).isNull();
    }

    @Test
    void 締切日数の変更はすぐに反映される() throws Exception {
        appSettingMapper.updateDeadlineDaysBefore(10);

        postOneDay("2026-10", "2026-10-01", "09:00", "17:00", "")
                .andExpect(model().attribute("error", OCT1_CLOSED));

        assertThat(requests()).isEmpty();
    }

    @Test
    void 不正な時刻は登録されない() throws Exception {
        String timeError = "10/1：時刻は8:00〜23:00の30分刻みで選択してください";
        String orderError = "10/1：OUTはINより後の時刻にしてください";
        String[][] cases = {
                {"07:30", "12:00", timeError}, {"08:15", "12:00", timeError}, {"22:30", "23:30", timeError},
                {"abc", "12:00", timeError}, {"12:00", "12:00", orderError}, {"13:00", "12:00", orderError},
                {"09:00", "", "10/1：INとOUTを選択してください"}};
        for (String[] c : cases) {
            postOneDay("2026-10", "2026-10-01", c[0], c[1], "")
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("error", c[2]));
        }
        assertThat(requests()).isEmpty();
    }

    @Test
    void 備考だけの申請や長すぎる備考は登録されない() throws Exception {
        postOneDay("2026-10", "2026-10-01", "", "", "午後なら可")
                .andExpect(model().attribute("error", "10/1：INとOUTを選択してください"));
        postOneDay("2026-10", "2026-10-01", "09:00", "17:00", "あ".repeat(201))
                .andExpect(model().attribute("error", "10/1：備考は200文字以内で入力してください"));

        assertThat(requests()).isEmpty();
    }

    @Test
    void 対象月以外の日付や申請できない月は登録されない() throws Exception {
        postOneDay("2026-10", "2026-11-01", "09:00", "17:00", "")
                .andExpect(model().attribute("error", "不正な日付です"));
        postOneDay("2026-10", "abc", "09:00", "17:00", "")
                .andExpect(model().attribute("error", "不正な日付です"));
        postOneDay("2027-01", "2027-01-05", "09:00", "17:00", "")
                .andExpect(model().attribute("error", "申請できない月です"));

        assertThat(shiftRequestMapper.findByUserAndPeriod(taro.getId(), OCT1, LocalDate.of(2027, 1, 31))).isEmpty();
    }

    @Test
    void 他のスタッフの申請は表示も変更もされない() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        data.request(hanako, OCT1, "09:00", "17:00", null);

        MvcResult result = mvc.perform(get("/requests").param("month", "2026-10").with(user(me))).andReturn();
        assertThat(day(view(result), OCT1).getStartTime()).isNull();

        postOneDay("2026-10", "2026-10-01", "", "", "");

        assertThat(shiftRequestMapper.findByUserAndPeriod(hanako.getId(), OCT1, OCT1)).hasSize(1);
    }

    private ResultActions postOneDay(String month, String date, String start, String end, String note)
            throws Exception {
        return mvc.perform(post("/requests").with(user(me)).with(csrf())
                .param("month", month)
                .param("days[0].date", date).param("days[0].startTime", start)
                .param("days[0].endTime", end).param("days[0].note", note));
    }

    private List<ShiftRequest> requests() {
        return shiftRequestMapper.findByUserAndPeriod(taro.getId(), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 11, 30));
    }

    private static RequestMonthView view(MvcResult result) {
        return (RequestMonthView) result.getModelAndView().getModel().get("view");
    }

    private static RequestDayView day(RequestMonthView view, LocalDate date) {
        return view.getCycles().stream()
                .flatMap(cycle -> cycle.getDays().stream())
                .filter(day -> day.getDate().equals(date))
                .findFirst().orElseThrow();
    }
}
