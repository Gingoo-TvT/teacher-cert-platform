package cn.edu.gpnu.platform.system.cache;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 字典 Redis 负载的进程内写窗口守卫。
 *
 * <p>Redis writers/version 负责跨节点保护；本守卫补足“当前 JVM 的 Redis 协调状态整体丢失”边界：
 * 只要本进程仍有某 typeCode 的写事务活跃，该 typeCode 的 Redis PUT 就不得执行，避免 MySQL
 * read-your-writes 的未提交值被发布到共享缓存。</p>
 *
 * <p>固定分片避免按 key 锁删除/重建产生 ABA 两把锁竞态；每个分片在同一锁内完成 writer 计数检查与整段
 * Redis PUT 动作。字典写是低频管理操作，固定分片既有明确内存上界，也不会像复用 Caffeine 全局锁那样把
 * 一个 Redis 往返串行化到所有缓存操作。</p>
 */
@Component
public class DictRedisPublishGuard {

    private static final int STRIPE_COUNT = 32;

    private final Stripe[] stripes = new Stripe[STRIPE_COUNT];

    public DictRedisPublishGuard() {
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new Stripe();
        }
    }

    /**
     * 登记一个本地 writer。返回 handle 必须保持到事务 afterCompletion 的全部清理结束后再关闭。
     */
    public WriteHandle begin(String canonicalTypeCode) {
        String identity = requireIdentity(canonicalTypeCode);
        Stripe stripe = stripe(identity);
        stripe.lock.lock();
        try {
            stripe.activeWriters.merge(identity, 1, Integer::sum);
        } finally {
            stripe.lock.unlock();
        }
        return new WriteHandle(stripe, identity);
    }

    /**
     * 仅在本 JVM 没有该 typeCode 的活跃 writer 时执行完整共享缓存发布动作。
     *
     * @return true 表示动作已执行；false 表示因本地 writer 存在而失败关闭
     */
    public boolean publishIfIdle(String canonicalTypeCode, Runnable action) {
        String identity = requireIdentity(canonicalTypeCode);
        Objects.requireNonNull(action, "action");
        Stripe stripe = stripe(identity);
        stripe.lock.lock();
        try {
            if (stripe.activeWriters.getOrDefault(identity, 0) > 0) {
                return false;
            }
            action.run();
            return true;
        } finally {
            stripe.lock.unlock();
        }
    }

    int activeWriterCount(String canonicalTypeCode) {
        String identity = requireIdentity(canonicalTypeCode);
        Stripe stripe = stripe(identity);
        stripe.lock.lock();
        try {
            return stripe.activeWriters.getOrDefault(identity, 0);
        } finally {
            stripe.lock.unlock();
        }
    }

    boolean isOperationQueued(String canonicalTypeCode, Thread thread) {
        return stripe(requireIdentity(canonicalTypeCode)).lock.hasQueuedThread(thread);
    }

    private Stripe stripe(String identity) {
        return stripes[Math.floorMod(identity.hashCode(), stripes.length)];
    }

    private static String requireIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("canonicalTypeCode 不能为空");
        }
        return identity;
    }

    public static final class WriteHandle implements AutoCloseable {

        private final Stripe stripe;
        private final String identity;
        private final AtomicBoolean closed = new AtomicBoolean();

        private WriteHandle(Stripe stripe, String identity) {
            this.stripe = stripe;
            this.identity = identity;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            stripe.lock.lock();
            try {
                stripe.activeWriters.computeIfPresent(identity,
                        (ignored, count) -> count <= 1 ? null : count - 1);
            } finally {
                stripe.lock.unlock();
            }
        }
    }

    private static final class Stripe {

        private final ReentrantLock lock = new ReentrantLock();
        private final Map<String, Integer> activeWriters = new HashMap<>();
    }
}
