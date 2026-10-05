package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.dto.ShiftRowView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.ShiftMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 転記の登録（今日は2026-09-25） */
class ShiftSaveTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    @Autowired
    ShiftMapper shiftMapper;

    LoginUser admin;
    Position kitchen;
    Position counter;
    User taro;
    User hanako;
    User jiro;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
        kitchen = data.position("キッチン", 1);
        counter = data.position("カウンター", 2);
        taro = data.user("taro", "山田太郎", false);
        data.assignPosition(taro, kitchen);
        hanako = data.user("hanako", "佐藤花子", false);
        data.assignPosition(hanako, counter);
        jiro = data.user("jiro", "鈴木次郎", false);
    }

    @Test
    void 登録すると空欄行を除いて保存し表示はポジション内のINの早い順になる() throws Exception {
        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, taro, "12:00", "20:00");
        row(request, 1, kitchen, null, "", "");
        row(request, 2, kitchen, jiro, "08:00", "12:00");
        row(request, 3, counter, hanako, "10:00", "15:00");
        row(request, 4, counter, null, "", "");

        mvc.perform(request)
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"))
                .andExpect(flash().attribute("message", "登録しました"));

        List<Shift> saved = shiftMapper.findByDate(OCT2);
        assertThat(saved).hasSize(3);
        Shift hanakoShift = saved.stream().filter(s -> s.getUserId().equals(hanako.getId())).findFirst().orElseThrow();
        assertThat(hanakoShift.getPositionId()).isEqualTo(counter.getId());
        assertThat(hanakoShift.getStartTime()).isEqualTo(LocalTime.of(10, 0));

        ShiftDayView view = dayView("2026-10-02");
        assertThat(view.getGroups().get(0).getRows()).extracting(ShiftRowView::getUserId)
                .containsExactly(jiro.getId().toString(), taro.getId().toString());
        assertThat(view.getGroups().get(1).getRows()).extracting(ShiftRowView::getUserId)
                .containsExactly(hanako.getId().toString());
    }

    @Test
    void 添字に欠番があっても登録できる() throws Exception {
        // 「+ 追加する」で追加した行は添字が飛ぶ
        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, taro, "09:00", "17:00");
        row(request, 13, counter, hanako, "10:00", "15:00");

        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftMapper.findByDate(OCT2)).hasSize(2);
    }

    @Test
    void 登録し直すと空にした行は削除される() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(jiro, kitchen, OCT2, "12:00", "18:00");

        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, taro, "10:00", "17:00");
        row(request, 1, kitchen, null, "", "");
        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        List<Shift> saved = shiftMapper.findByDate(OCT2);
        assertThat(saved).extracting(Shift::getUserId).containsExactly(taro.getId());
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void すべての行を空にして登録するとその日のシフトはなくなる() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, null, "", "");
        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 同じスタッフを2回選ぶと保存されず入力が残る() throws Exception {
        data.shift(hanako, counter, OCT2, "10:00", "15:00");

        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, taro, "08:00", "12:00");
        row(request, 1, counter, taro, "13:00", "18:00");
        row(request, 2, counter, hanako, "11:00", "15:00");
        MvcResult result = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("admin/shifts/day"))
                .andExpect(model().attribute("error", "山田太郎が2回選ばれています。1日に1回だけ選択してください"))
                .andReturn();

        // 何も保存されない
        List<Shift> saved = shiftMapper.findByDate(OCT2);
        assertThat(saved).extracting(Shift::getUserId).containsExactly(hanako.getId());
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));

        // 入力した行はそのまま残る（空欄行は付け足さない）
        ShiftDayView view = (ShiftDayView) result.getModelAndView().getModel().get("view");
        assertThat(view.getGroups().get(0).getRows()).hasSize(1);
        assertThat(view.getGroups().get(0).getRows().get(0).getUserId()).isEqualTo(taro.getId().toString());
        assertThat(view.getGroups().get(0).getRows().get(0).getStartTime()).isEqualTo("08:00");
        assertThat(view.getGroups().get(1).getRows()).extracting(ShiftRowView::getStartTime)
                .containsExactly("13:00", "11:00");
    }

    @Test
    void 行の一部だけの入力や不正な時刻は保存されずどの行か分かるエラーになる() throws Exception {
        Object[][] cases = {
                {null, "09:00", "17:00", "キッチン：名前を選択してください"},
                {taro, "", "", "キッチン・山田太郎：INとOUTを選択してください"},
                {taro, "09:00", "", "キッチン・山田太郎：INとOUTを選択してください"},
                {taro, "07:30", "12:00", "キッチン・山田太郎：時刻は8:00〜23:00の30分刻みで選択してください"},
                {taro, "12:00", "23:30", "キッチン・山田太郎：時刻は8:00〜23:00の30分刻みで選択してください"},
                {taro, "abc", "12:00", "キッチン・山田太郎：時刻は8:00〜23:00の30分刻みで選択してください"},
                {taro, "13:00", "12:00", "キッチン・山田太郎：OUTはINより後の時刻にしてください"},
                {taro, "12:00", "12:00", "キッチン・山田太郎：OUTはINより後の時刻にしてください"},
        };
        for (Object[] c : cases) {
            MockHttpServletRequestBuilder request = save("2026-10-02");
            row(request, 0, kitchen, (User) c[0], (String) c[1], (String) c[2]);
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("error", c[3]));
        }
        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 不正なIDや日付は入力エラーになる() throws Exception {
        mvc.perform(save("2026-10-02")
                        .param("rows[0].positionId", "abc").param("rows[0].userId", taro.getId().toString())
                        .param("rows[0].startTime", "09:00").param("rows[0].endTime", "17:00"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "不正なポジションです"));

        for (String userId : new String[] {"99999", "abc"}) {
            mvc.perform(save("2026-10-02")
                            .param("rows[0].positionId", kitchen.getId().toString()).param("rows[0].userId", userId)
                            .param("rows[0].startTime", "09:00").param("rows[0].endTime", "17:00"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("error", "スタッフが見つかりません"));
        }

        MockHttpServletRequestBuilder invalidDate = save("2026-13-01");
        row(invalidDate, 0, kitchen, taro, "09:00", "17:00");
        mvc.perform(invalidDate)
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "不正な日付です"));

        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 無効なスタッフは新しく選べないがその日に登録済みなら登録し直せる() throws Exception {
        User saburo = data.user("saburo", "高橋三郎", false);
        data.disable(saburo);

        MockHttpServletRequestBuilder request = save("2026-10-02");
        row(request, 0, kitchen, saburo, "09:00", "17:00");
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "キッチン・高橋三郎：無効なスタッフは選択できません"));
        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();

        data.shift(saburo, kitchen, OCT2, "09:00", "17:00");
        MockHttpServletRequestBuilder again = save("2026-10-02");
        row(again, 0, kitchen, saburo, "10:00", "17:00");
        mvc.perform(again).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));
        assertThat(shiftMapper.findByDate(OCT2).get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void 一般スタッフは登録できない() throws Exception {
        MockHttpServletRequestBuilder request = post("/admin/shifts").with(user(data.login(taro))).with(csrf())
                .param("date", "2026-10-02");
        row(request, 0, kitchen, taro, "09:00", "17:00");

        mvc.perform(request).andExpect(status().isForbidden());

        assertThat(shiftMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 登録できずに表示し直すとき_時刻が不正な行にはバーを出さない() throws Exception {
        String[][] invalid = {{"13:00", "12:00"}, {"12:00", "12:00"}, {"abc", "12:00"}, {"09:00", ""}};
        for (String[] times : invalid) {
            MockHttpServletRequestBuilder request = save("2026-10-02");
            row(request, 0, kitchen, taro, times[0], times[1]);
            MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
            ShiftDayView view = (ShiftDayView) result.getModelAndView().getModel().get("view");
            assertThat(view.getGroups().get(0).getRows().get(0).getBarStyle()).isNull();
        }

        // 存在しないスタッフIDでも、時刻が正しければポジションの色でバーを出す
        MvcResult result = mvc.perform(save("2026-10-02")
                        .param("rows[0].positionId", kitchen.getId().toString()).param("rows[0].userId", "99999")
                        .param("rows[0].startTime", "09:00").param("rows[0].endTime", "17:00"))
                .andExpect(status().isOk())
                .andReturn();
        ShiftDayView view = (ShiftDayView) result.getModelAndView().getModel().get("view");
        assertThat(view.getGroups().get(0).getRows().get(0).getBarClass()).isEqualTo("bg-stone-300 text-stone-950");
        assertThat(view.getGroups().get(0).getRows().get(0).getBarStyle()).isEqualTo("left:6.6667%;width:53.3333%");
    }

    private MockHttpServletRequestBuilder save(String date) {
        return post("/admin/shifts").with(user(admin)).with(csrf()).param("date", date);
    }

    /** 1行分の入力を付ける（user が null なら名前未選択） */
    private void row(MockHttpServletRequestBuilder request, int index, Position position, User user, String start,
            String end) {
        String prefix = "rows[" + index + "].";
        request.param(prefix + "positionId", position.getId().toString())
                .param(prefix + "userId", user == null ? "" : user.getId().toString())
                .param(prefix + "startTime", start)
                .param(prefix + "endTime", end);
    }

    private ShiftDayView dayView(String date) throws Exception {
        MvcResult result = mvc.perform(get("/admin/shifts").param("date", date).with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        return (ShiftDayView) result.getModelAndView().getModel().get("view");
    }
}
