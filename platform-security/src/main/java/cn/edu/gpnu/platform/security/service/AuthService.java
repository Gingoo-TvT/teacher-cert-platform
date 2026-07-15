package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.config.SecurityProperties;
import cn.edu.gpnu.platform.security.dto.ChangePasswordRequest;
import cn.edu.gpnu.platform.security.dto.LoginRequest;
import cn.edu.gpnu.platform.security.dto.RefreshRequest;
import cn.edu.gpnu.platform.security.vo.CaptchaVO;
import cn.edu.gpnu.platform.security.vo.LoginVO;
import cn.edu.gpnu.platform.security.vo.MeVO;
import cn.edu.gpnu.platform.system.service.UserSecurityService;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.vo.UserSecurityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final CaptchaService captchaService;
    private final JwtService jwtService;
    private final UserSecurityService userSecurityService;
    private final AuditLogService auditLogService;
    private final ParamService paramService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final TokenRevocationService tokenRevocationService;
    private final DataScopeService dataScopeService;

    public CaptchaVO captcha() {
        CaptchaService.Captcha captcha = captchaService.create();
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaId(captcha.captchaId());
        vo.setImage(captcha.image());
        return vo;
    }

    public LoginVO login(LoginRequest request) {
        captchaService.validate(request.getCaptchaId(), request.getCaptchaCode());
        UserSecurityVO user = userSecurityService.loadByUsername(normalize(request.getUsername()));
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        assertLoginAllowed(user);
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int threshold = paramService.getInt("login.lockThreshold", securityProperties.getLogin().getLockThreshold());
            int lockMinutes = paramService.getInt("login.lockMinutes", securityProperties.getLogin().getLockMinutes());
            LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
            if (userSecurityService.markLoginFailure(user.getId(), threshold, lockedUntil)) {
                throw new BizException("密码错误次数过多，账号已锁定");
            }
            throw new BizException("用户名或密码错误");
        }
        userSecurityService.markLoginSuccess(user.getId(), user.getPasswordHash(), LocalDateTime.now());
        UserSecurityVO refreshed = userSecurityService.loadById(user.getId());
        if (refreshed == null
                || !"ENABLED".equals(refreshed.getStatus())
                || !jwtService.hasSameCredential(user, refreshed)) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "登录状态已变化，请重新登录");
        }
        long sessionGeneration = tokenRevocationService.currentSessionGeneration(refreshed.getId());
        recordLoginAudit(refreshed);
        return loginVO(refreshed, sessionGeneration);
    }

    public LoginVO refresh(RefreshRequest request) {
        io.jsonwebtoken.Claims claims = jwtService.parse(request.getRefreshToken());
        if (!"refresh".equals(claims.get("typ", String.class))) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "token类型不正确");
        }
        Long userId = Long.valueOf(claims.getSubject());
        if (tokenRevocationService.isRevoked(
                userId, claims.getIssuedAt(), jwtService.preciseIssuedAtMillis(claims))) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "登录状态已失效，请重新登录");
        }
        Long sessionGeneration = jwtService.sessionGeneration(claims);
        if (!tokenRevocationService.isCurrentSessionGeneration(userId, sessionGeneration)) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "登录状态已失效，请重新登录");
        }
        UserSecurityVO user = userSecurityService.loadById(userId);
        if (user == null || !"ENABLED".equals(user.getStatus())) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "用户不存在或已停用");
        }
        if (!jwtService.hasCurrentCredentialVersion(claims, user)) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "登录凭据已变更，请重新登录");
        }
        return loginVO(user, sessionGeneration.longValue());
    }

    /** 登出：推进会话代次，并撤销当前用户此刻及之前签发的所有 token。 */
    public void logout() {
        tokenRevocationService.revoke(UserContext.getUserId());
    }

    public void changePassword(ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        UserSecurityVO user = userSecurityService.loadById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException("旧密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BizException("新密码不能与旧密码相同");
        }
        userSecurityService.changePassword(
                userId, user.getPasswordHash(), passwordEncoder.encode(request.getNewPassword()));
        tokenRevocationService.revoke(userId); // 改密后旧 token 立即失效
    }

    public MeVO me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        UserSecurityVO user = userSecurityService.loadById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return meVO(user);
    }

    private void assertLoginAllowed(UserSecurityVO user) {
        LocalDateTime now = LocalDateTime.now();
        if ("DISABLED".equals(user.getStatus())) {
            throw new BizException("账号已停用");
        }
        if ("LOCKED".equals(user.getStatus())) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                throw new BizException("账号已锁定，请稍后再试");
            }
            userSecurityService.unlockAfterExpired(user.getId());
            user.setStatus("ENABLED");
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }
    }

    private LoginVO loginVO(UserSecurityVO user, long sessionGeneration) {
        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtService.issueAccessToken(user, sessionGeneration));
        vo.setRefreshToken(jwtService.issueRefreshToken(user, sessionGeneration));
        vo.setExpiresIn(securityProperties.getJwt().getAccessTtlSeconds());
        vo.setMustChangePwd(user.getMustChangePwd() != null && user.getMustChangePwd() == 1);
        vo.setUser(meVO(user));
        return vo;
    }

    private MeVO meVO(UserSecurityVO user) {
        MeVO vo = new MeVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setUserType(user.getUserType());
        vo.setCollegeId(user.getCollegeId());
        vo.setStudentId(user.getStudentId());
        vo.setMustChangePwd(user.getMustChangePwd() != null && user.getMustChangePwd() == 1);
        vo.setUserManagementWritable(
                dataScopeService.hasAllSchoolScope(user.getId(), "system:user:manage"));
        vo.setRoles(new ArrayList<>(user.getRoles()));
        vo.setPermissions(new ArrayList<>(user.getPermissions()));
        return vo;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private void recordLoginAudit(UserSecurityVO user) {
        try {
            SysAuditLog log = new SysAuditLog();
            log.setBizType("auth");
            log.setBizId(user.getId());
            log.setTarget(user.getUsername());
            log.setOperatorId(user.getId());
            log.setOperation("login");
            log.setNewStatus("SUCCESS");
            auditLogService.record(log);
        } catch (Exception e) {
            log.warn("写登录审计日志失败: {}", e.getMessage());
        }
    }
}
