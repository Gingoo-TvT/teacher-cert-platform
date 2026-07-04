package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.config.CacheConfig;
import cn.edu.gpnu.platform.system.entity.SysRegion;
import cn.edu.gpnu.platform.system.mapper.SysRegionMapper;
import cn.edu.gpnu.platform.system.service.RegionService;
import cn.edu.gpnu.platform.system.vo.RegionPathVO;
import cn.edu.gpnu.platform.system.vo.RegionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private static final int ENABLED = 1;
    private static final int PROVINCE_LEVEL = 1;
    private static final int COUNTY_LEVEL = 3;

    private final SysRegionMapper regionMapper;

    // Phase 44c（§7.3）：行政区划为静态基础数据、应用层无运行时写路径，缓存读方法、仅靠 TTL 兜底（无写即无需逐出）。
    // path() 原按层级 while 循环逐级查库 → 整条 path 结果按 code 记忆化。
    @Override
    @Cacheable(cacheNames = CacheConfig.REGION_CHILDREN,
            key = "T(org.springframework.util.StringUtils).hasText(#parentCode) ? #parentCode.trim() : 'ROOT'")
    public List<RegionVO> children(String parentCode) {
        LambdaQueryWrapper<SysRegion> wrapper = new LambdaQueryWrapper<SysRegion>()
                .eq(SysRegion::getStatus, ENABLED)
                .orderByAsc(SysRegion::getSort)
                .orderByAsc(SysRegion::getCode);
        if (StringUtils.hasText(parentCode)) {
            String normalizedParent = normalizeCode(parentCode);
            requireRegion(normalizedParent);
            wrapper.eq(SysRegion::getParentCode, normalizedParent);
        } else {
            wrapper.eq(SysRegion::getLevel, PROVINCE_LEVEL);
        }
        return regionMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.REGION_PATH, key = "#code",
            condition = "T(org.springframework.util.StringUtils).hasText(#code)")
    public RegionPathVO path(String code) {
        String normalizedCode = normalizeCode(code);
        SysRegion current = requireRegion(normalizedCode);
        List<SysRegion> reversed = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        reversed.add(current);
        visited.add(current.getCode());
        while (StringUtils.hasText(current.getParentCode())) {
            if (!visited.add(current.getParentCode())) {
                throw new BizException("行政区划父级链路存在循环");
            }
            current = requireRegion(current.getParentCode());
            reversed.add(current);
        }
        Collections.reverse(reversed);
        validateLevelPath(reversed);
        RegionPathVO vo = new RegionPathVO();
        vo.setCode(normalizedCode);
        vo.setNodes(reversed.stream().map(this::toVO).toList());
        vo.setFullName(String.join("", reversed.stream().map(SysRegion::getName).toList()));
        return vo;
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.REGION_FULL_NAME, key = "#code",
            condition = "T(org.springframework.util.StringUtils).hasText(#code)")
    public String fullName(String code) {
        return path(code).getFullName();
    }

    @Override
    public void validateTriplet(String provinceCode, String cityCode, String countyCode) {
        SysRegion province = requireRegion(normalizeCode(provinceCode));
        SysRegion city = requireRegion(normalizeCode(cityCode));
        SysRegion county = requireRegion(normalizeCode(countyCode));
        if (!Integer.valueOf(PROVINCE_LEVEL).equals(province.getLevel())
                || city.getLevel() == null || city.getLevel() != 2
                || county.getLevel() == null || county.getLevel() != COUNTY_LEVEL) {
            throw new BizException("生源地省市区层级不正确");
        }
        if (!province.getCode().equals(city.getParentCode()) || !city.getCode().equals(county.getParentCode())) {
            throw new BizException("生源地省市区父子关系不匹配");
        }
    }

    private SysRegion requireRegion(String code) {
        SysRegion region = regionMapper.selectOne(new LambdaQueryWrapper<SysRegion>()
                .eq(SysRegion::getCode, code)
                .eq(SysRegion::getStatus, ENABLED)
                .last("LIMIT 1"));
        if (region == null) {
            throw new BizException("行政区划不存在或已停用");
        }
        return region;
    }

    private RegionVO toVO(SysRegion region) {
        RegionVO vo = new RegionVO();
        vo.setId(region.getId());
        vo.setCode(region.getCode());
        vo.setName(region.getName());
        vo.setParentCode(region.getParentCode());
        vo.setLevel(region.getLevel());
        vo.setSort(region.getSort());
        vo.setStatus(region.getStatus());
        vo.setLeaf(region.getLevel() != null && region.getLevel() >= COUNTY_LEVEL);
        return vo;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("行政区划代码不能为空");
        }
        String normalized = code.trim();
        if (!normalized.matches("\\d{6}")) {
            throw new BizException("行政区划代码必须为6位数字");
        }
        return normalized;
    }

    private void validateLevelPath(List<SysRegion> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            Integer level = nodes.get(i).getLevel();
            if (level == null || level != i + 1) {
                throw new BizException("行政区划父级链路层级不连续");
            }
        }
    }
}
