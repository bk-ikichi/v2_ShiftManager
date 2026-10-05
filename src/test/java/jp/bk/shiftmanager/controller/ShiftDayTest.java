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
import jp.bk.shiftmanager.dto.ShiftCandidate;
import jp.bk.shiftmanager.dto.ShiftDayView;
import jp.bk.shiftmanager.dto.ShiftGroupView;
import jp.bk.shiftmanager.dto.ShiftRequestInfo;
import jp.bk.shiftmanager.dto.ShiftRowView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.PositionMapper;
import jp.bk.shiftmanager.mapper.UserMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 転記画面の表示（今日は2026-09-25（金）） */
class ShiftDayTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);

    @Autowired
    PositionMapper positionMapper;
    @Autowired
    UserMapper userMapper;

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
        data.assignPosition(taro, kitchen);
        hanako = data.user("hanako", "佐藤花子", false);
        data.assignPosition(hanako, counter);
        jiro = data.user("jiro", "鈴木次郎", false);
    }

    @Test
    void 未登録の日はポジションの表示順にグループ分けし空欄行を6行ずつ表示する() throws Exception {
        ShiftDayView view = view("2026-10-02");

        assertThat(view.getDateLabel()).isEqualTo("10/2（金）");
        assertThat(view.isPublished()).isFalse();
        assertThat(view.getGroups()).extracting(ShiftGroupView::getPositionName)
                .containsExactly("キッチン", "カウンター");
        assertThat(view.getGroups().get(0).getRows()).hasSize(6).allSatisfy(row -> {
            assertThat(row.getUserId()).isNull();
            // 名前を選んでいない行は申請欄を空にし、警告も出さない
            assertThat(row.getRequestStart()).isEmpty();
            assertThat(row.getWarning()).isNull();
        });
        // 添字は画面全体で連番
        assertThat(view.getGroups().get(1).getRows()).extracting(ShiftRowView::getIndex)
                .containsExactly(6, 7, 8, 9, 10, 11);
        assertThat(view.getNextIndex()).isEqualTo(12);
    }

    @Test
    void 登録済みの日は登録済みの行だけをポジション内のINの早い順に表示する() throws Exception {
        data.shift(taro, kitchen, OCT2, "10:00", "15:00");
        data.shift(hanako, counter, OCT2, "12:00", "20:00");
        data.shift(jiro, kitchen, OCT2, "08:00", "12:00");

        ShiftDayView view = view("2026-10-02");

        assertThat(view.getGroups().get(0).getRows()).extracting(ShiftRowView::getUserId)
                .containsExactly(jiro.getId().toString(), taro.getId().toString());
        assertThat(view.getGroups().get(0).getRows().get(0).getStartTime()).isEqualTo("08:00");
        assertThat(view.getGroups().get(0).getRows().get(0).getEndTime()).isEqualTo("12:00");
        assertThat(view.getGroups().get(1).getRows()).extracting(ShiftRowView::getUserId)
                .containsExactly(hanako.getId().toString());
        assertThat(view.getNextIndex()).isEqualTo(3);
    }

    @Test
    void 非表示のポジションはシフトがある日だけ表示する() throws Exception {
        Position old = data.position("旧ポジション", 3);
        data.hide(old);
        assertThat(view("2026-10-02").getGroups()).extracting(ShiftGroupView::getPositionName)
                .containsExactly("キッチン", "カウンター");

        data.shift(taro, old, OCT2, "09:00", "17:00");
        ShiftDayView view = view("2026-10-02");

        assertThat(view.getGroups()).extracting(ShiftGroupView::getPositionName)
                .containsExactly("キッチン", "カウンター", "旧ポジション");
        // 登録済みの日は空欄行を付けない（追加は「+ 追加する」）
        assertThat(view.getGroups().get(0).getRows()).isEmpty();
        assertThat(view.getGroups().get(2).getRows()).hasSize(1);
    }

    @Test
    void 名前の候補はそのポジションのスタッフが先で無効なスタッフは出さない() throws Exception {
        User saburo = data.user("saburo", "高橋三郎", false);
        data.assignPosition(saburo, kitchen);
        data.disable(saburo);

        ShiftGroupView kitchenGroup = view("2026-10-02").getGroups().get(0);

        assertThat(kitchenGroup.getPrimaryCandidates()).extracting(ShiftCandidate::getName)
                .containsExactly("山田太郎");
        assertThat(kitchenGroup.getOtherCandidates()).extracting(ShiftCandidate::getName)
                .containsExactlyInAnyOrder("佐藤花子", "店長", "鈴木次郎");
        // その他はポジションの表示順（未設定は最後）
        assertThat(kitchenGroup.getOtherCandidates().get(0).getName()).isEqualTo("佐藤花子");
    }

    @Test
    void 無効なスタッフでもその日に登録済みなら候補に残る() throws Exception {
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.disable(taro);

        assertThat(view("2026-10-02").getGroups().get(0).getPrimaryCandidates())
                .extracting(ShiftCandidate::getName).containsExactly("山田太郎");
        assertThat(view("2026-10-03").getGroups().get(0).getPrimaryCandidates()).isEmpty();
    }

    @Test
    void 名前を選んだ行に申請のIN_OUT_備考と警告を表示する() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "早めに上がりたい");
        data.request(hanako, OCT2, "12:00", "20:00", null);
        data.shift(taro, kitchen, OCT2, "08:00", "17:00");
        data.shift(jiro, kitchen, OCT2, "10:00", "14:00");
        data.shift(hanako, counter, OCT2, "12:00", "18:00");

        ShiftDayView view = view("2026-10-02");

        ShiftRowView taroRow = view.getGroups().get(0).getRows().get(0);
        assertThat(taroRow.getRequestStart()).isEqualTo("09:00");
        assertThat(taroRow.getRequestEnd()).isEqualTo("17:00");
        assertThat(taroRow.getRequestNote()).isEqualTo("早めに上がりたい");
        assertThat(taroRow.getWarning()).isEqualTo("申請の時間外です");

        ShiftRowView jiroRow = view.getGroups().get(0).getRows().get(1);
        assertThat(jiroRow.getRequestStart()).isEqualTo("--:--");
        assertThat(jiroRow.getRequestEnd()).isEqualTo("--:--");
        assertThat(jiroRow.getWarning()).isEqualTo("申請がありません");

        ShiftRowView hanakoRow = view.getGroups().get(1).getRows().get(0);
        assertThat(hanakoRow.getRequestNote()).isNull();
        assertThat(hanakoRow.getWarning()).isNull();

        // 備考は「備考」列に常に表示し（※のアイコンは使わない）、警告は名前の真下に出す
        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString(">備考</th>")))
                .andExpect(content().string(Matchers.matchesRegex(
                        "(?s).*<td [^>]*data-note[^>]*>早めに上がりたい</td>.*")))
                .andExpect(content().string(Matchers.matchesRegex(
                        "(?s).*</select>\\s*<!--[^>]*-->\\s*<p data-warning[^>]*>申請の時間外です</p>\\s*</td>.*")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("※"))));
    }

    @Test
    void 名前を選んだときの表示用にその日の申請を画面に埋め込む() throws Exception {
        data.request(taro, OCT2, "09:00", "17:00", "メモ");

        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("data-user-id=\"" + taro.getId() + "\"")))
                .andExpect(content().string(Matchers.containsString("data-note=\"メモ\"")))
                .andExpect(content().string(Matchers.containsString("+ 追加する")))
                .andExpect(content().string(Matchers.containsString("下書き")));
    }

    @Test
    void 希望シフトの反映用に申請をINの早い順で名前と初期ポジション付きで埋め込む() throws Exception {
        data.request(taro, OCT2, "10:00", "17:00", null);
        data.request(hanako, OCT2, "09:00", "13:00", null);
        // 初期ポジション未設定
        data.request(jiro, OCT2, "09:00", "12:00", null);

        ShiftDayView view = view("2026-10-02");

        assertThat(view.getRequests()).extracting(ShiftRequestInfo::getName)
                .containsExactly("佐藤花子", "鈴木次郎", "山田太郎");
        assertThat(view.getRequests()).extracting(ShiftRequestInfo::getPositionId)
                .containsExactly(counter.getId().toString(), null, kitchen.getId().toString());
        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("希望シフトを反映する")))
                .andExpect(content().string(Matchers.matchesRegex(
                        "(?s).*data-name=\"山田太郎\"\\s+data-position-id=\"" + kitchen.getId() + "\".*")))
                .andExpect(content().string(Matchers.containsString(
                        "data-group data-position-id=\"" + kitchen.getId() + "\"")));
    }

    @Test
    void 前後の日へ移動でき不正な日付は今日を表示する() throws Exception {
        ShiftDayView view = view("2026-10-01");
        assertThat(view.getPreviousDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(view.getPreviousLabel()).isEqualTo("9/30（水）");
        assertThat(view.getNextDate()).isEqualTo(OCT2);
        assertThat(view.getNextLabel()).isEqualTo("10/2（金）");

        assertThat(view("abc").getDate()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void 公開済みの日は公開済みと表示する() throws Exception {
        data.publish(OCT2);

        assertThat(view("2026-10-02").isPublished()).isTrue();
        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(content().string(Matchers.containsString("公開済み")));
    }

    @Test
    void ポジションがないときは登録を案内する() throws Exception {
        data.reset();
        LoginUser boss = data.login(data.user("boss", "店長", true));

        mvc.perform(get("/admin/shifts").with(user(boss)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ポジションが登録されていません")));
    }

    @Test
    void 一般スタッフは転記画面を開けない() throws Exception {
        mvc.perform(get("/admin/shifts").with(user(data.login(taro))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 登録済みの行にバーを付け_管理者は社員の緑になる() throws Exception {
        kitchen.setColor("sky");
        positionMapper.update(kitchen);
        User boss = userMapper.findById(admin.getId());
        data.shift(taro, kitchen, OCT2, "09:00", "17:00");
        data.shift(boss, kitchen, OCT2, "17:00", "19:00");

        ShiftDayView view = view("2026-10-02");

        ShiftGroupView kitchenGroup = view.getGroups().get(0);
        assertThat(kitchenGroup.getBarClass()).isEqualTo("bg-sky-300 text-sky-950");
        assertThat(view.getEmployeeBarClass()).isEqualTo("bg-green-400 text-green-950");
        assertThat(kitchenGroup.getRows())
                .extracting(ShiftRowView::getUserId, ShiftRowView::getBarClass, ShiftRowView::getBarStyle)
                .containsExactly(
                        tuple(taro.getId().toString(), "bg-sky-300 text-sky-950", "left:6.6667%;width:53.3333%"),
                        tuple(boss.getId().toString(), "bg-green-400 text-green-950", "left:60.0000%;width:13.3333%"));
        assertThat(kitchenGroup.getOtherCandidates()).filteredOn(c -> c.getName().equals("店長"))
                .extracting(ShiftCandidate::isAdmin).containsExactly(true);
    }

    @Test
    void 空欄行にはバーを出さず_色はdata属性で渡す() throws Exception {
        kitchen.setColor("sky");
        positionMapper.update(kitchen);

        ShiftDayView view = view("2026-10-02");
        assertThat(view.getGroups().get(0).getRows()).allSatisfy(row -> {
            assertThat(row.getBarClass()).isNull();
            assertThat(row.getBarStyle()).isNull();
        });

        mvc.perform(get("/admin/shifts").param("date", "2026-10-02").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("data-bar-class=\"bg-sky-300 text-sky-950\"")))
                .andExpect(content().string(
                        Matchers.containsString("data-employee-bar-class=\"bg-green-400 text-green-950\"")))
                .andExpect(content().string(Matchers.containsString("data-admin=\"true\"")))
                .andExpect(content().string(Matchers.containsString("data-bar")));
    }

    private ShiftDayView view(String date) throws Exception {
        MvcResult result = mvc.perform(get("/admin/shifts").param("date", date).with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        return (ShiftDayView) result.getModelAndView().getModel().get("view");
    }
}
