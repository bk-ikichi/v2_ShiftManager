package jp.bk.shiftmanager;

import java.time.LocalDate;
import java.time.LocalTime;
import jp.bk.shiftmanager.auth.LoginUser;
import jp.bk.shiftmanager.entity.Position;
import jp.bk.shiftmanager.entity.ShiftPattern;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.PositionMapper;
import jp.bk.shiftmanager.mapper.ShiftPatternMapper;
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
    private final TestClock clock;
    private final ShiftPatternMapper shiftPatternMapper;

    /** 全データを削除し、設定を初期値に戻す */
    public void reset() {
        jdbc.execute("TRUNCATE shift_changes, published_dates, shifts, cycle_unavailable, "
                + "shift_requests, shift_patterns, users, positions RESTART IDENTITY CASCADE");
        jdbc.update("UPDATE app_settings SET deadline_days_before = 5");
        clock.setToday(TestClock.DEFAULT_TODAY);
    }

    /** アプリの「今日」を変更する（テストごとに初期値へ戻る） */
    public void today(LocalDate today) {
        clock.setToday(today);
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

    public ShiftPattern pattern(User user, String name, String start, String end) {
        ShiftPattern pattern = new ShiftPattern();
        pattern.setUserId(user.getId());
        pattern.setName(name);
        pattern.setStartTime(LocalTime.parse(start));
        pattern.setEndTime(LocalTime.parse(end));
        shiftPatternMapper.insert(pattern);
        return pattern;
    }

    public void request(User user, LocalDate date, String start, String end, String note) {
        jdbc.update("INSERT INTO shift_requests (user_id, work_date, start_time, end_time, note) "
                + "VALUES (?, ?, ?::time, ?::time, ?)", user.getId(), date, start, end, note);
    }

    /** 「この期間は出勤できない」にする */
    public void unavailable(User user, LocalDate cycleStart) {
        jdbc.update("INSERT INTO cycle_unavailable (user_id, cycle_start) VALUES (?, ?)", user.getId(), cycleStart);
    }

    /** 確定シフトを登録する（下書き） */
    public void shift(User user, Position position, LocalDate date, String start, String end) {
        jdbc.update("INSERT INTO shifts (work_date, user_id, position_id, start_time, end_time) "
                + "VALUES (?, ?, ?, ?::time, ?::time)", date, user.getId(), position.getId(), start, end);
    }

    /** その日を公開済みにする */
    public void publish(LocalDate date) {
        jdbc.update("INSERT INTO published_dates (work_date) VALUES (?)", date);
    }

    /** ポジションを非表示にする */
    public void hide(Position position) {
        jdbc.update("UPDATE positions SET hidden = TRUE WHERE id = ?", position.getId());
    }

    /** そのスタッフの未確認の変更をすべて確認済みにする */
    public void acknowledgeChanges(User user) {
        jdbc.update("UPDATE shift_changes SET acknowledged_at = now() WHERE user_id = ? AND acknowledged_at IS NULL",
                user.getId());
    }
}
