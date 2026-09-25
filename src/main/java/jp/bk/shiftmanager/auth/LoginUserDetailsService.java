package jp.bk.shiftmanager.auth;

import jp.bk.shiftmanager.repository.UserRepository;
import jp.bk.shiftmanager.util.LoginIds;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) {
        return userRepository.findByLoginId(LoginIds.normalize(loginId))
                .map(LoginUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが存在しません"));
    }
}
