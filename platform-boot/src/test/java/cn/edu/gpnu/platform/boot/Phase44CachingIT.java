package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.dto.DictItemSaveRequest;
import cn.edu.gpnu.platform.system.dto.DictTypeSaveRequest;
import cn.edu.gpnu.platform.system.dto.SysParamUpdateRequest;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.service.DictService;
import cn.edu.gpnu.platform.system.service.ParamService;
import cn.edu.gpnu.platform.system.service.RegionService;
import cn.edu.gpnu.platform.system.service.SystemManagementService;
import cn.edu.gpnu.platform.system.vo.RegionPathVO;
import cn.edu.gpnu.platform.system.vo.RegionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 44c（§7.3 参考数据缓存）：验证 <b>写时逐出的正确性</b>——若某写路径漏逐出，缓存会继续供应旧参考数据、
 * 断言即失败。故本类全绿＝已覆盖的逐出路径确实生效（这正是「缓存不供旧值」的直接证据）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>参数：经生产 {@code SystemManagementService.updateParam}（{@code @CacheEvict(sysParam)}）改值后，
 *       {@code ParamService.getInt} 立即反映新值（缓存已逐出）。</li>
 *   <li>字典标签：改字典项值 / 停用字典项后，{@code DictService.dictLabels} 与 {@code globalEnabledDictItems}
 *       立即反映（{@code evictItemsCache} 同步逐出 Caffeine）。</li>
 *   <li>行政区划：无写路径，冒烟验证缓存读一致、{@code fullName} 与 {@code path().getFullName()} 相符。</li>
 * </ul>
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase44CachingIT {

    private static final String PARAM_KEY = "video.diffThreshold";
    private static final String TEST_TYPE_CODE = "p44_cache_it";

    @Autowired
    private ParamService paramService;

    @Autowired
    private SystemManagementService systemManagementService;

    @Autowired
    private DictService dictService;

    @Autowired
    private RegionService regionService;

    @Autowired
    private SysParamMapper paramMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void updateParamThroughServiceEvictsCachedValue() {
        SysParam original = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, PARAM_KEY).last("LIMIT 1"));
        assertThat(original).as("editable seed param %s should exist", PARAM_KEY).isNotNull();
        Long id = original.getId();
        String originalValue = original.getParamValue();

        int originalInt = paramService.getInt(PARAM_KEY, -424242); // 读入缓存
        int changed = originalInt + 1;
        try {
            SysParamUpdateRequest req = new SysParamUpdateRequest();
            req.setParamValue(String.valueOf(changed));
            systemManagementService.updateParam(id, req); // 生产写路径 → @CacheEvict(sysParam)

            // 若 updateParam 漏逐出，这里会命中旧缓存返回 originalInt → 断言失败。
            assertThat(paramService.getInt(PARAM_KEY, -424242)).isEqualTo(changed);
        } finally {
            SysParamUpdateRequest restore = new SysParamUpdateRequest();
            restore.setParamValue(originalValue);
            systemManagementService.updateParam(id, restore);
        }
        assertThat(paramService.getInt(PARAM_KEY, -424242)).isEqualTo(originalInt);
    }

    @Test
    void dictItemWriteEvictsLabelAndItemCaches() {
        hardDeleteTestDict();
        try {
            dictService.createType(dictType(TEST_TYPE_CODE));
            Long itemId = dictService.createItem(dictItem(TEST_TYPE_CODE, "ALPHA", "V1", 1));

            // 读入缓存
            assertThat(dictService.dictLabels(TEST_TYPE_CODE)).containsEntry("ALPHA", "V1");
            assertThat(dictService.globalEnabledDictItems(TEST_TYPE_CODE)).containsKey("ALPHA");

            // 改值 → updateItem 内 evictItemsCache 逐出 Caffeine；漏逐出则仍读到 "V1"
            dictService.updateItem(itemId, dictItem(TEST_TYPE_CODE, "ALPHA", "V2", 1));
            assertThat(dictService.dictLabels(TEST_TYPE_CODE)).containsEntry("ALPHA", "V2");
            Map<String, SysDictItem> items = dictService.globalEnabledDictItems(TEST_TYPE_CODE);
            assertThat(items).containsKey("ALPHA");
            assertThat(items.get("ALPHA").getItemValue()).isEqualTo("V2");

            // 停用 → 启用项标签表/项表都应立即不含该项
            dictService.updateItem(itemId, dictItem(TEST_TYPE_CODE, "ALPHA", "V2", 0));
            assertThat(dictService.dictLabels(TEST_TYPE_CODE)).doesNotContainKey("ALPHA");
            assertThat(dictService.globalEnabledDictItems(TEST_TYPE_CODE)).doesNotContainKey("ALPHA");
        } finally {
            hardDeleteTestDict();
        }
    }

    @Test
    void regionReadCacheIsWiredAndConsistent() {
        List<RegionVO> provinces = regionService.children(null);
        assertThat(regionService.children(null)).hasSameSizeAs(provinces); // 二次读缓存命中、结果一致

        RegionPathVO path = regionService.path("440000");
        assertThat(path.getFullName()).isNotBlank();
        assertThat(regionService.fullName("440000")).isEqualTo(path.getFullName());
    }

    private DictTypeSaveRequest dictType(String typeCode) {
        DictTypeSaveRequest req = new DictTypeSaveRequest();
        req.setTypeCode(typeCode);
        req.setTypeName("Phase44 缓存测试类型");
        req.setStatus(1);
        return req;
    }

    private DictItemSaveRequest dictItem(String typeCode, String itemCode, String itemValue, int status) {
        DictItemSaveRequest req = new DictItemSaveRequest();
        req.setTypeCode(typeCode);
        req.setItemCode(itemCode);
        req.setItemValue(itemValue);
        req.setYearVersion("GLOBAL");
        req.setStatus(status);
        return req;
    }

    private void hardDeleteTestDict() {
        // 物理删除（绕过 @TableLogic 软删），保证跨运行可重复；否则软删残行会撞 existsTypeCode/existsItem。
        jdbcTemplate.update("DELETE FROM sys_dict_item WHERE type_code = ?", TEST_TYPE_CODE);
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE type_code = ?", TEST_TYPE_CODE);
    }
}
