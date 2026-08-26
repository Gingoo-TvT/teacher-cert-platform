package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.dto.ChangePasswordRequest;
import cn.edu.gpnu.platform.security.dto.LoginRequest;
import cn.edu.gpnu.platform.security.dto.RefreshRequest;
import cn.edu.gpnu.platform.security.service.AuthService;
import cn.edu.gpnu.platform.security.service.MediaAccessCookieService;
import cn.edu.gpnu.platform.security.service.RefreshTokenCookieService;
import cn.edu.gpnu.platform.security.vo.CaptchaVO;
import cn.edu.gpnu.platform.security.vo.LoginVO;
import cn.edu.gpnu.platform.security.vo.MeVO;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MediaAccessCookieService mediaAccessCookieService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final AuditLogService auditLogService;

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(authService.captcha());
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginVO result = authService.login(request);
        // 同一浏览器直接切换账号时，旧账号签发的媒体 Cookie 不能跨入新会话。
        mediaAccessCookieService.clear(response);
        refreshTokenCookieService.issue(response, result.getRefreshToken());
        return Result.ok(result);
    }

    @Operation(summary = "刷新 token")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(
            @CookieValue(name = RefreshTokenCookieService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "refresh Cookie 缺失，请重新登录");
        }
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(refreshToken);
        LoginVO result = authService.refresh(request);
        refreshTokenCookieService.issue(response, result.getRefreshToken());
        return Result.ok(result);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        Long operatorId = UserContext.getUserIdOrSystem();
        String target = "user:" + operatorId;
        try {
            auditLogService.recordRequiresNew(
                    "auth", operatorId, target, "logout", null, "PENDING", "服务端会话撤销已发起");
            try {
                authService.logout();
            } catch (RuntimeException | Error failure) {
                recordLogoutFailure(operatorId, target, failure);
                throw failure;
            }
            auditLogService.recordRequiresNew(
                    "auth", operatorId, target, "logout", "PENDING", "SUCCESS", "服务端会话撤销成功");
            return Result.ok();
        } finally {
            refreshTokenCookieService.clear(response);
            mediaAccessCookieService.clear(response);
        }
    }

    @Operation(summary = "修改密码")
    @AuditLog(bizType = "auth", operation = "changePwd")
    @PostMapping("/change-pwd")
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, HttpServletResponse response) {
        authService.changePassword(request);
        refreshTokenCookieService.clear(response);
        mediaAccessCookieService.clear(response);
        return Result.ok();
    }

    private void recordLogoutFailure(Long operatorId, String target, Throwable failure) {
        try {
            auditLogService.recordRequiresNew(
                    "auth", operatorId, target, "logout", "PENDING", "ERROR", "服务端会话撤销失败");
        } catch (RuntimeException auditFailure) {
            failure.addSuppressed(auditFailure);
        }
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public Result<MeVO> me() {
        return Result.ok(authService.me());
    }
}
