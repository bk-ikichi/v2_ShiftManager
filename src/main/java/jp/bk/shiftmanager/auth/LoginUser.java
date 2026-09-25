package jp.bk.shiftmanager.auth;

import java.util.Collection;
import java.util.List;
import jp.bk.shiftmanager.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** ログイン中のユーザー（セッションに保存される） */
@Getter
public class LoginUser implements UserDetails {

    private final long id;
    private final String loginId;
    private final String name;
    private final String passwordHash;
    private final boolean admin;
    private final boolean enabled;
    private final boolean mustChangePassword;

    private LoginUser(long id, String loginId, String name, String passwordHash,
            boolean admin, boolean enabled, boolean mustChangePassword) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
        this.passwordHash = passwordHash;
        this.admin = admin;
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
    }

    public static LoginUser from(User user) {
        return new LoginUser(user.getId(), user.getLoginId(), user.getName(), user.getPasswordHash(),
                user.isAdmin(), user.isEnabled(), user.isMustChangePassword());
    }

    /** パスワード変更後のセッション更新用 */
    public LoginUser withPasswordChanged(String newPasswordHash) {
        return new LoginUser(id, loginId, name, newPasswordHash, admin, enabled, false);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (admin) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginId;
    }
}
