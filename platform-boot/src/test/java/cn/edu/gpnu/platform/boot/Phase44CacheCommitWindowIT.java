package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
        "platform.security.jwt.access-ttl-seconds=30"
})
@Import(Phase44CacheCommitWindowIT.DictReadBarrierTestConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class Phase44CacheCommitWindowIT {

    private static final String TYPE_CODE = "p44_commit_window";
    private static final String ITEM_CODE = "ALPHA";
    private static final String PARAM_KEY = "video.diffThreshold";
    private static final int PARAM_SENTINEL = -424242;
    private static final String PARAM_CACHE_KEY = "getInt:" + PARAM_KEY + ":" + PARAM_SENTINEL;
    private static final String ITEMS_CACHE_KEY = "dict:items:" + TYPE_CODE;
    private static final String ITEMS_VERSION_KEY = "dict:items:ver:" + TYPE_CODE;

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

    // ---------------------------------------------------------------- 字典：提交/回滚时序

    @Test
    void dictEvictionIsDeferredUntilCommitAndFirstReadAfterCommitIsFresh() {
        Long itemId = createFixture("V1");
        Cache labels = cacheManager.getCache(CacheConfig.DICT_LABELS);
        assertThat(dictService.dictLabels(TYPE_CODE)).containsEntry(ITEM_CODE, "V1");
        assertThat(labels.get(TYPE_CODE)).isNotNull();

        transactionTemplate.executeWithoutResult(status -> {
            dictService.updateItem(itemId, dictItem("V2", 1));
            assertThat(labels.get(TYPE_CODE))
                    .as("提交前不得逐出：否则并发读会把提交前旧值回填")
                    .isNotNull();
        });

        assertThat(labels.get(TYPE_CODE)).as("事务提交后必须已逐出").isNull();
        assertThat(dictService.dictLabels(TYPE_CODE)).containsEntry(ITEM_CODE, "V2");
    }

    @Test
    void dictEvictionAlsoRunsOnRollbackSoUncommittedValuesNeverSurvive() {
        Long itemId = createFixture("V1");
        Cache labels = cacheManager.getCache(CacheConfig.DICT_LABELS);
        labels.evict(TYPE_CODE);
        AtomicReference<Map<String, String>> insideTransaction = new AtomicReference<>();

        transactionTemplate.executeWithoutResult(status -> {
            dictService.updateItem(itemId, dictItem("V2", 1));
            // 冷缓存下本事务自身的读穿会把未提交值装进共享缓存——afterCommit 语义会把它留到 TTL。
            insideTransaction.set(dictService.dictLabels(TYPE_CODE));
            status.setRollbackOnly();
        });

        assertThat(insideTransaction.get()).containsEntry(ITEM_CODE, "V2");
        assertThat(labels.get(TYPE_CODE))
                .as("回滚后不得留下未提交值（这就是用 afterCompletion 而非 afterCommit 的原因）")
                .isNull();
        assertThat(dictService.dictLabels(TYPE_CODE))
                .as("回滚后应回到 V1")
                .containsEntry(ITEM_CODE, "V1");
    }

    // ---------------------------------------------------------------- 参数：提交/回滚时序

    @Test
    void paramCacheEvictionIsDeferredUntilCommitAndFirstReadAfterCommitIsFresh() {
        SysParam original = editableParam();
        Cache params = cacheManager.getCache(CacheConfig.SYS_PARAM);
        int originalValue = paramService.getInt(PARAM_KEY, PARAM_SENTINEL);
        assertThat(params.get(PARAM_CACHE_KEY)).isNotNull();
        int changed = originalValue + 1;

        try {
            transactionTemplate.executeWithoutResult(status -> {
                systemManagementService.updateParam(original.getId(), paramRequest(String.valueOf(changed)));
                assertThat(params.get(PARAM_CACHE_KEY))
                        .as("提交前不得逐出参数缓存")
                        .isNotNull();
            });

            assertThat(params.get(PARAM_CACHE_KEY)).as("提交后必须已清空").isNull();
            assertThat(paramService.getInt(PARAM_KEY, PARAM_SENTINEL))
                    .as("提交后首次读取必须得到新值")
                    .isEqualTo(changed);
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
            status.setRollbackOnly();
        });

        assertThat(params.get(PARAM_CACHE_KEY))
                .as("回滚后不得留下未提交参数值")
                .isNull();
        assertThat(paramService.getInt(PARAM_KEY, PARAM_SENTINEL))
                .as("回滚后必须回到原值")
                .isEqualTo(originalValue);
    }

    // ---------------------------------------------------------------- fixtures

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
        DictTypeSaveRequest type = new DictTypeSaveRequest();
        type.setTypeCode(TYPE_CODE);
        type.setTypeName("Phase44 提交窗口测试类型");
        type.setStatus(1);
        dictService.createType(type);
        return dictService.createItem(dictItem(itemValue, 1));
    }

    private DictItemSaveRequest dictItem(String itemValue, int status) {
        DictItemSaveRequest request = new DictItemSaveRequest();
        request.setTypeCode(TYPE_CODE);
        request.setItemCode(ITEM_CODE);
        request.setItemValue(itemValue);
        request.setYearVersion("GLOBAL");
        request.setStatus(status);
        return request;
    }

    private void hardDeleteFixture() {
        // 物理删除（绕过 @TableLogic），保证跨运行可重复；软删残行会撞 existsTypeCode/existsItem。
        jdbcTemplate.update("DELETE FROM sys_dict_item WHERE type_code = ?", TYPE_CODE);
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE type_code = ?", TYPE_CODE);
        redisTemplate.delete(List.of(ITEMS_CACHE_KEY, ITEMS_VERSION_KEY));
        Cache labels = cacheManager.getCache(CacheConfig.DICT_LABELS);
        if (labels != null) {
            labels.evict(TYPE_CODE);
        }
        Cache orgItems = cacheManager.getCache(CacheConfig.ORG_DICT_ITEMS);
        if (orgItems != null) {
            orgItems.evict(TYPE_CODE);
        }
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
