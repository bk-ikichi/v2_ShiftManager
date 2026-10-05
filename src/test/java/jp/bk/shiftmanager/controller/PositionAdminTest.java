package jp.bk.shiftmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.PositionMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PositionAdminTest extends IntegrationTestBase {

    @Autowired
    PositionMapper positionMapper;

    LoginUser admin;

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
    }

    @Test
    void 一覧画面を表示できる() throws Exception {
        data.position("キッチン", 2);

        mvc.perform(get("/admin/positions").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("キッチン")));
    }

    @Test
    void 追加すると表示順で並ぶ() throws Exception {
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "カウンター").param("displayOrder", "3"))
                .andExpect(redirectedUrl("/admin/positions"));
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "社員").param("displayOrder", "1"))
                .andExpect(redirectedUrl("/admin/positions"));

        List<Position> all = positionMapper.findAll();
        assertThat(all).extracting(Position::getName).containsExactly("社員", "カウンター");
    }

    @Test
    void 名前が空だと追加されない() throws Exception {
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", " ").param("displayOrder", "1"))
                .andExpect(redirectedUrl("/admin/positions"))
                .andExpect(flash().attributeExists("error"));

        assertThat(positionMapper.findAll()).isEmpty();
    }

    @Test
    void 同じ名前のポジションは追加できない() throws Exception {
        data.position("キッチン", 1);

        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "キッチン").param("displayOrder", "2"))
                .andExpect(redirectedUrl("/admin/positions"))
                .andExpect(flash().attribute("error", "同じ名前のポジションが既にあります"));

        assertThat(positionMapper.findAll()).hasSize(1);
    }

    @Test
    void 名前_表示順_非表示を更新できる() throws Exception {
        Position kitchen = data.position("キッチン", 1);

        mvc.perform(post("/admin/positions/{id}", kitchen.getId()).with(user(admin)).with(csrf())
                        .param("name", "キッチン（夜）").param("displayOrder", "5").param("hidden", "true"))
                .andExpect(redirectedUrl("/admin/positions"));

        Position updated = positionMapper.findById(kitchen.getId());
        assertThat(updated.getName()).isEqualTo("キッチン（夜）");
        assertThat(updated.getDisplayOrder()).isEqualTo(5);
        assertThat(updated.isHidden()).isTrue();
    }

    @Test
    void 非表示のチェックを外すと表示に戻る() throws Exception {
        Position kitchen = data.position("キッチン", 1);
        kitchen.setHidden(true);
        positionMapper.update(kitchen);

        // チェックボックス未選択時はSpringのマーカー（_hidden）だけが送られる
        mvc.perform(post("/admin/positions/{id}", kitchen.getId()).with(user(admin)).with(csrf())
                        .param("name", "キッチン").param("displayOrder", "1").param("_hidden", "on"))
                .andExpect(redirectedUrl("/admin/positions"));

        assertThat(positionMapper.findById(kitchen.getId()).isHidden()).isFalse();
    }

    @Test
    void 未使用のポジションは削除できる() throws Exception {
        Position kitchen = data.position("キッチン", 1);

        mvc.perform(post("/admin/positions/{id}/delete", kitchen.getId()).with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/positions"));

        assertThat(positionMapper.findById(kitchen.getId())).isNull();
    }

    @Test
    void スタッフが使用中のポジションは削除できない() throws Exception {
        Position kitchen = data.position("キッチン", 1);
        User taro = data.user("taro", "山田太郎", false);
        data.assignPosition(taro, kitchen);

        mvc.perform(post("/admin/positions/{id}/delete", kitchen.getId()).with(user(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/positions"))
                .andExpect(flash().attribute("error", "使用中のため削除できません。非表示にしてください"));

        assertThat(positionMapper.findById(kitchen.getId())).isNotNull();
    }

    @Test
    void 色を選んで追加_変更できる_省略時はグレー() throws Exception {
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "キッチン").param("displayOrder", "1").param("color", "sky"))
                .andExpect(redirectedUrl("/admin/positions"));
        mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                        .param("name", "ホール").param("displayOrder", "2"))
                .andExpect(redirectedUrl("/admin/positions"));

        List<Position> all = positionMapper.findAll();
        assertThat(all).extracting(Position::getName, Position::getColor)
                .containsExactly(tuple("キッチン", "sky"), tuple("ホール", "gray"));

        mvc.perform(post("/admin/positions/{id}", all.get(1).getId()).with(user(admin)).with(csrf())
                        .param("name", "ホール").param("displayOrder", "2").param("color", "pink"))
                .andExpect(redirectedUrl("/admin/positions"));
        assertThat(positionMapper.findById(all.get(1).getId()).getColor()).isEqualTo("pink");
    }

    @Test
    void 選択肢にない色は入力エラーで保存されない() throws Exception {
        Position kitchen = data.position("キッチン", 1);

        // 社員の緑は選択肢にない
        for (String color : new String[] {"green", ""}) {
            mvc.perform(post("/admin/positions").with(user(admin)).with(csrf())
                            .param("name", "ホール").param("displayOrder", "2").param("color", color))
                    .andExpect(redirectedUrl("/admin/positions"))
                    .andExpect(flash().attribute("error", "色を選択してください"));
            mvc.perform(post("/admin/positions/{id}", kitchen.getId()).with(user(admin)).with(csrf())
                            .param("name", "キッチン").param("displayOrder", "1").param("color", color))
                    .andExpect(redirectedUrl("/admin/positions"))
                    .andExpect(flash().attribute("error", "色を選択してください"));
        }

        assertThat(positionMapper.findAll()).extracting(Position::getName, Position::getColor)
                .containsExactly(tuple("キッチン", "gray"));
    }

    @Test
    void 一覧画面に色の選択肢と色見本を表示する() throws Exception {
        Position kitchen = data.position("キッチン", 1);
        kitchen.setColor("sky");
        positionMapper.update(kitchen);

        mvc.perform(get("/admin/positions").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("水色")))
                .andExpect(content().string(Matchers.containsString("value=\"sky\" selected=\"selected\"")))
                .andExpect(content().string(Matchers.containsString("bg-sky-300 text-sky-950")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"green\""))));
    }

    @Test
    void 一般スタッフは操作できない() throws Exception {
        LoginUser staff = data.login(data.user("taro", "山田太郎", false));

        mvc.perform(post("/admin/positions").with(user(staff)).with(csrf())
                        .param("name", "キッチン").param("displayOrder", "1"))
                .andExpect(status().isForbidden());
    }
}
