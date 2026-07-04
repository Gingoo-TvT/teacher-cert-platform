package cn.edu.gpnu.platform.business.training.support;

import java.util.Arrays;

public enum TrainingStatus {
    DRAFT("草稿"),
    FIRST_REVIEW("待初审"),
    FIRST_REJECTED("初审退回"),
    SECOND_REVIEW("待复审"),
    SECOND_REJECTED("复审退回"),
    PASSED("复审通过"),
    FAILED("不合格");

    private final String label;

    TrainingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean editable() {
        return this == DRAFT || this == FIRST_REJECTED || this == SECOND_REJECTED;
    }

    public static TrainingStatus of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new cn.edu.gpnu.platform.common.exception.BizException("未知培养状态: " + value));
    }
}
