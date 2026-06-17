package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.common.exception.BizException;

import java.util.Arrays;

public enum ImportStrategy {
    INSERT_ONLY,
    OVERWRITE,
    SKIP_DUPLICATE,
    UPDATE_EMPTY;

    public static ImportStrategy of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BizException("未知导入策略: " + value));
    }
}
