package cn.edu.gpnu.platform.system.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 44（PG-M4 整改）：把底层 {@link CacheManager} 的每个缓存包一层 {@link EpochGuardedCache}。
 *
 * <p>刻意<b>不</b>用继承 {@code CaffeineCacheManager} + 覆写 protected 适配方法的写法，避免依赖 Spring 内部
 * 实现细节；本类只依赖 {@code CacheManager} 公开契约。
 *
 * <p>每个缓存名<b>只能有一个</b>包装实例——纪元计数器是包装实例的状态，若同名出现两个包装，逐出推进的就不是
 * 读穿装载所比对的那个计数器，守卫会静默失效。故用 {@code computeIfAbsent} 保证同名单例。
 */
public class EpochGuardedCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final Map<String, Cache> guarded = new ConcurrentHashMap<>();

    public EpochGuardedCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    @Nullable
    public Cache getCache(String name) {
        Cache existing = guarded.get(name);
        if (existing != null) {
            return existing;
        }
        Cache target = delegate.getCache(name);
        if (target == null) {
            return null;
        }
        return guarded.computeIfAbsent(name, ignored -> new EpochGuardedCache(target));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
