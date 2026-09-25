package jp.bk.shiftmanager.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import jp.bk.shiftmanager.entity.User;
import jp.bk.shiftmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ログイン中ユーザーの状態をリクエストごとにDBと照合する。
 * セッションにはログイン時点の情報が残るため、管理者による無効化・パスワードリセット・権限変更を即時に反映させる。
 * Beanにするとサーブレットフィルタとしても自動登録されてしまうため、SecurityConfigでnewして登録する
 */
@RequiredArgsConstructor
public class UserStateCheckFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    /** 照合しないリクエスト。静的ファイルの取得ごとにDBへ問い合わせないため */
    private static final List<RequestMatcher> SKIPPED = Arrays.stream(SecurityConfig.STATIC_RESOURCES)
            .<RequestMatcher>map(PathPatternRequestMatcher.withDefaults()::matcher)
            .toList();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SKIPPED.stream().anyMatch(matcher -> matcher.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser current)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<User> latest = userRepository.findById(current.getId());
        // 無効化・パスワードの変更（管理者によるリセット、別端末での変更）はログアウトさせる
        if (latest.isEmpty() || !latest.get().isEnabled()
                || !latest.get().getPasswordHash().equals(current.getPasswordHash())) {
            logoutHandler.logout(request, response, authentication);
            response.sendRedirect(request.getContextPath() + "/login?expired");
            return;
        }
        LoginUser refreshed = LoginUser.from(latest.get());
        if (changed(current, refreshed)) {
            // 権限・名前などの変更は、セッション上のログイン情報を差し替えて反映する
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(refreshed, null, refreshed.getAuthorities()));
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
        chain.doFilter(request, response);
    }

    private boolean changed(LoginUser current, LoginUser refreshed) {
        return current.isAdmin() != refreshed.isAdmin()
                || current.isMustChangePassword() != refreshed.isMustChangePassword()
                || !current.getLoginId().equals(refreshed.getLoginId())
                || !current.getName().equals(refreshed.getName());
    }
}
