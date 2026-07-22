package cn.edu.gpnu.platform.file.model;

import java.time.Instant;
import java.util.List;

public record MultipartUploadPlan(
        String multipartUploadId,
        String objectKey,
        Instant expiresAt,
        List<MultipartUploadedPart> uploadedParts,
        List<PresignedUploadPart> parts) {
}
