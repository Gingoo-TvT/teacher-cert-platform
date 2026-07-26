package cn.edu.gpnu.platform.system.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    void staleValueNeverReachesTheUnderlyingCacheSoNoReaderCanObserveIt() throws Exception {
        // 上一轮复核 Medium：旧实现先 delegate.put(STALE) 再补删，补删之前任何读者都可能命中陈旧值。
        // 现在「校验 + 发布」在同一把锁内串行，校验不过就根本不写——底层缓存<b>从未</b>出现过该值，
        // 因此不存在「能不能被读到」的时间窗（比事后断言最终状态强得多）。
        RecordingCache delegate = new RecordingCache();
        EpochGuardedCache cache = new EpochGuardedCache(delegate);

        ExecutorService staleLoader = Executors.newSingleThreadExecutor();
        try {
            run(staleLoader, () -> cache.get("T"));   // 旧 loader 在自己的线程上开始读穿
            cache.evict("T");                         // 写事务完成后逐出
            run(staleLoader, () -> {
                cache.put("T", "STALE");
                return null;
            });
        } finally {
            staleLoader.shutdownNow();
        }

        assertThat(delegate.putValues())
                .as("陈旧值必须从未写入底层缓存")
                .doesNotContain("STALE");
        assertThat(cache.get("T")).isNull();
    }

    @Test
    void rejectedStaleLoaderDoesNotDeleteFreshValuePublishedByAnotherThread() throws Exception {
        // 旧实现的补删会连带删掉另一线程刚发布的<b>新</b>值；现在拒绝＝不写，不再触碰既有条目。
        RecordingCache delegate = new RecordingCache();
        EpochGuardedCache cache = new EpochGuardedCache(delegate);

        ExecutorService staleLoader = Executors.newSingleThreadExecutor();
        ExecutorService freshLoader = Executors.newSingleThreadExecutor();
        try {
            run(staleLoader, () -> cache.get("T"));       // 旧 loader 取快照
            cache.evict("T");                             // 写完成、纪元推进
            run(freshLoader, () -> {                      // 新 loader 合法发布
                cache.get("T");
                cache.put("T", "FRESH");
                return null;
            });
            run(staleLoader, () -> {                      // 旧 loader 迟到发布
                cache.put("T", "STALE");
                return null;
            });
        } finally {
            staleLoader.shutdownNow();
            freshLoader.shutdownNow();
        }

        assertThat(cache.get("T")).as("新值必须保留").isNotNull();
        assertThat(cache.get("T").get()).isEqualTo("FRESH");
        assertThat(delegate.putValues()).doesNotContain("STALE");
    }

    @Test
    void invalidationCannotInterleaveBetweenTheCheckAndThePublish() throws Exception {
        // 直接验证串行化本身：发布线程停在 delegate.put 之内时，另一线程的逐出必须被挡在锁外。
        BlockingPutCache delegate = new BlockingPutCache();
        EpochGuardedCache cache = new EpochGuardedCache(delegate);
        ExecutorService publisher = Executors.newSingleThreadExecutor();
        ExecutorService invalidator = Executors.newSingleThreadExecutor();
        try {
            cache.get("T");
            Future<?> publishing = publisher.submit(() -> {
                cache.get("T");
                cache.put("T", "FRESH");
            });
            assertThat(delegate.awaitInsidePut(5, TimeUnit.SECONDS))
                    .as("发布线程应已进入 delegate.put")
                    .isTrue();

            CountDownLatch contenderStarted = new CountDownLatch(1);
            CountDownLatch contenderCompleted = new CountDownLatch(1);
            AtomicReference<Thread> contenderThread = new AtomicReference<>();
            Future<?> evicting = invalidator.submit(() -> {
                contenderThread.set(Thread.currentThread());
                contenderStarted.countDown();
                try {
                    cache.evict("T");
                } finally {
                    contenderCompleted.countDown();
                }
            });
            assertThat(contenderStarted.await(5, TimeUnit.SECONDS))
                    .as("失效线程必须已被调度并开始尝试目标操作")
                    .isTrue();
            assertThat(awaitPublishLockQueue(cache, contenderThread.get(), 5, TimeUnit.SECONDS))
                    .as("失效线程必须已真实排队等待 publishLock，不能用调用前信号冒充锁竞争")
                    .isTrue();
            assertThat(contenderCompleted.await(300, TimeUnit.MILLISECONDS))
                    .as("已启动的失效在发布结束前必须被锁挡住（否则校验与发布之间可插入失效）")
                    .isFalse();

            delegate.releasePut();
            publishing.get(5, TimeUnit.SECONDS);
            assertThat(contenderCompleted.await(5, TimeUnit.SECONDS))
                    .as("发布放行后失效必须完成")
                    .isTrue();
            evicting.get(5, TimeUnit.SECONDS);
        } finally {
            delegate.releasePut();
            publisher.shutdownNow();
            invalidator.shutdownNow();
        }

        assertThat(cache.get("T")).as("失效发生在发布之后，最终应为空").isNull();
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

    // ---------------------------------------------------------------- 写窗口（复核 Medium：陈旧值短暂公开窗口）

    @Test
    void pendingWindowStopsServingAlreadyCachedValue() {
        // 写发生到事务完成之间，缓存里的旧值一定已被本次写作废，不得再对外供应（哪怕逐出还没执行）。
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));
        cache.get("T");
        cache.put("T", "V1");
        assertThat(cache.get("T")).isNotNull();

        cache.beginPendingInvalidation("T");

        assertThat(cache.get("T")).as("写窗口内必须停止供应").isNull();
        assertThat(cache.isPendingInvalidation("T")).isTrue();
    }

    @Test
    void pendingWindowRejectsRefillAndCompletionEvictionClearsTheEntry() {
        RecordingCache delegate = new RecordingCache();
        EpochGuardedCache cache = new EpochGuardedCache(delegate);
        cache.get("T");
        cache.put("T", "V1");

        cache.beginPendingInvalidation("T");
        assertThat(cache.get("T")).as("窗口内不供应").isNull();
        cache.put("T", "V1");   // 窗口内的回填尝试（无论旧值还是未提交新值都不许进）

        assertThat(delegate.putValues())
                .as("窗口内的回填不得写入底层；且刻意不顺手删除既有条目，以免误删他人刚发布的新值")
                .containsExactly("V1");

        cache.evict("T");       // 事务完成后的逐出步骤
        cache.endPendingInvalidation("T");
        assertThat(cache.get("T")).as("完成后条目必须已清理").isNull();
    }

    @Test
    void cacheIsUsableAgainAfterWindowCloses() {
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));
        cache.beginPendingInvalidation("T");
        cache.endPendingInvalidation("T");

        assertThat(cache.get("T")).isNull();
        cache.put("T", "V2");

        assertThat(cache.get("T")).as("窗口结束后必须恢复缓存能力，否则等于把缓存禁用了").isNotNull();
        assertThat(cache.get("T").get()).isEqualTo("V2");
    }

    @Test
    void pendingWindowsAreReentrantAndCountedPerKey() {
        // updateItem 会对 oldTypeCode/newTypeCode 各登记一次；并发事务也可能同时登记同一键。
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));
        cache.beginPendingInvalidation("T");
        cache.beginPendingInvalidation("T");
        cache.endPendingInvalidation("T");

        assertThat(cache.isPendingInvalidation("T")).as("仍有一层窗口未解除").isTrue();

        cache.endPendingInvalidation("T");
        assertThat(cache.isPendingInvalidation("T")).isFalse();
        // 多余的解除不得把计数压成负数（否则下一次窗口会被提前失效）
        cache.endPendingInvalidation("T");
        cache.beginPendingInvalidation("T");
        assertThat(cache.isPendingInvalidation("T")).isTrue();
    }

    @Test
    void pendingWindowIsScopedToItsKey() {
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("dictLabels"));
        cache.get("OTHER");
        cache.put("OTHER", "V1");

        cache.beginPendingInvalidation("T");

        assertThat(cache.get("OTHER")).as("不相关的键不应被写窗口波及").isNotNull();
    }

    @Test
    void wholeCacheWindowSuppressesEveryKey() {
        // 参数缓存的键含默认值成分，生产按 allEntries 清空，故窗口也必须是整缓存维度。
        EpochGuardedCache cache = new EpochGuardedCache(new ConcurrentMapCache("sysParam"));
        cache.get("getInt:a:0");
        cache.put("getInt:a:0", 1);

        cache.beginPendingInvalidation(null);

        assertThat(cache.get("getInt:a:0")).isNull();
        assertThat(cache.get("getInt:b:0")).isNull();
        cache.put("getInt:b:0", 2);
        cache.endPendingInvalidation(null);
        assertThat(cache.get("getInt:b:0")).as("整缓存窗口内的回填同样必须被拒绝").isNull();
    }

    @Test
    void windowCannotOpenBetweenTheCheckAndThePublish() throws Exception {
        // 与失效同理：写窗口的开启也必须被 publishLock 挡在发布之外。
        BlockingPutCache delegate = new BlockingPutCache();
        EpochGuardedCache cache = new EpochGuardedCache(delegate);
        ExecutorService publisher = Executors.newSingleThreadExecutor();
        ExecutorService writer = Executors.newSingleThreadExecutor();
        try {
            Future<?> publishing = publisher.submit(() -> {
                cache.get("T");
                cache.put("T", "FRESH");
            });
            assertThat(delegate.awaitInsidePut(5, TimeUnit.SECONDS)).isTrue();

            CountDownLatch contenderStarted = new CountDownLatch(1);
            CountDownLatch contenderCompleted = new CountDownLatch(1);
            AtomicReference<Thread> contenderThread = new AtomicReference<>();
            Future<?> opening = writer.submit(() -> {
                contenderThread.set(Thread.currentThread());
                contenderStarted.countDown();
                try {
                    cache.beginPendingInvalidation("T");
                } finally {
                    contenderCompleted.countDown();
                }
            });
            assertThat(contenderStarted.await(5, TimeUnit.SECONDS))
                    .as("写线程必须已被调度并开始尝试开启窗口")
                    .isTrue();
            assertThat(awaitPublishLockQueue(cache, contenderThread.get(), 5, TimeUnit.SECONDS))
                    .as("写线程必须已真实排队等待 publishLock，不能用调用前信号冒充锁竞争")
                    .isTrue();
            assertThat(contenderCompleted.await(300, TimeUnit.MILLISECONDS))
                    .as("已启动的写窗口操作在发布结束前必须被锁挡住")
                    .isFalse();

            delegate.releasePut();
            publishing.get(5, TimeUnit.SECONDS);
            assertThat(contenderCompleted.await(5, TimeUnit.SECONDS))
                    .as("发布放行后写窗口必须成功开启")
                    .isTrue();
            opening.get(5, TimeUnit.SECONDS);
        } finally {
            delegate.releasePut();
            publisher.shutdownNow();
            writer.shutdownNow();
        }

        assertThat(cache.isPendingInvalidation("T")).isTrue();
        assertThat(cache.get("T")).as("窗口已开启，即便条目还在也不得供应").isNull();
    }

    // ---------------------------------------------------------------- helpers

    /** 在指定线程上执行一次缓存操作：{@code loadEpochs} 是线程本地的，模拟不同请求必须换线程。 */
    private static <T> T run(ExecutorService executor, Callable<T> task) throws Exception {
        return executor.submit(task).get(5, TimeUnit.SECONDS);
    }

    private static boolean awaitPublishLockQueue(EpochGuardedCache cache, Thread contender,
                                                 long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (cache.isPublishOperationQueued(contender)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(5);
        }
        return cache.isPublishOperationQueued(contender);
    }

    /** 记录底层实际收到的 put，用于断言「陈旧值从未写入」。 */
    private static final class RecordingCache extends ConcurrentMapCache {

        private final List<Object> putValues = new CopyOnWriteArrayList<>();

        private RecordingCache() {
            super("dictLabels");
        }

        @Override
        public void put(Object key, Object value) {
            putValues.add(value);
            super.put(key, value);
        }

        List<Object> putValues() {
            return List.copyOf(putValues);
        }
    }

    /** 在底层 put 内部阻塞，用于确定性观察「发布进行中」时其它操作是否被串行化挡住。 */
    private static final class BlockingPutCache extends ConcurrentMapCache {

        private final CountDownLatch insidePut = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingPutCache() {
            super("dictLabels");
        }

        @Override
        public void put(Object key, Object value) {
            insidePut.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("put 未在 10s 内被放行");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            super.put(key, value);
        }

        boolean awaitInsidePut(long timeout, TimeUnit unit) throws InterruptedException {
            return insidePut.await(timeout, unit);
        }

        void releasePut() {
            release.countDown();
        }
    }
}
