package cn.edu.gpnu.platform.business.video.support;

import java.util.Arrays;

public enum VideoUploadStatus {
    UPLOADING,
    MERGED,
    VALIDATION_FAILED,
    FAST_HIT;

    public static VideoUploadStatus of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(value))
                .findFirst()
                .orElse(UPLOADING);
    }
}
