package jp.bk.shiftmanager.auth;

import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** ユーザーが1人もいない場合に初期管理者（admin / admin）を作成する */
@Component
@RequiredArgsConstructor
public class InitialAdminRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setLoginId("admin");
        admin.setName("管理者");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setAdmin(true);
        admin.setEnabled(true);
        // 推測されやすい初期パスワードのため、初回ログインで必ず変更させる
        admin.setMustChangePassword(true);
        userRepository.insert(admin);
    }
}
