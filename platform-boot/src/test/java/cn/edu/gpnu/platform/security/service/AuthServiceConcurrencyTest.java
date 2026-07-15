package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.config.SecurityProperties;
import cn.edu.gpnu.platform.security.dto.LoginRequest;
import cn.edu.gpnu.platform.security.dto.RefreshRequest;
import cn.edu.gpnu.platform.security.vo.LoginVO;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import cn.edu.gpnu.platform.system.service.UserSecurityService;
import cn.edu.gpnu.platform.system.vo.UserSecurityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceConcurrencyTest {

    private static final long USER_ID = 81L;
    private static final String VERIFIED_HASH = "$2a$10$verified-password-hash-snapshot";

    @Mock
    private CaptchaService captchaService;
    @Mock
    private UserSecurityService userSecurityService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ParamService paramService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenRevocationService tokenRevocationService;
    @Mock
    private DataScopeService dataScopeService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = new SecurityProperties();
        authService = new AuthService(
                captchaService,
                new JwtService(securityProperties),
                userSecurityService,
                auditLogService,
                paramService,
                passwordEncoder,
                securityProperties,
                tokenRevocationService,
                dataScopeService);
    }

    @ParameterizedTest(name = "reload 后 {0} 时拒绝签发 token")
    @MethodSource("unsafeReloads")
    void loginRejectsUnsafeReloadAfterPasswordVerification(String scenario, UserSecurityVO refreshed) {
        UserSecurityVO verified = user("ENABLED", VERIFIED_HASH);
        when(userSecurityService.loadByUsername("concurrent-user")).thenReturn(verified);
        when(passwordEncoder.matches("Correct-Password-2026!", VERIFIED_HASH)).thenReturn(true);
        when(userSecurityService.loadById(USER_ID)).thenReturn(refreshed);

        LoginRequest request = new LoginRequest();
        request.setUsername("concurrent-user");
        request.setPassword("Correct-Password-2026!");
        request.setCaptchaId("captcha-id");
        request.setCaptchaCode("1234");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BizException.class)
                .hasMessage("登录状态已变化，请重新登录");

        verify(userSecurityService).markLoginSuccess(eq(USER_ID), eq(VERIFIED_HASH), any());
        verify(auditLogService, never()).record(any());
        verify(tokenRevocationService, never()).revoke(any());
    }

    @Test
    void refreshKeepsInputGenerationWhenLogoutWinsAfterGenerationCheck() {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getJwt().setSecret(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        JwtService jwtService = new JwtService(securityProperties);
        jwtService.init();
        UserSecurityVO user = user("ENABLED", VERIFIED_HASH);
        long inputGeneration = 7L;
        String refreshToken = jwtService.issueRefreshToken(user, inputGeneration);

        when(tokenRevocationService.isRevoked(eq(USER_ID), any(Date.class), anyLong())).thenReturn(false);
        AtomicLong currentGeneration = new AtomicLong(inputGeneration);
        AtomicBoolean logoutInjected = new AtomicBoolean();
        when(tokenRevocationService.isCurrentSessionGeneration(USER_ID, inputGeneration))
                .thenAnswer(invocation -> {
                    boolean matches = currentGeneration.get() == invocation.getArgument(1, Long.class);
                    if (logoutInjected.compareAndSet(false, true)) {
                        currentGeneration.incrementAndGet(); // 首次校验后并发 logout
                    }
                    return matches;
                });
        when(userSecurityService.loadById(USER_ID)).thenReturn(user);

        AuthService service = new AuthService(
                captchaService,
                jwtService,
                userSecurityService,
                auditLogService,
                paramService,
                passwordEncoder,
                securityProperties,
                tokenRevocationService,
                dataScopeService);
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(refreshToken);

        LoginVO refreshed = service.refresh(request);

        assertThat(currentGeneration).hasValue(inputGeneration + 1L);
        Long accessGeneration = jwtService.sessionGeneration(jwtService.parse(refreshed.getAccessToken()));
        Long refreshGeneration = jwtService.sessionGeneration(jwtService.parse(refreshed.getRefreshToken()));
        assertThat(accessGeneration).isEqualTo(inputGeneration);
        assertThat(refreshGeneration).isEqualTo(inputGeneration);
        assertThat(tokenRevocationService.isCurrentSessionGeneration(USER_ID, accessGeneration)).isFalse();
        assertThat(tokenRevocationService.isCurrentSessionGeneration(USER_ID, refreshGeneration)).isFalse();
        assertThat(currentGeneration).hasValue(inputGeneration + 1L);
        verify(tokenRevocationService, never()).currentSessionGeneration(USER_ID);
    }

    private static Stream<Arguments> unsafeReloads() {
        return Stream.of(
                Arguments.of("用户不存在", (UserSecurityVO) null),
                Arguments.of("账号已停用", user("DISABLED", VERIFIED_HASH)),
                Arguments.of("口令哈希已变化", user("ENABLED", "$2a$10$concurrently-reset-password-hash"))
        );
    }

    private static UserSecurityVO user(String status, String passwordHash) {
        UserSecurityVO user = new UserSecurityVO();
        user.setId(USER_ID);
        user.setUsername("concurrent-user");
        user.setStatus(status);
        user.setPasswordHash(passwordHash);
        return user;
    }
}
