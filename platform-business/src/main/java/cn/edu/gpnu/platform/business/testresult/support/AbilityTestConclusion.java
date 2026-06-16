package cn.edu.gpnu.platform.business.testresult.support;

import cn.edu.gpnu.platform.common.exception.BizException;

import java.util.Arrays;

public enum AbilityTestConclusion {
    QUALIFIED("qualified", "合格", true),
    UNQUALIFIED("unqualified", "不合格", false),
    EXEMPTED("exempted", "免考", true),
    PENDING_CONFIRM("pending_confirm", "待确认", false);

    private final String code;
    private final String label;
    private final boolean validForCertificate;

    AbilityTestConclusion(String code, String label, boolean validForCertificate) {
        this.code = code;
        this.label = label;
        this.validForCertificate = validForCertificate;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean validForCertificate() {
        return validForCertificate;
    }

    public static AbilityTestConclusion of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(value))
                .findFirst()
                .orElseThrow(() -> new BizException("未知测试结论: " + value));
    }
}
