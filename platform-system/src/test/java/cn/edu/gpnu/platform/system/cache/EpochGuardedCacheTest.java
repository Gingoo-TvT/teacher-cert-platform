package cn.edu.gpnu.platform.system.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 44（PG-M4 整改）：纪元守卫的<b>反例</b>单测。
 *
 * <p>审计 PG-M4 的失效模式是「并发读在逐出前从数据库载入旧值 → 逐出发生 → 读把旧值回填 → 缓存长期供旧值」。
 * 本类逐一构造该交错的各种形态，断言回填<b>必须</b>被拒绝；若守卫被摘除，这些用例会立刻变红。
 */
class EpochGuardedCacheTest {

    @Test
    void missThenPutIsCachedWhenNoEvictionInterleaves() {
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));

        assertThat(cache.get("T")).as("首次读应未命中").isNull();
        cache.put("T", "V1");

        assertThat(cache.get("T")).isNotNull();
        assertThat(cache.get("T").get()).isEqualTo("V1");
    }

    @Test
    void putIsRejectedWhenSameKeyEvictedDuringLoad() {
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));

        // 读穿开始（读线程此刻从数据库读到的是写事务提交前的旧值）
        assertThat(cache.get("T")).isNull();
        long epochBefore = cache.currentEpoch();
        // 写事务完成后逐出
        cache.evict("T");
        assertThat(cache.currentEpoch()).as("逐出必须推进纪元").isGreaterThan(epochBefore);
        // 读线程回填旧值
        cache.put("T", "STALE");

        assertThat(cache.get("T")).as("装载期间被逐出，旧值不得回填").isNull();
    }

    @Test
    void putIsRejectedWhenAnotherKeyEvictedDuringLoad() {
        // 纪元按缓存计数：任一键逐出即作废同缓存全部在途装载。方向保守（少缓存一次），绝不供旧值。
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));

        assertThat(cache.get("T")).isNull();
        cache.evict("OTHER");
        cache.put("T", "STALE");

        assertThat(cache.get("T")).isNull();
    }

    @Test
    void putIsRejectedWhenCacheClearedDuringLoad() {
        // 参数缓存的键含默认值成分，生产按 allEntries 清空；清空同样必须作废在途装载。
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("sysParam"));

        assertThat(cache.get("getInt:video.minDuration:0")).isNull();
        cache.clear();
        cache.put("getInt:video.minDuration:0", 180);

        assertThat(cache.get("getInt:video.minDuration:0")).isNull();
    }

    @Test
    void putIsUndoneWhenEvictionInterleavesAfterTheEpochCheck() {
        // 覆盖 check-then-act 交错：比对通过、写入完成之后才发生逐出。写后二次比对必须撤销该键。
        EvictDuringPutCache delegate = new EvictDuringPutCache();
        EpochGuardedCache cache = new EpochGuardedCache(delegate);
        delegate.onPut = () -> cache.evict("T");

        assertThat(cache.get("T")).isNull();
        cache.put("T", "STALE");

        assertThat(cache.get("T")).as("写入后发生的逐出必须撤销本次写入").isNull();
    }

    @Test
    void valueLoaderPathReturnsLoadedValueButDoesNotCacheStaleValue() {
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));

        String loaded = cache.get("T", () -> {
            cache.evict("T"); // 装载过程中发生逐出
            return "STALE";
        });

        assertThat(loaded).as("调用方仍应拿到自己装载的值").isEqualTo("STALE");
        assertThat(cache.get("T")).as("但不得进入缓存").isNull();
    }

    @Test
    void cachedNullValueIsAHitNotALoad() {
        // sysParam 允许缓存 null（参数缺失也缓存，避免重复空查）；已缓存的 null 必须按命中处理。
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("sysParam", true));

        assertThat(cache.get("missing")).isNull();
        cache.put("missing", null);

        Cache.ValueWrapper wrapper = cache.get("missing");
        assertThat(wrapper).as("缓存的 null 应命中").isNotNull();
        assertThat(wrapper.get()).isNull();
    }

    @Test
    void evictIfPresentAndInvalidateAlsoAdvanceEpoch() {
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));

        assertThat(cache.get("A")).isNull();
        cache.evictIfPresent("A");
        cache.put("A", "STALE");
        assertThat(cache.get("A")).isNull();

        assertThat(cache.get("B")).isNull();
        cache.invalidate();
        cache.put("B", "STALE");
        assertThat(cache.get("B")).isNull();
    }

    @Test
    void asyncRetrieveIsDeliberatelyUnsupported() {
        // 本仓无异步 @Cacheable；保留默认实现使将来新增异步缓存方法显式失败，而不是静默绕过守卫。
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));

        assertThatThrownBy(() -> cache.retrieve("T")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nameAndNativeCacheAreDelegated() {
        ConcurrentMapCache delegate = new ConcurrentMapCache("dictLabels");
        EpochGuardedCache cache = new EpochGuardedCache(delegate);

        assertThat(cache.getName()).isEqualTo("dictLabels");
        assertThat(cache.getNativeCache()).isSameAs(delegate.getNativeCache());
    }

    /** 在底层 put 完成后立即触发一次逐出，用于确定性构造「比对通过之后才逐出」的交错。 */
    private static final class EvictDuringPutCache extends ConcurrentMapCache {

        private Runnable onPut;

        private EvictDuringPutCache() {
            super("dictLabels");
        }

        @Override
        public void put(Object key, Object value) {
            super.put(key, value);
            Runnable hook = this.onPut;
            if (hook != null) {
                this.onPut = null;
                hook.run();
            }
        }
    }
}
