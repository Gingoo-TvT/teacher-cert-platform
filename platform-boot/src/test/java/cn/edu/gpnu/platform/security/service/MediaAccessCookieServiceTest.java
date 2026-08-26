package cn.edu.gpnu.platform.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaAccessCookieServiceTest {

    @Test
    void issuesShortLivedHttpOnlyStrictCookieFromValidatedBearerHeader() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        JwtService jwtService = accessTokenExpiringIn(600);
        MediaAccessCookieService service = new MediaAccessCookieService(environment, jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header.payload.signature");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issue(request, response, 300);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains(MediaAccessCookieService.COOKIE_NAME + "=header.payload.signature")
                .contains("Path=/api")
                .contains("Max-Age=300")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }

    @Test
    void capsCookieLifetimeAtAccessTokenRemainingLifetime() {
        MediaAccessCookieService service = new MediaAccessCookieService(
                new MockEnvironment(), accessTokenExpiringIn(45));
        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issue(request, response, 300);

        assertThat(maxAge(response)).isBetween(1L, 45L);
    }

    @Test
    void doesNotIssueCookieForRefreshToken() {
        JwtService jwtService = mock(JwtService.class);
        Claims claims = mock(Claims.class);
        when(jwtService.parse("header.payload.signature")).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("refresh");
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(300)));
        MediaAccessCookieService service = new MediaAccessCookieService(new MockEnvironment(), jwtService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issue(bearerRequest(), response, 300);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void doesNotIssueCookieForExpiredAccessToken() {
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.parse("header.payload.signature"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));
        MediaAccessCookieService service = new MediaAccessCookieService(new MockEnvironment(), jwtService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issue(bearerRequest(), response, 300);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void doesNotIssueCookieWithoutBearerHeader() {
        JwtService jwtService = mock(JwtService.class);
        MediaAccessCookieService service = new MediaAccessCookieService(new MockEnvironment(), jwtService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issue(new MockHttpServletRequest(), response, 300);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void clearsMediaCookieOnLogout() {
        MediaAccessCookieService service = new MediaAccessCookieService(
                new MockEnvironment(), mock(JwtService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains(MediaAccessCookieService.COOKIE_NAME + "=")
                .contains("Path=/api")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }

    private JwtService accessTokenExpiringIn(long seconds) {
        JwtService jwtService = mock(JwtService.class);
        Claims claims = mock(Claims.class);
        when(jwtService.parse("header.payload.signature")).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("access");
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(seconds)));
        return jwtService;
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header.payload.signature");
        return request;
    }

    private long maxAge(MockHttpServletResponse response) {
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return Long.parseLong(setCookie.replaceAll(".*Max-Age=([0-9]+).*", "$1"));
    }
}
