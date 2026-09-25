package jp.bk.shiftmanager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class PwaTest extends IntegrationTestBase {

    @Test
    void マニフェストとアイコンは未ログインでも取得できる() throws Exception {
        mvc.perform(get("/manifest.webmanifest"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("\"start_url\"")));
        mvc.perform(get("/icons/icon.svg")).andExpect(status().isOk());
    }

    @Test
    void ログイン画面からマニフェストが参照されている() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(Matchers.containsString("manifest.webmanifest")));
    }
}
