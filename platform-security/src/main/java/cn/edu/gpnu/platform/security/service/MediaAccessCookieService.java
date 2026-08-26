package cn.edu.gpnu.platform.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

/**
 * 为浏览器原生媒体元素签发短时访问 Cookie。
 *
 * <p>Cookie 只会被认证过滤器用于三个只读内容端点，不能替代普通 API 的 Bearer token。
 */
@Component
@RequiredArgsConstructor
public class MediaAccessCookieService {

    public static final String COOKIE_NAME = "TCP_MEDIA_ACCESS";

    private final Environment environment;
    private final JwtService jwtService;

    public void issue(HttpServletRequest request, HttpServletResponse response, int expirySeconds) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return;
        }
        String token = authorization.substring(7);
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException ignored) {
            return;
        }
        if (!"access".equals(claims.get("typ", String.class)) || claims.getExpiration() == null) {
            return;
        }
        long remainingSeconds = Duration.between(Instant.now(), claims.getExpiration().toInstant()).getSeconds();
        if (remainingSeconds <= 0) {
            return;
        }
        long maxAge = Math.min(Math.max(1, Math.min(expirySeconds, 3600)), remainingSeconds);
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(environment.acceptsProfiles(Profiles.of("prod")))
                .sameSite("Strict")
                .path("/api")
                .maxAge(Duration.ofSeconds(maxAge))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(environment.acceptsProfiles(Profiles.of("prod")))
                .sameSite("Strict")
                .path("/api")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
