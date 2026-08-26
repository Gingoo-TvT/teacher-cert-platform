package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.dto.ChangePasswordRequest;
import cn.edu.gpnu.platform.security.dto.LoginRequest;
import cn.edu.gpnu.platform.security.service.AuthService;
import cn.edu.gpnu.platform.security.service.MediaAccessCookieService;
import cn.edu.gpnu.platform.security.service.RefreshTokenCookieService;
import cn.edu.gpnu.platform.security.vo.LoginVO;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerLogoutTest {

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void shouldClearPreviousAccountMediaCookieAfterSuccessfulLogin() {
        AuthService authService = mock(AuthService.class);
        MediaAccessCookieService cookieService = mock(MediaAccessCookieService.class);
        RefreshTokenCookieService refreshCookieService = mock(RefreshTokenCookieService.class);
        AuthController controller = new AuthController(
                authService, cookieService, refreshCookieService, mock(AuditLogService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginRequest request = new LoginRequest();
        LoginVO login = new LoginVO();
        login.setRefreshToken("refresh-token");
        when(authService.login(request)).thenReturn(login);

        controller.login(request, response);

        var order = inOrder(authService, cookieService, refreshCookieService);
        order.verify(authService).login(request);
        order.verify(cookieService).clear(response);
        order.verify(refreshCookieService).issue(response, "refresh-token");
    }

    @Test
    void shouldRefreshFromCookieAndReissueCookieWithoutReturningBodyToken() throws Exception {
        AuthService authService = mock(AuthService.class);
        RefreshTokenCookieService refreshCookieService = mock(RefreshTokenCookieService.class);
        AuthController controller = new AuthController(
                authService, mock(MediaAccessCookieService.class), refreshCookieService,
                mock(AuditLogService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginVO refreshed = new LoginVO();
        refreshed.setRefreshToken("rotated-refresh");
        when(authService.refresh(argThat(request -> "cookie-refresh".equals(request.getRefreshToken()))))
                .thenReturn(refreshed);

        var result = controller.refresh("cookie-refresh", response);

        verify(authService).refresh(argThat(request -> "cookie-refresh".equals(request.getRefreshToken())));
        verify(refreshCookieService).issue(response, "rotated-refresh");
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("refreshToken");
    }

    @Test
    void shouldRejectRefreshWhenCookieIsMissing() {
        AuthService authService = mock(AuthService.class);
        RefreshTokenCookieService refreshCookieService = mock(RefreshTokenCookieService.class);
        AuthController controller = new AuthController(
                authService, mock(MediaAccessCookieService.class), refreshCookieService,
                mock(AuditLogService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.refresh(" ", response))
                .isInstanceOfSatisfying(BizException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode());
                    assertThat(failure.getMessage()).contains("Cookie 缺失");
                });

        verify(authService, never()).refresh(any());
        verify(refreshCookieService, never()).issue(any(), anyString());
    }

    @Test
    void shouldClearRefreshAndMediaCookiesAfterSuccessfulPasswordChange() {
        AuthService authService = mock(AuthService.class);
        MediaAccessCookieService mediaCookieService = mock(MediaAccessCookieService.class);
        RefreshTokenCookieService refreshCookieService = mock(RefreshTokenCookieService.class);
        AuthController controller = new AuthController(
                authService, mediaCookieService, refreshCookieService, mock(AuditLogService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();
        ChangePasswordRequest request = new ChangePasswordRequest();

        controller.changePassword(request, response);

        var order = inOrder(authService, refreshCookieService, mediaCookieService);
        order.verify(authService).changePassword(request);
        order.verify(refreshCookieService).clear(response);
        order.verify(mediaCookieService).clear(response);
    }

    @Test
    void shouldClearMediaCookieAfterSuccessfulLogout() {
        AuthService authService = mock(AuthService.class);
        MediaAccessCookieService cookieService = mock(MediaAccessCookieService.class);
        RefreshTokenCookieService refreshCookieService = mock(RefreshTokenCookieService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuthController controller = new AuthController(
                authService, cookieService, refreshCookieService, auditLogService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserContext.setUserId(73L);

        controller.logout(response);

        var order = inOrder(auditLogService, authService, refreshCookieService, cookieService);
        order.verify(auditLogService).recordRequiresNew(
                "auth", 73L, "user:73", "logout",
                null, "PENDING", "服务端会话撤销已发起");
        order.verify(authService).logout();
        order.verify(auditLogService).recordRequiresNew(
                "auth", 73L, "user:73", "logout",
                "PENDING", "SUCCESS", "服务端会话撤销成功");
        order.verify(refreshCookieService).clear(response);
        order.verify(cookieService).clear(response);
    }

    @Test
    void shouldClearMediaCookieWhenSessionRevocationFails() {
        AuthService authService = mock(AuthService.class);
        MediaAccessCookieService cookieService = mock(MediaAccessCookieService.class);
        RefreshTokenCookieService refreshCookieService = mock(RefreshTokenCookieService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuthController controller = new AuthController(
                authService, cookieService, refreshCookieService, auditLogService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserContext.setUserId(74L);
        doThrow(new IllegalStateException("redis unavailable")).when(authService).logout();

        assertThatThrownBy(() -> controller.logout(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");

        verify(auditLogService).recordRequiresNew(
                "auth", 74L, "user:74", "logout",
                null, "PENDING", "服务端会话撤销已发起");
        verify(auditLogService).recordRequiresNew(
                "auth", 74L, "user:74", "logout",
                "PENDING", "ERROR", "服务端会话撤销失败");
        verify(auditLogService, never()).recordRequiresNew(
                eq("auth"), eq(74L), eq("user:74"), eq("logout"),
                eq("PENDING"), eq("SUCCESS"), anyString());
        verify(refreshCookieService).clear(response);
        verify(cookieService).clear(response);
    }

    @Test
    void passwordWritesUseTheirSingleSameTransactionAuditPath() throws Exception {
        AuditLog changePassword = AuthController.class
                .getDeclaredMethod("changePassword", ChangePasswordRequest.class, HttpServletResponse.class)
                .getAnnotation(AuditLog.class);
        AuditLog resetPassword = SystemSecurityController.class
                .getDeclaredMethod("resetPassword", Long.class)
                .getAnnotation(AuditLog.class);

        assertThat(changePassword.before()).isFalse();
        assertThat(resetPassword).isNull();
    }
}
