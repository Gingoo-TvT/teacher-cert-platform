package cn.edu.gpnu.platform.system.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Phase 44c（§7.3 参考数据缓存）：进程内 Caffeine 缓存（读多写少的参考数据），读穿 {@code @Cacheable}、写时逐出。
 *
 * <p>缓存对象与逐出触发（<b>逐出的正确性是本改动的关键</b>，任一漏逐出＝旧参考数据被继续供应）：
 * <ul>
 *   <li>{@link #SYS_PARAM}：{@code ParamServiceImpl.getInt/getBoolean/getString}——证书生成等每请求读约 6 个参数，
 *       每次原为一次 DB 命中。<b>逐出</b>：{@code SystemManagementServiceImpl.updateParam}（唯一生产写路径，
 *       {@code @CacheEvict(allEntries)}）。测试 {@code resetParam} 越过 service 直接改 mapper，故其 helper 亦清该缓存。</li>
 *   <li>{@link #REGION_CHILDREN}/{@link #REGION_PATH}/{@link #REGION_FULL_NAME}：{@code RegionServiceImpl} 的
 *       {@code children/path/fullName}（{@code path} 原按层级 while 循环逐级查库）。行政区划为迁移灌入的静态基础数据，
 *       <b>应用层无任何运行时写路径</b>（{@code SysRegionMapper} 无 insert/update/delete 调用），故仅靠 TTL 兜底、无需逐出。</li>
 *   <li>{@link #DICT_LABELS}：{@code DictServiceImpl.dictLabels(typeCode)}（跨 Exemption/Material/TestResult/Stats 的
 *       code→label 标签表，原每次 toVO/校验一次 DB）。<b>逐出</b>：字典项/类型任一增改删都经
 *       {@code DictServiceImpl.evictItemsCache(typeCode)} 逐出（按 typeCode）。</li>
 *   <li>{@link #ORG_DICT_ITEMS}：{@code DictServiceImpl.globalEnabledDictItems(typeCode)}（GLOBAL 版启用字典项，
 *       {@code OrganizationServiceImpl} 专业列表原 ~3N+1 逐条查同一字典）。<b>逐出</b>：同 {@code evictItemsCache}。</li>
 * </ul>
 *
 * <p>DataScope（{@code DataScopeServiceImpl.resolve} 的 per-(user,permission) 授权解析）<b>刻意未缓存</b>：其失效面
 * 覆盖全部 RBAC 写（用户-角色 / 角色-权限+范围 / 用户数据范围 / 角色增删——角色级变更还会扇出到持该角色的所有用户），
 * 无法在不引入过度/遗漏逐出的前提下可靠覆盖；授权读缓存一旦陈旧＝越权（安全缺陷）。故按规格诚实推迟。
 *
 * <p>{@code @Cacheable} 方法返回不可变视图（{@code dictLabels}/{@code globalEnabledDictItems} 返回
 * {@code unmodifiableMap}；调用方各自复制后再改），杜绝共享缓存对象被调用方原地改写而污染。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SYS_PARAM = "sysParam";
    public static final String REGION_CHILDREN = "regionChildren";
    public static final String REGION_PATH = "regionPath";
    public static final String REGION_FULL_NAME = "regionFullName";
    public static final String DICT_LABELS = "dictLabels";
    public static final String ORG_DICT_ITEMS = "orgDictItems";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // allowNullValues 默认 true：参数缺失（selectValue 返回 null）也可缓存，避免重复空查；写时逐出兜底正确性。
        manager.registerCustomCache(SYS_PARAM,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(30)).maximumSize(1_000).build());
        manager.registerCustomCache(REGION_CHILDREN,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6)).maximumSize(4_096).build());
        manager.registerCustomCache(REGION_PATH,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6)).maximumSize(8_192).build());
        manager.registerCustomCache(REGION_FULL_NAME,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6)).maximumSize(8_192).build());
        manager.registerCustomCache(DICT_LABELS,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(12)).maximumSize(512).build());
        manager.registerCustomCache(ORG_DICT_ITEMS,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(12)).maximumSize(512).build());
        return manager;
    }
}
