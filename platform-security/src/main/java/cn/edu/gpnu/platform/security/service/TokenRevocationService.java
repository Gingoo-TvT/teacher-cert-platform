package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.security.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 会话/令牌撤销（Phase 37a 安全急修 P0）。
 * 记录"某用户在此刻之前签发的所有 access/refresh token 一律失效"的时间点到 Redis，
 * 由 JwtAuthenticationFilter 与 refresh 流程比对 token 的签发时间(iat)执行。
 * 登出、改密、管理员重置密码时调用 revoke，实现真正的会话撤销。
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String KEY_PREFIX = "auth:revoke-after:";

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    /** 使该用户此刻之前签发的所有 token 失效。 */
    public void revoke(Long userId) {
        if (userId == null) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        long ttl = Math.max(securityProperties.getJwt().getRefreshTtlSeconds(), 60L);
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, Long.toString(now), Duration.ofSeconds(ttl));
    }

    /** token 签发时间早于该用户的撤销时间点则已失效。 */
    public boolean isRevoked(Long userId, Date issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        String cutoff = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (cutoff == null) {
            return false;
        }
        try {
            return issuedAt.toInstant().getEpochSecond() < Long.parseLong(cutoff);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
