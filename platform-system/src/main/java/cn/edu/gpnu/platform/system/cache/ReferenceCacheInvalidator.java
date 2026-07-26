package cn.edu.gpnu.platform.system.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 44（PG-M4 整改）：参考数据缓存的<b>事务感知</b>失效器。
 *
 * <p>审计 PG-M4：原实现在写事务<b>内</b>手工逐出（{@code DictServiceImpl}）或用 {@code @CacheEvict}
 * （{@code updateParam}）逐出。两者都在提交之前发生，于是
 * ①并发读可在逐出后把提交前的旧值重填进缓存，提交后继续供旧值直到 TTL；
 * ②逐出后本事务自身的读穿会把<b>未提交</b>值装进共享缓存，其它请求由此读到脏值，事务回滚后该值还会留存。
 *
 * <p>本失效器把一次写的缓存失效拆成<b>三段</b>：
 * <ol>
 *   <li><b>登记即进入写窗口</b>（{@code begin}，同步执行）：受影响的键立刻停止对外供应缓存值、也不再接受回填。
 *       这一段关掉了「提交完成 → 逐出执行」之间仍能读到旧值的窗口，也杜绝本事务未提交值被读穿发布出去；</li>
 *   <li><b>事务完成后逐条执行失效步骤</b>（{@code steps}，用 {@code afterCompletion} 而非 {@code afterCommit}——
 *       回滚同样必须清理，否则窗口期内装入的任何值都会留到 TTL）。<b>每个步骤彼此隔离</b>：任一步骤抛异常只记 ERROR，
 *       不影响其余步骤，避免「Redis 故障连带本地缓存也不失效」这类故障耦合；</li>
 *   <li><b>无条件离开写窗口</b>（{@code end}，放在 {@code finally}）：即使全部步骤都失败也要解除，否则该键将被永久旁路。</li>
 * </ol>
 *
 * <p>无事务上下文（带外维护、测试直改）时三段就地顺序执行，语义与原来一致。
 *
 * <p>失效步骤失败只能记 ERROR：事务已完成，不能因缓存清理失败回滚业务；此时该缓存最长陈旧到 TTL
 * （字典 12h / 参数 30m），运维据日志处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceCacheInvalidator {

    private final CacheManager cacheManager;

    /** 一个具名的失效步骤；名字只用于失败日志定位。 */
    public record InvalidationStep(String name, Runnable action) {
    }

    public static InvalidationStep step(String name, Runnable action) {
        return new InvalidationStep(name, action);
    }

    /**
     * 登记一次写窗口失效。
     *
     * @param description 失效目标描述，仅用于失败日志定位
     * @param begin       同步执行：进入写窗口（可为 no-op）
     * @param steps       事务完成后执行的失效步骤，彼此隔离
     * @param end         无条件执行：离开写窗口（可为 no-op）
     */
    public void invalidateAfterCompletion(String description, Runnable begin,
                                          List<InvalidationStep> steps, Runnable end) {
        begin.run();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                runIsolated(description, steps);
            } finally {
                end.run();
            }
            return;
        }
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        runIsolated(description, steps);
                    } finally {
                        end.run();
                    }
                }
            });
        } catch (RuntimeException e) {
            // 登记失败就没有人负责解除写窗口，必须立刻解除，否则该键被永久旁路。
            end.run();
            throw e;
        }
    }

    /** 事务完成后逐出单键；窗口期内该键不供应缓存值也不接受回填。 */
    public void evictAfterCompletion(String cacheName, Object key) {
        guardedInvalidation(cacheName, key);
    }

    /** 事务完成后清空整个缓存（键含默认值等派生成分、无法精准定位单键时使用）。 */
    public void clearAfterCompletion(String cacheName) {
        guardedInvalidation(cacheName, null);
    }

    private void guardedInvalidation(String cacheName, @Nullable Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        EpochGuardedCache guarded = cache instanceof EpochGuardedCache epochGuarded ? epochGuarded : null;
        String description = cacheName + "[" + (key == null ? "*" : key) + "]";
        invalidateAfterCompletion(description,
                () -> {
                    if (guarded != null) {
                        guarded.beginPendingInvalidation(key);
                    }
                },
                List.of(step(description, () -> {
                    if (key == null) {
                        cache.clear();
                    } else {
                        cache.evict(key);
                    }
                })),
                () -> {
                    if (guarded != null) {
                        guarded.endPendingInvalidation(key);
                    }
                });
    }

    private void runIsolated(String description, List<InvalidationStep> steps) {
        List<String> failed = new ArrayList<>(0);
        for (InvalidationStep invalidationStep : steps) {
            try {
                invalidationStep.action().run();
            } catch (RuntimeException e) {
                failed.add(invalidationStep.name());
                log.error("参考数据缓存失效步骤失败，该步骤对应的缓存可能供应旧值直到 TTL: target={}, step={}",
                        description, invalidationStep.name(), e);
            }
        }
        if (!failed.isEmpty()) {
            log.error("参考数据缓存失效未完全生效: target={}, failedSteps={}", description, failed);
        }
    }
}
