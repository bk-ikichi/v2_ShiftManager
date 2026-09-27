package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.dto.ChangeNotice;
import jp.bk.shiftmanager.dto.HomeView;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.ShiftChange;
import jp.bk.shiftmanager.entity.ShiftChangeType;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.ShiftChangeMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** トップ画面の「変更あり」（今日は2026-09-25（金）） */
class HomeChangeTest extends IntegrationTestBase {

    private static final LocalDate OCT2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate OCT3 = LocalDate.of(2026, 10, 3);

    @Autowired
    ShiftChangeMapper shiftChangeMapper;

    Position kitchen;
    User taro;
    User hanako;
    LoginUser taroLogin;

    @BeforeEach
    void setUp() {
        kitchen = data.position("キッチン", 1);
        taro = data.user("taro", "山田太郎", false);
        hanako = data.user("hanako", "佐藤花子", false);
        taroLogin = data.login(taro);
    }

    @Test
    void 未確認の変更を日付順に表示し_取り消しは専用の文言を出す() throws Exception {
        data.shift(taro, kitchen, OCT3, "10:00", "17:00");
        data.publish(OCT2);
        data.publish(OCT3);
        data.change(taro, OCT3, ShiftChangeType.UPDATED);
        data.change(taro, OCT2, ShiftChangeType.CANCELLED);

        MvcResult result = mvc.perform(get("/").with(user(taroLogin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("この日のシフトは取り消されました")))
                .andExpect(content().string(Matchers.containsString("href=\"/shifts?date=2026-10-03\"")))
                .andReturn();

        HomeView view = (HomeView) result.getModelAndView().getModel().get("view");
        assertThat(view.getChanges())
                .extracting(ChangeNotice::getDateLabel, ChangeNotice::getTypeLabel, ChangeNotice::isCancelled,
                        ChangeNotice::getTimeLabel, ChangeNotice::getPositionName)
                .containsExactly(
                        tuple("10/2（金）", "取り消し", true, null, null),
                        tuple("10/3（土）", "変更", false, "10:00〜17:00", "キッチン"));
    }

    @Test
    void 追加の変更は追加として現在の時刻を表示する() throws Exception {
        data.shift(taro, kitchen, OCT2, "08:00", "12:00");
        data.publish(OCT2);
        data.change(taro, OCT2, ShiftChangeType.ADDED);

        assertThat(view().getChanges())
                .extracting(ChangeNotice::getTypeLabel, ChangeNotice::getTimeLabel)
                .containsExactly(tuple("追加", "08:00〜12:00"));
    }

    @Test
    void 確認済みの変更と他のスタッフの変更は表示しない() throws Exception {
        data.shift(taro, kitchen, OCT2, "08:00", "12:00");
        data.publish(OCT2);
        data.change(taro, OCT2, ShiftChangeType.ADDED);
        data.acknowledgeChanges(taro);
        data.change(hanako, OCT2, ShiftChangeType.CANCELLED);

        assertThat(view().getChanges()).isEmpty();
    }

    @Test
    void 確認済みを押すと表示されなくなり_記録は残る() throws Exception {
        data.shift(taro, kitchen, OCT2, "08:00", "12:00");
        data.publish(OCT2);
        long id = data.change(taro, OCT2, ShiftChangeType.ADDED);

        mvc.perform(post("/changes/acknowledge").with(user(taroLogin)).with(csrf()).param("id", String.valueOf(id)))
                .andExpect(redirectedUrl("/"));

        List<ShiftChange> changes = shiftChangeMapper.findByDate(OCT2);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getAcknowledgedAt()).isNotNull();
        assertThat(view().getChanges()).isEmpty();
    }

    @Test
    void 他のスタッフの変更や不正なIDを送っても何も変わらない() throws Exception {
        long hanakoChange = data.change(hanako, OCT2, ShiftChangeType.ADDED);
        data.change(taro, OCT2, ShiftChangeType.ADDED);

        for (String id : List.of(String.valueOf(hanakoChange), "abc", "", "999999")) {
            mvc.perform(post("/changes/acknowledge").with(user(taroLogin)).with(csrf()).param("id", id))
                    .andExpect(redirectedUrl("/"));
        }
        mvc.perform(post("/changes/acknowledge").with(user(taroLogin)).with(csrf()))
                .andExpect(redirectedUrl("/"));

        assertThat(shiftChangeMapper.findByDate(OCT2))
                .allSatisfy(change -> assertThat(change.getAcknowledgedAt()).isNull());
    }

    private HomeView view() throws Exception {
        MvcResult result = mvc.perform(get("/").with(user(taroLogin))).andExpect(status().isOk()).andReturn();
        return (HomeView) result.getModelAndView().getModel().get("view");
    }
}
