package jp.bk.shiftmanager.config;

import jp.bk.shiftmanager.auth.ForcePasswordChangeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ForcePasswordChangeInterceptor())
                .excludePathPatterns("/password", "/login", "/error", "/css/**", "/js/**", "/icons/**",
                        "/manifest.webmanifest");
    }
}
