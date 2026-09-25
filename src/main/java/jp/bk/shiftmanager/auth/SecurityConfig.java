package jp.bk.shiftmanager.auth;

import jp.bk.shiftmanager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;

@Configuration
public class SecurityConfig {

    /** ログイン状態の保持期間（10日） */
    public static final int REMEMBER_ME_SECONDS = 10 * 24 * 60 * 60;

    /** 未ログインでも取得でき、ログイン中ユーザーの状態照合も行わない静的ファイル */
    public static final String[] STATIC_RESOURCES = {"/css/**", "/js/**", "/icons/**", "/manifest.webmanifest"};

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService,
            @Value("${app.remember-me-key}") String rememberMeKey, UserRepository userRepository) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(STATIC_RESOURCES).permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("loginId")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                // パスワードハッシュを含む署名のため、パスワード変更で古いCookieは無効になる
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                        .userDetailsService(userDetailsService))
                // 管理者による無効化・リセット・権限変更をログイン中のセッションにも反映する
                .addFilterAfter(new UserStateCheckFilter(userRepository), RememberMeAuthenticationFilter.class);
        return http.build();
    }
}
