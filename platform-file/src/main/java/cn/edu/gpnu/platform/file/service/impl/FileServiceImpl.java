package cn.edu.gpnu.platform.file.service.impl;

import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.exception.MultipartUploadNotFoundException;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.model.DirectFileUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartObjectInfo;
import cn.edu.gpnu.platform.file.model.MultipartUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {

    private static final long MIN_MULTIPART_PART_SIZE = 5L * 1024 * 1024;
    private static final long MAX_MULTIPART_PART_SIZE = 64L * 1024 * 1024;
    private static final int MAX_MULTIPART_PARTS = 10_000;

    private final MinioClient minioClient;
    private final S3Presigner s3Presigner;
    private final MultipartObjectService multipartObjectService;
    private final MinioProperties props;
    private final FileObjectMapper fileObjectMapper;

    @Override
    public FileObject upload(InputStream in, String originalName, String contentType, long size, String bizType, String md5) {
        String ext = (originalName != null && originalName.contains(".")) ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String prefix = (bizType == null || bizType.isEmpty()) ? "misc" : bizType;
        String objectKey = prefix + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        // Phase 41.3（§7.2 P1）：try-with-resources 关闭入参流，防高并发下流/底层 socket/临时文件句柄泄漏。
        // MinIO putObject 在返回前已按声明的 size 同步读完整个 stream，故可在本方法内安全关闭，
        // 调用方（controller/service）均不在 upload 返回后继续使用该流。
        try (in) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new BizException("文件上传失败: " + e.getMessage());
        }
        FileObject fo = new FileObject();
        fo.setOriginalName(originalName);
        fo.setStoredName(objectKey.substring(objectKey.indexOf('/') + 1));
        fo.setBucket(props.getBucket());
        fo.setObjectKey(objectKey);
        fo.setSize(size);
        fo.setContentType(contentType);
        fo.setMd5(md5);
        fo.setBizType(bizType);
        fo.setStatus("READY");
        fo.setUploaderId(UserContext.getUserIdOrSystem());
        fo.setUploadTime(LocalDateTime.now());
        fileObjectMapper.insert(fo);
        return fo;
    }

    @Override
    public DirectFileUploadPlan initDirectUpload(String originalName, String contentType, long size,
                                                 String bizType, String contentHash, long partSize,
                                                 Map<String, String> businessMetadata) {
        if (size <= 0 || partSize <= 0 || partSize > MAX_MULTIPART_PART_SIZE) {
            throw new BizException("直传文件或分片大小不合法");
        }
        int totalParts = totalParts(size, partSize);
        if (totalParts > 1 && partSize < MIN_MULTIPART_PART_SIZE) {
            throw new BizException("除最后一片外，直传分片不得小于5MiB");
        }
        String prefix = requireBizPrefix(bizType);
        String safeName = originalName == null ? "file" : originalName.trim();
        Long ownerId = UserContext.getUserIdOrSystem();
        String normalizedContentType = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        String contextHash = businessContextHash(businessMetadata);
        String metadataHash = businessMetadataHash(businessMetadata);
        FileObject active = findActiveDirectUpload(ownerId, prefix, contextHash);
        if (active != null) {
            if ("UPLOADING".equals(active.getStatus())
                    && sameDirectUpload(active, safeName, normalizedContentType, size, contentHash,
                    partSize, totalParts, metadataHash)) {
                try {
                    MultipartUploadPlan resumed = multipartObjectService.resumeUpload(
                            active.getObjectKey(), active.getMultipartUploadId(), size, partSize, totalParts,
                            props.getPresignExpirySeconds());
                    updateUploadExpiry(active.getId(), resumed);
                    return DirectFileUploadPlan.uploading(active.getId(), partSize, resumed);
                } catch (MultipartUploadNotFoundException missing) {
                    failActiveDirectUpload(active, false);
                }
            } else if ("UPLOADING".equals(active.getStatus())) {
                failActiveDirectUpload(active, true);
            } else if ("INITIATING".equals(active.getStatus())) {
                failStaleInitiatingUpload(active);
            } else if ("COMPLETING".equals(active.getStatus())) {
                DirectFileUploadPlan recovered = recoverCompletingUpload(active, safeName, normalizedContentType,
                        size, contentHash, partSize, totalParts, metadataHash, businessMetadata);
                if (recovered != null) {
                    return recovered;
                }
            } else {
                throw new BizException("该业务对象已有直传正在进行，请稍后重试");
            }
        }

        String extension = safeExtension(safeName);
        String objectKey = prefix + "/" + ownerId + "/"
                + UUID.randomUUID().toString().replace("-", "") + extension;

        FileObject file = new FileObject();
        file.setOriginalName(safeName);
        file.setStoredName(objectKey.substring(objectKey.lastIndexOf('/') + 1));
        file.setBucket(props.getBucket());
        file.setObjectKey(objectKey);
        file.setSize(size);
        file.setContentType(normalizedContentType);
        file.setMd5(contentHash);
        file.setBizType(prefix);
        file.setStatus("INITIATING");
        file.setUploadPartSize(partSize);
        file.setUploadTotalParts(totalParts);
        file.setUploadContextHash(contextHash);
        file.setUploadMetadataHash(metadataHash);
        file.setUploaderId(ownerId);
        file.setUploadTime(LocalDateTime.now());
        try {
            fileObjectMapper.insert(file);
        } catch (DuplicateKeyException duplicate) {
            throw new BizException("该业务对象已有直传正在进行，请刷新后续传");
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        if (businessMetadata != null) {
            metadata.putAll(businessMetadata);
        }
        metadata.put("file-id", String.valueOf(file.getId()));
        metadata.put("owner-id", String.valueOf(ownerId));
        metadata.put("expected-size", String.valueOf(size));
        metadata.put("biz-type", prefix);
        MultipartUploadPlan plan;
        try {
            plan = multipartObjectService.createUpload(objectKey, file.getContentType(), metadata,
                    size, partSize, totalParts, props.getPresignExpirySeconds());
        } catch (RuntimeException e) {
            markInitiatingUploadFailed(file.getId());
            throw e;
        }
        try {
            int activated = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                    .eq(FileObject::getId, file.getId())
                    .eq(FileObject::getStatus, "INITIATING")
                    .set(FileObject::getMultipartUploadId, plan.multipartUploadId())
                    .set(FileObject::getUploadExpiresAt,
                            LocalDateTime.ofInstant(plan.expiresAt(), ZoneId.systemDefault()))
                    .set(FileObject::getStatus, "UPLOADING")
                    .set(FileObject::getUpdatedAt, LocalDateTime.now()));
            if (activated != 1) {
                safeAbortUpload(file.getObjectKey(), plan.multipartUploadId(), file.getId());
                throw new BizException("直传会话在初始化期间已被取消或回收，请重试");
            }
            return DirectFileUploadPlan.uploading(file.getId(), partSize, plan);
        } catch (RuntimeException e) {
            safeAbortUpload(file.getObjectKey(), plan.multipartUploadId(), file.getId());
            markInitiatingUploadFailed(file.getId());
            throw e;
        }
    }

    @Override
    public FileObject completeDirectUpload(Long fileId, List<MultipartUploadedPart> clientParts,
                                           Map<String, String> expectedBusinessMetadata) {
        FileObject file = requireOwnedFile(fileId);
        if (!Objects.equals(file.getUploadMetadataHash(), businessMetadataHash(expectedBusinessMetadata))) {
            invalidateMismatchedUpload(file);
            throw new BizException("直传文件的业务绑定版本已变化，请重新发起上传");
        }
        if ("READY".equals(file.getStatus())) {
            MultipartObjectInfo objectInfo = multipartObjectService.findObject(file.getObjectKey())
                    .orElseThrow(() -> new BizException("已定稿文件对象不存在"));
            validateDirectObject(file, objectInfo, expectedBusinessMetadata);
            return file;
        }
        boolean claimed = false;
        if ("UPLOADING".equals(file.getStatus())) {
            claimed = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                    .eq(FileObject::getId, file.getId())
                    .eq(FileObject::getStatus, "UPLOADING")
                    .set(FileObject::getStatus, "COMPLETING")
                    .set(FileObject::getUpdatedAt, LocalDateTime.now())) == 1;
            file = requireOwnedFile(fileId);
        }
        if (!"COMPLETING".equals(file.getStatus())) {
            throw new BizException("文件直传会话不可定稿");
        }
        FileObject activeFile = file;
        try {
            MultipartObjectInfo objectInfo = multipartObjectService.findObject(activeFile.getObjectKey())
                    .orElseGet(() -> {
                        List<MultipartUploadedPart> storedParts = multipartObjectService.listUploadedParts(
                                activeFile.getObjectKey(), activeFile.getMultipartUploadId());
                        validateDirectParts(activeFile, storedParts);
                        return multipartObjectService.completeUpload(
                                activeFile.getObjectKey(), activeFile.getMultipartUploadId(), clientParts);
                    });
            validateDirectObject(activeFile, objectInfo, expectedBusinessMetadata);
            int finalized = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                    .eq(FileObject::getId, activeFile.getId())
                    .eq(FileObject::getStatus, "COMPLETING")
                    .set(FileObject::getStatus, "READY")
                    .set(FileObject::getMultipartUploadId, null)
                    .set(FileObject::getUploadExpiresAt, null)
                    .set(FileObject::getUpdatedAt, LocalDateTime.now()));
            FileObject completed = requireOwnedFile(fileId);
            if (finalized != 1 && !"READY".equals(completed.getStatus())) {
                if ("FAILED".equals(completed.getStatus())) {
                    safeDeleteObject(activeFile.getObjectKey(), activeFile.getId());
                }
                throw new BizException("文件定稿状态发生变化，请刷新后重试");
            }
            return completed;
        } catch (RuntimeException e) {
            if (claimed && multipartObjectService.findObject(activeFile.getObjectKey()).isEmpty()) {
                fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                        .eq(FileObject::getId, activeFile.getId())
                        .eq(FileObject::getStatus, "COMPLETING")
                        .set(FileObject::getStatus, "UPLOADING")
                        .set(FileObject::getUpdatedAt, LocalDateTime.now()));
            }
            throw e;
        }
    }

    @Override
    public void cancelDirectUpload(Long fileId) {
        FileObject file = requireOwnedFile(fileId);
        if ("FAILED".equals(file.getStatus()) || "READY".equals(file.getStatus())) {
            return;
        }
        if ("COMPLETING".equals(file.getStatus())) {
            throw new BizException("文件正在定稿，当前不可取消");
        }
        int changed = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                .eq(FileObject::getId, file.getId())
                .in(FileObject::getStatus, "INITIATING", "UPLOADING")
                .set(FileObject::getStatus, "FAILED")
                .set(FileObject::getUpdatedAt, LocalDateTime.now()));
        if (changed == 0) {
            throw new BizException("直传会话状态已变化，请刷新后重试");
        }
        if (StringUtils.hasText(file.getMultipartUploadId())) {
            try {
                multipartObjectService.abortUpload(file.getObjectKey(), file.getMultipartUploadId());
            } catch (RuntimeException e) {
                log.warn("取消直传后 abort MinIO 失败，生命周期规则将兜底: fileId={}, message={}",
                        file.getId(), e.getMessage());
            }
        }
    }

    @Override
    public String presignedGet(Long fileId, int expirySeconds) {
        FileObject fo = fileObjectMapper.selectById(fileId);
        if (fo == null) {
            throw new BizException("文件不存在");
        }
        if (StringUtils.hasText(fo.getStatus()) && !"READY".equals(fo.getStatus())) {
            throw new BizException("文件尚未上传完成");
        }
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(fo.getBucket())
                    .key(fo.getObjectKey())
                    .build();
            return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(Math.max(1, Math.min(expirySeconds, 3600))))
                            .getObjectRequest(request)
                            .build())
                    .url()
                    .toString();
        } catch (Exception e) {
            throw new BizException("生成下载链接失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(Long fileId) {
        FileObject fo = fileObjectMapper.selectById(fileId);
        if (fo == null) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(fo.getBucket())
                    .object(fo.getObjectKey())
                    .build());
        } catch (Exception e) {
            throw new BizException("文件删除失败: " + e.getMessage());
        }
        fileObjectMapper.deleteById(fileId);
    }

    @Override
    public FileObject getByMd5(String md5) {
        return getByMd5(md5, null);
    }

    @Override
    public FileObject getByMd5(String md5, String bizType) {
        if (md5 == null || md5.isEmpty()) {
            return null;
        }
        var query = Wrappers.<FileObject>lambdaQuery()
                .eq(FileObject::getMd5, md5)
                .eq(FileObject::getStatus, "READY");
        if (StringUtils.hasText(bizType)) {
            query.eq(FileObject::getBizType, bizType);
        }
        return fileObjectMapper.selectOne(query.last("limit 1"));
    }

    private FileObject requireOwnedFile(Long fileId) {
        if (fileId == null) {
            throw new BizException("文件ID不能为空");
        }
        FileObject file = fileObjectMapper.selectById(fileId);
        if (file == null) {
            throw new BizException("文件不存在");
        }
        Long userId = UserContext.getUserIdOrSystem();
        if (!userId.equals(file.getUploaderId())) {
            throw new BizException("无权定稿该文件");
        }
        String expectedPrefix = requireBizPrefix(file.getBizType()) + "/" + userId + "/";
        if (!StringUtils.hasText(file.getObjectKey()) || !file.getObjectKey().startsWith(expectedPrefix)
                || file.getObjectKey().contains("..")) {
            throw new BizException("文件对象Key不合法");
        }
        return file;
    }

    private FileObject findActiveDirectUpload(Long ownerId, String bizType, String contextHash) {
        return fileObjectMapper.selectOne(Wrappers.<FileObject>lambdaQuery()
                .eq(FileObject::getUploaderId, ownerId)
                .eq(FileObject::getBizType, bizType)
                .eq(FileObject::getUploadContextHash, contextHash)
                .in(FileObject::getStatus, "INITIATING", "UPLOADING", "COMPLETING")
                .orderByDesc(FileObject::getCreatedAt)
                .last("LIMIT 1"));
    }

    private void failStaleInitiatingUpload(FileObject file) {
        if (!isUploadStateStale(file, props.getDirectUploadInitTimeoutSeconds())) {
            throw new BizException("该业务对象的直传正在初始化，请稍后重试");
        }
        int changed = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                .eq(FileObject::getId, file.getId())
                .eq(FileObject::getStatus, "INITIATING")
                .set(FileObject::getStatus, "FAILED")
                .set(FileObject::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new BizException("直传初始化状态已变化，请刷新后重试");
        }
    }

    private DirectFileUploadPlan recoverCompletingUpload(FileObject file, String originalName,
                                                          String contentType, long size, String contentHash,
                                                          long partSize, int totalParts,
                                                          String metadataHash,
                                                          Map<String, String> businessMetadata) {
        boolean sameUpload = sameDirectUpload(file, originalName, contentType, size, contentHash,
                partSize, totalParts, metadataHash);
        if (!Objects.equals(file.getUploadMetadataHash(), metadataHash)) {
            invalidateMismatchedUpload(file);
            return null;
        }
        MultipartObjectInfo completedObject = multipartObjectService.findObject(file.getObjectKey()).orElse(null);
        if (completedObject != null) {
            validateDirectObject(file, completedObject, businessMetadata);
            finalizeRecoveredUpload(file, "COMPLETING");
            return sameUpload ? DirectFileUploadPlan.ready(file.getId(), partSize) : null;
        }
        if (!isUploadStateStale(file, props.getDirectUploadCompleteTimeoutSeconds())) {
            throw new BizException("该业务对象的直传正在定稿，请稍后重试");
        }
        if (!sameUpload) {
            int failed = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                    .eq(FileObject::getId, file.getId())
                    .eq(FileObject::getStatus, "COMPLETING")
                    .set(FileObject::getStatus, "FAILED")
                    .set(FileObject::getUpdatedAt, LocalDateTime.now()));
            if (failed != 1) {
                throw new BizException("直传定稿状态已变化，请刷新后重试");
            }
            safeAbortUpload(file.getObjectKey(), file.getMultipartUploadId(), file.getId());
            return null;
        }
        int reopened = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                .eq(FileObject::getId, file.getId())
                .eq(FileObject::getStatus, "COMPLETING")
                .set(FileObject::getStatus, "UPLOADING")
                .set(FileObject::getUpdatedAt, LocalDateTime.now()));
        if (reopened != 1) {
            throw new BizException("直传定稿状态已变化，请刷新后重试");
        }
        file.setStatus("UPLOADING");
        try {
            MultipartUploadPlan resumed = multipartObjectService.resumeUpload(
                    file.getObjectKey(), file.getMultipartUploadId(), size, partSize, totalParts,
                    props.getPresignExpirySeconds());
            updateUploadExpiry(file.getId(), resumed);
            return DirectFileUploadPlan.uploading(file.getId(), partSize, resumed);
        } catch (MultipartUploadNotFoundException missing) {
            MultipartObjectInfo racedObject = multipartObjectService.findObject(file.getObjectKey()).orElse(null);
            if (racedObject != null) {
                validateDirectObject(file, racedObject, businessMetadata);
                finalizeRecoveredUpload(file, "UPLOADING");
                return DirectFileUploadPlan.ready(file.getId(), partSize);
            }
            failActiveDirectUpload(file, false);
            return null;
        }
    }

    private void updateUploadExpiry(Long fileId, MultipartUploadPlan plan) {
        int changed = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                .eq(FileObject::getId, fileId)
                .eq(FileObject::getStatus, "UPLOADING")
                .set(FileObject::getUploadExpiresAt,
                        LocalDateTime.ofInstant(plan.expiresAt(), ZoneId.systemDefault()))
                .set(FileObject::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new BizException("直传会话状态已变化，请刷新后重试");
        }
    }

    private void finalizeRecoveredUpload(FileObject file, String expectedStatus) {
        int changed = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                .eq(FileObject::getId, file.getId())
                .eq(FileObject::getStatus, expectedStatus)
                .set(FileObject::getStatus, "READY")
                .set(FileObject::getMultipartUploadId, null)
                .set(FileObject::getUploadExpiresAt, null)
                .set(FileObject::getUpdatedAt, LocalDateTime.now()));
        if (changed == 1) {
            return;
        }
        FileObject current = fileObjectMapper.selectById(file.getId());
        if (current == null || !"READY".equals(current.getStatus())) {
            throw new BizException("直传恢复状态发生变化，请刷新后重试");
        }
    }

    private boolean isUploadStateStale(FileObject file, int timeoutSeconds) {
        LocalDateTime activityAt = file.getUpdatedAt() != null ? file.getUpdatedAt() : file.getCreatedAt();
        return activityAt != null
                && !activityAt.isAfter(LocalDateTime.now().minusSeconds(Math.max(1, timeoutSeconds)));
    }

    private void markInitiatingUploadFailed(Long fileId) {
        fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                .eq(FileObject::getId, fileId)
                .eq(FileObject::getStatus, "INITIATING")
                .set(FileObject::getStatus, "FAILED")
                .set(FileObject::getUpdatedAt, LocalDateTime.now()));
    }

    private void safeAbortUpload(String objectKey, String uploadId, Long fileId) {
        if (!StringUtils.hasText(uploadId)) {
            return;
        }
        try {
            multipartObjectService.abortUpload(objectKey, uploadId);
        } catch (RuntimeException e) {
            log.warn("终止直传会话失败，生命周期规则将兜底: fileId={}, message={}", fileId, e.getMessage());
        }
    }

    private boolean sameDirectUpload(FileObject file, String originalName, String contentType, long size,
                                     String contentHash, long partSize, int totalParts, String metadataHash) {
        return Objects.equals(file.getOriginalName(), originalName)
                && Objects.equals(file.getContentType(), contentType)
                && Objects.equals(file.getSize(), size)
                && Objects.equals(file.getMd5(), contentHash)
                && Objects.equals(file.getUploadPartSize(), partSize)
                && Objects.equals(file.getUploadTotalParts(), totalParts)
                && Objects.equals(file.getUploadMetadataHash(), metadataHash)
                && StringUtils.hasText(file.getMultipartUploadId());
    }

    private void failActiveDirectUpload(FileObject file, boolean abort) {
        int changed = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                .eq(FileObject::getId, file.getId())
                .eq(FileObject::getStatus, "UPLOADING")
                .set(FileObject::getStatus, "FAILED")
                .set(FileObject::getUpdatedAt, LocalDateTime.now()));
        if (changed == 0) {
            throw new BizException("直传会话状态已变化，请刷新后重试");
        }
        if (abort && StringUtils.hasText(file.getMultipartUploadId())) {
            safeAbortUpload(file.getObjectKey(), file.getMultipartUploadId(), file.getId());
        }
    }

    private String businessContextHash(Map<String, String> metadata) {
        return canonicalMetadataHash(metadata, false);
    }

    private String businessMetadataHash(Map<String, String> metadata) {
        return canonicalMetadataHash(metadata, true);
    }

    private String canonicalMetadataHash(Map<String, String> metadata, boolean includeBindingVersion) {
        TreeMap<String, String> canonical = new TreeMap<>();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
                // 绑定版本参与对象定稿校验，但不改变同一业务目标的活动上传唯一键。
                if (includeBindingVersion || !"binding-version".equals(normalizedKey)) {
                    canonical.put(normalizedKey, String.valueOf(value));
                }
            });
        }
        StringBuilder text = new StringBuilder();
        canonical.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM不支持SHA-256", impossible);
        }
    }

    private void invalidateMismatchedUpload(FileObject file) {
        if (!"UPLOADING".equals(file.getStatus()) && !"COMPLETING".equals(file.getStatus())) {
            return;
        }
        int changed = fileObjectMapper.update(null, Wrappers.<FileObject>lambdaUpdate()
                .eq(FileObject::getId, file.getId())
                .eq(FileObject::getStatus, file.getStatus())
                .set(FileObject::getStatus, "FAILED")
                .set(FileObject::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new BizException("直传绑定状态已变化，请刷新后重试");
        }
        safeAbortUpload(file.getObjectKey(), file.getMultipartUploadId(), file.getId());
        safeDeleteObject(file.getObjectKey(), file.getId());
    }

    private void safeDeleteObject(String objectKey, Long fileId) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("删除未绑定直传对象失败，孤儿扫描将兜底: fileId={}, message={}", fileId, e.getMessage());
        }
    }

    private void validateDirectParts(FileObject file, List<MultipartUploadedPart> parts) {
        if (file.getUploadTotalParts() == null || parts.size() != file.getUploadTotalParts()) {
            throw new BizException("直传分片尚未全部上传");
        }
        long totalSize = 0L;
        for (int index = 0; index < parts.size(); index++) {
            MultipartUploadedPart part = parts.get(index);
            long expected = index == parts.size() - 1
                    ? file.getSize() - file.getUploadPartSize() * index
                    : file.getUploadPartSize();
            if (part.partNumber() != index + 1 || part.size() != expected) {
                throw new BizException("直传分片序号或大小不合法");
            }
            totalSize += part.size();
        }
        if (totalSize != file.getSize()) {
            throw new BizException("直传对象总大小不一致");
        }
    }

    private void validateDirectObject(FileObject file, MultipartObjectInfo objectInfo,
                                      Map<String, String> expectedBusinessMetadata) {
        if (!file.getBucket().equals(objectInfo.bucket())
                || !file.getObjectKey().equals(objectInfo.objectKey())
                || file.getSize() != objectInfo.size()
                || !file.getContentType().equalsIgnoreCase(objectInfo.contentType())
                || !String.valueOf(file.getId()).equals(objectInfo.metadata().get("file-id"))
                || !String.valueOf(file.getUploaderId()).equals(objectInfo.metadata().get("owner-id"))
                || !String.valueOf(file.getSize()).equals(objectInfo.metadata().get("expected-size"))
                || !file.getBizType().equals(objectInfo.metadata().get("biz-type"))) {
            throw new BizException("直传对象与授权会话不一致");
        }
        if (expectedBusinessMetadata != null) {
            expectedBusinessMetadata.forEach((key, value) -> {
                if (!String.valueOf(value).equals(objectInfo.metadata().get(key.toLowerCase(Locale.ROOT)))) {
                    throw new BizException("直传对象业务上下文不一致");
                }
            });
        }
    }

    private int totalParts(long size, long partSize) {
        long count = (size + partSize - 1) / partSize;
        if (count < 1 || count > MAX_MULTIPART_PARTS) {
            throw new BizException("直传分片数量不合法");
        }
        return (int) count;
    }

    private String requireBizPrefix(String bizType) {
        if (!StringUtils.hasText(bizType) || !bizType.matches("[a-z0-9-]{1,64}")) {
            throw new BizException("文件业务类型不合法");
        }
        return bizType.toLowerCase(Locale.ROOT);
    }

    private String safeExtension(String originalName) {
        int dot = originalName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        String extension = originalName.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,10}") ? extension : "";
    }
}
