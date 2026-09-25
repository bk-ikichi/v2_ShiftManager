package jp.bk.shiftmanager.repository;

import java.util.Optional;
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
}
