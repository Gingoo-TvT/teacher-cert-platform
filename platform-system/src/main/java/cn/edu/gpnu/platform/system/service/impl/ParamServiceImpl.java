package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.config.CacheConfig;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ParamServiceImpl implements ParamService {

    private final SysParamMapper sysParamMapper;

    // Phase 44c（§7.3）：参数读多写少，缓存 getX 结果（key 含方法名+参数键+默认值，故不同默认值/不同 getter 互不串味）。
    // 生产唯一写路径 SystemManagementServiceImpl.updateParam 以 @CacheEvict(allEntries) 逐出整个 sysParam 缓存。
    @Override
    @Cacheable(cacheNames = CacheConfig.SYS_PARAM,
            key = "#root.methodName + ':' + #key + ':' + #defaultValue")
    public int getInt(String key, int defaultValue) {
        String value = sysParamMapper.selectValue(key);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.SYS_PARAM,
            key = "#root.methodName + ':' + #key + ':' + #defaultValue")
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = sysParamMapper.selectValue(key);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase()) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> defaultValue;
        };
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.SYS_PARAM,
            key = "#root.methodName + ':' + #key + ':' + #defaultValue")
    public String getString(String key, String defaultValue) {
        String value = sysParamMapper.selectValue(key);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
