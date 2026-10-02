package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

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
                .andExpect(content().string(Matchers.containsString("900")))
                .andExpect(content().string(Matchers.containsString("遅れるかも")))
                // 備考は i を押して表示する
                .andExpect(content().string(Matchers.containsString(
                        "popovertarget=\"note-" + taro.getId() + "-2026-10-02\"")))
                // 日付から転記画面へ移動できる
                .andExpect(content().string(Matchers.containsString("href=\"/admin/shifts?date=2026-10-02\"")))
                // 「Excel用にコピー」用の時刻
                .andExpect(content().string(Matchers.containsString("data-start=\"900\" data-end=\"1700\"")))
                .andReturn();

        RequestTableView view = view(result);
        assertThat(view.getLabel()).isEqualTo("10/1〜10/10");
        assertThat(view.getDateLabels()).hasSize(10).startsWith("1（木）");
        RequestTableRow row = row(view, "山田太郎");
        assertThat(row.getPositionName()).isEqualTo("キッチン");
        assertThat(row.getCells()).hasSize(10);
        assertThat(view.getDates()).hasSize(10).startsWith(OCT1);
        // Excelに数値として貼り付けられるよう、コロンと先頭のゼロを付けない
        assertThat(row.getCells().get(1).getStartTime()).isEqualTo("900");
        assertThat(row.getCells().get(1).getEndTime()).isEqualTo("1700");
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

    @Test
    void ドラッグで変えた並び順を保存すると申請一覧がその順番になる() throws Exception {
        User hanako = data.user("hanako", "佐藤花子", false);
        // 並び順を保存する前は、ポジションの表示順（未設定は後ろ）→名前の順
        assertThat(names()).containsExactly("山田太郎", "佐藤花子", "店長");

        saveOrder(admin, hanako, boss, taro).andExpect(status().isOk());

        assertThat(names()).containsExactly("佐藤花子", "店長", "山田太郎");
    }

    @Test
    void 並び順を保存した後に登録したスタッフは一番下に並ぶ() throws Exception {
        saveOrder(admin, taro, boss).andExpect(status().isOk());

        data.user("hanako", "佐藤花子", false);

        assertThat(names()).containsExactly("山田太郎", "店長", "佐藤花子");
    }

    @Test
    void 存在しないスタッフを含む並び順は保存しない() throws Exception {
        mvc.perform(post("/admin/requests/order").with(user(admin)).with(csrf())
                        .param("userIds", taro.getId().toString(), "9999"))
                .andExpect(status().isBadRequest());

        assertThat(names()).containsExactly("山田太郎", "店長");
    }

    @Test
    void 同じスタッフが重複した並び順は保存しない() throws Exception {
        saveOrder(admin, boss, taro, boss).andExpect(status().isBadRequest());

        assertThat(names()).containsExactly("山田太郎", "店長");
    }

    @Test
    void 一般スタッフは並び順を保存できない() throws Exception {
        saveOrder(data.login(taro), boss, taro).andExpect(status().isForbidden());
    }

    @Test
    void 申請一覧に並び順を保存するための行IDとCSRFトークンがある() throws Exception {
        mvc.perform(get("/admin/requests").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("data-user-id=\"" + taro.getId() + "\"")))
                .andExpect(content().string(Matchers.containsString("data-csrf-header=")));
    }

    private ResultActions saveOrder(LoginUser login, User... users)
            throws Exception {
        String[] ids = Arrays.stream(users).map(u -> u.getId().toString()).toArray(String[]::new);
        return mvc.perform(post("/admin/requests/order").with(user(login)).with(csrf()).param("userIds", ids));
    }

    private List<String> names() throws Exception {
        return view(mvc.perform(get("/admin/requests").with(user(admin))).andReturn()).getRows().stream()
                .map(RequestTableRow::getName).toList();
    }

    private static RequestTableView view(MvcResult result) {
        return (RequestTableView) result.getModelAndView().getModel().get("view");
    }

    private static RequestTableRow row(RequestTableView view, String name) {
        return view.getRows().stream().filter(row -> row.getName().equals(name)).findFirst().orElseThrow();
    }
}
