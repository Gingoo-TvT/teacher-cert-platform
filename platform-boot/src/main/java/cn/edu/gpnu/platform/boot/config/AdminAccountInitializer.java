package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.service.TokenRevocationService;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * WS-2（审计#1 High）：生产 admin bootstrap —— 关闭「公开固定口令超管可登录」的账号接管窗口。
 *
 * <p>{@code admin} 由已应用迁移 {@code V8__rbac_seed.sql} 创建（{@code @rbac_seed_password_hash} =
 * bcrypt("ChangeMe123!")、{@code V26} 未删、Phase 52 视为生产标准保留）。生产若原样起栈即存在
 * 「公开固定口令超管」（审计 #1）。§0 禁止编辑已应用迁移，故不改 V8，改在应用层 bootstrap：
 * <ul>
 *   <li>注入了 {@code ADMIN_INITIAL_PASSWORD_HASH}（bcrypt 值）且 admin 仍使用 V8 公开种子口令 →
 *       一次性覆盖其口令哈希（{@code must_change_pwd=1} 强制首登改密）并撤销旧 token；</li>
 *   <li>未注入且为 <b>prod</b> profile → 抛错 <b>fail-fast 拒绝启动</b>（同 {@code JwtService}/JWT_SECRET 风格），
 *       生产绝不以公开口令 admin 起栈；</li>
 *   <li>未注入且非 prod（dev/test）→ no-op：admin 保留 {@code db/testseed} 的 ChangeMe123!（本地/IT 便利，
 *       故 ITs 走默认 dev profile 时本组件不改任何账号、断言不受影响）。</li>
 * </ul>
 * 已完成 bootstrap 或首登改密后不再覆写，避免重启把一次性凭据变成永久重置入口。覆盖在 Web 开始服务
 * <b>之前</b>发生（{@link SmartInitializingSingleton} 在全部单例完成初始化后、上下文 refresh 前执行；
 * 因而 JWT/STAFF 等其它 fail-fast 已先通过，且 Web server 尚未对外服务）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements SmartInitializingSingleton {

    private static final String LEGACY_PUBLIC_PASSWORD = "ChangeMe123!";
    private static final Pattern BCRYPT_HASH = Pattern.compile(
            "^\\$2[ayb]\\$([12]\\d|3[01])\\$[./A-Za-z0-9]{53}$");

    private final SysUserMapper userMapper;
    private final Environment environment;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;

    @Value("${platform.security.admin.initial-password-hash:}")
    private String adminInitialPasswordHash;

    @Value("${platform.security.admin.username:admin}")
    private String adminUsername;

    @Override
    public void afterSingletonsInstantiated() {
        bootstrapAdmin();
    }

    public void bootstrapAdmin() {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (StringUtils.hasText(adminInitialPasswordHash)) {
            String bootstrapHash = adminInitialPasswordHash.trim();
            if (!BCRYPT_HASH.matcher(bootstrapHash).matches()) {
                throw new BizException("ADMIN_INITIAL_PASSWORD_HASH 必须是合法 BCrypt 哈希（$2a/$2b/$2y）");
            }
            if (passwordEncoder.matches(LEGACY_PUBLIC_PASSWORD, bootstrapHash)) {
                throw new BizException("ADMIN_INITIAL_PASSWORD_HASH 不能仍对应公开默认口令 ChangeMe123!");
            }
            SysUser admin = userMapper.selectByUsername(adminUsername);
            if (admin == null) {
                throw new BizException("未找到 admin 账号（应由 V8 迁移创建），无法完成生产 bootstrap");
            }
            if (!StringUtils.hasText(admin.getPasswordHash())
                    || !BCRYPT_HASH.matcher(admin.getPasswordHash()).matches()) {
                throw new BizException("admin 当前口令哈希不是受支持的 BCrypt 格式，拒绝启动");
            }
            if (admin.getPasswordHash().equals(bootstrapHash)) {
                tokenRevocationService.revoke(admin.getId());
                log.info("[admin-bootstrap] admin 已使用配置的 bootstrap 哈希，已补偿撤销旧 token");
                return;
            }
            if (!passwordEncoder.matches(LEGACY_PUBLIC_PASSWORD, admin.getPasswordHash())) {
                log.info("[admin-bootstrap] admin 已脱离 V8 公开种子口令，保留现有口令与账号状态");
                return;
            }
            tokenRevocationService.revoke(admin.getId());
            int updated = userMapper.update(new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, admin.getId())
                    .eq(SysUser::getPasswordHash, admin.getPasswordHash())
                    .set(SysUser::getPasswordHash, bootstrapHash)
                    .set(SysUser::getMustChangePwd, 1)
                    .set(SysUser::getUpdatedBy, 0L)
                    .set(SysUser::getUpdatedAt, LocalDateTime.now()));
            if (updated != 1) {
                throw new BizException("admin bootstrap 发生并发冲突，拒绝启动，请核查账号状态后重试");
            }
            tokenRevocationService.revoke(admin.getId());
            log.info("[admin-bootstrap] 已一次性替换 V8 公开 admin 口令并撤销旧 token（must_change_pwd=1）");
        } else if (prod) {
            throw new BizException("生产环境未配置 ADMIN_INITIAL_PASSWORD_HASH："
                    + "拒绝以公开固定口令的 admin 启动，请注入 bcrypt 口令哈希后重启");
        } else {
            log.debug("[admin-bootstrap] 非生产且未注入 ADMIN_INITIAL_PASSWORD_HASH：保留种子 admin 口令（仅 dev/test）");
        }
    }
}
