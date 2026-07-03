package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.security.dto.ChangePasswordRequest;
import cn.edu.gpnu.platform.security.dto.LoginRequest;
import cn.edu.gpnu.platform.security.dto.RefreshRequest;
import cn.edu.gpnu.platform.security.service.AuthService;
import cn.edu.gpnu.platform.security.vo.CaptchaVO;
import cn.edu.gpnu.platform.security.vo.LoginVO;
import cn.edu.gpnu.platform.security.vo.MeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(authService.captcha());
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "刷新 token")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request));
    }

    @Operation(summary = "退出登录")
    @AuditLog(bizType = "auth", operation = "logout")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @Operation(summary = "修改密码")
    @AuditLog(bizType = "auth", operation = "changePwd")
    @PostMapping("/change-pwd")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.ok();
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public Result<MeVO> me() {
        return Result.ok(authService.me());
    }
}
