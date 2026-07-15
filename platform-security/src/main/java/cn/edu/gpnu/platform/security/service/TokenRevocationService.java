package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.security.config.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Clock;
import java.util.Date;

/**
 * 会话/令牌撤销（Phase 37a 安全急修 P0）。
 * 记录"某用户在此刻及之前签发的所有 access/refresh token 一律失效"的时间点到 Redis，
 * 由 JwtAuthenticationFilter 与 refresh 流程比对 token 的签发时间(iat)执行。
 * 登出、改密、管理员重置密码时调用 revoke，实现真正的会话撤销。
 */
@Service
public class TokenRevocationService {

    private static final String CUTOFF_KEY_PREFIX = "auth:revoke-after:";
    private static final String GENERATION_KEY_PREFIX = "auth:session-generation:";
    /** 2286 年前的 Unix 秒值小于该边界，而当前毫秒值远大于它。 */
    private static final long EPOCH_MILLIS_THRESHOLD = 10_000_000_000L;
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            local generation = redis.call('INCR', KEYS[2])
            redis.call('PERSIST', KEYS[2])
            local incoming = tonumber(ARGV[1])
            local ttlMillis = tonumber(ARGV[2])
            local selected = incoming
            local selectedRaw = ARGV[1]
            local existingRaw = redis.call('GET', KEYS[1])
            if existingRaw then
                local existing = tonumber(existingRaw)
                if existing and existing >= 0 then
                    if existing < 10000000000 then
                        existing = existing * 1000 + 999
                    end
                    selected = math.max(incoming, existing)
                    if selected == existing and existing > incoming then
                        selectedRaw = string.format('%.0f', existing)
                    end
                end
            end
            redis.call('SET', KEYS[1], selectedRaw)
            redis.call('PEXPIRE', KEYS[1], ttlMillis)
            return selected
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    @Autowired
    public TokenRevocationService(StringRedisTemplate redisTemplate, SecurityProperties securityProperties) {
        this(redisTemplate, securityProperties, Clock.systemUTC());
    }

    TokenRevocationService(StringRedisTemplate redisTemplate, SecurityProperties securityProperties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    /** 使该用户此刻及之前签发的所有 token 失效。 */
    public void revoke(Long userId) {
        if (userId == null) {
            return;
        }
        long now = clock.millis();
        long ttl = Math.max(Math.max(
                securityProperties.getJwt().getAccessTtlSeconds(),
                securityProperties.getJwt().getRefreshTtlSeconds()), 60L);
        redisTemplate.execute(
                REVOKE_SCRIPT,
                java.util.List.of(CUTOFF_KEY_PREFIX + userId, GENERATION_KEY_PREFIX + userId),
                Long.toString(now),
                Long.toString(Duration.ofSeconds(ttl).toMillis()));
    }

    /** 当前会话代次；尚未发生过撤销时 generation key 缺失，按第 0 代处理。 */
    public long currentSessionGeneration(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        String raw = redisTemplate.opsForValue().get(GENERATION_KEY_PREFIX + userId);
        if (raw == null) {
            return 0L;
        }
        try {
            long generation = Long.parseLong(raw);
            if (generation < 0) {
                throw new NumberFormatException("negative generation");
            }
            return generation;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Redis 会话代次数据非法", e);
        }
    }

    /** token 未携带代次或 Redis 代次数据非法时一律视为不匹配。 */
    public boolean isCurrentSessionGeneration(Long userId, Long claimedGeneration) {
        if (userId == null || claimedGeneration == null || claimedGeneration < 0) {
            return false;
        }
        try {
            return currentSessionGeneration(userId) == claimedGeneration;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * 判断 token 是否已被撤销。新 token 使用毫秒级 {@code iatMs}；历史 token 缺少该 claim 时，
     * 回退到标准 {@code iat} 并对 cutoff 所在整秒作保守撤销。
     */
    public boolean isRevoked(Long userId, Date issuedAt, Long issuedAtMillis) {
        if (userId == null) {
            return false;
        }
        RevocationCutoff cutoff = loadCutoff(userId);
        if (cutoff == null) {
            return false;
        }
        if (issuedAtMillis != null && issuedAtMillis >= 0) {
            return cutoff.legacySeconds()
                    ? Math.floorDiv(issuedAtMillis, 1000L) <= cutoff.value()
                    : issuedAtMillis <= cutoff.value();
        }
        if (issuedAt == null) {
            return true;
        }
        long issuedAtSecond = issuedAt.toInstant().getEpochSecond();
        long cutoffSecond = cutoff.legacySeconds()
                ? cutoff.value()
                : Math.floorDiv(cutoff.value(), 1000L);
        return issuedAtSecond <= cutoffSecond;
    }

    /** 兼容尚未传递 {@code iatMs} 的调用方。 */
    public boolean isRevoked(Long userId, Date issuedAt) {
        return isRevoked(userId, issuedAt, null);
    }

    private RevocationCutoff loadCutoff(Long userId) {
        String raw = redisTemplate.opsForValue().get(CUTOFF_KEY_PREFIX + userId);
        if (raw == null) {
            return null;
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 0) {
                return null;
            }
            return new RevocationCutoff(value, value < EPOCH_MILLIS_THRESHOLD);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record RevocationCutoff(long value, boolean legacySeconds) {
    }
}
