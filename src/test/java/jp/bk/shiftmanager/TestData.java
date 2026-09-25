package jp.bk.shiftmanager;

import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.PositionMapper;
import jp.bk.shiftmanager.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** テストデータの作成・初期化 */
@TestComponent
@RequiredArgsConstructor
public class TestData {

    public static final String PASSWORD = "password1";

    private final JdbcTemplate jdbc;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PositionMapper positionMapper;

    /** 全データを削除し、設定を初期値に戻す */
    public void reset() {
        jdbc.execute("TRUNCATE shift_changes, published_dates, shifts, cycle_unavailable, "
                + "shift_requests, shift_patterns, users, positions RESTART IDENTITY CASCADE");
        jdbc.update("UPDATE app_settings SET deadline_days_before = 5");
    }

    /** パスワード変更済み・有効なスタッフを作成する */
    public User user(String loginId, String name, boolean admin) {
        User user = new User();
        user.setLoginId(loginId);
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setAdmin(admin);
        user.setEnabled(true);
        user.setMustChangePassword(false);
        userMapper.insert(user);
        return user;
    }

    /** DBの最新状態からログインユーザーを作る */
    public LoginUser login(User user) {
        return LoginUser.from(userMapper.findById(user.getId()));
    }

    public void disable(User user) {
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", user.getId());
    }

    public void requirePasswordChange(User user) {
        jdbc.update("UPDATE users SET must_change_password = TRUE WHERE id = ?", user.getId());
    }

    public Position position(String name, int displayOrder) {
        Position position = new Position();
        position.setName(name);
        position.setDisplayOrder(displayOrder);
        positionMapper.insert(position);
        return position;
    }

    public void assignPosition(User user, Position position) {
        jdbc.update("UPDATE users SET position_id = ? WHERE id = ?", position.getId(), user.getId());
        user.setPositionId(position.getId());
    }
}
