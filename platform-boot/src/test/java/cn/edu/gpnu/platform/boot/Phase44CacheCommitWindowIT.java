package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.config.CacheConfig;
import cn.edu.gpnu.platform.system.dto.DictItemSaveRequest;
import cn.edu.gpnu.platform.system.dto.DictTypeSaveRequest;
import cn.edu.gpnu.platform.system.dto.SysParamUpdateRequest;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.service.DictService;
import cn.edu.gpnu.platform.system.service.ParamService;
import cn.edu.gpnu.platform.system.service.SystemManagementService;
import cn.edu.gpnu.platform.system.vo.DictItemVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 44（PG-M4 整改）：参考数据缓存「提交窗口」与「并发重填」的真实交错反例。
 *
 * <p>审计 PG-M4 的失败场景：管理员改参数/字典触发逐出，但逐出发生在<b>提交前</b>；并发请求在提交前读到旧值并把它
 * 重填进缓存，提交后缓存继续按旧规则供数分钟。整改后必须同时成立：
 * <ol>
 *   <li><b>提交前不逐出</b>：写事务内断言缓存仍持有旧条目（若有人改回事务内逐出，此负向断言变红）；</li>
 *   <li><b>完成后逐出</b>：写方法返回后缓存已清空，「提交后首次读取」必得新值；</li>
 *   <li><b>回滚也逐出</b>：事务内读穿装入的<b>未提交</b>值必须在回滚后消失（这是 {@code afterCommit} 语义会漏掉的脏值，
 *       故实现用 {@code afterCompletion}）；</li>
 *   <li><b>旧值不得回填</b>：用 MyBatis {@code StatementHandler.query} 屏障把并发读<b>停在「数据库读已完成、缓存回填未发生」</b>，
 *       期间完成写提交与逐出，再放行读线程——其回填必须被拒绝（Caffeine 侧靠纪元、Redis 侧靠版本 CAS）。</li>
 * </ol>
 *
 * <p>屏障命中与否有显式断言：{@code awaitLoaded} 为 false 直接失败，避免 SQL 漂移把并发用例变成空转绿灯。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30",
        "platform.cache.dictionary.writer-lease=6s",
        "platform.cache.dictionary.writer-renew-interval=1s"
})
@Import(Phase44CacheCommitWindowIT.DictReadBarrierTestConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class Phase44CacheCommitWindowIT {

    private static final String TYPE_CODE = "p44_commit_window";
    private static final String TYPE_CODE_ALIAS = "P44_COMMIT_WINDOW";
    private static final String TYPE_CODE_B = "p44_commit_window_b";
    private static final String ITEM_CODE = "ALPHA";
    private static final String ITEM_CODE_B = "BETA";
    private static final String PARAM_KEY = "video.diffThreshold";
    private static final int PARAM_SENTINEL = -424242;
    private static final String PARAM_CACHE_KEY = "getInt:" + PARAM_KEY + ":" + PARAM_SENTINEL;
    private static final String ITEMS_CACHE_KEY = "dict:items:" + TYPE_CODE;
    private static final String ITEMS_VERSION_KEY = "dict:items-version:" + TYPE_CODE;
    private static final String ITEMS_WRITERS_KEY = "dict:items-writers:" + TYPE_CODE;
    /** 与 {@code DictServiceImpl.PENDING_VERSION_PREFIX} 同值：写窗口令牌前缀。 */
    private static final String PENDING_VERSION_PREFIX = "P:";

    @Autowired
    private DictService dictService;

    @Autowired
    private ParamService paramService;

    @Autowired
    private SystemManagementService systemManagementService;

    @Autowired
    private SysParamMapper paramMapper;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DictReadBarrier barrier;

    private ExecutorService readers;

    @BeforeEach
    void setUp() {
        barrier.reset();
        readers = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "p44-cache-reader");
            thread.setDaemon(true);
            return thread;
        });
        hardDeleteFixture();
    }

    @AfterEach
    void tearDown() {
        barrier.reset();
        readers.shutdownNow();
        hardDeleteFixture();
    }

    // ---------------------------------------------------------------- 字典：并发重填反例

    @Test
    @Timeout(120)
    void preCommitDictLabelsLoadIsNotRefilledAfterEviction() throws Exception {
        Long itemId = createFixture("V1");
        Cache labels = cacheManager.getCache(CacheConfig.DICT_LABELS);
        labels.evict(TYPE_CODE); // 冷缓存：模拟 TTL 过期或此前的无关逐出

        barrier.armDictLabelsRead();
        Future<Map<String, String>> reader = readers.submit(() -> dictService.dictLabels(TYPE_CODE));
        assertThat(barrier.awaitLoaded(20, TimeUnit.SECONDS))
                .as("并发读应已完成数据库读并停在缓存回填之前；armed 期间看到的 sys_dict_item SQL=%s",
                        barrier.seenWhileArmed())
                .isTrue();

        // 写事务在读线程「已持旧值、未回填」期间完成提交，afterCompletion 逐出随之执行
        dictService.updateItem(itemId, dictItem("V2", 1));

        barrier.release();
        Map<String, String> observedByReader = reader.get(20, TimeUnit.SECONDS);

        assertThat(observedByReader)
                .as("读线程读到的确实是提交前的旧值（否则反例不成立）")
                .containsEntry(ITEM_CODE, "V1");
        assertThat(labels.get(TYPE_CODE))
                .as("提交前载入的旧值不得回填进缓存（PG-M4 的根因）")
                .isNull();
        assertThat(dictService.dictLabels(TYPE_CODE))
                .as("提交后首次读取必须得到新值")
                .containsEntry(ITEM_CODE, "V2");
    }

    @Test
    @Timeout(120)
    void preCommitRedisItemsLoadIsNotRefilledAfterEviction() throws Exception {
        Long itemId = createFixture("V1");
        redisTemplate.delete(List.of(ITEMS_CACHE_KEY, ITEMS_VERSION_KEY)); // 冷缓存 + 无版本

        barrier.armDictItemListRead();
        Future<List<DictItemVO>> reader = readers.submit(() -> dictService.listItems(TYPE_CODE, true));
        assertThat(barrier.awaitLoaded(20, TimeUnit.SECONDS))
                .as("并发读应已完成数据库读并停在 Redis 回填之前；armed 期间看到的 sys_dict_item SQL=%s",
                        barrier.seenWhileArmed())
                .isTrue();

        dictService.updateItem(itemId, dictItem("V2", 1));

        barrier.release();
        List<DictItemVO> observedByReader = reader.get(20, TimeUnit.SECONDS);

        assertThat(observedByReader).extracting(DictItemVO::getItemValue).containsExactly("V1");
        assertThat(redisTemplate.opsForValue().get(ITEMS_CACHE_KEY))
                .as("版本已推进，提交前旧值不得写入 Redis")
                .isNull();
        assertThat(redisTemplate.opsForValue().get(ITEMS_VERSION_KEY))
                .as("逐出必须推进内容版本")
                .isNotBlank();
        assertThat(dictService.listItems(TYPE_CODE, true))
                .extracting(DictItemVO::getItemValue)
                .containsExactly("V2");
        assertThat(redisTemplate.opsForValue().get(ITEMS_CACHE_KEY))
                .as("版本稳定时正常回填仍必须生效，否则缓存等于被禁用")
                .isNotBlank();
    }

    // ---------------------------------------------------------------- 字典：写窗口时序

    @Test
    @Timeout(120)
    void dictWriteWindowNeitherServesStaleValueNorPublishesUncommitted() {
        Long itemId = createFixture("V1");
        Cache labels = cacheManager.getCache(CacheConfig.DICT_LABELS);
        assertThat(dictService.dictLabels(TYPE_CODE)).containsEntry(ITEM_CODE, "V1");
        assertThat(labels.get(TYPE_CODE)).as("预热后应已缓存").isNotNull();

        transactionTemplate.executeWithoutResult(status -> {
            dictService.updateItem(itemId, dictItem("V2", 1));

            // ①写窗口内不得再对外供应缓存里的旧值——这正是上一轮复核指出的「提交完成到逐出执行之间」的公开窗口。
            assertThat(labels.get(TYPE_CODE))
                    .as("写窗口内缓存必须停止供应该键")
                    .isNull();

            // ②并发读只能看到旧的「已提交」值，且不得把它回填进缓存。
            Map<String, String> concurrentRead = readInAnotherThread(() -> dictService.dictLabels(TYPE_CODE));
            assertThat(concurrentRead)
                    .as("并发读应看到旧的已提交值，而不是本事务未提交的新值")
                    .containsEntry(ITEM_CODE, "V1");
            assertThat(labels.get(TYPE_CODE))
                    .as("写窗口内的并发读不得回填缓存")
                    .isNull();

            // ③本事务自己的读穿看到自己的未提交值是正常的，但同样不得发布到共享缓存。
            assertThat(dictService.dictLabels(TYPE_CODE)).containsEntry(ITEM_CODE, "V2");
            assertThat(labels.get(TYPE_CODE))
                    .as("未提交值不得进入共享缓存")
                    .isNull();
        });

        assertThat(dictService.dictLabels(TYPE_CODE))
                .as("提交后首次读取必须得到新值")
                .containsEntry(ITEM_CODE, "V2");
        assertThat(labels.get(TYPE_CODE))
                .as("窗口结束后缓存必须恢复可用，否则等于把缓存禁用了")
                .isNotNull();
    }

    @Test
    @Timeout(120)
    void redisWriteWindowNeitherServesStaleValueNorPublishesUncommitted() {
        Long itemId = createFixture("V1");
        assertThat(dictService.listItems(TYPE_CODE, true)).extracting(DictItemVO::getItemValue).containsExactly("V1");
        assertThat(redisTemplate.opsForValue().get(ITEMS_CACHE_KEY)).as("预热后应已缓存").isNotBlank();

        transactionTemplate.executeWithoutResult(status -> {
            dictService.updateItem(itemId, dictItem("V2", 1));

            assertThat(redisTemplate.opsForValue().get(ITEMS_VERSION_KEY))
                    .as("写窗口内版本必须是写窗口令牌——其它节点也据它拒绝供应/回填")
                    .startsWith(PENDING_VERSION_PREFIX);

            List<DictItemVO> concurrentRead = readInAnotherThread(() -> dictService.listItems(TYPE_CODE, true));
            assertThat(concurrentRead).extracting(DictItemVO::getItemValue)
                    .as("并发读应看到旧的已提交值")
                    .containsExactly("V1");
            assertThat(redisTemplate.opsForValue().get(ITEMS_CACHE_KEY))
                    .as("写窗口内不得回填 Redis 负载")
                    .isNull();
        });

        assertThat(dictService.listItems(TYPE_CODE, true)).extracting(DictItemVO::getItemValue).containsExactly("V2");
        assertThat(redisTemplate.opsForValue().get(ITEMS_VERSION_KEY))
                .as("窗口结束后版本必须换成正常令牌")
                .doesNotStartWith(PENDING_VERSION_PREFIX);
        assertThat(redisTemplate.opsForValue().get(ITEMS_CACHE_KEY))
                .as("窗口结束后正常回填必须生效")
                .isNotBlank();
    }

    @Test
    @Timeout(120)
    void overlappingWritersKeepRedisPendingUntilTheLastOwnerCompletes() throws Exception {
        Long firstItemId = createFixture("A1");
        Long secondItemId = dictService.createItem(dictItemOf(TYPE_CODE, ITEM_CODE_B, "B1", 1));
        assertThat(dictService.listItems(TYPE_CODE, true)).extracting(DictItemVO::getItemValue)
                .containsExactly("A1", "B1");

        CountDownLatch firstWindowOpen = new CountDownLatch(1);
        CountDownLatch secondWindowOpen = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);
        CountDownLatch finishSecond = new CountDownLatch(1);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = writers.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                dictService.updateItem(firstItemId, dictItemOf(TYPE_CODE, ITEM_CODE, "A2", 1));
                firstWindowOpen.countDown();
                awaitOrThrow(finishFirst, "第一写事务未获放行");
            }));
            assertThat(firstWindowOpen.await(20, TimeUnit.SECONDS)).as("第一写事务必须已进入 pending").isTrue();

            Future<?> second = writers.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                dictService.updateItem(secondItemId, dictItemOf(TYPE_CODE, ITEM_CODE_B, "B2", 1));
                secondWindowOpen.countDown();
                awaitOrThrow(finishSecond, "第二写事务未获放行");
            }));
            assertThat(secondWindowOpen.await(20, TimeUnit.SECONDS)).as("第二写事务必须与第一事务重叠").isTrue();
            assertThat(redisTemplate.opsForZSet().size(ITEMS_WRITERS_KEY))
                    .as("每个活跃事务必须有独立 owner，不能互相覆盖")
                    .isEqualTo(2L);
            assertThat(redisTemplate.getExpire(ITEMS_WRITERS_KEY))
                    .as("writer 容器使用可续租的崩溃回收 TTL，而不是永久残留")
                    .isPositive();

            finishFirst.countDown();
            first.get(20, TimeUnit.SECONDS);

            assertThat(redisTemplate.opsForZSet().size(ITEMS_WRITERS_KEY))
                    .as("先完成者只能移除自己的 owner")
                    .isEqualTo(1L);
            assertThat(redisTemplate.opsForValue().get(ITEMS_VERSION_KEY))
                    .as("仍有写事务时必须继续 pending")
                    .startsWith(PENDING_VERSION_PREFIX);
            assertThat(readInAnotherThread(() -> dictService.listItems(TYPE_CODE, true)))
                    .extracting(DictItemVO::getItemValue)
                    .as("中间读只能看到第一事务已提交、第二事务未提交的数据库快照")
                    .containsExactly("A2", "B1");
            assertThat(redisTemplate.opsForValue().get(ITEMS_CACHE_KEY))
                    .as("仍有 owner 时中间快照不得回填")
                    .isNull();

            finishSecond.countDown();
            second.get(20, TimeUnit.SECONDS);

            assertThat(redisTemplate.hasKey(ITEMS_WRITERS_KEY)).isFalse();
            assertThat(redisTemplate.opsForValue().get(ITEMS_VERSION_KEY))
                    .as("最后一个 owner 离开后才恢复正常版本")
                    .doesNotStartWith(PENDING_VERSION_PREFIX);
            assertThat(dictService.listItems(TYPE_CODE, true))
                    .extracting(DictItemVO::getItemValue)
                    .containsExactly("A2", "B2");
        } finally {
            finishFirst.countDown();
            finishSecond.countDown();
            writers.shutdownNow();
        }
    }

    @Test
    @Timeout(120)
    void liveWriterRenewsItsOwnerBeyondTwoLeasePeriods() throws Exception {
        Long itemId = createFixture("V1");
        CountDownLatch windowOpen = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ExecutorService writer = Executors.newSingleThreadExecutor();
        try {
            Future<?> transaction = writer.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                dictService.updateItem(itemId, dictItem("V2", 1));
                windowOpen.countDown();
                awaitOrThrow(finish, "长事务未获放行");
            }));
            assertThat(windowOpen.await(20, TimeUnit.SECONDS)).as("写事务必须已进入 pending").isTrue();

            Double initialExpiry = redisTemplate.opsForZSet().score(ITEMS_WRITERS_KEY,
                    onlyWriterOwner(ITEMS_WRITERS_KEY));
            assertThat(initialExpiry).as("owner 必须携带独立到期分数").isNotNull();

            // 测试租约为 6s；保持事务 13s，跨过两个原始租约周期。若没有后台续租，owner 会在读 Lua 中被清理。
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(13);
            while (System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(750);
                assertThat(readInAnotherThread(() -> dictService.listItems(TYPE_CODE, true)))
                        .extracting(DictItemVO::getItemValue)
                        .as("真实 READ Lua 应持续看到活跃 owner，并只从数据库取得已提交旧值")
                        .containsExactly("V1");
                assertThat(redisTemplate.opsForZSet().size(ITEMS_WRITERS_KEY))
                        .as("READ Lua 已执行 prune 后，存活事务的 owner 仍不得消失")
                        .isEqualTo(1L);
                assertThat(redisTemplate.opsForValue().get(ITEMS_VERSION_KEY))
                        .as("续租期间必须持续 fail closed")
                        .startsWith(PENDING_VERSION_PREFIX);
                assertThat(redisTemplate.opsForValue().get(ITEMS_CACHE_KEY))
                        .as("长事务窗口内的已提交旧快照也不得回填")
                        .isNull();
            }

            Double renewedExpiry = redisTemplate.opsForZSet().score(ITEMS_WRITERS_KEY,
                    onlyWriterOwner(ITEMS_WRITERS_KEY));
            assertThat(renewedExpiry)
                    .as("owner 到期分数必须由后台线程推进，不能仍是登记时的固定 TTL")
                    .isGreaterThan(initialExpiry);

            finish.countDown();
            transaction.get(20, TimeUnit.SECONDS);
            assertThat(redisTemplate.hasKey(ITEMS_WRITERS_KEY)).isFalse();
            assertThat(dictService.listItems(TYPE_CODE, true))
                    .extracting(DictItemVO::getItemValue)
                    .containsExactly("V2");
        } finally {
            finish.countDown();
            writer.shutdownNow();
        }
    }

    @Test
    @Timeout(120)
    void ownerLossBeforeCommitAbortsTheTransactionAndRollsBackDb() throws Exception {
        Long itemId = createFixture("V1");
        CountDownLatch windowOpen = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ExecutorService writer = Executors.newSingleThreadExecutor();
        try {
            Future<?> transaction = writer.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                dictService.updateItem(itemId, dictItem("V2", 1));
                windowOpen.countDown();
                awaitOrThrow(finish, "丢租约事务未获放行");
            }));
            assertThat(windowOpen.await(20, TimeUnit.SECONDS)).as("写事务必须已进入 pending").isTrue();
            assertThat(redisTemplate.delete(ITEMS_WRITERS_KEY))
                    .as("故障注入必须真实删除当前事务的 owner，而不是空转")
                    .isTrue();

            finish.countDown();
            assertThatThrownBy(() -> transaction.get(20, TimeUnit.SECONDS))
                    .as("beforeCommit 发现 owner 丢失时必须阻止提交")
                    .hasRootCauseInstanceOf(BizException.class)
                    .hasRootCauseMessage("字典缓存写窗口租约已丢失，事务已中止");

            assertThat(jdbcTemplate.queryForObject("SELECT item_value FROM sys_dict_item WHERE id = ?",
                    String.class, itemId))
                    .as("提交被阻止后数据库必须保持 V1")
                    .isEqualTo("V1");
            assertThat(dictService.listItems(TYPE_CODE, true))
                    .extracting(DictItemVO::getItemValue)
                    .containsExactly("V1");
        } finally {
            finish.countDown();
            writer.shutdownNow();
        }
    }

    @Test
    @Timeout(120)
    void caseAliasesShareCanonicalDbRedisAndCaffeineIdentityOnCommitAndRollback() {
        Long itemId = createFixture("V1");
        Cache labels = cacheManager.getCache(CacheConfig.DICT_LABELS);
        Cache orgItems = cacheManager.getCache(CacheConfig.ORG_DICT_ITEMS);

        assertThat(dictService.listItems(TYPE_CODE_ALIAS, true)).extracting(DictItemVO::getItemValue)
                .containsExactly("V1");
        assertThat(dictService.dictLabels(TYPE_CODE_ALIAS)).containsEntry(ITEM_CODE, "V1");
        assertThat(dictService.globalEnabledDictItems(TYPE_CODE_ALIAS)).containsKey(ITEM_CODE);
        assertThat(labels.get(TYPE_CODE)).as("Caffeine 必须只使用 canonical 小写键").isNotNull();
        assertThat(labels.get(TYPE_CODE_ALIAS)).as("大小写别名不得形成第二个 Caffeine 键").isNull();
        assertThat(orgItems.get(TYPE_CODE)).as("组织字典缓存同样必须使用 canonical 小写键").isNotNull();
        assertThat(orgItems.get(TYPE_CODE_ALIAS)).as("组织字典缓存不得形成大小写别名键").isNull();
        assertNoUppercaseRedisAlias();

        dictService.updateItem(itemId, dictItemOf(TYPE_CODE_ALIAS, ITEM_CODE, "V2", 1));

        assertThat(jdbcTemplate.queryForObject("SELECT type_code FROM sys_dict_item WHERE id = ?",
                String.class, itemId)).isEqualTo(TYPE_CODE);
        assertThat(dictService.listItems(TYPE_CODE, true)).extracting(DictItemVO::getItemValue).containsExactly("V2");
        assertThat(dictService.listItems(TYPE_CODE_ALIAS, true)).extracting(DictItemVO::getItemValue).containsExactly("V2");
        assertThat(dictService.dictLabels(TYPE_CODE)).containsEntry(ITEM_CODE, "V2");
        assertThat(dictService.dictLabels(TYPE_CODE_ALIAS)).containsEntry(ITEM_CODE, "V2");
        assertThat(dictService.globalEnabledDictItems(TYPE_CODE_ALIAS).get(ITEM_CODE).getItemValue()).isEqualTo("V2");
        assertNoUppercaseRedisAlias();

        transactionTemplate.executeWithoutResult(status -> {
            dictService.updateItem(itemId, dictItemOf(TYPE_CODE_ALIAS, ITEM_CODE, "V3", 1));
            assertThat(redisTemplate.opsForValue().get(ITEMS_VERSION_KEY)).startsWith(PENDING_VERSION_PREFIX);
            assertNoUppercaseRedisAlias();
            status.setRollbackOnly();
        });

        assertThat(dictService.listItems(TYPE_CODE, true)).extracting(DictItemVO::getItemValue).containsExactly("V2");
        assertThat(dictService.listItems(TYPE_CODE_ALIAS, true)).extracting(DictItemVO::getItemValue).containsExactly("V2");
        assertThat(dictService.dictLabels(TYPE_CODE_ALIAS)).containsEntry(ITEM_CODE, "V2");
        assertThat(dictService.globalEnabledDictItems(TYPE_CODE_ALIAS).get(ITEM_CODE).getItemValue()).isEqualTo("V2");
        assertNoUppercaseRedisAlias();
    }

    @Test
    void dictWriteWindowClosesOnRollbackAndLeavesNoUncommittedValue() {
        Long itemId = createFixture("V1");
        Cache labels = cacheManager.getCache(CacheConfig.DICT_LABELS);
        labels.evict(TYPE_CODE);
        AtomicReference<Map<String, String>> insideTransaction = new AtomicReference<>();

        transactionTemplate.executeWithoutResult(status -> {
            dictService.updateItem(itemId, dictItem("V2", 1));
            // 冷缓存下本事务自身的读穿会看到未提交值；写窗口保证它不会被发布出去。
            insideTransaction.set(dictService.dictLabels(TYPE_CODE));
            assertThat(labels.get(TYPE_CODE)).as("未提交值不得进入共享缓存").isNull();
            status.setRollbackOnly();
        });

        assertThat(insideTransaction.get()).containsEntry(ITEM_CODE, "V2");
        assertThat(labels.get(TYPE_CODE))
                .as("回滚后不得留下未提交值（这就是用 afterCompletion 而非 afterCommit 的原因）")
                .isNull();
        assertThat(dictService.dictLabels(TYPE_CODE))
                .as("回滚后应回到 V1")
                .containsEntry(ITEM_CODE, "V1");
        assertThat(labels.get(TYPE_CODE)).as("窗口已解除，读取应能正常回填").isNotNull();
    }

    // ---------------------------------------------------------------- 参数：写窗口时序

    @Test
    @Timeout(120)
    void paramWriteWindowNeitherServesStaleValueNorPublishesUncommitted() {
        SysParam original = editableParam();
        Cache params = cacheManager.getCache(CacheConfig.SYS_PARAM);
        int originalValue = paramService.getInt(PARAM_KEY, PARAM_SENTINEL);
        assertThat(params.get(PARAM_CACHE_KEY)).isNotNull();
        int changed = originalValue + 1;

        try {
            transactionTemplate.executeWithoutResult(status -> {
                systemManagementService.updateParam(original.getId(), paramRequest(String.valueOf(changed)));

                assertThat(params.get(PARAM_CACHE_KEY))
                        .as("写窗口内参数缓存必须停止供应（allEntries 口径下抑制整缓存）")
                        .isNull();
                assertThat(readInAnotherThread(() -> paramService.getInt(PARAM_KEY, PARAM_SENTINEL)))
                        .as("并发读应看到旧的已提交值")
                        .isEqualTo(originalValue);
                assertThat(params.get(PARAM_CACHE_KEY))
                        .as("写窗口内的并发读不得回填")
                        .isNull();
            });

            assertThat(paramService.getInt(PARAM_KEY, PARAM_SENTINEL))
                    .as("提交后首次读取必须得到新值")
                    .isEqualTo(changed);
            assertThat(params.get(PARAM_CACHE_KEY)).as("窗口结束后缓存恢复可用").isNotNull();
        } finally {
            systemManagementService.updateParam(original.getId(), paramRequest(original.getParamValue()));
        }
        assertThat(paramService.getInt(PARAM_KEY, PARAM_SENTINEL)).isEqualTo(originalValue);
    }

    @Test
    void paramRollbackClearsValuesCachedInsideTheUncommittedTransaction() {
        SysParam original = editableParam();
        Cache params = cacheManager.getCache(CacheConfig.SYS_PARAM);
        int originalValue = paramService.getInt(PARAM_KEY, PARAM_SENTINEL);
        int changed = originalValue + 7;
        params.clear();

        transactionTemplate.executeWithoutResult(status -> {
            systemManagementService.updateParam(original.getId(), paramRequest(String.valueOf(changed)));
            assertThat(paramService.getInt(PARAM_KEY, PARAM_SENTINEL))
                    .as("冷缓存下事务内读穿会看到自己的未提交值")
                    .isEqualTo(changed);
            assertThat(params.get(PARAM_CACHE_KEY))
                    .as("但未提交值不得进入共享缓存")
                    .isNull();
            status.setRollbackOnly();
        });

        assertThat(params.get(PARAM_CACHE_KEY))
                .as("回滚后不得留下未提交参数值")
                .isNull();
        assertThat(paramService.getInt(PARAM_KEY, PARAM_SENTINEL))
                .as("回滚后必须回到原值")
                .isEqualTo(originalValue);
    }

    // ---------------------------------------------------------------- Redis 键空间与残留负载

    @Test
    void stalePayloadWithOutdatedVersionStampIsNeverServed() {
        // 复刻「版本已推进、负载删除未生效」的部分失败：负载自带版本戳，戳不符即不得作为有效命中。
        Long itemId = createFixture("V1");
        assertThat(dictService.listItems(TYPE_CODE, true)).extracting(DictItemVO::getItemValue).containsExactly("V1");
        String stalePayload = redisTemplate.opsForValue().get(ITEMS_CACHE_KEY);
        assertThat(stalePayload).as("预热后应有负载").isNotBlank();

        dictService.updateItem(itemId, dictItem("V2", 1));           // 原子推进版本 + 删除负载
        redisTemplate.opsForValue().set(ITEMS_CACHE_KEY, stalePayload, Duration.ofMinutes(5)); // 旧负载“复活”

        assertThat(dictService.listItems(TYPE_CODE, true))
                .as("版本戳与当前版本不符的残留负载绝不能作为有效命中")
                .extracting(DictItemVO::getItemValue)
                .containsExactly("V2");
    }

    @Test
    void expiredCrashOwnerAndPendingVersionRecoverFromCommittedDbTruth() {
        Long itemId = createFixture("V1");
        assertThat(dictService.listItems(TYPE_CODE, true))
                .extracting(DictItemVO::getItemValue)
                .containsExactly("V1");
        String stalePayload = redisTemplate.opsForValue().get(ITEMS_CACHE_KEY);
        assertThat(stalePayload).as("预热后必须有可用于故障注入的旧 payload").isNotBlank();

        // 复刻“数据库已提交、进程在 afterCompletion 前崩溃”：owner 已过租约，pending/payload 仍可能残留。
        jdbcTemplate.update("UPDATE sys_dict_item SET item_value = ? WHERE id = ?", "V2", itemId);
        redisTemplate.opsForZSet().add(ITEMS_WRITERS_KEY, "crashed-owner", 0D);
        redisTemplate.opsForValue().set(ITEMS_VERSION_KEY, PENDING_VERSION_PREFIX + "ACTIVE",
                Duration.ofMinutes(5));
        redisTemplate.opsForValue().set(ITEMS_CACHE_KEY, stalePayload, Duration.ofMinutes(5));

        assertThat(dictService.listItems(TYPE_CODE, true))
                .as("READ Lua 必须清理过期 owner/pending/旧 payload，再从已提交数据库真值重建")
                .extracting(DictItemVO::getItemValue)
                .containsExactly("V2");
        assertThat(redisTemplate.hasKey(ITEMS_WRITERS_KEY)).isFalse();
        assertThat(redisTemplate.opsForValue().get(ITEMS_VERSION_KEY)).doesNotStartWith(PENDING_VERSION_PREFIX);
        assertThat(redisTemplate.opsForValue().get(ITEMS_CACHE_KEY)).contains("\"itemValue\":\"V2\"");
    }

    @Test
    void legacyEnvelopeWithMatchingVersionCannotSurviveCaseAliasUpgrade() throws Exception {
        Long itemId = createFixture("V1");
        List<DictItemVO> v1 = dictService.listItems(TYPE_CODE, true);
        String version = redisTemplate.opsForValue().get(ITEMS_VERSION_KEY);
        assertThat(version).isNotBlank();

        // 复刻旧节点留下的 {v,items} 包络：它没有 schema/canonical identity，但版本仍与当前键匹配。
        String legacyEnvelope = objectMapper.writeValueAsString(Map.of("v", version, "items", v1));
        redisTemplate.opsForValue().set(ITEMS_CACHE_KEY, legacyEnvelope, Duration.ofMinutes(5));
        jdbcTemplate.update("UPDATE sys_dict_item SET item_value = ? WHERE id = ?", "V2", itemId);

        assertThat(dictService.listItems(TYPE_CODE_ALIAS, true))
                .as("旧包络即使版本匹配也必须被拒绝，并从 canonical DB identity 重载")
                .extracting(DictItemVO::getItemValue)
                .containsExactly("V2");

        JsonNode upgraded = objectMapper.readTree(redisTemplate.opsForValue().get(ITEMS_CACHE_KEY));
        assertThat(upgraded.path("schema").asInt()).isEqualTo(2);
        assertThat(upgraded.path("typeCode").asText()).isEqualTo(TYPE_CODE);
        assertThat(upgraded.path("items").get(0).path("itemValue").asText()).isEqualTo("V2");
        assertNoUppercaseRedisAlias();
    }

    @Test
    void typeCodesThatWouldPolluteTheRedisKeyspaceAreRejectedByTheBackend() {
        // 上一轮版本键前缀嵌套在负载前缀内，合法 typeCode "ver:xxx" 会让两类键互相覆盖。
        // 现在既改成不相交前缀，也把字符集变成后端硬约束（红线 R7：前端规则不可作为唯一防线）。
        for (String polluting : List.of("ver:" + TYPE_CODE, "dict:items:" + TYPE_CODE, TYPE_CODE + ":x",
                TYPE_CODE + "-version:x")) {
            assertThatThrownBy(() -> dictService.createType(dictType(polluting)))
                    .as("含分隔符的类型编码必须被后端拒绝：%s", polluting)
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("仅支持英文、数字、下划线");
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type WHERE type_code LIKE ?", Long.class, "%:%"))
                .as("被拒绝的类型不得落库")
                .isZero();
    }

    @Test
    void writesToOneDictTypeDoNotDisturbAnotherTypesCache() {
        createFixture("V1");
        createFixtureOf(TYPE_CODE_B, "B1");
        assertThat(dictService.listItems(TYPE_CODE, true)).extracting(DictItemVO::getItemValue).containsExactly("V1");
        assertThat(dictService.listItems(TYPE_CODE_B, true)).extracting(DictItemVO::getItemValue).containsExactly("B1");
        assertThat(dictService.dictLabels(TYPE_CODE_B)).containsEntry(ITEM_CODE, "B1");

        Long itemIdA = dictService.listItems(TYPE_CODE, true).get(0).getId();
        dictService.updateItem(itemIdA, dictItem("V2", 1));

        assertThat(redisTemplate.opsForValue().get("dict:items:" + TYPE_CODE_B))
                .as("另一类型的负载不得被波及")
                .isNotBlank();
        assertThat(dictService.listItems(TYPE_CODE_B, true)).extracting(DictItemVO::getItemValue).containsExactly("B1");
        assertThat(dictService.dictLabels(TYPE_CODE_B)).containsEntry(ITEM_CODE, "B1");
        assertThat(dictService.listItems(TYPE_CODE, true)).extracting(DictItemVO::getItemValue).containsExactly("V2");
    }

    // ---------------------------------------------------------------- fixtures

    /** 在另一线程执行读取：与写事务真正并发，避免同线程复用写事务的连接与事务上下文。 */
    private <T> T readInAnotherThread(Callable<T> read) {
        try {
            return readers.submit(read).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("并发读执行失败", e);
        }
    }

    private void awaitOrThrow(CountDownLatch latch, String message) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String onlyWriterOwner(String writersKey) {
        assertThat(redisTemplate.opsForZSet().size(writersKey)).isEqualTo(1L);
        return redisTemplate.opsForZSet().range(writersKey, 0, 0).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("writer owner 不存在"));
    }

    private void assertNoUppercaseRedisAlias() {
        assertThat(redisTemplate.hasKey("dict:items:" + TYPE_CODE_ALIAS)).isFalse();
        assertThat(redisTemplate.hasKey("dict:items-version:" + TYPE_CODE_ALIAS)).isFalse();
        assertThat(redisTemplate.hasKey("dict:items-writers:" + TYPE_CODE_ALIAS)).isFalse();
    }

    private SysParam editableParam() {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, PARAM_KEY).last("LIMIT 1"));
        assertThat(param).as("可编辑种子参数 %s 应存在", PARAM_KEY).isNotNull();
        return param;
    }

    private SysParamUpdateRequest paramRequest(String value) {
        SysParamUpdateRequest request = new SysParamUpdateRequest();
        request.setParamValue(value);
        return request;
    }

    private Long createFixture(String itemValue) {
        return createFixtureOf(TYPE_CODE, itemValue);
    }

    private Long createFixtureOf(String typeCode, String itemValue) {
        dictService.createType(dictType(typeCode));
        return dictService.createItem(dictItemOf(typeCode, itemValue, 1));
    }

    private DictTypeSaveRequest dictType(String typeCode) {
        DictTypeSaveRequest type = new DictTypeSaveRequest();
        type.setTypeCode(typeCode);
        type.setTypeName("Phase44 提交窗口测试类型");
        type.setStatus(1);
        return type;
    }

    private DictItemSaveRequest dictItem(String itemValue, int status) {
        return dictItemOf(TYPE_CODE, itemValue, status);
    }

    private DictItemSaveRequest dictItemOf(String typeCode, String itemValue, int status) {
        return dictItemOf(typeCode, ITEM_CODE, itemValue, status);
    }

    private DictItemSaveRequest dictItemOf(String typeCode, String itemCode, String itemValue, int status) {
        DictItemSaveRequest request = new DictItemSaveRequest();
        request.setTypeCode(typeCode);
        request.setItemCode(itemCode);
        request.setItemValue(itemValue);
        request.setYearVersion("GLOBAL");
        request.setStatus(status);
        return request;
    }

    private void hardDeleteFixture() {
        // 物理删除（绕过 @TableLogic），保证跨运行可重复；软删残行会撞 existsTypeCode/existsItem。
        for (String typeCode : List.of(TYPE_CODE, TYPE_CODE_B)) {
            jdbcTemplate.update("DELETE FROM sys_dict_item WHERE type_code = ?", typeCode);
            jdbcTemplate.update("DELETE FROM sys_dict_type WHERE type_code = ?", typeCode);
            redisTemplate.delete(List.of("dict:items:" + typeCode, "dict:items-version:" + typeCode,
                    "dict:items-writers:" + typeCode));
            for (String cacheName : List.of(CacheConfig.DICT_LABELS, CacheConfig.ORG_DICT_ITEMS)) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.evict(typeCode);
                    cache.evict(typeCode.toUpperCase(Locale.ROOT));
                }
            }
        }
        redisTemplate.delete(List.of("dict:items:" + TYPE_CODE_ALIAS,
                "dict:items-version:" + TYPE_CODE_ALIAS,
                "dict:items-writers:" + TYPE_CODE_ALIAS));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DictReadBarrierTestConfiguration {

        @Bean
        DictReadBarrier dictReadBarrier() {
            return new DictReadBarrier();
        }
    }

    /**
     * 一次性屏障：命中目标 SQL 时先 {@code proceed()}（数据库读完成、结果已物化），再挂住调用线程，
     * 使「读到旧值」与「回填缓存」之间可以插入真实的写提交与逐出。
     */
    @Intercepts(@Signature(type = StatementHandler.class, method = "query",
            args = {Statement.class, ResultHandler.class}))
    static final class DictReadBarrier implements Interceptor {

        private volatile Target target = Target.DISARMED;
        private volatile CountDownLatch loaded = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);
        private final List<String> seenWhileArmed = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            Target current = this.target;
            if (current == Target.DISARMED) {
                return invocation.proceed();
            }
            StatementHandler handler = (StatementHandler) invocation.getTarget();
            // 归一：折叠空白并收紧逗号后空格，使匹配不受 MyBatis-Plus 分隔符风格影响。
            String sql = handler.getBoundSql().getSql()
                    .replaceAll("\\s+", " ")
                    .replace(", ", ",")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (!current.matches(sql)) {
                if (sql.contains("sys_dict_item")) {
                    seenWhileArmed.add(sql); // 便于 SQL 漂移时定位，而不是给出无信息的 false
                }
                return invocation.proceed();
            }
            this.target = Target.DISARMED; // 一次性：后续校验读不受影响
            Object result = invocation.proceed();
            loaded.countDown();
            if (!release.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("屏障未在 30s 内被放行");
            }
            return result;
        }

        void armDictLabelsRead() {
            rearm(Target.DICT_LABELS);
        }

        void armDictItemListRead() {
            rearm(Target.DICT_ITEM_LIST);
        }

        boolean awaitLoaded(long timeout, TimeUnit unit) throws InterruptedException {
            return loaded.await(timeout, unit);
        }

        void release() {
            release.countDown();
        }

        void reset() {
            target = Target.DISARMED;
            release.countDown();
            loaded = new CountDownLatch(1);
            release = new CountDownLatch(1);
            seenWhileArmed.clear();
        }

        List<String> seenWhileArmed() {
            return List.copyOf(seenWhileArmed);
        }

        private void rearm(Target next) {
            seenWhileArmed.clear();
            loaded = new CountDownLatch(1);
            release = new CountDownLatch(1);
            target = next;
        }

        private enum Target {
            DISARMED {
                @Override
                boolean matches(String sql) {
                    return false;
                }
            },
            /** {@code DictServiceImpl.dictLabels}：typeCode + status，按 sort 排序（无 item_code 次序）。 */
            DICT_LABELS {
                @Override
                boolean matches(String sql) {
                    return sql.contains("from sys_dict_item")
                            && sql.contains("type_code = ?")
                            && sql.contains("status = ?")
                            && !sql.contains("year_version = ?")
                            && sql.endsWith("order by sort asc");
                }
            },
            /** {@code DictServiceImpl.listItems → queryItems}：按 sort、item_code 排序。 */
            DICT_ITEM_LIST {
                @Override
                boolean matches(String sql) {
                    return sql.contains("from sys_dict_item")
                            && sql.contains("type_code = ?")
                            && sql.endsWith("order by sort asc,item_code asc");
                }
            };

            abstract boolean matches(String sql);
        }
    }
}
