package cn.edu.gpnu.platform.security.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterMediaRouteTest {

    @Test
    void acceptsOnlyExactReadOnlyNumericMediaContentRoutes() {
        assertThat(matches("GET", "/api/video/reviews/12/content")).isTrue();
        assertThat(matches("HEAD", "/api/material/preview/9/content")).isTrue();
        assertThat(matches("GET", "/api/exemption/materials/3/content")).isTrue();

        assertThat(matches("POST", "/api/video/reviews/12/content")).isFalse();
        assertThat(matches("GET", "/api/video/reviews/12/play")).isFalse();
        assertThat(matches("GET", "/api/video/reviews/abc/content")).isFalse();
        assertThat(matches("GET", "/api/material/preview/9/content/extra")).isFalse();
        assertThat(matches("GET", "/api/auth/me")).isFalse();
    }

    private boolean matches(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        return JwtAuthenticationFilter.isMediaContentRequest(request);
    }
}
