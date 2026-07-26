package cn.edu.gpnu.platform.system.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Phase 44（PG-M4 整改）：失效时序单测——证明「写窗口」与「完成后失效」两段都成立。
 *
 * <p>关键断言有两组：①<b>窗口内</b>缓存必须停止供应该键、且不接受回填（若有人去掉写窗口，退回上一轮被复核指出的
 * 「提交完成到逐出执行之间仍能读到旧值」，这些断言立刻变红）；②<b>完成后</b>（提交与回滚都算）条目被清理、窗口被解除，
 * 缓存恢复可用。另有步骤隔离与解除保证：任一失效步骤抛异常都不得影响其余步骤，也不得把缓存永久旁路。
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
        EpochGuardedCache cache = (EpochGuardedCache) cacheManager.getCache(CACHE);
        cache.get("k");
        cache.put("k", "OLD");
        TransactionSynchronizationManager.initSynchronization();

        invalidator.clearAfterCompletion(CACHE);

        assertThat(cache.isPendingInvalidation("k"))
                .as("登记即进入写窗口：窗口内不得供应旧值，也不得回填")
                .isTrue();
        assertThat(cache.get("k")).as("写窗口内必须停止供应").isNull();

        triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(cache.isPendingInvalidation("k")).as("完成后必须解除写窗口").isFalse();
        assertThat(cache.get("k")).as("事务完成后条目必须已清理").isNull();
        cache.put("k", "NEW");
        assertThat(cache.get("k")).as("窗口解除后缓存恢复可用").isNotNull();
    }

    @Test
    void withinTransactionDefersEvictUntilCompletion() {
        EpochGuardedCache cache = (EpochGuardedCache) cacheManager.getCache("dictLabels");
        cache.get("p44");
        cache.put("p44", "OLD");
        TransactionSynchronizationManager.initSynchronization();

        invalidator.evictAfterCompletion("dictLabels", "p44");

        assertThat(cache.get("p44")).isNull();
        assertThat(cache.isPendingInvalidation("p44")).isTrue();

        triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(cache.get("p44")).isNull();
        assertThat(cache.isPendingInvalidation("p44")).isFalse();
    }

    @Test
    void windowIsReleasedEvenWhenEveryStepFails() {
        // 步骤失败不能把缓存永久旁路——解除必须在 finally 里。
        EpochGuardedCache cache = (EpochGuardedCache) cacheManager.getCache(CACHE);
        TransactionSynchronizationManager.initSynchronization();

        invalidator.invalidateAfterCompletion("boom",
                () -> cache.beginPendingInvalidation("k"),
                List.of(ReferenceCacheInvalidator.step("always-fails", () -> {
                    throw new IllegalStateException("redis down");
                })),
                () -> cache.endPendingInvalidation("k"));

        assertThat(cache.isPendingInvalidation("k")).isTrue();
        triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(cache.isPendingInvalidation("k")).as("步骤全失败也必须解除写窗口").isFalse();
    }

    @Test
    void failingStepDoesNotBlockTheRemainingSteps() {
        // 复核 Medium：Redis 故障不得连带本地逐出也不执行。
        List<String> executed = new ArrayList<>();
        TransactionSynchronizationManager.initSynchronization();

        invalidator.invalidateAfterCompletion("mixed", () -> { },
                List.of(
                        ReferenceCacheInvalidator.step("local", () -> executed.add("local")),
                        ReferenceCacheInvalidator.step("redis-version", () -> {
                            throw new IllegalStateException("redis down");
                        }),
                        ReferenceCacheInvalidator.step("redis-payload", () -> executed.add("redis-payload"))),
                () -> executed.add("end"));

        triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(executed)
                .as("失败步骤之后的步骤必须照常执行，解除动作必须最后执行")
                .containsExactly("local", "redis-payload", "end");
    }

    @Test
    void registrationFailureReleasesTheWindowImmediately() {
        // 没有活动同步时 registerSynchronization 会抛异常；此时若不解除，该键将被永久旁路。
        EpochGuardedCache cache = (EpochGuardedCache) cacheManager.getCache(CACHE);
        List<String> executed = new ArrayList<>();

        invalidator.invalidateAfterCompletion("no-tx",
                () -> cache.beginPendingInvalidation("k"),
                List.of(ReferenceCacheInvalidator.step("evict", () -> executed.add("evict"))),
                () -> cache.endPendingInvalidation("k"));

        assertThat(executed).as("无事务时应就地执行").containsExactly("evict");
        assertThat(cache.isPendingInvalidation("k")).isFalse();
    }

    @Test
    void rollbackAlsoInvalidatesAndReleasesTheWindow() {
        // 回滚必须同样清理：事务失败前若已有读穿把未提交值缓存，afterCommit 语义会把脏值留到 TTL。
        EpochGuardedCache cache = (EpochGuardedCache) cacheManager.getCache(CACHE);
        cache.get("k");
        cache.put("k", "UNCOMMITTED");
        TransactionSynchronizationManager.initSynchronization();

        invalidator.clearAfterCompletion(CACHE);
        triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(cache.get("k")).as("回滚后不得留下未提交值").isNull();
        assertThat(cache.isPendingInvalidation("k")).as("回滚后同样必须解除写窗口").isFalse();
    }

    @Test
    void invalidationFailureDoesNotEscapeTheCallback() {
        // 事务已完成，缓存清理失败不能再影响业务；只能记 ERROR 并让缓存陈旧到 TTL。
        TransactionSynchronizationManager.initSynchronization();
        invalidator.invalidateAfterCompletion("boom", () -> { },
                List.of(ReferenceCacheInvalidator.step("boom", () -> {
                    throw new IllegalStateException("redis down");
                })),
                () -> { });

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
