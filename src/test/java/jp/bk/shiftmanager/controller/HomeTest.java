package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.CalendarDay;
import jp.bk.shiftmanager.dto.CalendarView;
import jp.bk.shiftmanager.dto.DeadlineNotice;
import jp.bk.shiftmanager.dto.HomeView;
import jp.bk.shiftmanager.dto.MyShiftView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.entity.User;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** トップ画面の次回の出勤・カレンダー・締切案内・予定時間（今日は2026-09-25（金）、締切は5日前） */
class HomeTest extends IntegrationTestBase {

    Position kitchen;
    User taro;
    User hanako;
    User boss;
    LoginUser taroLogin;
    LoginUser bossLogin;

    @BeforeEach
    void setUp() {
        kitchen = data.position("キッチン", 1);
        taro = data.user("taro", "山田太郎", false);
        hanako = data.user("hanako", "佐藤花子", false);
        boss = data.user("boss", "店長", true);
        taroLogin = data.login(taro);
        bossLogin = data.login(boss);
    }

    // ---- 次回の出勤 ----

    @Test
    void 次回の出勤は今日以降で最も早い公開済みシフト() throws Exception {
        published(taro, "2026-09-24", "09:00", "17:00");   // 過ぎた日
        published(taro, "2026-09-25", "17:00", "22:00");   // 今日
        data.shift(taro, kitchen, LocalDate.parse("2026-09-27"), "09:00", "17:00"); // 下書き
        published(taro, "2026-09-28", "09:00", "13:00");
        published(hanako, "2026-09-26", "09:00", "17:00"); // 他のスタッフ

        MyShiftView next = view().getNextShift();

        assertThat(next.getDateLabel()).isEqualTo("9/25（金）");
        assertThat(next.isToday()).isTrue();
        assertThat(next.getTimeLabel()).isEqualTo("17:00〜22:00");
        assertThat(next.getPositionName()).isEqualTo("キッチン");
    }

    @Test
    void 次回の出勤は来月のシフトからも探す() throws Exception {
        published(taro, "2026-10-20", "10:00", "15:00");

        MyShiftView next = view().getNextShift();

        assertThat(next.getDateLabel()).isEqualTo("10/20（火）");
        assertThat(next.isToday()).isFalse();
    }

    @Test
    void 公開済みの次回の出勤がなければその旨を表示する() throws Exception {
        data.shift(taro, kitchen, LocalDate.parse("2026-09-26"), "09:00", "17:00"); // 下書き

        MvcResult result = mvc.perform(get("/").with(user(taroLogin)))
                .andExpect(content().string(Matchers.containsString("公開されている次回の出勤はありません")))
                .andReturn();

        assertThat(((HomeView) result.getModelAndView().getModel().get("view")).getNextShift()).isNull();
    }

    // ---- カレンダー ----

