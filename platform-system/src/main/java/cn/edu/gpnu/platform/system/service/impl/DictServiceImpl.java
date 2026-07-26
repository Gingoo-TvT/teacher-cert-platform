package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.cache.EpochGuardedCache;
import cn.edu.gpnu.platform.system.cache.ReferenceCacheInvalidator;
import cn.edu.gpnu.platform.system.config.CacheConfig;
import cn.edu.gpnu.platform.system.config.DictCacheProperties;
import cn.edu.gpnu.platform.system.dto.DictItemSaveRequest;
import cn.edu.gpnu.platform.system.dto.DictTypeSaveRequest;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysDictType;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.SysDictTypeMapper;
import cn.edu.gpnu.platform.system.service.DictService;
import cn.edu.gpnu.platform.system.vo.DictItemVO;
import cn.edu.gpnu.platform.system.vo.DictTypeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private static final int ENABLED = 1;
    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";
    private static final String CACHE_PREFIX = "dict:items:";
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    /**
     * Phase 44（PG-M4 整改）：Redis 侧内容版本键——<b>版本是权威</b>，负载只是它的从属副本。
     *
     * <p>协议：每次写登记生成唯一 owner，原子加入 {@link #CACHE_WRITERS_PREFIX} 对应的 ZSET、把版本置为
     * {@link #PENDING_VERSION} 并删负载；事务完成回调只移除自己的 owner，只有最后一个 owner 离开时才恢复正常随机版本。
     * 读穿一次 EVAL 原子检查「活跃 owner + 负载 + 版本」：只要还有 owner，即使负载还在也不供应、不回填；否则以取回的
     * 版本做 CAS 回填，只有版本未变才写入。于是
     * ①「提交前载入旧值、逐出后回填」被 CAS 拒绝；②「提交完成到逐出执行之间」由写窗口令牌挡住；两者<b>跨节点成立</b>
     * （与 Caffeine 的进程内纪元/写窗口守卫互补）。
     *
     * <p>版本键 TTL 必须长于负载 TTL，否则版本先过期、CAS 退化为「无版本＝允许写」。用随机 UUID 而非 INCR：
     * 版本键若因内存压力被驱逐后重建，随机值不可能与此前捕获值相等，不会误判为「未变」。writer 使用逐 owner ZSET
     * 租约：活事务后台续租，{@code beforeCommit} 再强制校验并续租；丢失 owner 即阻止提交。完成回调只移除自己的 owner，
     * 崩溃或回调失败则由逐 owner 到期分数安全回收，不再依赖一个会覆盖所有事务的固定 60 秒 pending 值。
     *
     * <p><b>命名空间不相交（复核 Medium）</b>：负载前缀 {@code dict:items:} 与版本前缀 {@code dict:items-version:}
     * 在第 11 个字符上分别为 {@code ':'} 与 {@code '-'}，因此<b>任何</b> typeCode 都无法让「某类型的负载键」等于
     * 「另一类型的版本键」——旧的 {@code dict:items:ver:} 前缀在 typeCode 恰为 {@code ver:X} 时会碰撞。
     *
     * <p>集群部署下两个键分属不同 slot，Lua 需要 hash tag 才能同槽；本项目为单实例 Redis，暂不引入 tag
     * 以免改动既有负载键格式。
     */
    private static final String CACHE_VERSION_PREFIX = "dict:items-version:";
    private static final String CACHE_WRITERS_PREFIX = "dict:items-writers:";
    private static final Duration CACHE_VERSION_TTL = Duration.ofHours(24);
    private static final String PENDING_VERSION_PREFIX = "P:";
    private static final String PENDING_VERSION = PENDING_VERSION_PREFIX + "ACTIVE";
    private static final String UNREADABLE_VERSION_PREFIX = "U:";
    private static final String ABSENT_VERSION = "";
    private static final String PAYLOAD_SCHEMA_FIELD = "schema";
    private static final int PAYLOAD_SCHEMA_VERSION = 2;
    private static final String PAYLOAD_TYPE_CODE_FIELD = "typeCode";
    private static final String PAYLOAD_VERSION_FIELD = "v";
    private static final String PAYLOAD_ITEMS_FIELD = "items";

    /**
     * Phase 44（PG-M4 第三轮整改）：字典类型编码采用唯一 canonical identity：
     * {@code trim + Locale.ROOT 小写}，并施加字符集<b>后端硬约束</b>（红线 R7：前端联动仅为体验，校验后端必做）。
     * 此前该规则只存在于 {@code DictTypeDrawer.vue}，直接调用 API 可绕过；而 typeCode 被原样拼进 Redis 键，
     * 含分隔符的编码会让不同类型的键互相污染；数据库 {@code *_ai_ci} 又把大小写别名视为同一身份，而 Java/Redis
     * 默认区分大小写。所有 DB 查询、写入、Redis/Caffeine key、pending 与逐出均调用同一 canonicalizer，
     * 因而 {@code material_category}/{@code MATERIAL_CATEGORY} 不再形成两套缓存。
     */
    private static final Pattern TYPE_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    /**
     * 原子清理过期 writer、检查活跃 writer、取回负载与版本，并在版本缺失时就地建立一个。
     * writer、版本与负载在同一脚本内读取，因此三者恒为同一时刻的一致快照。若活跃 owner 存在，脚本会修复被误删的
     * pending 版本并删除负载；若没有 owner 却残留旧版 pending 值，则原子推进为新正常版本后再回源。
     * 缺失一律返回空串（负载为 JSON、版本为令牌，均不会是空串）。
     */
    private static final RedisScript<List> READ_ITEMS_WITH_VERSION = new DefaultRedisScript<>(
            "local clock = redis.call('TIME')\n"
                    + "local now = (clock[1] * 1000) + math.floor(clock[2] / 1000)\n"
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', now)\n"
                    + "local writers = redis.call('ZCARD', KEYS[3])\n"
                    + "if writers > 0 then\n"
                    + "  redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])\n"
                    + "  redis.call('DEL', KEYS[1])\n"
                    + "  return { '', ARGV[3] }\n"
                    + "end\n"
                    + "local version = redis.call('GET', KEYS[2])\n"
                    + "if version == false or string.sub(version, 1, string.len(ARGV[5])) == ARGV[5] then\n"
                    + "  redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])\n"
                    + "  redis.call('DEL', KEYS[1])\n"
                    + "  version = ARGV[1]\n"
                    + "end\n"
                    + "local payload = redis.call('GET', KEYS[1])\n"
                    + "return { payload or '', version or '' }", List.class);

    /**
     * 原子加入本事务 owner、进入 pending 并删除负载。ZSET member 是唯一事务 owner，score 使用 Redis TIME 计算，
     * 避免应用节点时钟漂移；每个 owner 独立续租，后一个事务不会覆盖前一个。
     */
    private static final RedisScript<Long> BEGIN_WRITE_WINDOW = new DefaultRedisScript<>(
            "local clock = redis.call('TIME')\n"
                    + "local now = (clock[1] * 1000) + math.floor(clock[2] / 1000)\n"
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', now)\n"
                    + "redis.call('ZADD', KEYS[3], now + ARGV[2], ARGV[1])\n"
                    + "redis.call('PEXPIRE', KEYS[3], ARGV[4])\n"
                    + "redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])\n"
                    + "redis.call('DEL', KEYS[1])\n"
                    + "return redis.call('ZCARD', KEYS[3])", Long.class);

    /**
     * 只有未过期的同 owner 才能续租；一旦丢失绝不重新加入。续租同时修复 pending 版本并删除负载。
     */
    private static final RedisScript<Long> RENEW_WRITE_WINDOW = new DefaultRedisScript<>(
            "local clock = redis.call('TIME')\n"
                    + "local now = (clock[1] * 1000) + math.floor(clock[2] / 1000)\n"
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', now)\n"
                    + "if redis.call('ZSCORE', KEYS[3], ARGV[1]) == false then\n"
                    + "  return 0\n"
                    + "end\n"
                    + "redis.call('ZADD', KEYS[3], now + ARGV[2], ARGV[1])\n"
                    + "redis.call('PEXPIRE', KEYS[3], ARGV[4])\n"
                    + "redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])\n"
                    + "redis.call('DEL', KEYS[1])\n"
                    + "return 1", Long.class);

    /**
     * 原子移除<b>自己的</b> owner 并删除负载。仅当 owner 集合为空时恢复正常版本；仍有其它写事务时继续 pending。
     * Lua 原子性保证任何读者都看不到「owner 尚存但版本已正常」的中间态。
     */
    private static final RedisScript<Long> COMPLETE_WRITE_WINDOW = new DefaultRedisScript<>(
            "local clock = redis.call('TIME')\n"
                    + "local now = (clock[1] * 1000) + math.floor(clock[2] / 1000)\n"
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', now)\n"
                    + "redis.call('ZREM', KEYS[3], ARGV[1])\n"
                    + "local remaining = redis.call('ZCARD', KEYS[3])\n"
                    + "redis.call('DEL', KEYS[1])\n"
                    + "if remaining == 0 then\n"
                    + "  redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])\n"
                    + "  redis.call('DEL', KEYS[3])\n"
                    + "else\n"
                    + "  redis.call('PEXPIRE', KEYS[3], ARGV[5])\n"
                    + "  redis.call('SET', KEYS[2], ARGV[4], 'PX', ARGV[5])\n"
                    + "end\n"
                    + "return remaining", Long.class);

    /**
     * 仅当版本既不处于写窗口、又与读穿开始时捕获的一致，才写入负载；同时把版本键 TTL 续到不短于负载 TTL，
     * 避免版本先过期导致有效负载被判为失效。ARGV[4] 传入写窗口前缀，避免与 Java 侧常量漂移。
     */
    private static final RedisScript<Long> PUT_IF_VERSION_UNCHANGED = new DefaultRedisScript<>(
            "local clock = redis.call('TIME')\n"
                    + "local now = (clock[1] * 1000) + math.floor(clock[2] / 1000)\n"
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', now)\n"
                    + "if redis.call('ZCARD', KEYS[3]) > 0 then\n"
                    + "  return 0\n"
                    + "end\n"
                    + "local current = redis.call('GET', KEYS[2])\n"
                    + "local expected = ARGV[2]\n"
                    + "local pending = ARGV[4]\n"
                    + "if current == false or expected == '' then\n"
                    + "  return 0\n"
                    + "end\n"
                    + "if string.sub(current, 1, string.len(pending)) == pending then\n"
                    + "  return 0\n"
                    + "end\n"
                    + "if current ~= expected then\n"
                    + "  return 0\n"
                    + "end\n"
                    + "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])\n"
                    + "redis.call('EXPIRE', KEYS[2], ARGV[5])\n"
                    + "return 1", Long.class);

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictItemMapper dictItemMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final ReferenceCacheInvalidator referenceCacheInvalidator;
    private final DictCacheProperties dictCacheProperties;
    private final ScheduledExecutorService writerLeaseRenewer =
            Executors.newSingleThreadScheduledExecutor(new WriterLeaseThreadFactory());

    @Override
    public List<DictTypeVO> listTypes() {
        return dictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>()
                        .orderByAsc(SysDictType::getSort)
                        .orderByAsc(SysDictType::getTypeCode))
                .stream()
                .map(this::toTypeVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createType(DictTypeSaveRequest request) {
        String typeCode = normalizeTypeCode(request.getTypeCode());
        if (existsTypeCode(typeCode, null)) {
            throw new BizException("字典类型编码已存在");
        }
        SysDictType entity = new SysDictType();
        entity.setTypeCode(typeCode);
        fillType(entity, request);
        dictTypeMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateType(Long id, DictTypeSaveRequest request) {
        SysDictType entity = requireType(id);
        String newTypeCode = normalizeTypeCode(request.getTypeCode());
        String oldTypeCode = normalizeTypeCode(entity.getTypeCode());
        if (!oldTypeCode.equals(newTypeCode) && existsTypeCode(newTypeCode, id)) {
            throw new BizException("字典类型编码已存在");
        }
        if (!oldTypeCode.equals(newTypeCode) && countItems(oldTypeCode) > 0) {
            throw new BizException("字典类型下存在字典项，不能修改编码");
        }
        entity.setTypeCode(newTypeCode);
        fillType(entity, request);
        dictTypeMapper.updateById(entity);
        if (!oldTypeCode.equals(newTypeCode)) {
            evictItemsCache(oldTypeCode);
        }
        evictItemsCache(newTypeCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        SysDictType entity = requireType(id);
        if (countItems(normalizeTypeCode(entity.getTypeCode())) > 0) {
            throw new BizException("字典类型下存在字典项，不能删除");
        }
        dictTypeMapper.deleteById(id);
        evictItemsCache(entity.getTypeCode());
    }

    @Override
    public List<DictItemVO> listItems(String typeCode, Boolean onlyEnabled) {
        String normalizedTypeCode = normalizeTypeCode(typeCode);
        boolean enabledOnly = onlyEnabled == null || onlyEnabled;
        if (!enabledOnly) {
            return queryItems(normalizedTypeCode, false);
        }
        // Phase 44（PG-M4 整改）：一次 EVAL 原子取回「负载 + 版本」。版本必须在数据库读<b>之前</b>拿到——只有这样，
        // 「本次读到的数据早于某次逐出」才能表现为「回填时版本已变」；放到读之后捕获等于把窗口保留下来。
        CachedItems cached = readCachedItems(normalizedTypeCode);
        if (cached.items() != null) {
            return cached.items();
        }
        List<DictItemVO> items = queryItems(normalizedTypeCode, true);
        putCachedItems(normalizedTypeCode, items, cached.version());
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createItem(DictItemSaveRequest request) {
        String typeCode = normalizeTypeCode(request.getTypeCode());
        requireType(typeCode);
        String itemCode = normalizeRequired(request.getItemCode(), "字典项编码不能为空");
        String yearVersion = normalizeYearVersion(request.getYearVersion());
        if (existsItem(typeCode, itemCode, yearVersion, null)) {
            throw new BizException("字典项编码在当前年度版本已存在");
        }
        SysDictItem entity = new SysDictItem();
        entity.setTypeCode(typeCode);
        entity.setItemCode(itemCode);
        entity.setYearVersion(yearVersion);
        fillItem(entity, request);
        dictItemMapper.insert(entity);
        evictItemsCache(typeCode);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long id, DictItemSaveRequest request) {
        SysDictItem entity = requireItem(id);
        String typeCode = normalizeTypeCode(request.getTypeCode());
        requireType(typeCode);
        String itemCode = normalizeRequired(request.getItemCode(), "字典项编码不能为空");
        String yearVersion = normalizeYearVersion(request.getYearVersion());
        if (existsItem(typeCode, itemCode, yearVersion, id)) {
            throw new BizException("字典项编码在当前年度版本已存在");
        }
        String oldTypeCode = normalizeTypeCode(entity.getTypeCode());
        entity.setTypeCode(typeCode);
        entity.setItemCode(itemCode);
        entity.setYearVersion(yearVersion);
        fillItem(entity, request);
        dictItemMapper.updateById(entity);
        if (!oldTypeCode.equals(typeCode)) {
            evictItemsCache(oldTypeCode);
        }
        evictItemsCache(typeCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        SysDictItem entity = requireItem(id);
        dictItemMapper.deleteById(id);
        evictItemsCache(entity.getTypeCode());
    }

    @Override
    public void evictItemsCache(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            return;
        }
        String normalized = normalizeTypeCode(typeCode);
        WriteWindowOwner owner = new WriteWindowOwner(normalized);
        // Phase 44（PG-M4 整改）：一次写的缓存失效分四段，见 ReferenceCacheInvalidator。
        // ①登记即进入写窗口：本地两个 Caffeine 与 Redis（事务 owner + pending 版本 + 删负载）自此都不再供应/接受缓存值，
        //   关掉「提交完成→逐出执行」之间仍供旧值的窗口，也杜绝本事务未提交值被读穿发布出去；
        // ②提交前强制校验并续租自己的 Redis owner；后台续租失败或 owner 丢失时抛异常阻止数据库提交，
        //   避免事务仍存活但固定 TTL 已过期而提前解除跨节点保护；
        // ③事务完成后（提交与回滚都做）逐条执行失效步骤，步骤间互相隔离——Redis 故障不得连带本地缓存也不失效；
        // ④无条件解除写窗口。
        // evictItemsCache 是所有字典增改删的唯一 choke point（createItem/updateItem×2/deleteItem/
        // updateType×2/deleteType 都调它），故按 typeCode 处理即覆盖全部写路径。
        // 手工调用（非 @CacheEvict）：本方法被同类的写方法内部调用（self-invocation），注解式 AOP 不会生效。
        referenceCacheInvalidator.invalidateAfterCompletion("dict:" + normalized,
                () -> beginWriteWindow(normalized, owner),
                owner::renewOrThrow,
                List.of(
                        ReferenceCacheInvalidator.step("caffeine:" + CacheConfig.DICT_LABELS,
                                () -> evictCaffeine(CacheConfig.DICT_LABELS, normalized)),
                        ReferenceCacheInvalidator.step("caffeine:" + CacheConfig.ORG_DICT_ITEMS,
                                () -> evictCaffeine(CacheConfig.ORG_DICT_ITEMS, normalized))),
                () -> endWriteWindow(normalized, owner));
    }

    /**
     * 进入本地与 Redis 写窗口。Redis 登记失败先由 owner 记录为 lost，提交前守卫再阻止事务提交；
     * begin 本身不抛出，确保 {@link ReferenceCacheInvalidator} 一定能登记负责清理本地窗口的完成回调。
     */
    private void beginWriteWindow(String typeCode, WriteWindowOwner owner) {
        for (String cacheName : List.of(CacheConfig.DICT_LABELS, CacheConfig.ORG_DICT_ITEMS)) {
            guardedCache(cacheName).ifPresent(cache -> cache.beginPendingInvalidation(typeCode));
        }
        owner.begin();
    }

    /**
     * 事务完成后只释放自己的 Redis owner；若 Redis 不可用，逐 owner 租约会在续租停止后到期，读/写 Lua 再原子清理。
     * 本地窗口必须在 finally 中解除，避免一次 Redis 故障把当前进程的 Caffeine 永久禁用。
     */
    private void endWriteWindow(String typeCode, WriteWindowOwner owner) {
        try {
            owner.complete();
        } finally {
            for (String cacheName : List.of(CacheConfig.DICT_LABELS, CacheConfig.ORG_DICT_ITEMS)) {
                guardedCache(cacheName).ifPresent(cache -> cache.endPendingInvalidation(typeCode));
            }
        }
    }

    private Optional<EpochGuardedCache> guardedCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        return cache instanceof EpochGuardedCache guarded ? Optional.of(guarded) : Optional.empty();
    }

    private void evictCaffeine(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    // Phase 44c（§7.3）：跨模块共用的 code→label 标签表（typeCode + 启用，不限年度版本；与原 4 处私有 dictLabels/
    // categoryLabels 语义一致）。返回不可变视图护住缓存对象；重复 itemCode（跨年度版本）取首个（保序）。
    @Override
    @Cacheable(cacheNames = CacheConfig.DICT_LABELS,
            key = "T(cn.edu.gpnu.platform.system.service.impl.DictServiceImpl).normalizeTypeCode(#typeCode)")
    public Map<String, String> dictLabels(String typeCode) {
        String canonicalTypeCode = normalizeTypeCode(typeCode);
        Map<String, String> labels = new LinkedHashMap<>();
        for (SysDictItem item : dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, canonicalTypeCode)
                .eq(SysDictItem::getStatus, ENABLED)
                .orderByAsc(SysDictItem::getSort))) {
            labels.putIfAbsent(item.getItemCode(), item.getItemValue());
        }
        return Collections.unmodifiableMap(labels);
    }

    // Phase 44c（§7.3）：GLOBAL 版启用字典项（code→item），对齐原 OrganizationServiceImpl.dictItems 查询。
    @Override
    @Cacheable(cacheNames = CacheConfig.ORG_DICT_ITEMS,
            key = "T(cn.edu.gpnu.platform.system.service.impl.DictServiceImpl).normalizeTypeCode(#typeCode)")
    public Map<String, SysDictItem> globalEnabledDictItems(String typeCode) {
        String canonicalTypeCode = normalizeTypeCode(typeCode);
        Map<String, SysDictItem> result = new LinkedHashMap<>();
        for (SysDictItem item : dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, canonicalTypeCode)
                .eq(SysDictItem::getYearVersion, DEFAULT_YEAR_VERSION)
                .eq(SysDictItem::getStatus, ENABLED)
                .orderByAsc(SysDictItem::getSort))) {
            result.putIfAbsent(item.getItemCode(), item);
        }
        return Collections.unmodifiableMap(result);
    }

    private List<DictItemVO> queryItems(String typeCode, boolean enabledOnly) {
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .orderByAsc(SysDictItem::getSort)
                .orderByAsc(SysDictItem::getItemCode);
        if (enabledOnly) {
            wrapper.eq(SysDictItem::getStatus, ENABLED);
        }
        return dictItemMapper.selectList(wrapper).stream().map(this::toItemVO).toList();
    }

    /** 读穿快照：{@code items} 为 null 表示未命中（含写窗口内不供应、版本戳不符），{@code version} 是回填 CAS 的期望值。 */
    private record CachedItems(@Nullable List<DictItemVO> items, String version) {
    }

    private CachedItems readCachedItems(String typeCode) {
        String payload;
        String version;
        try {
            List<?> result = redisTemplate.execute(READ_ITEMS_WITH_VERSION,
                    List.of(cacheKey(typeCode), versionKey(typeCode), writersKey(typeCode)),
                    UUID.randomUUID().toString(),
                    String.valueOf(CACHE_VERSION_TTL.toSeconds()),
                    PENDING_VERSION,
                    String.valueOf(writerRecoveryTtlMillis()),
                    PENDING_VERSION_PREFIX);
            payload = elementAt(result, 0);
            version = elementAt(result, 1);
        } catch (RuntimeException e) {
            // 读不到版本就不能安全回填：给一个绝不可能与真实版本相等的哨兵，使本次回填必被 CAS 拒绝。
            log.warn("读取字典项缓存失败，本次回源且不回填，typeCode={}", typeCode, e);
            return new CachedItems(null, UNREADABLE_VERSION_PREFIX + UUID.randomUUID());
        }
        if (isPendingVersion(version)) {
            // 写窗口内：版本是权威，负载即使还在也不得供应。
            return new CachedItems(null, version);
        }
        if (!StringUtils.hasText(payload) || !StringUtils.hasText(version)) {
            return new CachedItems(null, version);
        }
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            if (envelope.path(PAYLOAD_SCHEMA_FIELD).asInt(-1) != PAYLOAD_SCHEMA_VERSION
                    || !typeCode.equals(envelope.path(PAYLOAD_TYPE_CODE_FIELD).asText(null))
                    || !version.equals(envelope.path(PAYLOAD_VERSION_FIELD).asText(null))) {
                // 旧协议、大小写 identity 不一致或版本戳不符：属于升级前/已失效残留，绝不作为有效命中。
                return new CachedItems(null, version);
            }
            return new CachedItems(objectMapper.convertValue(envelope.path(PAYLOAD_ITEMS_FIELD),
                    new TypeReference<List<DictItemVO>>() {
                    }), version);
        } catch (Exception e) {
            return new CachedItems(null, version);
        }
    }

    private void putCachedItems(String typeCode, List<DictItemVO> items, String expectedVersion) {
        if (isPendingVersion(expectedVersion) || !StringUtils.hasText(expectedVersion)) {
            return; // 写窗口内或版本不可用时不回填；窗口结束后的读会重新装载
        }
        try {
            // 负载自带版本戳：即使某次删除未生效，残留负载也会因版本戳不符而永远不被当作有效命中。
            String payload = objectMapper.writeValueAsString(Map.of(
                    PAYLOAD_SCHEMA_FIELD, PAYLOAD_SCHEMA_VERSION,
                    PAYLOAD_TYPE_CODE_FIELD, typeCode,
                    PAYLOAD_VERSION_FIELD, expectedVersion,
                    PAYLOAD_ITEMS_FIELD, items));
            Long stored = redisTemplate.execute(PUT_IF_VERSION_UNCHANGED,
                    List.of(cacheKey(typeCode), versionKey(typeCode), writersKey(typeCode)),
                    payload,
                    expectedVersion,
                    String.valueOf(CACHE_TTL.toSeconds()),
                    PENDING_VERSION_PREFIX,
                    String.valueOf(CACHE_VERSION_TTL.toSeconds()));
            if (stored == null || stored == 0L) {
                // 装载期间进入过写窗口或版本已推进：本次结果可能来自提交前快照，丢弃即可（下次读重新装载）。
                log.debug("字典项缓存回填被版本守卫拒绝，typeCode={}", typeCode);
            }
        } catch (Exception e) {
            log.warn("回填字典项缓存失败，typeCode={}", typeCode, e);
        }
    }

    private static boolean isPendingVersion(@Nullable String version) {
        return version != null && version.startsWith(PENDING_VERSION_PREFIX);
    }

    private static String elementAt(@Nullable List<?> result, int index) {
        if (result == null || result.size() <= index) {
            return ABSENT_VERSION;
        }
        Object value = result.get(index);
        return value == null ? ABSENT_VERSION : String.valueOf(value);
    }

    // 包内可见 + static：供 DictCacheKeyTest 直接对抗性验证「负载键与版本键命名空间不相交」。
    static String cacheKey(String typeCode) {
        return CACHE_PREFIX + normalizeTypeCode(typeCode);
    }

    static String versionKey(String typeCode) {
        return CACHE_VERSION_PREFIX + normalizeTypeCode(typeCode);
    }

    static String writersKey(String typeCode) {
        return CACHE_WRITERS_PREFIX + normalizeTypeCode(typeCode);
    }

    private long writerLeaseMillis() {
        return dictCacheProperties.getWriterLease().toMillis();
    }

    private long writerRecoveryTtlMillis() {
        return Math.multiplyExact(writerLeaseMillis(), 2L);
    }

    /**
     * 单次失效登记对应一个独立 owner。后台线程只做续租；提交线程必须再次确认 owner，
     * 因而后台任何一次不确定状态都会 fail closed，而不会静默提交并失去跨节点保护。
     */
    private final class WriteWindowOwner {

        private final String typeCode;
        private final String token = UUID.randomUUID().toString();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean lost = new AtomicBoolean();
        private volatile ScheduledFuture<?> renewal;

        private WriteWindowOwner(String typeCode) {
            this.typeCode = typeCode;
        }

        private void begin() {
            try {
                Long ownerCount = redisTemplate.execute(BEGIN_WRITE_WINDOW,
                        ownerKeys(),
                        token,
                        String.valueOf(writerLeaseMillis()),
                        PENDING_VERSION,
                        String.valueOf(writerRecoveryTtlMillis()));
                if (ownerCount == null || ownerCount < 1L) {
                    markLost("Redis 未确认 owner 登记", null);
                    return;
                }
                renewal = writerLeaseRenewer.scheduleWithFixedDelay(
                        this::renewQuietly,
                        dictCacheProperties.getWriterRenewInterval().toMillis(),
                        dictCacheProperties.getWriterRenewInterval().toMillis(),
                        TimeUnit.MILLISECONDS);
            } catch (RuntimeException e) {
                markLost("Redis owner 登记失败", e);
            }
        }

        private void renewOrThrow() {
            if (closed.get() || lost.get()) {
                throw new BizException("字典缓存写窗口租约已丢失，事务已中止");
            }
            try {
                Long renewed = executeRenew();
                if (!Long.valueOf(1L).equals(renewed)) {
                    markLost("Redis owner 已过期或不存在", null);
                    throw new BizException("字典缓存写窗口租约已丢失，事务已中止");
                }
            } catch (BizException e) {
                throw e;
            } catch (RuntimeException e) {
                markLost("提交前续租 Redis owner 失败", e);
                throw new BizException("字典缓存写窗口续租失败，事务已中止");
            }
        }

        private void renewQuietly() {
            if (closed.get() || lost.get()) {
                return;
            }
            try {
                Long renewed = executeRenew();
                if (!Long.valueOf(1L).equals(renewed)) {
                    markLost("后台续租发现 Redis owner 已过期或不存在", null);
                }
            } catch (RuntimeException e) {
                markLost("后台续租 Redis owner 失败", e);
            }
        }

        private Long executeRenew() {
            return redisTemplate.execute(RENEW_WRITE_WINDOW,
                    ownerKeys(),
                    token,
                    String.valueOf(writerLeaseMillis()),
                    PENDING_VERSION,
                    String.valueOf(writerRecoveryTtlMillis()));
        }

        private void complete() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> scheduled = renewal;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            try {
                Long remaining = redisTemplate.execute(COMPLETE_WRITE_WINDOW,
                        ownerKeys(),
                        token,
                        UUID.randomUUID().toString(),
                        String.valueOf(CACHE_VERSION_TTL.toSeconds()),
                        PENDING_VERSION,
                        String.valueOf(writerRecoveryTtlMillis()));
                if (remaining == null) {
                    log.error("完成字典缓存写窗口未收到 Redis 确认，owner 将按租约回收: typeCode={}, owner={}",
                            typeCode, token);
                }
            } catch (RuntimeException e) {
                log.error("完成字典缓存写窗口失败（Redis 侧），owner 将按租约回收: typeCode={}, owner={}",
                        typeCode, token, e);
            }
        }

        private List<String> ownerKeys() {
            return List.of(cacheKey(typeCode), versionKey(typeCode), writersKey(typeCode));
        }

        private void markLost(String reason, @Nullable RuntimeException cause) {
            if (!lost.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> scheduled = renewal;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            if (cause == null) {
                log.error("{}: typeCode={}, owner={}", reason, typeCode, token);
            } else {
                log.error("{}: typeCode={}, owner={}", reason, typeCode, token, cause);
            }
        }
    }

    @PreDestroy
    void shutdownWriterLeaseRenewer() {
        writerLeaseRenewer.shutdownNow();
    }

    private static final class WriterLeaseThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "dict-cache-writer-lease-renewer");
            thread.setDaemon(true);
            return thread;
        }
    }

    private boolean existsTypeCode(String typeCode, Long excludeId) {
        // 唯一键 uk_sys_dict_type_code 不含 deleted：必须含软删行一并检查（对齐 OrganizationServiceImpl
        // 的 countByCodeIncludingDeleted 做法），否则软删后同码重建会绕过本检查、直接撞库裸抛 DuplicateKeyException（Phase43.4）。
        return dictTypeMapper.countByTypeCodeIncludingDeleted(typeCode, excludeId) > 0;
    }

    private boolean existsItem(String typeCode, String itemCode, String yearVersion, Long excludeId) {
        // 唯一键 uk_sys_dict_item_type_code_year 不含 deleted，同上（Phase43.4）。
        return dictItemMapper.countByItemIncludingDeleted(typeCode, itemCode, yearVersion, excludeId) > 0;
    }

    private Long countItems(String typeCode) {
        return dictItemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode));
    }

    private SysDictType requireType(Long id) {
        if (id == null) {
            throw new BizException("字典类型ID不能为空");
        }
        SysDictType entity = dictTypeMapper.selectById(id);
        if (entity == null) {
            throw new BizException("字典类型不存在");
        }
        return entity;
    }

    private void requireType(String typeCode) {
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTypeCode, typeCode));
        if (count == 0) {
            throw new BizException("字典类型不存在");
        }
    }

    private SysDictItem requireItem(Long id) {
        if (id == null) {
            throw new BizException("字典项ID不能为空");
        }
        SysDictItem entity = dictItemMapper.selectById(id);
        if (entity == null) {
            throw new BizException("字典项不存在");
        }
        return entity;
    }

    private void fillType(SysDictType entity, DictTypeSaveRequest request) {
        entity.setTypeName(normalizeRequired(request.getTypeName(), "字典类型名称不能为空"));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setSort(defaultInt(request.getSort(), 0));
        entity.setStatus(defaultInt(request.getStatus(), ENABLED));
    }

    private void fillItem(SysDictItem entity, DictItemSaveRequest request) {
        entity.setItemValue(normalizeRequired(request.getItemValue(), "字典项值不能为空"));
        entity.setParentCode(trimToNull(request.getParentCode()));
        entity.setSort(defaultInt(request.getSort(), 0));
        entity.setStatus(defaultInt(request.getStatus(), ENABLED));
        entity.setExtJson(normalizeExtJson(request.getExtJson()));
    }

    private DictTypeVO toTypeVO(SysDictType entity) {
        DictTypeVO vo = new DictTypeVO();
        vo.setId(entity.getId());
        vo.setTypeCode(normalizeTypeCode(entity.getTypeCode()));
        vo.setTypeName(entity.getTypeName());
        vo.setDescription(entity.getDescription());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private DictItemVO toItemVO(SysDictItem entity) {
        DictItemVO vo = new DictItemVO();
        vo.setId(entity.getId());
        vo.setTypeCode(normalizeTypeCode(entity.getTypeCode()));
        vo.setItemCode(entity.getItemCode());
        vo.setItemValue(entity.getItemValue());
        vo.setParentCode(entity.getParentCode());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setYearVersion(entity.getYearVersion());
        vo.setExtJson(entity.getExtJson());
        return vo;
    }

    private String normalizeRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(message);
        }
        return trimmed;
    }

    /**
     * 字典类型的唯一身份：trim、后端字符集校验、再以 {@link Locale#ROOT} 转小写。
     * public 是为了让两处 {@code @Cacheable} 的 SpEL 与方法体、Redis 键复用同一个实现。
     */
    public static String normalizeTypeCode(String typeCode) {
        String normalized = trimToNullStatic(typeCode);
        if (normalized == null) {
            throw new BizException("字典类型编码不能为空");
        }
        if (!TYPE_CODE_PATTERN.matcher(normalized).matches()) {
            throw new BizException("字典类型编码仅支持英文、数字、下划线，且不超过64位");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String trimToNullStatic(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeYearVersion(String yearVersion) {
        String normalized = trimToNull(yearVersion);
        return normalized == null ? DEFAULT_YEAR_VERSION : normalized;
    }

    private String normalizeExtJson(String extJson) {
        String normalized = trimToNull(extJson);
        if (normalized == null) {
            return null;
        }
        try {
            objectMapper.readTree(normalized);
            return normalized;
        } catch (Exception e) {
            throw new BizException("扩展JSON格式不正确");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Integer defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
