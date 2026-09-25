package jp.bk.shiftmanager.service;

import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.exception.BusinessException;
import jp.bk.shiftmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 本人によるパスワード変更。新しいハッシュを返す */
    @Transactional
    public String change(long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("ユーザーが見つかりません"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("現在のパスワードが違います");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("現在と同じパスワードは使えません");
        }
        String hash = passwordEncoder.encode(newPassword);
        userRepository.updatePassword(userId, hash, false);
        return hash;
    }
}
