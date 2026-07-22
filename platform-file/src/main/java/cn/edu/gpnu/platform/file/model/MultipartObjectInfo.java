package cn.edu.gpnu.platform.file.model;

import java.util.Map;

public record MultipartObjectInfo(
        String bucket,
        String objectKey,
        long size,
        String contentType,
        String eTag,
        Map<String, String> metadata) {
}
