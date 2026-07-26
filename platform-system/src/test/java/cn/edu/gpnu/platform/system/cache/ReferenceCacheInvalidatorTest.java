package cn.edu.gpnu.platform.system.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Phase 44（PG-M4 整改）：失效时序单测——证明逐出确实发生在事务<b>完成之后</b>，且回滚同样清理。
 *
 * <p>审计 PG-M4 的核心是「提交前逐出」。因此这里的关键断言是<b>负向</b>的：注册后、完成前，缓存必须<b>仍持有</b>旧条目；
 * 只有 {@code afterCompletion} 被触发后才清除。若有人把实现改回事务内立即逐出，负向断言立刻变红。
 */
class ReferenceCacheInvalidatorTest {

    private static final String CACHE = "sysParam";

    private final CacheManager cacheManager =
            new EpochGuardedCacheManager(new ConcurrentMapCacheManager(CACHE, "dictLabels"));
    private final ReferenceCacheInvalidator invalidator = new ReferenceCacheInvalidator(cacheManager);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withoutTransactionInvalidatesImmediately() {
        Cache cache = cacheManager.getCache(CACHE);
        cache.put("k", "OLD");

        invalidator.clearAfterCompletion(CACHE);

        assertThat(cache.get("k")).as("无事务上下文时应立即清理").isNull();
    }

    @Test
    void withinTransactionDefersClearUntilCompletion() {
        Cache cache = cacheManager.getCache(CACHE);
        cache.put("k", "OLD");
        TransactionSynchronizationManager.initSynchronization();

        invalidator.clearAfterCompletion(CACHE);

        assertThat(cache.get("k"))
                .as("提交前不得逐出：否则并发读会把提交前旧值回填（PG-M4）")
                .isNotNull();

        triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(cache.get("k")).as("事务完成后必须已清理").isNull();
    }

    @Test
    void withinTransactionDefersEvictUntilCompletion() {
        Cache cache = cacheManager.getCache("dictLabels");
        cache.put("p44", "OLD");
        TransactionSynchronizationManager.initSynchronization();

        invalidator.evictAfterCompletion("dictLabels", "p44");

        assertThat(cache.get("p44")).isNotNull();

        triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(cache.get("p44")).isNull();
    }

    @Test
    void rollbackAlsoInvalidates() {
        // 回滚必须同样清理：事务失败前若已有读穿把未提交值缓存，afterCommit 语义会把脏值留到 TTL。
        Cache cache = cacheManager.getCache(CACHE);
        cache.put("k", "UNCOMMITTED");
        TransactionSynchronizationManager.initSynchronization();

        invalidator.clearAfterCompletion(CACHE);
        triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(cache.get("k")).as("回滚后不得留下未提交值").isNull();
    }

    @Test
    void invalidationFailureDoesNotEscapeTheCallback() {
        // 事务已完成，缓存清理失败不能再影响业务；只能记 ERROR 并让缓存陈旧到 TTL。
        TransactionSynchronizationManager.initSynchronization();
        invalidator.afterCompletion("boom", () -> {
            throw new IllegalStateException("redis down");
        });

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        assertThatCode(() -> synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED))
                .doesNotThrowAnyException();
    }

    @Test
    void unknownCacheNameIsNoop() {
        assertThatCode(() -> invalidator.clearAfterCompletion("nonexistent")).doesNotThrowAnyException();
        assertThatCode(() -> invalidator.evictAfterCompletion("nonexistent", "k")).doesNotThrowAnyException();
    }

    @Test
    void guardedCacheManagerReturnsOneWrapperPerName() {
        // 纪元计数器是包装实例的状态；同名两个包装＝守卫静默失效。
        assertThat(cacheManager.getCache(CACHE)).isSameAs(cacheManager.getCache(CACHE));
        assertThat(cacheManager.getCache(CACHE)).isInstanceOf(EpochGuardedCache.class);
        assertThat(cacheManager.getCacheNames()).contains(CACHE, "dictLabels");
    }

    private void triggerAfterCompletion(int status) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
    }
}
