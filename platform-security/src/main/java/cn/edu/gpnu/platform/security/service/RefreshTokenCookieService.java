package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.security.config.SecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/** refresh token 仅通过窄路径 HttpOnly Cookie 在浏览器与刷新端点之间传递。 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieService {

    public static final String COOKIE_NAME = "TCP_REFRESH";
    public static final String COOKIE_PATH = "/api/auth/refresh";

    private final Environment environment;
    private final SecurityProperties securityProperties;

    public void issue(HttpServletResponse response, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalStateException("refresh token 缺失，无法签发 Cookie");
        }
        ResponseCookie cookie = baseCookie(refreshToken)
                .maxAge(Duration.ofSeconds(securityProperties.getJwt().getRefreshTtlSeconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(environment.acceptsProfiles(Profiles.of("prod")))
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
