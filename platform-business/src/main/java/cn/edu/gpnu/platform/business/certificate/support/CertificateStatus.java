package cn.edu.gpnu.platform.business.certificate.support;

import cn.edu.gpnu.platform.common.exception.BizException;

import java.util.Arrays;

public enum CertificateStatus {
    WAIT_GENERATE("待生成"),
    GENERATED("已生成"),
    ISSUED("已签发"),
    EXPORTED("已导出"),
    ARCHIVED("已归档"),
    VOIDED("已作废"),
    REISSUED("已重开");

    private final String label;

    CertificateStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean locked() {
        return this != WAIT_GENERATE && this != VOIDED && this != REISSUED;
    }

    public static CertificateStatus of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BizException("未知证书状态: " + value));
    }
}
