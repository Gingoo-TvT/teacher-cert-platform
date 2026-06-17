package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.common.exception.BizException;

import java.util.Arrays;

public enum ExchangeBatchStatus {
    PREVALIDATED,
    IMPORTED,
    ROLLED_BACK,
    PARTIAL_ROLLBACK,
    FAILED,
    EXPORTED;

    public static ExchangeBatchStatus of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BizException("未知批次状态: " + value));
    }
}
