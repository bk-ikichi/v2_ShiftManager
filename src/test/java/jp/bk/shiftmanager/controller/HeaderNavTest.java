package jp.bk.shiftmanager.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.auth.LoginUser;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeaderNavTest extends IntegrationTestBase {

    LoginUser admin;

    /** 指定したパスのリンクに現在地の印（aria-current）が付いているか。属性の並び順は問わない */
    static Matcher<String> current(String path) {
        return Matchers.matchesRegex("(?s).*<a href=\"" + path + "\"[^>]*aria-current=\"page\".*");
    }

    @BeforeEach
    void setUp() {
        admin = data.login(data.user("boss", "店長", true));
    }

    @Test
    void 今いるページのリンクに現在地の印が付く() throws Exception {
        mvc.perform(get("/admin/requests").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(current("/admin/requests")))
                // 前方一致で /requests が現在地扱いにならないこと
                .andExpect(content().string(Matchers.not(current("/requests"))));
    }

    @Test
    void 配下のページでも親のリンクが現在地になる() throws Exception {
        mvc.perform(get("/mypage/patterns").with(user(admin)))
                .andExpect(content().string(current("/mypage")));
    }

    @Test
    void ホームのリンクは他のページで現在地にならない() throws Exception {
        mvc.perform(get("/shifts").with(user(admin)))
                .andExpect(content().string(current("/shifts")))
                .andExpect(content().string(Matchers.not(current("/"))));
    }
}
