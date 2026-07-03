package cn.edu.gpnu.platform.business.video.support;

import java.util.Arrays;

public enum VideoUploadStatus {
    UPLOADING,
    MERGING,
    MERGED,
    VALIDATION_FAILED,
    FAST_HIT;

    public static VideoUploadStatus of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知视频上传状态: " + value));
    }
}
