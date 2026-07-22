package cn.edu.gpnu.platform.file.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.exception.MultipartUploadNotFoundException;
import cn.edu.gpnu.platform.file.model.MultipartObjectInfo;
import cn.edu.gpnu.platform.file.model.MultipartUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;
import cn.edu.gpnu.platform.file.model.PresignedUploadPart;
import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class S3MultipartObjectService implements MultipartObjectService {

    private static final int MAX_MULTIPART_PARTS = 10_000;
    private static final int MAX_PRESIGN_SECONDS = 3_600;
    private static final long MIN_MULTIPART_PART_SIZE = 5L * 1024 * 1024;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties properties;

    @Override
    public MultipartUploadPlan createUpload(String objectKey, String contentType, Map<String, String> metadata,
                                             long objectSize, long partSize, int totalParts, int expirySeconds) {
        String key = requireObjectKey(objectKey);
        validateUploadDimensions(objectSize, partSize, totalParts);
        try {
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                    .metadata(sanitizeMetadata(metadata))
                    .build());
            return buildPlan(key, response.uploadId(), objectSize, partSize, totalParts, expirySeconds, List.of());
        } catch (S3Exception e) {
            throw minioFailure("创建分片上传会话失败", e);
        }
    }

    @Override
    public MultipartUploadPlan resumeUpload(String objectKey, String multipartUploadId,
                                             long objectSize, long partSize, int totalParts, int expirySeconds) {
        String key = requireObjectKey(objectKey);
        String uploadId = requireUploadId(multipartUploadId);
        validateUploadDimensions(objectSize, partSize, totalParts);
        return buildPlan(key, uploadId, objectSize, partSize, totalParts, expirySeconds,
                listUploadedParts(key, uploadId));
    }

    @Override
    public List<MultipartUploadedPart> listUploadedParts(String objectKey, String multipartUploadId) {
        String key = requireObjectKey(objectKey);
        String uploadId = requireUploadId(multipartUploadId);
        List<MultipartUploadedPart> result = new ArrayList<>();
        Integer marker = null;
        try {
            do {
                ListPartsResponse response = s3Client.listParts(ListPartsRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .uploadId(uploadId)
                        .partNumberMarker(marker)
                        .maxParts(1_000)
                        .build());
                response.parts().forEach(part -> result.add(new MultipartUploadedPart(
                        part.partNumber(), part.eTag(), part.size())));
                marker = Boolean.TRUE.equals(response.isTruncated()) ? response.nextPartNumberMarker() : null;
            } while (marker != null);
            return result.stream().sorted(Comparator.comparingInt(MultipartUploadedPart::partNumber)).toList();
        } catch (S3Exception e) {
            throw minioFailure("读取已上传分片失败", e);
        }
    }

    @Override
    public MultipartObjectInfo completeUpload(String objectKey, String multipartUploadId,
                                               List<MultipartUploadedPart> clientParts) {
        String key = requireObjectKey(objectKey);
        String uploadId = requireUploadId(multipartUploadId);
        Optional<MultipartObjectInfo> existing = findObject(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        List<MultipartUploadedPart> storedParts = listUploadedParts(key, uploadId);
        List<MultipartUploadedPart> verified = verifyCompletionParts(clientParts, storedParts);
        try {
            List<CompletedPart> completedParts = verified.stream()
                    .map(part -> CompletedPart.builder()
                            .partNumber(part.partNumber())
                            .eTag(part.eTag())
                            .build())
                    .toList();
            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build());
            return findObject(key).orElseThrow(() -> new BizException("MinIO 已定稿但对象不可见"));
        } catch (S3Exception e) {
            Optional<MultipartObjectInfo> completed = findObject(key);
            if (completed.isPresent()) {
                return completed.get();
            }
            throw minioFailure("分片上传定稿失败", e);
        }
    }

    @Override
    public Optional<MultipartObjectInfo> findObject(String objectKey) {
        String key = requireObjectKey(objectKey);
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            return Optional.of(new MultipartObjectInfo(properties.getBucket(), key,
                    response.contentLength(), response.contentType(), response.eTag(), response.metadata()));
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || "NoSuchKey".equals(errorCode(e))) {
                return Optional.empty();
            }
            throw minioFailure("读取对象元数据失败", e);
        }
    }

    @Override
    public void abortUpload(String objectKey, String multipartUploadId) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(requireObjectKey(objectKey))
                    .uploadId(requireUploadId(multipartUploadId))
                    .build());
        } catch (S3Exception e) {
            if (!"NoSuchUpload".equals(errorCode(e))) {
                throw minioFailure("终止分片上传失败", e);
            }
        }
    }

    private MultipartUploadPlan buildPlan(String objectKey, String uploadId, long objectSize, long partSize,
                                          int totalParts, int expirySeconds,
                                          List<MultipartUploadedPart> uploadedParts) {
        int ttl = Math.max(1, Math.min(expirySeconds, MAX_PRESIGN_SECONDS));
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        Map<Integer, MultipartUploadedPart> uploadedByNumber = new HashMap<>();
        List<MultipartUploadedPart> validUploadedParts = new ArrayList<>();
        for (MultipartUploadedPart part : uploadedParts) {
            if (part.partNumber() < 1 || part.partNumber() > totalParts) {
                throw new BizException("MinIO 返回了超出会话范围的分片");
            }
            if (part.size() != expectedPartSize(objectSize, partSize, totalParts, part.partNumber())) {
                continue;
            }
            uploadedByNumber.put(part.partNumber(), part);
            validUploadedParts.add(part);
        }
        List<PresignedUploadPart> signedParts = new ArrayList<>();
        for (int partNumber = 1; partNumber <= totalParts; partNumber++) {
            if (uploadedByNumber.containsKey(partNumber)) {
                continue;
            }
            UploadPartRequest request = UploadPartRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(expectedPartSize(objectSize, partSize, totalParts, partNumber))
                    .build();
            String url = s3Presigner.presignUploadPart(UploadPartPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(ttl))
                            .uploadPartRequest(request)
                            .build())
                    .url()
                    .toString();
            signedParts.add(new PresignedUploadPart(partNumber, url, expiresAt));
        }
        return new MultipartUploadPlan(uploadId, objectKey, expiresAt, List.copyOf(validUploadedParts), signedParts);
    }

    private List<MultipartUploadedPart> verifyCompletionParts(List<MultipartUploadedPart> clientParts,
                                                              List<MultipartUploadedPart> storedParts) {
        if (clientParts == null || clientParts.isEmpty()) {
            throw new BizException("上传分片不能为空");
        }
        Map<Integer, MultipartUploadedPart> stored = storedParts.stream().collect(
                LinkedHashMap::new,
                (map, part) -> map.put(part.partNumber(), part),
                LinkedHashMap::putAll);
        if (stored.size() != storedParts.size() || clientParts.size() != stored.size()) {
            throw new BizException("上传分片数量不一致");
        }
        Map<Integer, MultipartUploadedPart> verified = new LinkedHashMap<>();
        for (MultipartUploadedPart clientPart : clientParts) {
            if (clientPart == null || verified.containsKey(clientPart.partNumber())) {
                throw new BizException("上传分片序号重复或为空");
            }
            MultipartUploadedPart storedPart = stored.get(clientPart.partNumber());
            if (storedPart == null || !normalizeETag(storedPart.eTag()).equals(normalizeETag(clientPart.eTag()))) {
                throw new BizException("上传分片ETag校验失败");
            }
            verified.put(clientPart.partNumber(), storedPart);
        }
        List<MultipartUploadedPart> ordered = verified.values().stream()
                .sorted(Comparator.comparingInt(MultipartUploadedPart::partNumber))
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).partNumber() != index + 1) {
                throw new BizException("上传分片序号必须连续");
            }
        }
        return ordered;
    }

    private Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null) {
                sanitized.put(key.trim().toLowerCase(Locale.ROOT), value);
            }
        });
        return sanitized;
    }

    private void validateUploadDimensions(long objectSize, long partSize, int totalParts) {
        if (objectSize < 1 || partSize < 1) {
            throw new BizException("对象大小或分片大小不合法");
        }
        if (totalParts < 1 || totalParts > MAX_MULTIPART_PARTS) {
            throw new BizException("分片数量不合法");
        }
        long expectedTotalParts = ((objectSize - 1) / partSize) + 1;
        if (expectedTotalParts != totalParts) {
            throw new BizException("分片数量与对象大小不一致");
        }
        if (totalParts > 1 && partSize < MIN_MULTIPART_PART_SIZE) {
            throw new BizException("非末分片大小不得小于5MiB");
        }
    }

    private long expectedPartSize(long objectSize, long partSize, int totalParts, int partNumber) {
        if (partNumber < totalParts) {
            return partSize;
        }
        return objectSize - ((long) (totalParts - 1) * partSize);
    }

    private String requireObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey) || objectKey.startsWith("/") || objectKey.contains("..")
                || objectKey.length() > 512) {
            throw new BizException("对象Key不合法");
        }
        return objectKey;
    }

    private String requireUploadId(String uploadId) {
        if (!StringUtils.hasText(uploadId) || uploadId.length() > 255) {
            throw new BizException("S3上传会话不合法");
        }
        return uploadId;
    }

    private String normalizeETag(String eTag) {
        if (!StringUtils.hasText(eTag)) {
            return "";
        }
        String value = eTag.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private BizException minioFailure(String operation, S3Exception exception) {
        String code = errorCode(exception);
        if ("NoSuchUpload".equals(code)) {
            return new MultipartUploadNotFoundException(operation);
        }
        return new BizException(operation + (StringUtils.hasText(code) ? " [" + code + "]" : ""));
    }

    private String errorCode(S3Exception exception) {
        return exception.awsErrorDetails() == null ? null : exception.awsErrorDetails().errorCode();
    }
}
