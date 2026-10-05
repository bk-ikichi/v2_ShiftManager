package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.DailyShiftGroup;
import jp.bk.shiftmanager.dto.DailyShiftRow;
import jp.bk.shiftmanager.dto.DailyShiftView;
import jp.bk.shiftmanager.dto.LegendItem;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.PositionMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 日別シフト一覧（今日は2026-09-25（金）） */
class DailyShiftTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate OCT4 = LocalDate.of(2026, 10, 4);
    private static final LocalDate OCT5 = LocalDate.of(2026, 10, 5);
    /** 前月1日（スタッフが閲覧できる最も古い日） */
    private static final LocalDate AUG1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate JUL31 = LocalDate.of(2026, 7, 31);

    @Autowired
    PositionMapper positionMapper;

    LoginUser admin;
    Position kitchen;
    Position counter;
    User taro;
    User hanako;
    User jiro;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
        // 作成順と表示順を逆にして、表示順で並ぶことを確かめる
        counter = data.position("カウンター", 2);
        kitchen = data.position("キッチン", 1);
        taro = data.user("taro", "山田太郎", false);
        hanako = data.user("hanako", "佐藤花子", false);
        jiro = data.user("jiro", "鈴木次郎", false);
    }

    @Test
    void 公開済みの日はポジションの表示順とINの早い順で全員分を表示する() throws Exception {
        // 出勤者のいないポジションは表示しない
        data.position("社員", 0);
        data.shift(hanako, counter, OCT2, "12:00", "18:00");
        data.shift(jiro, kitchen, OCT2, "10:00", "15:00");
        data.shift(taro, kitchen, OCT2, "08:00", "17:00");
        data.publish(OCT2);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getDateLabel()).isEqualTo("10/2（金）");
        assertThat(view.getMessage()).isNull();
        assertThat(view.isAdmin()).isFalse();
        assertThat(view.getGroups()).extracting(DailyShiftGroup::getPositionName)
                .containsExactly("キッチン", "カウンター");
        assertThat(view.getGroups().get(0).getRows())
                .extracting(DailyShiftRow::getName, DailyShiftRow::getTimeLabel, DailyShiftRow::isMine)
                .containsExactly(tuple("山田太郎", "08:00〜17:00", true), tuple("鈴木次郎", "10:00〜15:00", false));
        assertThat(view.getGroups().get(1).getRows()).extracting(DailyShiftRow::getName)
                .containsExactly("佐藤花子");
    }

    @Test
    void 公開後に無効化したスタッフと非表示にしたポジションのシフトも表示する() throws Exception {
        data.shift(jiro, counter, OCT2, "10:00", "15:00");
        data.publish(OCT2);
        data.disable(jiro);
        data.hide(counter);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getGroups()).extracting(DailyShiftGroup::getPositionName).containsExactly("カウンター");
        assertThat(view.getGroups().get(0).getRows()).extracting(DailyShiftRow::getName).containsExactly("鈴木次郎");
    }

    @Test
    void 公開済みの最後の日より手前の未公開の日はシフトを表示せず次の日へ進める() throws Exception {
        data.shift(taro, kitchen, OCT2, "08:00", "17:00");
        data.publish(OCT4);

        MvcResult result = mvc.perform(get("/shifts").param("date", "2026-10-02").with(user(data.login(hanako))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("この日のシフトはまだ公開されていません")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("山田太郎"))))
                .andReturn();

        DailyShiftView view = (DailyShiftView) result.getModelAndView().getModel().get("view");
        assertThat(view.getGroups()).isEmpty();
        assertThat(view.isNextVisible()).isTrue();
    }

    @Test
    void スタッフは公開済みの最後の日まで進め_その先は閲覧可能なシフトがない旨を表示する() throws Exception {
        data.shift(taro, kitchen, OCT4, "08:00", "17:00");
        data.shift(taro, kitchen, OCT5, "08:00", "17:00");
        data.publish(OCT4);
        LoginUser me = data.login(taro);

        DailyShiftView last = view(me, "2026-10-04");
        assertThat(last.getGroups()).hasSize(1);
        assertThat(last.isNextVisible()).isFalse();

        // URLで直接指定しても表示しない
        MvcResult result = mvc.perform(get("/shifts").param("date", "2026-10-05").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("閲覧可能なシフトはありません")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/shifts?date=2026-10-06\""))))
                .andReturn();
        DailyShiftView beyond = (DailyShiftView) result.getModelAndView().getModel().get("view");
        assertThat(beyond.getGroups()).isEmpty();
        assertThat(beyond.isNextVisible()).isFalse();
        assertThat(beyond.isPreviousVisible()).isTrue();
    }

    @Test
    void 公開済みの日が1日もなければスタッフには閲覧可能なシフトがない旨を表示する() throws Exception {
        DailyShiftView view = view(data.login(taro), null);

        assertThat(view.getMessage()).isEqualTo("閲覧可能なシフトはありません");
        assertThat(view.isNextVisible()).isFalse();
    }

    @Test
    void 管理者は公開済みの最後の日より先へも進める() throws Exception {
        data.publish(OCT4);

        DailyShiftView view = view(admin, "2026-10-05");

        assertThat(view.getMessage()).isEqualTo("この日のシフトはまだ公開されていません");
        assertThat(view.isNextVisible()).isTrue();
    }

    @Test
    void 公開後に全員を取り消した日は出勤者がいない旨を表示する() throws Exception {
        data.publish(OCT2);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getGroups()).isEmpty();
        assertThat(view.getMessage()).isEqualTo("この日の出勤者はいません");
    }

    @Test
    void スタッフは前月1日まで表示でき_それより前の日へは移動できない() throws Exception {
        data.shift(taro, kitchen, AUG1, "08:00", "17:00");
        data.publish(AUG1);

        DailyShiftView view = view(data.login(taro), "2026-08-01");

        assertThat(view.getGroups()).hasSize(1);
        assertThat(view.isPreviousVisible()).isFalse();
        assertThat(view(data.login(taro), "2026-08-02").isPreviousVisible()).isTrue();
    }

    @Test
    void スタッフには前月より前のシフトを表示しない() throws Exception {
        data.shift(taro, kitchen, JUL31, "08:00", "17:00");
        data.publish(JUL31);

        DailyShiftView view = view(data.login(taro), "2026-07-31");

        assertThat(view.getGroups()).isEmpty();
        assertThat(view.getMessage()).isEqualTo("先月より前のシフトは表示できません");
        assertThat(view.isPreviousVisible()).isFalse();
    }

    @Test
    void 管理者は前月より前のシフトも表示でき転記画面へのリンクが出る() throws Exception {
        data.shift(taro, kitchen, JUL31, "08:00", "17:00");
        data.publish(JUL31);

        MvcResult result = mvc.perform(get("/shifts").param("date", "2026-07-31").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/admin/shifts?date=2026-07-31\"")))
                .andReturn();

        DailyShiftView view = (DailyShiftView) result.getModelAndView().getModel().get("view");
        assertThat(view.getGroups()).hasSize(1);
        assertThat(view.isPreviousVisible()).isTrue();
    }

    @Test
    void 日付の指定がない_または不正なときは今日を表示する() throws Exception {
        LoginUser me = data.login(taro);

        assertThat(view(me, null).getDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(view(me, "2026-13-40").getDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(view(me, "abc").getDateLabel()).isEqualTo("9/25（金）");
    }

    @Test
    void ヘッダーから日別シフト一覧へ移動できる() throws Exception {
        mvc.perform(get("/shifts").with(user(data.login(taro))))
                .andExpect(content().string(Matchers.containsString("href=\"/shifts\"")));
    }

    @Test
    void バーの位置と色を持ち_管理者は社員の緑になる() throws Exception {
        kitchen.setColor("sky");
        positionMapper.update(kitchen);
        User boss = data.user("boss2", "副店長", true);
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(boss, kitchen, OCT2, "17:00", "19:00");
        data.publish(OCT2);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getGroups().get(0).getRows())
                .extracting(DailyShiftRow::getName, DailyShiftRow::getBarClass, DailyShiftRow::getBarStyle,
                        DailyShiftRow::isLabelInside, DailyShiftRow::getLabelStyle)
                .containsExactly(
                        tuple("山田太郎", "bg-sky-300 text-sky-950", "left:6.6667%;width:53.3333%", true, null),
                        tuple("副店長", "bg-green-400 text-green-950", "left:60.0000%;width:13.3333%", false,
                                "left:73.3333%"));
        // 凡例はその日に出てくるポジション（表示順）と、管理者がいれば社員
        assertThat(view.getLegend()).extracting(LegendItem::getLabel, LegendItem::getBarClass)
                .containsExactly(tuple("キッチン", "bg-sky-300 text-sky-950"),
                        tuple("社員", "bg-green-400 text-green-950"));
    }

    @Test
    void 管理者のいない日は凡例に社員を出さない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(hanako, counter, OCT2, "12:00", "18:00");
        data.publish(OCT2);

        DailyShiftView view = view(data.login(taro), "2026-10-02");

        assertThat(view.getLegend()).extracting(LegendItem::getLabel).containsExactly("キッチン", "カウンター");
    }

    @Test
    void ガントチャートとして描画する() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        mvc.perform(get("/shifts").param("date", "2026-10-02").with(user(data.login(taro))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("style=\"left:6.6667%;width:53.3333%\"")))
                .andExpect(content().string(Matchers.containsString("bg-stone-300 text-stone-950")))
                .andExpect(content().string(Matchers.containsString("09:00〜17:00")));
    }

    private DailyShiftView view(LoginUser me, String date) throws Exception {
        MockHttpServletRequestBuilder request = get("/shifts").with(user(me));
        if (date != null) {
            request.param("date", date);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return (DailyShiftView) result.getModelAndView().getModel().get("view");
    }
}
