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
}
