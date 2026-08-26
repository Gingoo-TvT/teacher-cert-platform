package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieServiceTest {

    @Test
    void prodCookieIsHttpOnlySecureStrictAndLimitedToRefreshPath() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setRefreshTtlSeconds(604800);
        RefreshTokenCookieService service = new RefreshTokenCookieService(environment, properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issue(response, "header.payload.signature");

        assertThat(response.getHeader("Set-Cookie"))
                .contains("TCP_REFRESH=header.payload.signature")
                .contains("Path=/api/auth/refresh")
                .contains("Max-Age=604800")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .doesNotContain("Domain=");
    }

    @Test
    void clearUsesTheSameAttributesAndExpiresImmediately() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        RefreshTokenCookieService service = new RefreshTokenCookieService(
                environment, new SecurityProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains("TCP_REFRESH=")
                .contains("Path=/api/auth/refresh")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .doesNotContain("Domain=");
    }
}
