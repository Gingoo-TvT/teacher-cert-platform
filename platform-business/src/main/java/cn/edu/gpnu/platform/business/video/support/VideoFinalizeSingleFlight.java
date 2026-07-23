package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 视频定稿分布式单飞租约。
 *
 * <p>数据库 MERGING 状态需要允许崩溃后的请求接管，不能单独充当“正在执行”的依据；
 * Redis 短租约使用不可重复的随机 owner 标识，仅用于区分活跃执行者与无人处理的遗留 MERGING 会话；
 * 不可回退的 fencing 世代由数据库会话行在认领事务内生成并永久保留。Redis 丢失或恢复旧快照时，
 * 随机 owner 与数据库高水位仍能共同拒绝旧执行者。</p>
 */
@Component
@RequiredArgsConstructor
public class VideoFinalizeSingleFlight {

    private static final String KEY_PREFIX = "video:finalize:";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            local acquired = redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX')
            if acquired then
                return 1
            end
            return 0
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
        String ownerId = UUID.randomUUID().toString();
        Duration ttl = properties.getLeaseDuration();
        Long acquired;
        try {
            acquired = redisTemplate.execute(ACQUIRE_SCRIPT, List.of(lockKey),
                    ownerId, String.valueOf(ttl.toMillis()));
        } catch (RuntimeException e) {
            throw new BizException("视频定稿协调服务暂不可用，请稍后重试");
        }
        return Long.valueOf(1L).equals(acquired)
                ? new Lease(lockKey, ownerId, ttl, properties.getLeaseRenewInterval())
                : null;
    }

    @PreDestroy
    void shutdown() {
        renewer.shutdownNow();
    }

    public final class Lease implements AutoCloseable {

        private final String key;
        private final String ownerId;
        private final Duration ttl;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean lost = new AtomicBoolean();
        private final AtomicLong generation = new AtomicLong();
        private final ScheduledFuture<?> renewal;

        private Lease(String key, String ownerId, Duration ttl, Duration renewInterval) {
            this.key = key;
            this.ownerId = ownerId;
            this.ttl = ttl;
            long intervalMillis = renewInterval.toMillis();
            this.renewal = renewer.scheduleWithFixedDelay(
                    this::renewQuietly, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        }

        /**
         * 认领事务锁住上传会话并递增持久高水位后，将本次数据库世代绑定到租约。
         */
        public void bindGeneration(long value) {
            if (value < 1) {
                throw new IllegalArgumentException("视频定稿世代必须为正数");
            }
            long current = generation.get();
            if (current == value) {
                return;
            }
            if (current != 0L || !generation.compareAndSet(0L, value)) {
                throw new IllegalStateException("视频定稿租约不能重复绑定不同世代");
            }
        }

        public long generation() {
            long value = generation.get();
            if (value < 1) {
                throw new IllegalStateException("视频定稿租约尚未绑定数据库世代");
            }
            return value;
        }

        /**
         * 在进入耗时阶段或提交事务前主动续租；旧 owner 不能续租并立即失败关闭。
         */
        public void renewOrThrow() {
            ensureOpen();
            final Long renewed;
            try {
                renewed = redisTemplate.execute(RENEW_SCRIPT, List.of(key),
                        ownerId, String.valueOf(ttl.toMillis()));
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
                owned = redisTemplate.execute(OWNER_SCRIPT, List.of(key), ownerId);
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
                        ownerId, String.valueOf(ttl.toMillis()));
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
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), ownerId);
            } catch (RuntimeException ignored) {
                // 释放失败由 TTL 兜底；不能用无 owner 的 DEL 误删新执行者的租约。
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
