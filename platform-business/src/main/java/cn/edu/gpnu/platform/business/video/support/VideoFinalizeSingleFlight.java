package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 视频定稿分布式单飞租约。
 *
 * <p>数据库 MERGING 状态需要允许崩溃后的请求接管，不能单独充当“正在执行”的依据；
 * Redis 短租约用于区分活跃执行者与无人处理的遗留 MERGING 会话。</p>
 */
@Component
@RequiredArgsConstructor
public class VideoFinalizeSingleFlight {

    private static final String KEY_PREFIX = "video:finalize:";
    private static final Duration LEASE_GRACE = Duration.ofMinutes(1);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final VideoProbeProperties properties;

    /**
     * 尝试取得 uploadId 的唯一执行权；返回 null 表示已有活跃定稿者。
     */
    public Lease tryAcquire(String uploadId) {
        String key = KEY_PREFIX + uploadId;
        String token = UUID.randomUUID().toString();
        Duration configured = properties.getMaxDuration();
        Duration ttl = (configured == null || configured.isZero() || configured.isNegative())
                ? Duration.ofMinutes(11)
                : configured.plus(LEASE_GRACE);
        final Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        } catch (RuntimeException e) {
            throw new BizException("视频定稿协调服务暂不可用，请稍后重试");
        }
        return Boolean.TRUE.equals(acquired) ? new Lease(key, token) : null;
    }

    public final class Lease implements AutoCloseable {

        private final String key;
        private final String token;
        private boolean released;

        private Lease(String key, String token) {
            this.key = key;
            this.token = token;
        }

        @Override
        public void close() {
            if (released) {
                return;
            }
            released = true;
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
            } catch (RuntimeException ignored) {
                // 释放失败由 TTL 兜底；不能用无 token 的 DEL 误删新执行者的租约。
            }
        }
    }
}
