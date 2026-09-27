package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.RequestTableRow;
import jp.bk.shiftmanager.dto.RequestTableView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.util.Cycle;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 管理者の申請一覧（今日は2026-09-25） */
class AdminRequestTest extends IntegrationTestBase {

    private static final LocalDate OCT1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    User boss;
    LoginUser admin;
    User taro;

    @BeforeEach
    void setUp() {
        boss = data.user("boss", "店長", true);
        admin = data.login(boss);
        taro = data.user("taro", "山田太郎", false);
        Position kitchen = data.position("キッチン", 1);
        data.assignPosition(taro, kitchen);
    }

    @Test
    void サイクルの全員の申請が表で表示される() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "遅れるかも");

        MvcResult result = mvc.perform(get("/admin/requests").param("date", "2026-10-01").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("山田太郎")))
                .andExpect(content().string(Matchers.containsString("09:00")))
                .andExpect(content().string(Matchers.containsString("遅れるかも")))
                .andReturn();

        RequestTableView view = view(result);
        assertThat(view.getLabel()).isEqualTo("10/1〜10/10");
        assertThat(view.getDateLabels()).hasSize(10).startsWith("1（木）");
        RequestTableRow row = row(view, "山田太郎");
        assertThat(row.getPositionName()).isEqualTo("キッチン");
        assertThat(row.getCells()).hasSize(10);
        assertThat(row.getCells().get(1).getStartTime()).isEqualTo("09:00");
        assertThat(row.getCells().get(1).getEndTime()).isEqualTo("17:00");
        assertThat(row.getCells().get(0).getStartTime()).isNull();
    }

    @Test
    void 申請か出勤できないがあれば提出済み() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        User jiro = data.user("jiro", "鈴木次郎", false);
        data.request(taro, OCT1, "09:00", "17:00", null);
        data.unavailable(hanako, OCT1);
        // 別のサイクルの申請は数えない
        data.request(jiro, LocalDate.of(2026, 9, 30), "09:00", "17:00", null);

        RequestTableView view = view(mvc.perform(get("/admin/requests").param("date", "2026-10-01")
                .with(user(admin))).andReturn());

        assertThat(row(view, "山田太郎").isSubmitted()).isTrue();
        assertThat(row(view, "佐藤花子").isSubmitted()).isTrue();
        assertThat(row(view, "佐藤花子").isUnavailable()).isTrue();
        assertThat(row(view, "鈴木次郎").isSubmitted()).isFalse();
        // 管理者も出勤者なので表に載る
        assertThat(row(view, "店長").isSubmitted()).isFalse();
    }

    @Test
    void 無効化したスタッフは表示されない() throws Exception {
        User retired = data.user("retired", "退職者", false);
        data.disable(retired);

        RequestTableView view = view(mvc.perform(get("/admin/requests").with(user(admin))).andReturn());

        assertThat(view.getRows()).extracting(RequestTableRow::getName).doesNotContain("退職者");
    }

    @Test
    void 日付の指定がない又は不正なら今日の次のサイクルを表示する() throws Exception {
        Cycle expected = Cycle.of(OCT1);

        assertThat(view(mvc.perform(get("/admin/requests").with(user(admin))).andReturn()).getCycle())
                .isEqualTo(expected);
        assertThat(view(mvc.perform(get("/admin/requests").param("date", "abc").with(user(admin))).andReturn())
                .getCycle()).isEqualTo(expected);
        assertThat(view(mvc.perform(get("/admin/requests").param("date", "2026-10-15").with(user(admin)))
                .andReturn()).getCycle()).isEqualTo(Cycle.of(LocalDate.of(2026, 10, 11)));
    }

    @Test
    void 前後のサイクルへ移動できる() throws Exception {
        RequestTableView view = view(mvc.perform(get("/admin/requests").param("date", "2026-10-01")
                .with(user(admin))).andReturn());

        assertThat(view.getPreviousStart()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(view.getNextStart()).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    @Test
    void 一般スタッフは申請一覧を表示できない() throws Exception {
        mvc.perform(get("/admin/requests").with(user(data.login(taro))))
                .andExpect(status().isForbidden());
    }

    private static RequestTableView view(MvcResult result) {
        return (RequestTableView) result.getModelAndView().getModel().get("view");
    }

    private static RequestTableRow row(RequestTableView view, String name) {
        return view.getRows().stream().filter(row -> row.getName().equals(name)).findFirst().orElseThrow();
    }
}
