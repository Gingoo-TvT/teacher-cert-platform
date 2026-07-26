package cn.edu.gpnu.platform.system.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Phase 44（PG-M4 整改）：参考数据缓存的<b>事务感知</b>失效器。
 *
 * <p>审计 PG-M4：原实现在写事务<b>内</b>手工逐出（{@code DictServiceImpl}）或用 {@code @CacheEvict}
 * （{@code updateParam}）逐出。两者都在提交之前发生，于是
 * ①并发读可在逐出后把提交前的旧值重填进缓存，提交后继续供旧值直到 TTL；
 * ②逐出后本事务自身的读穿会把<b>未提交</b>值装进共享缓存，其它请求由此读到脏值，事务回滚后该值还会留存。
 *
 * <p>本失效器把失效动作<b>推迟到事务完成之后</b>执行：
 * <ul>
 *   <li>用 {@code afterCompletion} 而非 {@code afterCommit}——回滚同样必须清理，否则上述②留下的未提交值会长期驻留；</li>
 *   <li>回调在事务管理器提交/回滚收尾时触发，<b>早于</b>写方法的事务代理返回调用方，故「写接口返回后的首次读」
 *       必定不再命中旧值（这正是 PG-M4 要求的回归口径）；</li>
 *   <li>无事务上下文（如带外维护、测试直改）时立即执行，行为与原来一致。</li>
 * </ul>
 *
 * <p>「提交后才逐出」只关掉了旧值的<b>供应端</b>，并发读仍可能在提交前载入旧值、在逐出后回填；该重填由
 * {@link EpochGuardedCache} 的纪元守卫拒绝。两者<b>必须配套</b>，缺一不可。
 *
 * <p>失效动作本身失败（如 Redis 不可用）只能记 ERROR：事务已完成，不能因缓存清理失败回滚业务；此时该缓存
 * 最长陈旧到 TTL（字典 12h / 参数 30m），运维据日志手工处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceCacheInvalidator {

    private final CacheManager cacheManager;

    /**
     * 在事务完成后执行 {@code invalidation}；无事务时立即执行。
     *
     * @param description 失效目标描述，仅用于失败日志定位
     */
    public void afterCompletion(String description, Runnable invalidation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    invalidation.run();
                } catch (RuntimeException e) {
                    log.error("参考数据缓存失效失败，该缓存可能供应旧值直到 TTL: {}", description, e);
                }
            }
        });
    }

    /** 事务完成后逐出单键。 */
    public void evictAfterCompletion(String cacheName, Object key) {
        afterCompletion(cacheName + "[" + key + "]", () -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        });
    }

    /** 事务完成后清空整个缓存（键含默认值等派生成分、无法精准定位单键时使用）。 */
    public void clearAfterCompletion(String cacheName) {
        afterCompletion(cacheName + "[*]", () -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
