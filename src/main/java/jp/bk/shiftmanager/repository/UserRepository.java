package jp.bk.shiftmanager.repository;

import java.util.List;
import java.util.Optional;
import jp.bk.shiftmanager.dto.StaffRow;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final UserMapper userMapper;

    public Optional<User> findById(long id) {
        return Optional.ofNullable(userMapper.findById(id));
    }

    public Optional<User> findByLoginId(String loginId) {
        return Optional.ofNullable(userMapper.findByLoginId(loginId));
    }

    public long count() {
        return userMapper.count();
    }

    public void insert(User user) {
        userMapper.insert(user);
    }

    public void updatePassword(long id, String hash, boolean mustChange) {
        userMapper.updatePassword(id, hash, mustChange);
    }

    public List<StaffRow> findStaffRows() {
        return userMapper.findStaffRows();
    }

    public void updateProfile(User user) {
        userMapper.updateProfile(user);
    }

    public void updateDisplayOrder(long id, int displayOrder) {
        userMapper.updateDisplayOrder(id, displayOrder);
    }

    public void updateEnabled(long id, boolean enabled) {
        userMapper.updateEnabled(id, enabled);
    }

    public boolean existsLoginId(String loginId, long excludeId) {
        return userMapper.countByLoginId(loginId, excludeId) > 0;
    }

    /** 全スタッフ（無効を含む。名前の順） */
    public List<User> findAll() {
        return userMapper.findAll();
    }
}
