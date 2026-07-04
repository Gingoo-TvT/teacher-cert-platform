package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.config.CacheConfig;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private static final int ENABLED = 1;
    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";
    private static final String CACHE_PREFIX = "dict:items:";
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictItemMapper dictItemMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

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
        String typeCode = normalizeRequired(request.getTypeCode(), "字典类型编码不能为空");
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
        String newTypeCode = normalizeRequired(request.getTypeCode(), "字典类型编码不能为空");
        if (!entity.getTypeCode().equals(newTypeCode) && existsTypeCode(newTypeCode, id)) {
            throw new BizException("字典类型编码已存在");
        }
        String oldTypeCode = entity.getTypeCode();
        if (!oldTypeCode.equals(newTypeCode) && countItems(oldTypeCode) > 0) {
            throw new BizException("字典类型下存在字典项，不能修改编码");
        }
        entity.setTypeCode(newTypeCode);
        fillType(entity, request);
        dictTypeMapper.updateById(entity);
        evictItemsCache(oldTypeCode);
        evictItemsCache(newTypeCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        SysDictType entity = requireType(id);
        if (countItems(entity.getTypeCode()) > 0) {
            throw new BizException("字典类型下存在字典项，不能删除");
        }
        dictTypeMapper.deleteById(id);
        evictItemsCache(entity.getTypeCode());
    }

    @Override
    public List<DictItemVO> listItems(String typeCode, Boolean onlyEnabled) {
        String normalizedTypeCode = normalizeRequired(typeCode, "字典类型编码不能为空");
        boolean enabledOnly = onlyEnabled == null || onlyEnabled;
        if (enabledOnly) {
            List<DictItemVO> cached = getCachedItems(normalizedTypeCode);
            if (cached != null) {
                return cached;
            }
        }
        List<DictItemVO> items = queryItems(normalizedTypeCode, enabledOnly);
        if (enabledOnly) {
            putCachedItems(normalizedTypeCode, items);
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createItem(DictItemSaveRequest request) {
        String typeCode = normalizeRequired(request.getTypeCode(), "字典类型编码不能为空");
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
        String typeCode = normalizeRequired(request.getTypeCode(), "字典类型编码不能为空");
        requireType(typeCode);
        String itemCode = normalizeRequired(request.getItemCode(), "字典项编码不能为空");
        String yearVersion = normalizeYearVersion(request.getYearVersion());
        if (existsItem(typeCode, itemCode, yearVersion, id)) {
            throw new BizException("字典项编码在当前年度版本已存在");
        }
        String oldTypeCode = entity.getTypeCode();
        entity.setTypeCode(typeCode);
        entity.setItemCode(itemCode);
        entity.setYearVersion(yearVersion);
        fillItem(entity, request);
        dictItemMapper.updateById(entity);
        evictItemsCache(oldTypeCode);
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
        if (StringUtils.hasText(typeCode)) {
            String normalized = typeCode.trim();
            redisTemplate.delete(cacheKey(normalized));
            // Phase 44c（§7.3）：同步逐出进程内 Caffeine 参考缓存。evictItemsCache 是所有字典增改删的唯一 choke point
            // （createItem/updateItem×2/deleteItem/updateType×2/deleteType 都调它），故按 typeCode 逐出即覆盖全部写路径。
            // 手工逐出（非 @CacheEvict）：本方法被同类的写方法内部调用（self-invocation），注解式 AOP 不会生效。
            evictCaffeine(CacheConfig.DICT_LABELS, normalized);
            evictCaffeine(CacheConfig.ORG_DICT_ITEMS, normalized);
        }
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
    @Cacheable(cacheNames = CacheConfig.DICT_LABELS, key = "#typeCode")
    public Map<String, String> dictLabels(String typeCode) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (SysDictItem item : dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getStatus, ENABLED)
                .orderByAsc(SysDictItem::getSort))) {
            labels.putIfAbsent(item.getItemCode(), item.getItemValue());
        }
        return Collections.unmodifiableMap(labels);
    }

    // Phase 44c（§7.3）：GLOBAL 版启用字典项（code→item），对齐原 OrganizationServiceImpl.dictItems 查询。
    @Override
    @Cacheable(cacheNames = CacheConfig.ORG_DICT_ITEMS, key = "#typeCode")
    public Map<String, SysDictItem> globalEnabledDictItems(String typeCode) {
        Map<String, SysDictItem> result = new LinkedHashMap<>();
        for (SysDictItem item : dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
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

    private List<DictItemVO> getCachedItems(String typeCode) {
        String json = redisTemplate.opsForValue().get(cacheKey(typeCode));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<DictItemVO>>() {
            });
        } catch (Exception e) {
            redisTemplate.delete(cacheKey(typeCode));
            return null;
        }
    }

    private void putCachedItems(String typeCode, List<DictItemVO> items) {
        try {
            redisTemplate.opsForValue().set(cacheKey(typeCode), objectMapper.writeValueAsString(items), CACHE_TTL);
        } catch (Exception e) {
            redisTemplate.delete(cacheKey(typeCode));
        }
    }

    private String cacheKey(String typeCode) {
        return CACHE_PREFIX + typeCode;
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
        vo.setTypeCode(entity.getTypeCode());
        vo.setTypeName(entity.getTypeName());
        vo.setDescription(entity.getDescription());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private DictItemVO toItemVO(SysDictItem entity) {
        DictItemVO vo = new DictItemVO();
        vo.setId(entity.getId());
        vo.setTypeCode(entity.getTypeCode());
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
