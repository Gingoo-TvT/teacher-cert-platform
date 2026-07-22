package cn.edu.gpnu.platform.file.model;

import java.time.Instant;

public record PresignedUploadPart(int partNumber, String url, Instant expiresAt) {
}
