package cn.edu.gpnu.platform.system.cache;

import org.springframework.cache.Cache;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 44（PG-M4 整改）：纪元（epoch）守卫的缓存装饰器——阻止「逐出前已载入旧值的并发读」把旧值重填回缓存。
 *
 * <p>原缺陷（审计 PG-M4）：写事务<b>未提交</b>时就逐出缓存，另一请求可从旧数据库值重新填充，提交后缓存继续
 * 返回旧值直到 TTL。仅把逐出移到提交之后<b>并不能</b>消除该窗口：读线程只要在提交前完成数据库读、在逐出<b>之后</b>
 * 执行 put，同样重填旧值。
 *
 * <p>本装饰器的两条不变量：
 * <ol>
 *   <li><b>写窗口内不供应、不接受</b>：从写发生（失效登记）到事务完成之间，该键（或整缓存）处于 <em>pending</em>，
 *       {@link #get} 一律按未命中返回、{@link #put} 一律拒绝。这关掉了「提交完成到逐出执行之间仍对外供应旧值」的窗口，
 *       也顺带杜绝写事务自身的读穿把<b>未提交</b>值发布给其它请求；</li>
 *   <li><b>陈旧值从不被写入</b>：{@link #get} 未命中时把当时 epoch 记入线程本地（Spring {@code @Cacheable}
 *       恒为同线程 get → 调用目标方法 → put）；{@link #put} 与全部失效动作（含 {@code beginPendingInvalidation}）
 *       在同一把 {@code publishLock} 下串行，因此「校验 epoch/pending」与「写入底层」之间<b>不可能</b>插入失效，
 *       校验不过就<b>直接不写</b>。</li>
 * </ol>
 * 两条缺一不可：不变量 1 覆盖「写窗口期间开始的读」，不变量 2 覆盖「写窗口开始<b>之前</b>就已开始装载的读」。
 *
 * <p><b>为什么不是「先写后补删」</b>（上一轮复核 Medium）：先 {@code delegate.put} 再二次比对补删，只保证最终状态，
 * 不保证陈旧值从未被读到——补删之前任何读者都可能命中它，而参考数据参与后端硬校验，一次旧值命中就可能让请求按已失效
 * 规则通过，事后删除无法撤销已经返回的业务结果。补删还会误删另一线程刚发布的<b>新</b>值。改为「串行校验后再写」两者一并消除。
 * 读路径 {@link #get} 刻意<b>不</b>加锁：底层缓存自身线程安全，而任何被写入的值都已在锁内校验过，读者不可能看到未经校验的值。
 *
 * <p>配套时序由 {@link ReferenceCacheInvalidator} 提供：pending 在失效登记时进入，真正的逐出与 pending 解除都发生在事务
 * <b>完成之后</b>（提交与回滚都做）。pending 解除放在 {@code finally}，逐出步骤失败也不会把缓存永久旁路；反过来，若解除
 * 因进程崩溃而丢失，效果是该键永久绕过缓存（只损失性能，不会供旧值），方向是安全的。
 *
 * <p>epoch 与 pending 都按<b>缓存</b>（而非单键）额外提供整缓存维度：参数缓存的键含默认值成分、生产按 allEntries 清空，
 * 故 {@code pendingAll} 抑制该缓存全部键。方向偏保守（多几次回源），不会供旧值。
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
    /** 串行化「校验 + 发布」与全部失效动作；读路径不参与，见类注释。 */
    private final ReentrantLock publishLock = new ReentrantLock();
    private final ThreadLocal<Map<Object, Long>> loadEpochs = ThreadLocal.withInitial(HashMap::new);
    /** 处于写窗口的键 → 重入计数（同一事务可多次登记，并发事务也可能同时登记同一键）。 */
    private final Map<Object, Integer> pendingKeys = new ConcurrentHashMap<>();
    /** 处于写窗口的整缓存失效计数（参数缓存按 allEntries 清空时使用）。 */
    private final AtomicInteger pendingAll = new AtomicInteger();

    public EpochGuardedCache(Cache delegate) {
        this.delegate = delegate;
    }

    /**
     * 进入写窗口：该键（{@code key} 为 null 表示整缓存）自此既不供应缓存值也不接受回填，直到配对的
     * {@link #endPendingInvalidation} 被调用。必须与 {@code endPendingInvalidation} 严格配对。
     * 与 {@link #put} 共用 {@code publishLock}，杜绝「put 已通过 pending 校验、窗口随后才开启」的交错。
     */
    public void beginPendingInvalidation(@Nullable Object key) {
        publishLock.lock();
        try {
            if (key == null) {
                pendingAll.incrementAndGet();
                return;
            }
            pendingKeys.merge(normalizeKey(key), 1, Integer::sum);
        } finally {
            publishLock.unlock();
        }
    }

    /** 离开写窗口。多于 begin 的调用是安全的（计数不会降到负数）。 */
    public void endPendingInvalidation(@Nullable Object key) {
        publishLock.lock();
        try {
            if (key == null) {
                pendingAll.updateAndGet(current -> current > 0 ? current - 1 : 0);
                return;
            }
            pendingKeys.computeIfPresent(normalizeKey(key), (ignored, count) -> count <= 1 ? null : count - 1);
        } finally {
            publishLock.unlock();
        }
    }

    /** 该键当前是否处于写窗口，仅供测试与诊断。 */
    public boolean isPendingInvalidation(@Nullable Object key) {
        return pendingAll.get() > 0 || (key != null && pendingKeys.containsKey(normalizeKey(key)));
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
        if (isPendingInvalidation(key)) {
            // 写窗口内：既有条目可能已被本次写作废，不得对外供应；按未命中处理，调用方回源数据库。
            // 仍然记录装载纪元——窗口可能在本次 put 之前就结束，届时必须靠 epoch 识别「装载期跨越了失效」。
            rememberLoadEpoch(key);
            return null;
        }
        ValueWrapper wrapper = delegate.get(key);
        if (wrapper == null) {
            rememberLoadEpoch(key);
        }
        return wrapper;
    }

    @Override
    @Nullable
    public <T> T get(Object key, @Nullable Class<T> type) {
        if (isPendingInvalidation(key)) {
            rememberLoadEpoch(key);
            return null;
        }
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
        publishLock.lock();
        try {
            if (isPendingInvalidation(key)) {
                // 写窗口内：该值要么来自尚未提交的事务，要么来自即将被作废的旧快照，一律不发布。
                // 刻意<b>不</b>顺手删除既有条目：那会误删另一线程刚发布的新值；窗口内该键本就不供应，
                // 收尾逐出步骤也会清理。
                return;
            }
            if (loadEpoch != null && loadEpoch.longValue() != epoch.get()) {
                // 装载期间发生过失效：该值可能已陈旧（或来自尚未提交的事务），不发布。
                return;
            }
            delegate.put(key, value);
        } finally {
            publishLock.unlock();
        }
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        // 走受守卫的 get：写窗口内不得把可能陈旧的既有条目返回给调用方。
        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        publishLock.lock();
        try {
            epoch.incrementAndGet();
            delegate.evict(key);
        } finally {
            publishLock.unlock();
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        publishLock.lock();
        try {
            epoch.incrementAndGet();
            return delegate.evictIfPresent(key);
        } finally {
            publishLock.unlock();
        }
    }

    @Override
    public void clear() {
        publishLock.lock();
        try {
            epoch.incrementAndGet();
            delegate.clear();
        } finally {
            publishLock.unlock();
        }
    }

    @Override
    public boolean invalidate() {
        publishLock.lock();
        try {
            epoch.incrementAndGet();
            return delegate.invalidate();
        } finally {
            publishLock.unlock();
        }
    }

    /** 当前纪元，仅供测试断言「失效动作确实推进了纪元」。 */
    public long currentEpoch() {
        return epoch.get();
    }

    /**
     * 包内只读诊断：目标线程是否已经真实排队等待 publishLock。
     * 用于并发反例排除“任务已提交但尚未进入目标方法”的调度假绿，不暴露为业务 API。
     */
    boolean isPublishOperationQueued(Thread thread) {
        return publishLock.hasQueuedThread(thread);
    }

    private void rememberLoadEpoch(Object key) {
        Map<Object, Long> tracked = loadEpochs.get();
        if (tracked.size() >= MAX_TRACKED_LOADS) {
            // 只可能是目标方法抛异常导致 get 未配对 put；直接清空，后续 put 因缺快照按「无在途装载」处理。
            tracked.clear();
        }
        tracked.put(normalizeKey(key), epoch.get());
    }

    @Nullable
    private Long forgetLoadEpoch(Object key) {
        Map<Object, Long> tracked = loadEpochs.get();
        Long loadEpoch = tracked.remove(normalizeKey(key));
        if (tracked.isEmpty()) {
            loadEpochs.remove();
        }
        return loadEpoch;
    }

    private static Object normalizeKey(@Nullable Object key) {
        return key == null ? NULL_KEY : key;
    }
}
