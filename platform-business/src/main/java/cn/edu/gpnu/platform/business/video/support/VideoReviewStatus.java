package cn.edu.gpnu.platform.business.video.support;

import java.util.Arrays;

public enum VideoReviewStatus {
    WAIT_UPLOAD("待上传"),
    VALIDATING("校验中"),
    VALIDATION_FAILED("校验失败"),
    WAIT_REVIEW("待评审"),
    REVIEWING("评审中"),
    NEED_REVIEW("需复评"),
    REVIEW_COMPLETED("评审完成"),
    CONFIRMED("已确认");

    private final String label;

    VideoReviewStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean locked() {
        return this == REVIEW_COMPLETED || this == CONFIRMED;
    }

    public boolean reuploadable() {
        return this == WAIT_UPLOAD
                || this == VALIDATING
                || this == VALIDATION_FAILED
                || this == WAIT_REVIEW;
    }

    public static VideoReviewStatus of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知视频评审状态: " + value));
    }
}
