package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.common.exception.BizException;

import java.util.Arrays;

public enum ExchangeExportType {
    STANDARD,
    FULL_REVIEW,
    CERT_SUMMARY,
    ERROR,
    ATTACHMENT_LIST;

    public static ExchangeExportType of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BizException("未知导出类型: " + value));
    }
}
