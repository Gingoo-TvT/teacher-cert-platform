package cn.edu.gpnu.platform.system.cache;

import org.springframework.cache.Cache;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 44（PG-M4 整改）：纪元（epoch）守卫的缓存装饰器——阻止「逐出前已载入旧值的并发读」把旧值重填回缓存。
 *
 * <p>原缺陷（审计 PG-M4）：写事务<b>未提交</b>时就逐出缓存，另一请求可从旧数据库值重新填充，提交后缓存继续
 * 返回旧值直到 TTL。仅把逐出移到提交之后<b>并不能</b>消除该窗口：读线程只要在提交前完成数据库读、在逐出<b>之后</b>
 * 执行 put，同样重填旧值。
 *
 * <p>本装饰器的不变量：<b>一次 put 只有在「该键读穿装载开始」到「写入完成」之间没有发生过任何失效动作时才生效。</b>
 * 用单调递增的 {@code epoch} 实现：
 * <ol>
 *   <li>{@link #get(Object)} 未命中时把当时 epoch 记入线程本地（Spring {@code @Cacheable} 恒为同线程
 *       get → 调用目标方法 → put）；</li>
 *   <li>{@link #evict}/{@link #clear} 等失效动作<b>先</b>推进 epoch <b>再</b>委托底层，使全部在途装载失效；</li>
 *   <li>{@link #put} 先比对快照 epoch，写入后<b>再</b>比对一次，任一不等即撤销该键——覆盖「比对通过之后才发生逐出」
 *       的交错。</li>
 * </ol>
 *
 * <p>配套时序前提由 {@link ReferenceCacheInvalidator} 提供：逐出发生在事务<b>完成之后</b>。于是「读到提交前旧值」
 * 必然满足 <em>读装载开始 &lt; 写提交 &lt; 逐出</em>，其 put 一定被本装饰器拒绝；而提交后开始的读只会装载新值。
 * epoch 按<b>缓存</b>（非单键）计数：任一键被逐出会使同缓存在途装载全部作废，方向偏保守（少缓存一次），不会供旧值。
 *
 * <p><b>已知边界（诚实记录，不属本次修复目标）</b>：
 * ①本装饰器是<b>单 JVM</b> 语义。多实例部署时，A 节点的字典/参数写不会逐出 B 节点的进程内缓存，B 仍靠 TTL 收敛；
 * 跨节点广播失效正是审计给出的长期方案（统一参考数据版本号 + 广播协议），已登记为独立开放项。
 * ②若读线程处于一个「读视图早于写提交」的长事务中（REPEATABLE READ 快照早于本次 get），其读到旧值却未跨越任何
 * 逐出，put 会被接受。该窗口由长事务自身的快照语义决定，缓存层无法判别；参考数据读发生在长事务里本身属反模式。
 * ③刻意不实现 Spring 6.2 的 {@code retrieve}（异步/响应式读穿）：本仓无异步 {@code @Cacheable}，保留默认的
 * {@code UnsupportedOperationException} 可以让将来新增异步缓存方法<b>显式失败</b>，而不是静默绕过纪元守卫。
 */
public class EpochGuardedCache implements Cache {

    /** 线程本地在途装载记录的上限：正常流程恒为 get→put 立刻成对，超限说明有未配对 get（目标方法抛异常），整体丢弃。 */
    private static final int MAX_TRACKED_LOADS = 32;

    private static final Object NULL_KEY = new Object();

    private final Cache delegate;
    private final AtomicLong epoch = new AtomicLong();
    private final ThreadLocal<Map<Object, Long>> loadEpochs = ThreadLocal.withInitial(HashMap::new);

    public EpochGuardedCache(Cache delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    @Nullable
    public ValueWrapper get(Object key) {
        ValueWrapper wrapper = delegate.get(key);
        if (wrapper == null) {
            rememberLoadEpoch(key);
        }
        return wrapper;
    }

    @Override
    @Nullable
    public <T> T get(Object key, @Nullable Class<T> type) {
        T value = delegate.get(key, type);
        if (value == null) {
            rememberLoadEpoch(key);
        }
        return value;
    }

    /**
     * 刻意不委托底层的原子装载（那会绕过纪元守卫）。本仓未使用 {@code @Cacheable(sync = true)}；此处语义与
     * sync=false 一致：不保证同键装载去重，但保证不重填陈旧值。
     */
    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }
        T value;
        try {
            value = valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
        put(key, value);
        return value;
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        Long loadEpoch = forgetLoadEpoch(key);
        long before = epoch.get();
        if (loadEpoch != null && loadEpoch.longValue() != before) {
            // 装载期间发生过失效：该值可能已陈旧（或来自尚未提交的事务），不得进入缓存。
            delegate.evictIfPresent(key);
            return;
        }
        delegate.put(key, value);
        if (epoch.get() != before) {
            // 与并发失效交错（比对通过之后才推进 epoch）：撤销本次写入。
            delegate.evictIfPresent(key);
        }
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        ValueWrapper existing = delegate.get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        epoch.incrementAndGet();
        delegate.evict(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        epoch.incrementAndGet();
        return delegate.evictIfPresent(key);
    }

    @Override
    public void clear() {
        epoch.incrementAndGet();
        delegate.clear();
    }

    @Override
    public boolean invalidate() {
        epoch.incrementAndGet();
        return delegate.invalidate();
    }

    /** 当前纪元，仅供测试断言「失效动作确实推进了纪元」。 */
    public long currentEpoch() {
        return epoch.get();
    }

    private void rememberLoadEpoch(Object key) {
        Map<Object, Long> tracked = loadEpochs.get();
        if (tracked.size() >= MAX_TRACKED_LOADS) {
            // 只可能是目标方法抛异常导致 get 未配对 put；直接清空，后续 put 因缺快照按「无在途装载」处理。
            tracked.clear();
        }
        tracked.put(key == null ? NULL_KEY : key, epoch.get());
    }

    @Nullable
    private Long forgetLoadEpoch(Object key) {
        Map<Object, Long> tracked = loadEpochs.get();
        Long loadEpoch = tracked.remove(key == null ? NULL_KEY : key);
        if (tracked.isEmpty()) {
            loadEpochs.remove();
        }
        return loadEpoch;
    }
}
