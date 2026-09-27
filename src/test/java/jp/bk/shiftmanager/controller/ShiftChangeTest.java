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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.Shift;
import jp.bk.shiftmanager.entity.ShiftChange;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.ShiftChangeMapper;
import jp.bk.shiftmanager.mapper.ShiftMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 公開済みの日の編集（今日は2026-09-25） */
class ShiftChangeTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    @Autowired
    ShiftMapper shiftMapper;

    @Autowired
    ShiftChangeMapper shiftChangeMapper;

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
        hanako = data.user("hanako", "佐藤花子", false);
        jiro = data.user("jiro", "鈴木次郎", false);
    }

    @Test
    void 下書きの日の登録では記録しない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        MockHttpServletRequestBuilder request = save("2026-10-02", false);
        row(request, 0, kitchen, taro, "10:00", "17:00");
        row(request, 1, kitchen, hanako, "10:00", "15:00");
        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftChangeMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 初回の公開は変更として記録しない() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");

        mvc.perform(post("/admin/shifts/publish").with(user(admin)).with(csrf()).param("date", "2026-10-02"))
                .andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftChangeMapper.findByDate(OCT2)).isEmpty();
    }

    @Test
    void 公開済みの日の追加_時刻変更_ポジション変更_取り消しを記録する() throws Exception {
        User saburo = data.user("saburo", "高橋三郎", false);
        User shiro = data.user("shiro", "伊藤四郎", false);
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(hanako, counter, OCT2, "10:00", "15:00");
        data.shift(jiro, kitchen, OCT2, "12:00", "18:00");
        data.shift(shiro, counter, OCT2, "08:00", "12:00");
        data.publish(OCT2);

        MockHttpServletRequestBuilder request = save("2026-10-02", true);
        row(request, 0, kitchen, taro, "10:00", "17:00");     // 時刻変更
        row(request, 1, kitchen, hanako, "10:00", "15:00");   // ポジションだけ変更
        row(request, 2, counter, saburo, "12:00", "20:00");   // 追加
        row(request, 3, counter, shiro, "08:00", "12:00");    // 変更なし
        // 鈴木次郎の行は送らない（取り消し）
        mvc.perform(request).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        Map<Long, ShiftChangeType> changes = shiftChangeMapper.findByDate(OCT2).stream()
                .collect(Collectors.toMap(ShiftChange::getUserId, ShiftChange::getChangeType));
        assertThat(changes).containsOnly(
                Map.entry(taro.getId(), ShiftChangeType.UPDATED),
                Map.entry(hanako.getId(), ShiftChangeType.UPDATED),
                Map.entry(saburo.getId(), ShiftChangeType.ADDED),
                Map.entry(jiro.getId(), ShiftChangeType.CANCELLED));
    }

    @Test
    void 未確認の変更は1人1日1件で新しい種別に置き換える() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        MockHttpServletRequestBuilder change = save("2026-10-02", true);
        row(change, 0, kitchen, taro, "10:00", "17:00");
        mvc.perform(change).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        MockHttpServletRequestBuilder cancel = save("2026-10-02", true);
        row(cancel, 0, kitchen, null, "", "");
        mvc.perform(cancel).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        List<ShiftChange> changes = shiftChangeMapper.findByDate(OCT2);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getChangeType()).isEqualTo(ShiftChangeType.CANCELLED);
        assertThat(changes.get(0).getAcknowledgedAt()).isNull();
    }

    @Test
    void 変更なしで登録し直しても未確認の変更は残る() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        MockHttpServletRequestBuilder change = save("2026-10-02", true);
        row(change, 0, kitchen, taro, "10:00", "17:00");
        mvc.perform(change);
        MockHttpServletRequestBuilder same = save("2026-10-02", true);
        row(same, 0, kitchen, taro, "10:00", "17:00");
        mvc.perform(same).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        assertThat(shiftChangeMapper.findByDate(OCT2)).extracting(ShiftChange::getChangeType)
                .containsExactly(ShiftChangeType.UPDATED);
    }

    @Test
    void 確認済みの変更は残し新しい変更を追加する() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        MockHttpServletRequestBuilder change = save("2026-10-02", true);
        row(change, 0, kitchen, taro, "10:00", "17:00");
        mvc.perform(change);
        data.acknowledgeChanges(taro);

        MockHttpServletRequestBuilder again = save("2026-10-02", true);
        row(again, 0, kitchen, taro, "11:00", "17:00");
        mvc.perform(again).andExpect(redirectedUrl("/admin/shifts?date=2026-10-02"));

        List<ShiftChange> changes = shiftChangeMapper.findByDate(OCT2);
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).getAcknowledgedAt()).isNotNull();
        assertThat(changes.get(1).getAcknowledgedAt()).isNull();
        assertThat(changes.get(1).getChangeType()).isEqualTo(ShiftChangeType.UPDATED);
    }

    @Test
    void 下書きで開いた画面から公開後に登録すると保存されず入力が残る() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        // 画面を開いた後に別のタブで公開された
        data.publish(OCT2);

        MockHttpServletRequestBuilder request = save("2026-10-02", false);
        row(request, 0, kitchen, taro, "12:00", "17:00");
        MvcResult result = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(model().attribute("error",
                        "この日は画面を開いた後に公開されました。変更はすぐスタッフに表示されるため、内容を確認してもう一度登録してください"))
                .andReturn();

        List<Shift> saved = shiftMapper.findByDate(OCT2);
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(shiftChangeMapper.findByDate(OCT2)).isEmpty();

        // 公開済みとして表示し直すため、もう一度登録すれば保存できる
        ShiftDayView view = (ShiftDayView) result.getModelAndView().getModel().get("view");
        assertThat(view.isPublished()).isTrue();
        assertThat(view.getGroups().get(0).getRows().get(0).getStartTime()).isEqualTo("12:00");
    }

    @Test
    void 公開済みの日の画面には登録前の確認用の印と案内を出す() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.publish(OCT2);

        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("data-published=\"true\"")))
                .andExpect(content().string(Matchers.containsString("name=\"published\" value=\"true\"")))
                .andExpect(content().string(Matchers.containsString("公開済みの日です。登録するとすぐスタッフに表示されます")));
    }

    private MockHttpServletRequestBuilder save(String date, boolean published) {
        return post("/admin/shifts").with(user(admin)).with(csrf())
                .param("date", date).param("published", String.valueOf(published));
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
}