    @Test
    void カレンダーは日曜始まりで今月を表示し_前後の月の日は月外とする() throws Exception {
        CalendarView calendar = view().getCalendar();

        assertThat(calendar.getMonthLabel()).isEqualTo("2026年9月");
        // 8/30（日）〜10/3（土）の5週
        assertThat(calendar.getWeeks()).hasSize(5).allSatisfy(week -> assertThat(week).hasSize(7));
        CalendarDay first = calendar.getWeeks().get(0).get(0);
        assertThat(first.getDate()).isEqualTo(LocalDate.parse("2026-08-30"));
        assertThat(first.isInMonth()).isFalse();
        assertThat(calendar.getWeeks().get(0).get(2).getDate()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(calendar.getWeeks().get(4).get(6).getDate()).isEqualTo(LocalDate.parse("2026-10-03"));
        assertThat(day(calendar, "2026-09-25").isToday()).isTrue();
        assertThat(day(calendar, "2026-09-25").getDay()).isEqualTo(25);
    }

    @Test
    void 自分の公開済みシフトの日はINを表示し_下書きの日は押せない() throws Exception {
        published(taro, "2026-09-28", "11:00", "17:00");
        data.shift(taro, kitchen, LocalDate.parse("2026-09-27"), "09:00", "17:00"); // 下書き
        published(hanako, "2026-09-26", "09:00", "17:00"); // 他のスタッフだけの公開済みの日

        MvcResult result = mvc.perform(get("/").with(user(taroLogin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/shifts?date=2026-09-28\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/shifts?date=2026-09-26\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/shifts?date=2026-09-27"))))
                .andReturn();

        CalendarView calendar = ((HomeView) result.getModelAndView().getModel().get("view")).getCalendar();
        assertThat(day(calendar, "2026-09-28").getStartLabel()).isEqualTo("11:00");
        assertThat(day(calendar, "2026-09-28").isLinkable()).isTrue();
        assertThat(day(calendar, "2026-09-27").getStartLabel()).isNull();
        assertThat(day(calendar, "2026-09-27").isLinkable()).isFalse();
        assertThat(day(calendar, "2026-09-26").getStartLabel()).isNull();
        assertThat(day(calendar, "2026-09-26").isLinkable()).isTrue();
    }

    @Test
    void スタッフは7日前より前の日を押せず_INも表示しない() throws Exception {
        published(taro, "2026-09-17", "09:00", "17:00");   // 8日前
        published(taro, "2026-09-18", "10:00", "17:00");   // 7日前

        CalendarView calendar = view().getCalendar();

        assertThat(day(calendar, "2026-09-17").isLinkable()).isFalse();
        assertThat(day(calendar, "2026-09-17").getStartLabel()).isNull();
        assertThat(day(calendar, "2026-09-18").isLinkable()).isTrue();
        assertThat(day(calendar, "2026-09-18").getStartLabel()).isEqualTo("10:00");
        // 先月（8月）は全日が7日前より前のため、前の月へは移動できない
        assertThat(calendar.isPreviousVisible()).isFalse();
    }

    @Test
    void 管理者は過去の日も押せて前の月へ移動できる() throws Exception {
        LocalDate sep1 = LocalDate.parse("2026-09-01");
        data.shift(boss, kitchen, sep1, "08:00", "12:00");
        data.publish(sep1);

        CalendarView calendar = view(bossLogin, "/").getCalendar();

        assertThat(day(calendar, "2026-09-01").isLinkable()).isTrue();
        assertThat(day(calendar, "2026-09-01").getStartLabel()).isEqualTo("08:00");
        assertThat(calendar.isPreviousVisible()).isTrue();
        assertThat(calendar.getPreviousMonth()).isEqualTo(YearMonth.of(2026, 8));
    }

    @Test
    void 月を指定して表示でき_不正な指定は今月を表示する() throws Exception {
        published(taro, "2026-10-31", "08:00", "12:00");

        CalendarView october = view(taroLogin, "/?month=2026-10").getCalendar();
        assertThat(october.getMonthLabel()).isEqualTo("2026年10月");
        assertThat(day(october, "2026-10-31").getStartLabel()).isEqualTo("08:00");
        assertThat(october.isPreviousVisible()).isTrue();
        assertThat(october.getPreviousMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(october.getNextMonth()).isEqualTo(YearMonth.of(2026, 11));

        for (String month : List.of("abc", "2026-13", "")) {
            assertThat(view(taroLogin, "/?month=" + month).getCalendar().getMonthLabel()).isEqualTo("2026年9月");
        }
    }

    @Test
    void 未確認の変更がある日に印を付ける() throws Exception {
        published(taro, "2026-09-28", "09:00", "13:00");
        data.publish(LocalDate.parse("2026-09-29"));
        data.change(taro, LocalDate.parse("2026-09-28"), ShiftChangeType.UPDATED);
        data.change(taro, LocalDate.parse("2026-09-29"), ShiftChangeType.CANCELLED);

        CalendarView calendar = view().getCalendar();

        assertThat(day(calendar, "2026-09-28").isChanged()).isTrue();
        assertThat(day(calendar, "2026-09-29").isChanged()).isTrue();
        assertThat(day(calendar, "2026-09-29").getStartLabel()).isNull();
        assertThat(day(calendar, "2026-09-30").isChanged()).isFalse();
    }

    // ---- 予定時間 ----

    @Test
    void 予定時間は今月と来月の公開済みシフトのOUTからINを引いた合計() throws Exception {
        published(taro, "2026-08-31", "08:00", "23:00");   // 先月
        published(taro, "2026-09-01", "09:00", "17:30");   // 8時間30分
        published(taro, "2026-09-30", "17:00", "22:00");   // 5時間
        data.shift(taro, kitchen, LocalDate.parse("2026-09-29"), "09:00", "23:00"); // 下書き
        published(taro, "2026-10-31", "08:00", "12:00");   // 4時間
        published(taro, "2026-11-01", "08:00", "12:00");   // 翌々月
        published(hanako, "2026-10-01", "08:00", "23:00"); // 他のスタッフ

        HomeView view = view();

        assertThat(view.getThisMonthLabel()).isEqualTo("9月");
        assertThat(view.getThisMonthHours()).isEqualTo("13時間30分");
        assertThat(view.getNextMonthLabel()).isEqualTo("10月");
        assertThat(view.getNextMonthHours()).isEqualTo("4時間");
    }

    @Test
    void シフトがない月の予定時間は0時間() throws Exception {
        HomeView view = view();

        assertThat(view.getThisMonthHours()).isEqualTo("0時間");
        assertThat(view.getNextMonthHours()).isEqualTo("0時間");
    }

    @Test
    void カレンダーの月を変えても予定時間は今月と来月のまま() throws Exception {
        assertThat(view(taroLogin, "/?month=2026-12").getThisMonthLabel()).isEqualTo("9月");
    }

    // ---- 締切案内 ----

    @Test
    void 締切前で最も近いサイクルを案内し_未提出なら強調する() throws Exception {
        // 次のサイクル（10/11〜）の申請だけでは、10/1〜10/10は未提出
        data.request(taro, LocalDate.parse("2026-10-11"), "09:00", "17:00", null);

        MvcResult result = mvc.perform(get("/").with(user(taroLogin)))
                .andExpect(content().string(Matchers.containsString("未提出です")))
                .andExpect(content().string(Matchers.containsString("href=\"/requests?month=2026-10\"")))
                .andReturn();

        DeadlineNotice deadline = ((HomeView) result.getModelAndView().getModel().get("view")).getDeadline();
        assertThat(deadline.getCycleLabel()).isEqualTo("10/1〜10/10");
        assertThat(deadline.getDeadlineLabel()).isEqualTo("9/26（土）");
        assertThat(deadline.isSubmitted()).isFalse();
        assertThat(deadline.getMonth()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void サイクル内に申請が1日以上あれば提出済み() throws Exception {
        data.request(taro, LocalDate.parse("2026-10-10"), "09:00", "17:00", null);

        assertThat(view().getDeadline().isSubmitted()).isTrue();
    }

    @Test
    void 出勤できないにチェックがあれば提出済み() throws Exception {
        data.unavailable(taro, LocalDate.parse("2026-10-01"));

        assertThat(view().getDeadline().isSubmitted()).isTrue();
    }

    @Test
    void 今日が締切日ならそのサイクルを_翌日なら次のサイクルを案内する() throws Exception {
        data.today(LocalDate.parse("2026-09-26"));
        assertThat(view().getDeadline().getCycleLabel()).isEqualTo("10/1〜10/10");

        data.today(LocalDate.parse("2026-09-27"));
        DeadlineNotice deadline = view().getDeadline();
        assertThat(deadline.getCycleLabel()).isEqualTo("10/11〜10/20");
        assertThat(deadline.getDeadlineLabel()).isEqualTo("10/6（火）");
    }

    /** 公開済みのシフトを登録する（同じ日を2回公開しないよう、1日1件で使う） */
    private void published(User user, String date, String start, String end) {
        LocalDate day = LocalDate.parse(date);
        data.shift(user, kitchen, day, start, end);
        data.publish(day);
    }

    /** カレンダーからその日のマスを取り出す */
    private CalendarDay day(CalendarView calendar, String date) {
        LocalDate target = LocalDate.parse(date);
        return calendar.getWeeks().stream()
                .flatMap(List::stream)
                .filter(day -> day.getDate().equals(target))
                .findFirst()
                .orElseThrow();
    }

    private HomeView view() throws Exception {
        return view(taroLogin, "/");
    }

    private HomeView view(LoginUser login, String url) throws Exception {
        MvcResult result = mvc.perform(get(url).with(user(login))).andExpect(status().isOk()).andReturn();
        return (HomeView) result.getModelAndView().getModel().get("view");
    }
}
