package jp.bk.shiftmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void マイグレーションで全テーブルが作成される() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        assertThat(tables).contains(
                "positions", "users", "app_settings", "shift_patterns", "shift_requests",
                "cycle_unavailable", "shifts", "published_dates", "shift_changes");
    }

    @Test
    void 締切日数の初期値は5() {
        Integer days = jdbc.queryForObject(
                "SELECT deadline_days_before FROM app_settings WHERE id = 1", Integer.class);
        assertThat(days).isEqualTo(5);
    }

    @Test
    void ポジションの色の初期値はgray() {
        jdbc.update("INSERT INTO positions (name, display_order) VALUES ('色テスト', 1)");
        String color = jdbc.queryForObject("SELECT color FROM positions WHERE name = '色テスト'", String.class);
        jdbc.update("DELETE FROM positions WHERE name = '色テスト'");
        assertThat(color).isEqualTo("gray");
    }
}
