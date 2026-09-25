package jp.bk.shiftmanager.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jp.bk.shiftmanager.IntegrationTestBase;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class InitialAdminRunnerTest extends IntegrationTestBase {

    @Autowired
    InitialAdminRunner runner;

    @Autowired
    UserMapper userMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void ユーザーが1人もいなければ初期管理者を作成する() {
        runner.run(new DefaultApplicationArguments());

        User admin = userMapper.findByLoginId("admin");
        assertThat(admin).isNotNull();
        assertThat(admin.getName()).isEqualTo("管理者");
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches("admin", admin.getPasswordHash())).isTrue();
    }

    @Test
    void ユーザーが既にいれば何もしない() {
        data.user("taro", "山田太郎", false);

        runner.run(new DefaultApplicationArguments());

        assertThat(userMapper.findByLoginId("admin")).isNull();
        assertThat(userMapper.count()).isEqualTo(1);
    }
}
