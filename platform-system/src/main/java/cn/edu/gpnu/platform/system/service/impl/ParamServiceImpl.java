package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ParamServiceImpl implements ParamService {

    private final SysParamMapper sysParamMapper;

    @Override
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
    public String getString(String key, String defaultValue) {
        String value = sysParamMapper.selectValue(key);
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
