package cn.edu.gpnu.platform.business.testresult.support;

import cn.edu.gpnu.platform.common.exception.BizException;

import java.util.Arrays;

public enum TestConfirmStatus {
    PENDING("待确认"),
    CONFIRMED("已确认");

    private final String label;

    TestConfirmStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean editable() {
        return this == PENDING;
    }

    public static TestConfirmStatus of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BizException("未知测试确认状态: " + value));
    }
}
