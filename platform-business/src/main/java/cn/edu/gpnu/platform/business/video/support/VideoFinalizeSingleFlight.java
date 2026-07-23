package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频定稿分布式单飞租约。
 *
 * <p>数据库 MERGING 状态需要允许崩溃后的请求接管，不能单独充当“正在执行”的依据；
 * Redis 短租约用于区分活跃执行者与无人处理的遗留 MERGING 会话。递增 token 同时写入数据库，
 * 过期 owner 即使恢复运行也不能提交旧结果。</p>
 */
@Component
@RequiredArgsConstructor
public class VideoFinalizeSingleFlight {

    private static final String KEY_PREFIX = "video:finalize:";
    private static final long TOKEN_RETENTION_MILLIS = Duration.ofDays(7).toMillis();
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return nil
            end
            local token = redis.call('INCR', KEYS[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            redis.call('SET', KEYS[1], tostring(token), 'PX', ARGV[1])
            return token
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> OWNER_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final VideoProbeProperties properties;
    private final ScheduledExecutorService renewer = Executors.newSingleThreadScheduledExecutor(
            new LeaseThreadFactory());

    /**
     * 尝试取得 uploadId 的唯一执行权；返回 null 表示已有活跃定稿者。
     */
    public Lease tryAcquire(String uploadId) {
        String baseKey = KEY_PREFIX + "{" + uploadId + "}";
        String lockKey = baseKey + ":lock";
        String sequenceKey = baseKey + ":sequence";
        Duration ttl = properties.getLeaseDuration();
        Long token;
        try {
            token = redisTemplate.execute(ACQUIRE_SCRIPT, List.of(lockKey, sequenceKey),
                    String.valueOf(ttl.toMillis()), String.valueOf(TOKEN_RETENTION_MILLIS));
        } catch (RuntimeException e) {
            throw new BizException("视频定稿协调服务暂不可用，请稍后重试");
        }
        return token == null ? null : new Lease(lockKey, token, ttl, properties.getLeaseRenewInterval());
    }

    @PreDestroy
    void shutdown() {
        renewer.shutdownNow();
    }

    public final class Lease implements AutoCloseable {

        private final String key;
        private final long token;
        private final Duration ttl;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean lost = new AtomicBoolean();
        private final ScheduledFuture<?> renewal;

        private Lease(String key, long token, Duration ttl, Duration renewInterval) {
            this.key = key;
            this.token = token;
            this.ttl = ttl;
            long intervalMillis = renewInterval.toMillis();
            this.renewal = renewer.scheduleWithFixedDelay(
                    this::renewQuietly, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        }

        public long token() {
            return token;
        }

        /**
         * 在进入耗时阶段或提交事务前主动续租；旧 owner 不能续租并立即失败关闭。
         */
        public void renewOrThrow() {
            ensureOpen();
            final Long renewed;
            try {
                renewed = redisTemplate.execute(RENEW_SCRIPT, List.of(key),
                        String.valueOf(token), String.valueOf(ttl.toMillis()));
            } catch (RuntimeException e) {
                lost.set(true);
                throw new BizException("视频定稿租约续期失败，请稍后重试");
            }
            if (!Long.valueOf(1L).equals(renewed)) {
                lost.set(true);
                throw new BizException("视频定稿执行权已转移，本次结果已丢弃");
            }
        }

        /**
         * 耗时阶段返回后检查当前 owner，防止过期执行者继续探测或落库。
         */
        public void assertOwned() {
            ensureOpen();
            final Long owned;
            try {
                owned = redisTemplate.execute(OWNER_SCRIPT, List.of(key), String.valueOf(token));
            } catch (RuntimeException e) {
                lost.set(true);
                throw new BizException("视频定稿租约校验失败，请稍后重试");
            }
            if (!Long.valueOf(1L).equals(owned)) {
                lost.set(true);
                throw new BizException("视频定稿执行权已转移，本次结果已丢弃");
            }
        }

        private void renewQuietly() {
            if (closed.get() || lost.get()) {
                return;
            }
            try {
                Long renewed = redisTemplate.execute(RENEW_SCRIPT, List.of(key),
                        String.valueOf(token), String.valueOf(ttl.toMillis()));
                if (!Long.valueOf(1L).equals(renewed)) {
                    lost.set(true);
                }
            } catch (RuntimeException e) {
                lost.set(true);
            }
        }

        private void ensureOpen() {
            if (closed.get() || lost.get()) {
                throw new BizException("视频定稿执行权已失效，请重新发起");
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            renewal.cancel(false);
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), String.valueOf(token));
            } catch (RuntimeException ignored) {
                // 释放失败由 TTL 兜底；不能用无 token 的 DEL 误删新执行者的租约。
            }
        }
    }

    private static final class LeaseThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "video-finalize-lease-renewer");
            thread.setDaemon(true);
            return thread;
        }
    }
}
