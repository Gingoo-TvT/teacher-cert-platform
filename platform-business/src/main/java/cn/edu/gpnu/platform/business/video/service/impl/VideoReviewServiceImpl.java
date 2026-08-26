package cn.edu.gpnu.platform.business.video.service.impl;

import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.video.dto.VideoArbitrateRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoAssignRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoQuery;
import cn.edu.gpnu.platform.business.video.dto.VideoReturnRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoScoreRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoThirdReviewRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoUploadInitRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoUploadCompleteRequest;
import cn.edu.gpnu.platform.business.video.dto.MultipartCompletedPartRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoUploadMergeRequest;
import cn.edu.gpnu.platform.business.video.entity.ReviewerGroup;
import cn.edu.gpnu.platform.business.video.entity.ReviewerGroupMember;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadChunk;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadSession;
import cn.edu.gpnu.platform.business.video.mapper.ReviewerGroupMapper;
import cn.edu.gpnu.platform.business.video.mapper.ReviewerGroupMemberMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadChunkMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadSessionMapper;
import cn.edu.gpnu.platform.business.video.service.VideoReviewService;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizeSingleFlight;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizationHook;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectLifecycleService;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectReconciler;
import cn.edu.gpnu.platform.business.video.support.VideoMediaAcceptancePolicy;
import cn.edu.gpnu.platform.business.video.support.VideoMediaInspection;
import cn.edu.gpnu.platform.business.video.support.VideoMediaProbe;
import cn.edu.gpnu.platform.business.video.support.VideoProbeCapacityGuard;
import cn.edu.gpnu.platform.business.video.support.VideoReviewStatus;
import cn.edu.gpnu.platform.business.video.support.VideoUploadStatus;
import cn.edu.gpnu.platform.business.video.vo.ReviewerCandidateVO;
import cn.edu.gpnu.platform.business.video.vo.VideoPlaybackVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewTaskVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadInitVO;
import cn.edu.gpnu.platform.business.video.vo.VideoPresignedPartVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadProgressVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadedPartVO;
import cn.edu.gpnu.platform.business.support.ReviewNotificationHelper;
import cn.edu.gpnu.platform.common.api.PageQuery;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.exception.MultipartUploadNotFoundException;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.model.MultipartObjectInfo;
import cn.edu.gpnu.platform.file.model.MultipartUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoReviewServiceImpl implements VideoReviewService {

    private static final String VIDEO_BIZ_TYPE = "teaching-video";
    private static final String CHUNK_BIZ_TYPE = "video-chunk";
    private static final String PRESIGNED_MULTIPART_MODE = "PRESIGNED_MULTIPART";
    private static final String SERVER_CHUNK_MODE = "SERVER_CHUNK";
    // MinIO/S3 服务端合并（composeObject→multipart UploadPartCopy）要求：除最后一片外每一源片 ≥5MiB
    // （io.minio.ObjectWriteArgs.MIN_MULTIPART_SIZE）。前端分片 8MiB（VIDEO_UPLOAD_CHUNK_SIZE）即满足此下限，
    // 令多分片上传走服务端合并快路径；小于此阈值的分片（末片/单分片场景）回退流式拼接。此常量即与 SDK 下限锁步。
    private static final long MIN_COMPOSE_PART_SIZE = ObjectWriteArgs.MIN_MULTIPART_SIZE;
    private static final long MAX_DIRECT_PART_SIZE = 67_108_864L;
    private static final int MAX_DIRECT_PARTS = 10_000;
    private static final int DEFAULT_PASS_LINE = 60;
    private static final int DEFAULT_DIFF_THRESHOLD = 12;
    private static final int DEFAULT_REVIEWER_COUNT = 2;
    private static final int DEFAULT_PRESIGN_SECONDS = 300;
    private static final String REVIEW_TEACHER_ROLE = "REVIEW_TEACHER";

    private final VideoUploadSessionMapper sessionMapper;
    private final VideoUploadChunkMapper chunkMapper;
    private final VideoReviewMapper reviewMapper;
    private final VideoReviewTaskMapper taskMapper;
    private final ReviewerGroupMapper groupMapper;
    private final ReviewerGroupMemberMapper groupMemberMapper;
    private final StudentMapper studentMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDictItemMapper dictItemMapper;
    private final FileObjectMapper fileObjectMapper;
    private final FileService fileService;
    private final MultipartObjectService multipartObjectService;
    private final VideoMediaProbe videoMediaProbe;
    private final VideoProbeCapacityGuard videoProbeCapacityGuard;
    private final VideoFinalizeSingleFlight videoFinalizeSingleFlight;
    private final VideoFinalizationHook videoFinalizationHook;
    private final VideoFinalizationObjectLifecycleService finalizationObjectLifecycleService;
    private final VideoFinalizationObjectReconciler finalizationObjectReconciler;
    private final DataScopeService dataScopeService;
    private final ParamService paramService;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final ObjectMapper objectMapper;
    private final ReviewNotificationHelper notificationHelper;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public VideoUploadInitVO initUpload(VideoUploadInitRequest request) {
        Student student = requireStudent(request.getStudentId());
        ensureCanWriteStudent(student, "video:upload");
        validateUploadMeta(request.getFileName(), request.getContentType(), request.getSize());
        validateDirectChunkPlan(request.getSize(), request.getChunkSize());
        String assessmentYear = requiredTrim(request.getAssessmentYear(), "考核年度不能为空");
        String fileHash = requiredTrim(request.getFileMd5(), "文件MD5不能为空").toLowerCase(Locale.ROOT);
        String legacyFileHash = StringUtils.hasText(request.getLegacyFileHash())
                ? request.getLegacyFileHash().trim().toLowerCase(Locale.ROOT) : null;
        transactionTemplate.executeWithoutResult(status ->
                ensureReviewReuploadable(student.getId(), assessmentYear));
        VideoUploadSession existing = currentOpenUploadSession(student.getId(), assessmentYear);
        String currentUploadId = existing == null ? null : existing.getUploadId();
        transactionTemplate.executeWithoutResult(status ->
                archiveReturnedUploadSessions(student.getId(), assessmentYear, currentUploadId));
        if (existing != null && PRESIGNED_MULTIPART_MODE.equals(existing.getUploadMode())
                && VideoUploadStatus.UPLOADING.name().equals(existing.getStatus())) {
            try {
                multipartObjectService.listUploadedParts(existing.getObjectKey(), existing.getS3UploadId());
            } catch (MultipartUploadNotFoundException missing) {
                failDirectSession(existing);
                existing = null;
            }
        }
        if (existing != null) {
            boolean legacyMatch = (SERVER_CHUNK_MODE.equals(existing.getUploadMode())
                    || !StringUtils.hasText(existing.getUploadMode()))
                    && Integer.valueOf(0).equals(existing.getSlotClaimed())
                    && legacyFileHash != null && legacyFileHash.equals(existing.getFileMd5());
            if (!fileHash.equals(existing.getFileMd5()) && !legacyMatch) {
                throw new BizException("该学生本年度已有其他视频正在上传，请先完成原上传");
            }
            validateResumableSession(existing, request);
            if (SERVER_CHUNK_MODE.equals(existing.getUploadMode()) || !StringUtils.hasText(existing.getUploadMode())) {
                return toUploadInitVO(updateResumeMetadata(
                        existing, request.getDurationSeconds(), legacyMatch ? fileHash : null), null);
            }
            if (VideoUploadStatus.MERGING.name().equals(existing.getStatus())) {
                return toMergingUploadInitVO(existing);
            }
            if (!minioProperties.isDirectUploadEnabled()) {
                failDirectSession(existing);
                existing = null;
            } else if (VideoUploadStatus.UPLOADING.name().equals(existing.getStatus())) {
                try {
                    MultipartUploadPlan resumed = multipartObjectService.resumeUpload(
                            requireVideoObjectKey(existing.getObjectKey()), existing.getS3UploadId(),
                            existing.getFileSize(), existing.getChunkSize(), existing.getTotalChunks(),
                            minioProperties.getPresignExpirySeconds());
                    VideoUploadSession resumedSession = updateDirectResume(existing, resumed, request.getDurationSeconds());
                    return toUploadInitVO(resumedSession, resumed);
                } catch (MultipartUploadNotFoundException missing) {
                    failDirectSession(existing);
                    existing = null;
                }
            } else {
                throw new BizException("该视频正在定稿，请稍后刷新结果");
            }
        }
        // 秒传仅复用服务端已验真的对象，并绑定同一上传人 + 同一学生；客户端指纹不是跨主体的持有证明。
        VerifiedInstantHit instantHit = fileHash.length() == 64
                ? findVerifiedInstantHit(student, fileHash) : null;
        if (instantHit != null) {
            FileObject existingFile = instantHit.file();
            VideoMediaInspection inspection = VideoMediaInspection.valid(
                    existingFile.getMd5(), instantHit.durationSeconds(), existingFile.getMediaCodec(), 1,
                    existingFile.getMediaValidationPolicyHash(), existingFile.getMediaProbeVersion());
            VideoReview review = transactionTemplate.execute(status -> upsertReviewAfterValidation(
                    student, assessmentYear, existingFile, inspection, true, null));
            VideoUploadInitVO vo = new VideoUploadInitVO();
            vo.setUploadId(null);
            vo.setUploadMode("FAST_HIT");
            vo.setPartSize(request.getChunkSize());
            vo.setInstantHit(true);
            vo.setFileId(existingFile.getId());
            vo.setReviewId(review == null ? null : review.getId());
            vo.setUploadedChunks(List.of());
            vo.setUploadedParts(List.of());
            vo.setParts(List.of());
            vo.setStatus(review == null ? null : review.getStatus());
            vo.setValidationMessage(review == null ? null : review.getValidationMessage());
            return vo;
        }
        if (!minioProperties.isDirectUploadEnabled()) {
            VideoUploadSession serverSession;
            try {
                serverSession = transactionTemplate.execute(status -> createSession(student, request));
            } catch (DuplicateKeyException duplicate) {
                throw new BizException("该学生本年度已有视频正在上传，请刷新后继续原上传");
            }
            return toUploadInitVO(serverSession, null);
        }
        DirectUploadStart started = startDirectUpload(student, request, assessmentYear, fileHash);
        return toUploadInitVO(started.session(), started.plan());
    }

    @Override
    public void uploadChunk(String uploadId, Integer index, String md5, InputStream input, long size) {
        VideoUploadSession session = requireSession(uploadId);
        ensureCanWriteStudent(requireStudent(session.getStudentId()), "video:upload");
        if (PRESIGNED_MULTIPART_MODE.equals(session.getUploadMode())) {
            throw new BizException("该会话必须由浏览器直传MinIO");
        }
        if (VideoUploadStatus.of(session.getStatus()) != VideoUploadStatus.UPLOADING) {
            throw new BizException("当前上传会话不可继续上传");
        }
        if (index == null || index < 0 || index >= session.getTotalChunks()) {
            throw new BizException("分片序号不合法");
        }
        if (size <= 0) {
            throw new BizException("分片不能为空");
        }
        String chunkMd5 = requiredTrim(md5, "分片MD5不能为空").toLowerCase(Locale.ROOT);
        String objectKey = CHUNK_BIZ_TYPE + "/" + uploadId + "/" + index;
        // MinIO 分片上传在事务外执行：网络往返期间不占用 DB 连接，避免并发大上传耗尽连接池（P0-11）
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(input, size, -1)
                    .contentType("application/octet-stream")
                    .build());
        } catch (Exception e) {
            log.error("视频分片上传失败 uploadId={} index={}", uploadId, index, e);
            throw new BizException("分片上传失败，请稍后重试");
        }
        // 分片元数据写入单独短事务
        transactionTemplate.executeWithoutResult(txStatus -> {
            VideoUploadChunk existing = chunkMapper.selectOne(new LambdaQueryWrapper<VideoUploadChunk>()
                    .eq(VideoUploadChunk::getUploadId, uploadId)
                    .eq(VideoUploadChunk::getChunkIndex, index)
                    .last("LIMIT 1"));
            if (existing == null) {
                VideoUploadChunk chunk = new VideoUploadChunk();
                chunk.setUploadId(uploadId);
                chunk.setChunkIndex(index);
                chunk.setChunkMd5(chunkMd5);
                chunk.setChunkSize(size);
                chunk.setObjectKey(objectKey);
                chunk.setUploadedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);
            } else {
                existing.setChunkMd5(chunkMd5);
                existing.setChunkSize(size);
                existing.setObjectKey(objectKey);
                existing.setUploadedAt(LocalDateTime.now());
                chunkMapper.updateById(existing);
            }
            refreshSessionProgress(uploadId);
        });
    }

    @Override
    public void cancelUpload(String uploadId) {
        String normalizedUploadId = requiredTrim(uploadId, "上传会话不能为空");
        VideoUploadSession session = requireSession(normalizedUploadId);
        ensureCanWriteStudent(requireStudent(session.getStudentId()), "video:upload");
        session = transactionTemplate.execute(status -> {
            VideoUploadSession locked = lockUploadSession(normalizedUploadId);
            VideoUploadStatus current = VideoUploadStatus.of(locked.getStatus());
            if (current == VideoUploadStatus.FAILED || current == VideoUploadStatus.MERGED
                    || current == VideoUploadStatus.VALIDATION_FAILED) {
                return null;
            }
            if (current == VideoUploadStatus.MERGING) {
                throw new BizException("视频正在定稿，当前不可取消");
            }
            if (current != VideoUploadStatus.UPLOADING) {
                throw new BizException("上传会话状态已变化，请刷新后重试");
            }
            locked.setStatus(VideoUploadStatus.FAILED.name());
            if (sessionMapper.updateById(locked) != 1) {
                throw new BizException("上传会话状态已变化，请刷新后重试");
            }
            finalizationObjectLifecycleService.markFailed(locked.getUploadId());
            return locked;
        });
        if (session == null) {
            return;
        }
        if (PRESIGNED_MULTIPART_MODE.equals(session.getUploadMode())) {
            try {
                multipartObjectService.abortUpload(
                        requireVideoObjectKey(session.getObjectKey()), session.getS3UploadId());
            } catch (RuntimeException e) {
                log.warn("取消视频上传后 abort MinIO 失败，生命周期规则将兜底: uploadId={}, message={}",
                        session.getUploadId(), e.getMessage());
            }
            bestEffortCandidateCleanup(session.getUploadId());
            return;
        }
        List<VideoUploadChunk> uploaded = chunks(session.getUploadId());
        for (VideoUploadChunk chunk : uploaded) {
            if (!StringUtils.hasText(chunk.getObjectKey())) {
                continue;
            }
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioProperties.getBucket()).object(chunk.getObjectKey()).build());
            } catch (Exception e) {
                log.warn("取消视频上传后删除服务端分片失败: uploadId={}, objectKey={}, message={}",
                        session.getUploadId(), chunk.getObjectKey(), e.getMessage());
            }
        }
        if (!uploaded.isEmpty()) {
            chunkMapper.update(null, new LambdaUpdateWrapper<VideoUploadChunk>()
                    .eq(VideoUploadChunk::getUploadId, session.getUploadId())
                    .setSql("deleted = 1"));
        }
        bestEffortCandidateCleanup(session.getUploadId());
    }

    @Override
    public VideoReviewVO complete(VideoUploadCompleteRequest request) {
        VideoUploadSession session = requireSession(request.getUploadId());
        Student student = requireStudent(session.getStudentId());
        ensureCanWriteStudent(student, "video:upload");

        VideoUploadStatus initialStatus = VideoUploadStatus.of(session.getStatus());
        if (initialStatus == VideoUploadStatus.MERGED || initialStatus == VideoUploadStatus.VALIDATION_FAILED) {
            return handleNonClaimableMerge(session);
        }
        if (initialStatus == VideoUploadStatus.FAILED) {
            throw new BizException("上传会话已失效，请重新发起上传");
        }
        requirePresignedSession(session);
        VideoFinalizeSingleFlight.Lease singleFlight =
                videoFinalizeSingleFlight.tryAcquire(session.getUploadId());
        if (singleFlight == null) {
            return handleNonClaimableMerge(session);
        }
        try (singleFlight;
             VideoProbeCapacityGuard.Lease ignored =
                     videoProbeCapacityGuard.acquire(session.getFileSize())) {
            return completeWithReservedCapacity(
                    request, requireSession(session.getUploadId()), student, singleFlight);
        }
    }

    private VideoReviewVO completeWithReservedCapacity(VideoUploadCompleteRequest request,
                                                       VideoUploadSession session,
                                                       Student student,
                                                       VideoFinalizeSingleFlight.Lease lease) {
        lease.renewOrThrow();
        VideoUploadStatus initialStatus = VideoUploadStatus.of(session.getStatus());
        boolean claimed = false;
        if (initialStatus == VideoUploadStatus.UPLOADING) {
            List<MultipartUploadedPart> s3Parts = multipartObjectService.listUploadedParts(
                    session.getObjectKey(), session.getS3UploadId());
            lease.assertOwned();
            validateDirectParts(session, s3Parts);
            List<MultipartUploadedPart> verified = verifyClientParts(request.getParts(), s3Parts);
            Boolean claimResult = transactionTemplate.execute(status -> {
                VideoUploadSession locked = lockUploadSession(request.getUploadId());
                VideoUploadStatus lockedStatus = VideoUploadStatus.of(locked.getStatus());
                if (lockedStatus != VideoUploadStatus.UPLOADING) {
                    return false;
                }
                ensureReviewReuploadable(locked.getStudentId(), locked.getAssessmentYear());
                persistDirectParts(locked, verified);
                locked.setStatus(VideoUploadStatus.MERGING.name());
                long previousGeneration = normalizedFinalizationGeneration(locked);
                String previousObjectKey = locked.getObjectKey();
                advanceFinalizationGeneration(locked, lease);
                finalizationObjectLifecycleService.activateCandidate(
                        locked, previousGeneration, previousObjectKey);
                if (request.getDurationSeconds() != null) {
                    locked.setDurationSeconds(request.getDurationSeconds());
                }
                sessionMapper.updateById(locked);
                lease.assertOwned();
                return true;
            });
            claimed = Boolean.TRUE.equals(claimResult);
            session = requireSession(session.getUploadId());
            if (!claimed) {
                return handleNonClaimableMerge(session);
            }
        } else if (initialStatus == VideoUploadStatus.MERGING) {
            Boolean takenOver = transactionTemplate.execute(status -> {
                VideoUploadSession locked = lockUploadSession(request.getUploadId());
                if (VideoUploadStatus.of(locked.getStatus()) != VideoUploadStatus.MERGING) {
                    return false;
                }
                long previousGeneration = normalizedFinalizationGeneration(locked);
                String previousObjectKey = locked.getObjectKey();
                advanceFinalizationGeneration(locked, lease);
                finalizationObjectLifecycleService.activateCandidate(
                        locked, previousGeneration, previousObjectKey);
                sessionMapper.updateById(locked);
                lease.assertOwned();
                return true;
            });
            if (!Boolean.TRUE.equals(takenOver)) {
                return handleNonClaimableMerge(session);
            }
            session = requireSession(session.getUploadId());
        }
        VideoUploadStatus activeStatus = VideoUploadStatus.of(session.getStatus());
        if (activeStatus == VideoUploadStatus.MERGED || activeStatus == VideoUploadStatus.VALIDATION_FAILED) {
            return handleNonClaimableMerge(session);
        }
        if (activeStatus != VideoUploadStatus.MERGING) {
            throw new BizException("视频上传会话状态已变化，请刷新后重试");
        }
        VideoUploadSession activeSession = session;

        try {
            if (claimed) {
                videoFinalizationHook.afterDirectStage(
                        VideoFinalizationHook.DirectStage.CLAIMED,
                        activeSession.getUploadId());
            }
            lease.renewOrThrow();
            MultipartObjectInfo existingObject = multipartObjectService.findObject(activeSession.getObjectKey())
                    .orElse(null);
            lease.assertOwned();
            List<MultipartUploadedPart> storedParts = directPartsFromDatabase(activeSession.getUploadId());
            List<MultipartUploadedPart> verifiedParts;
            if (storedParts.isEmpty() && existingObject == null) {
                lease.renewOrThrow();
                storedParts = multipartObjectService.listUploadedParts(
                        activeSession.getObjectKey(), activeSession.getS3UploadId());
                lease.assertOwned();
                validateDirectParts(activeSession, storedParts);
                verifiedParts = verifyClientParts(request.getParts(), storedParts);
                List<MultipartUploadedPart> recoveryParts = verifiedParts;
                transactionTemplate.executeWithoutResult(status -> {
                    VideoUploadSession locked = lockUploadSession(activeSession.getUploadId());
                    requireFinalizationOwner(locked, lease);
                    persistDirectParts(locked, recoveryParts);
                    lease.assertOwned();
                });
            } else if (storedParts.isEmpty()) {
                verifiedParts = List.of();
            } else {
                validateDirectParts(activeSession, storedParts);
                verifiedParts = verifyClientParts(request.getParts(), storedParts);
            }
            lease.renewOrThrow();
            MultipartObjectInfo objectInfo = existingObject != null
                    ? existingObject
                    : multipartObjectService.completeUpload(
                            activeSession.getObjectKey(), activeSession.getS3UploadId(), verifiedParts);
            lease.assertOwned();
            validateCompletedVideoObject(activeSession, objectInfo);
            lease.renewOrThrow();
            VideoMediaInspection inspection = videoMediaProbe.inspect(
                    objectInfo.objectKey(), objectInfo.size(), activeSession.getFileMd5());
            lease.assertOwned();
            return finalizeDirectUpload(activeSession.getUploadId(), student, objectInfo, inspection, lease);
        } catch (RuntimeException e) {
            if (stillOwns(lease)) {
                Boolean objectExists = finalObjectExists(activeSession.getObjectKey());
                if (e instanceof MultipartUploadNotFoundException && Boolean.FALSE.equals(objectExists)) {
                    convergeFailedFinalization(activeSession, "直传分片已过期，请重新发起上传",
                            lease, true, false);
                } else if (e instanceof DirectUploadTerminalException && Boolean.TRUE.equals(objectExists)) {
                    convergeFailedFinalization(activeSession, e.getMessage(), lease, true, true);
                } else if (claimed && Boolean.FALSE.equals(objectExists)) {
                    returnDirectSessionToUploading(activeSession.getUploadId(), lease);
                }
            }
            throw e;
        }
    }

    @Override
    public VideoReviewVO merge(VideoUploadMergeRequest request) {
        VideoUploadSession session = requireSession(request.getUploadId());
        if (PRESIGNED_MULTIPART_MODE.equals(session.getUploadMode())) {
            throw new BizException("浏览器直传会话请调用complete定稿");
        }
        Student student = requireStudent(session.getStudentId());
        ensureCanWriteStudent(student, "video:upload");
        VideoUploadStatus initialStatus = VideoUploadStatus.of(session.getStatus());
        if (initialStatus == VideoUploadStatus.MERGED || initialStatus == VideoUploadStatus.VALIDATION_FAILED) {
            return handleNonClaimableMerge(session);
        }
        if (initialStatus == VideoUploadStatus.FAILED) {
            throw new BizException("上传会话已失效，请重新发起上传");
        }
        if (initialStatus == VideoUploadStatus.UPLOADING) {
            ensureReviewReuploadable(student.getId(), session.getAssessmentYear());
        }
        List<VideoUploadChunk> chunks = chunks(session.getUploadId());
        if (chunks.size() != session.getTotalChunks()) {
            throw new BizException("分片尚未全部上传");
        }
        VideoFinalizeSingleFlight.Lease singleFlight =
                videoFinalizeSingleFlight.tryAcquire(session.getUploadId());
        if (singleFlight == null) {
            return handleNonClaimableMerge(session);
        }
        try (singleFlight;
             VideoProbeCapacityGuard.Lease ignored =
                     videoProbeCapacityGuard.acquire(session.getFileSize())) {
            return mergeWithReservedCapacity(session, student, chunks, singleFlight);
        }
    }

    private VideoReviewVO mergeWithReservedCapacity(VideoUploadSession session, Student student,
                                                    List<VideoUploadChunk> chunks,
                                                    VideoFinalizeSingleFlight.Lease lease) {
        lease.renewOrThrow();
        VideoUploadSession claimedSession = transactionTemplate.execute(status -> {
            VideoUploadSession locked = lockUploadSession(session.getUploadId());
            VideoUploadStatus current = VideoUploadStatus.of(locked.getStatus());
            if (current == VideoUploadStatus.MERGED || current == VideoUploadStatus.VALIDATION_FAILED) {
                return locked;
            }
            if (current == VideoUploadStatus.FAILED) {
                throw new BizException("上传会话已失效，请重新发起上传");
            }
            if (current != VideoUploadStatus.UPLOADING && current != VideoUploadStatus.MERGING) {
                throw new BizException("视频上传会话状态已变化，请刷新后重试");
            }
            if (current == VideoUploadStatus.UPLOADING) {
                ensureReviewReuploadable(locked.getStudentId(), locked.getAssessmentYear());
            }
            locked.setStatus(VideoUploadStatus.MERGING.name());
            long previousGeneration = normalizedFinalizationGeneration(locked);
            String previousObjectKey = locked.getObjectKey();
            advanceFinalizationGeneration(locked, lease);
            locked.setObjectKey(serverCandidateObjectKey(
                    locked.getUploadId(), locked.getFinalizationToken()));
            finalizationObjectLifecycleService.activateCandidate(
                    locked, previousGeneration, previousObjectKey);
            sessionMapper.updateById(locked);
            lease.assertOwned();
            return locked;
        });
        if (claimedSession == null
                || VideoUploadStatus.of(claimedSession.getStatus()) == VideoUploadStatus.MERGED
                || VideoUploadStatus.of(claimedSession.getStatus()) == VideoUploadStatus.VALIDATION_FAILED) {
            return handleNonClaimableMerge(session);
        }
        bestEffortCandidateCleanup(claimedSession.getUploadId());
        String objectKey = claimedSession.getObjectKey();
        List<VideoUploadChunk> sortedChunks = chunks.stream()
                .sorted(Comparator.comparing(VideoUploadChunk::getChunkIndex))
                .toList();
        try {
            videoFinalizationHook.afterServerChunkStage(
                    VideoFinalizationHook.ServerChunkStage.CLAIMED, claimedSession.getUploadId());
            lease.renewOrThrow();
            MultipartObjectInfo existingObject = multipartObjectService.findObject(objectKey).orElse(null);
            lease.assertOwned();
            if (existingObject == null || existingObject.size() != claimedSession.getFileSize()) {
                // objectKey 已在认领事务持久化；节点退出后接管者可重用已合并对象，或以同一 key 安全重合并。
                requireServerChunkSources(sortedChunks);
                if (canServerSideCompose(sortedChunks)) {
                    composeServerSide(sortedChunks, objectKey);
                } else {
                    streamComposeForSmallChunks(sortedChunks, objectKey, claimedSession.getFileSize());
                }
            }
            lease.assertOwned();
            videoFinalizationHook.afterServerChunkStage(
                    VideoFinalizationHook.ServerChunkStage.OBJECT_READY, claimedSession.getUploadId());
            lease.renewOrThrow();
            VideoMediaInspection inspection = videoMediaProbe.inspect(
                    objectKey, claimedSession.getFileSize(), claimedSession.getFileMd5());
            lease.assertOwned();
            videoFinalizationHook.afterServerChunkStage(
                    VideoFinalizationHook.ServerChunkStage.PROBED, claimedSession.getUploadId());
            lease.renewOrThrow();
            // MinIO 合并已在事务外完成；元数据落库（文件对象 + 评审 + 会话状态）单独短事务（P0-11）
            Long reviewId = transactionTemplate.execute(txStatus -> {
                VideoUploadSession locked = lockUploadSession(claimedSession.getUploadId());
                requireFinalizationOwner(locked, lease);
                FileObject file = registerComposedFile(locked, objectKey, inspection);
                finalizationObjectLifecycleService.markRegistered(
                        locked.getUploadId(), lease.generation(), objectKey, file.getId());
                if (StringUtils.hasText(inspection.fingerprint())) {
                    locked.setFileMd5(inspection.fingerprint());
                }
                locked.setDurationSeconds(inspection.durationSeconds());
                locked.setFileId(file.getId());
                VideoReview review = upsertReviewAfterValidation(student, locked.getAssessmentYear(), file,
                        inspection, false, locked.getUploadId());
                locked.setStatus(VideoReviewStatus.of(review.getStatus()) == VideoReviewStatus.VALIDATION_FAILED
                        ? VideoUploadStatus.VALIDATION_FAILED.name()
                        : VideoUploadStatus.MERGED.name());
                locked.setValidationMessage(review.getValidationMessage());
                sessionMapper.updateById(locked);
                lease.assertOwned();
                return review.getId();
            });
            bestEffortCandidateCleanup(claimedSession.getUploadId());
            return detail(reviewId);
        } catch (RuntimeException e) {
            String terminalReason = null;
            if (e instanceof DirectUploadTerminalException
                    || e instanceof ServerChunkTerminalException) {
                terminalReason = e.getMessage();
            } else if (stillOwns(lease)
                    && Boolean.FALSE.equals(finalServerObjectReady(
                    objectKey, claimedSession.getFileSize()))
                    && Boolean.TRUE.equals(serverChunkSourcesMissing(sortedChunks))) {
                // 分片可能在预检后、compose 期间被清理；二次确认最终对象也不可用后才能判为不可恢复。
                terminalReason = "已上传分片不存在或不完整，请重新发起上传";
            }
            if (terminalReason != null && stillOwns(lease)) {
                // 确定性业务终态必须释放活跃槽位，不能永久停在 MERGING。
                convergeFailedFinalization(claimedSession, terminalReason, lease, false, true);
            }
            // 其它基础设施异常保留 MERGING + 持久 objectKey，后续请求可取得更高世代接管。
            throw e;
        }
    }

    private VideoReviewVO handleNonClaimableMerge(VideoUploadSession session) {
        // 认领失败：会话已不在 UPLOADING。重读最新状态做幂等处理。
        VideoUploadSession latest = requireSession(session.getUploadId());
        VideoUploadStatus status = VideoUploadStatus.of(latest.getStatus());
        if (status == VideoUploadStatus.MERGED || status == VideoUploadStatus.VALIDATION_FAILED) {
            // 已合并 / 已出校验结论：回放既有评审结果（幂等成功），不重复 compose+register。
            VideoReview review = existingReview(latest.getStudentId(), latest.getAssessmentYear());
            if (review != null) {
                return detail(review.getId());
            }
        }
        // MERGING（另一次合并进行中）或其它异常态：拒绝重复提交。
        throw new BizException("视频正在合并或已完成，请勿重复提交");
    }

    @Override
    public VideoUploadProgressVO progress(String uploadId) {
        VideoUploadSession session = requireSession(uploadId);
        ensureCanWriteStudent(requireStudent(session.getStudentId()), "video:upload");
        List<MultipartUploadedPart> directParts = List.of();
        if (PRESIGNED_MULTIPART_MODE.equals(session.getUploadMode())) {
            requirePresignedSession(session);
            if (VideoUploadStatus.of(session.getStatus()) == VideoUploadStatus.UPLOADING) {
                directParts = multipartObjectService.listUploadedParts(session.getObjectKey(), session.getS3UploadId());
            } else {
                directParts = directPartsFromDatabase(uploadId);
            }
            session.setUploadedChunks(directParts.size());
            session.setUploadedBytes(directParts.stream().mapToLong(MultipartUploadedPart::size).sum());
        }
        VideoUploadProgressVO vo = new VideoUploadProgressVO();
        vo.setUploadId(session.getUploadId());
        vo.setUploadMode(StringUtils.hasText(session.getUploadMode()) ? session.getUploadMode() : SERVER_CHUNK_MODE);
        vo.setStatus(session.getStatus());
        vo.setTotalChunks(session.getTotalChunks());
        vo.setUploadedChunks(session.getUploadedChunks());
        vo.setUploadedBytes(session.getUploadedBytes());
        vo.setUploadedChunkIndexes(PRESIGNED_MULTIPART_MODE.equals(session.getUploadMode())
                ? directParts.stream().map(part -> part.partNumber() - 1).sorted().toList()
                : uploadedIndexes(uploadId));
        vo.setUploadedParts(directParts.stream().map(this::toUploadedPartVO).toList());
        vo.setFileId(session.getFileId());
        vo.setValidationMessage(session.getValidationMessage());
        return vo;
    }

    @Override
    public PageResult<VideoReviewVO> list(VideoQuery query) {
        VideoQuery q = query == null ? new VideoQuery() : query;
        Page<VideoReview> result = reviewMapper.selectPage(PageQuery.of(q.getPage(), q.getSize()), buildListWrapper(q));
        return new PageResult<>(result.getTotal(), toVOList(result.getRecords()));
    }

    @Override
    public List<ReviewerCandidateVO> reviewerCandidates() {
        return reviewerCandidateCollegeIds().stream()
                .flatMap(collegeId -> userMapper.selectEnabledByRoleAndCollege(REVIEW_TEACHER_ROLE, collegeId).stream())
                .collect(Collectors.toMap(SysUser::getId, this::toReviewerCandidateVO, (left, right) -> left, LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    @Override
    public VideoReviewVO detail(Long id) {
        VideoReview review = requireReview(id);
        ensureReadableReview(review);
        return toVO(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long reviewId, VideoAssignRequest request) {
        // 分配与上传定稿共用 review 行锁，禁止任务已创建后被旧定稿快照覆盖回 WAIT_REVIEW。
        VideoReview review = lockReview(reviewId);
        ensureCanWriteReview(review, "video:assign");
        VideoReviewStatus status = VideoReviewStatus.of(review.getStatus());
        if (status != VideoReviewStatus.WAIT_REVIEW && status != VideoReviewStatus.REVIEWING) {
            throw new BizException("当前状态不可分配评审教师");
        }
        int expected = paramService.getInt("video.reviewerCount", DEFAULT_REVIEWER_COUNT);
        List<Long> reviewerIds = resolveAssignReviewerIds(request, review);
        if (reviewerIds.size() != expected) {
            throw new BizException("评审教师人数需等于系统参数 video.reviewerCount");
        }
        for (Long reviewerId : reviewerIds) {
            SysUser reviewer = requireReviewerForReview(reviewerId, review.getCollegeId());
            VideoReviewTask existing = taskMapper.selectOne(new LambdaQueryWrapper<VideoReviewTask>()
                    .eq(VideoReviewTask::getVideoReviewId, review.getId())
                    .eq(VideoReviewTask::getReviewerId, reviewerId)
                    .last("LIMIT 1"));
            if (existing == null) {
                VideoReviewTask task = new VideoReviewTask();
                task.setVideoReviewId(review.getId());
                task.setStudentId(review.getStudentId());
                task.setCollegeId(review.getCollegeId());
                task.setReviewerId(reviewer.getId());
                task.setReviewerRole("REVIEWER");
                task.setSubmitted(0);
                taskMapper.insert(task);
            }
        }
        review.setStatus(VideoReviewStatus.REVIEWING.name());
        reviewMapper.updateById(review);
        notificationHelper.notifyVideoAssigned(reviewerIds, review.getStudentId(), "video_review", review.getId());
    }

    @Override
    public PageResult<VideoReviewTaskVO> myTasks(String status, Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        LambdaQueryWrapper<VideoReviewTask> wrapper = new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getReviewerId, userId)
                .orderByDesc(VideoReviewTask::getCreatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(VideoReviewTask::getSubmitted, "SUBMITTED".equalsIgnoreCase(status.trim()) ? 1 : 0);
        }
        Page<VideoReviewTask> result = taskMapper.selectPage(PageQuery.of(page, size), wrapper);
        List<VideoReviewTaskVO> records = result.getRecords().stream().map(task -> toTaskVO(task, false)).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public VideoReviewTaskVO taskDetail(Long taskId) {
        VideoReviewTask task = requireTask(taskId);
        ensureTaskOwner(task);
        return toTaskVO(task, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long taskId, VideoScoreRequest request) {
        VideoReviewTask task = requireTask(taskId);
        ensureTaskOwner(task);
        if (task.getSubmitted() != null && task.getSubmitted() == 1) {
            throw new BizException("已提交的评审任务不可修改");
        }
        // 对 review 行加 SELECT ... FOR UPDATE 行锁：串行化并发提交末分的评审事务（P0-10/§7.1 结算丢失更新）。
        // 只有先拿到锁的事务先提交自己的任务，后到的事务在锁释放后才计票，从而能读到已提交的对方任务，杜绝
        // 「两评审并发提交、各自 REPEATABLE_READ 快照只见自己 → 都不结算、review 卡 REVIEWING」的死局。
        VideoReview review = lockReview(task.getVideoReviewId());
        if (VideoReviewStatus.of(review.getStatus()) != VideoReviewStatus.REVIEWING) {
            throw new BizException("当前视频状态不可评分");
        }
        String oldStatus = review.getStatus();
        fillScore(task, request);
        task.setSubmitted(1);
        task.setSubmitTime(LocalDateTime.now());
        taskMapper.updateById(task);
        settleIfReady(review);
        if (!oldStatus.equals(review.getStatus())) {
            auditLogService.record("video", review.getId(), videoTarget(review), "settle",
                    oldStatus, review.getStatus(), "自动结算");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void thirdReview(Long reviewId, VideoThirdReviewRequest request) {
        VideoReview review = requireReview(reviewId);
        ensureCanWriteReview(review, "video:arbitrate");
        if (VideoReviewStatus.of(review.getStatus()) != VideoReviewStatus.NEED_REVIEW) {
            throw new BizException("当前状态不需要复评");
        }
        String oldStatus = review.getStatus();
        VideoReviewTask task = new VideoReviewTask();
        task.setVideoReviewId(review.getId());
        task.setStudentId(review.getStudentId());
        task.setCollegeId(review.getCollegeId());
        task.setReviewerId(requireUser(request.getReviewerId()).getId());
        task.setReviewerRole("THIRD_EXPERT");
        fillScore(task, request);
        task.setSubmitted(1);
        task.setSubmitTime(LocalDateTime.now());
        taskMapper.insert(task);
        settleThirdExpert(review);
        auditLogService.record("video", review.getId(), videoTarget(review), "thirdReview",
                oldStatus, review.getStatus(), request.getComment());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void arbitrate(Long reviewId, VideoArbitrateRequest request) {
        VideoReview review = requireReview(reviewId);
        ensureCanWriteReview(review, "video:arbitrate");
        if (VideoReviewStatus.of(review.getStatus()) != VideoReviewStatus.NEED_REVIEW) {
            throw new BizException("当前状态不需要仲裁");
        }
        String oldStatus = review.getStatus();
        int score = request.getFinalScore();
        review.setArbitrateMode("collegeArbitrate");
        review.setArbitrateReviewer(UserContext.getUserIdOrSystem());
        review.setFinalScore(score);
        review.setFinalConclusion(conclusionByScore(score));
        review.setStatus(VideoReviewStatus.REVIEW_COMPLETED.name());
        review.setLocked(1);
        // 原子条件更新：仅当仍为待仲裁态时才写入，防并发/重复仲裁竞态（P0-10）
        if (reviewMapper.update(review, new LambdaUpdateWrapper<VideoReview>()
                .eq(VideoReview::getId, reviewId).eq(VideoReview::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        auditLogService.record("video", review.getId(), videoTarget(review), "arbitrate",
                oldStatus, review.getStatus(), trimToNull(request.getComment()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long reviewId) {
        VideoReview review = requireReview(reviewId);
        ensureCanWriteReview(review, "video:confirm");
        if (VideoReviewStatus.of(review.getStatus()) != VideoReviewStatus.REVIEW_COMPLETED) {
            throw new BizException("当前状态不可确认");
        }
        String oldStatus = review.getStatus();
        review.setStatus(VideoReviewStatus.CONFIRMED.name());
        review.setConfirmedBy(UserContext.getUserIdOrSystem());
        review.setConfirmedAt(LocalDateTime.now());
        review.setLocked(1);
        // 原子条件更新：仅当仍为待确认态时才写入，防并发/重复确认竞态（P0-10）
        if (reviewMapper.update(review, new LambdaUpdateWrapper<VideoReview>()
                .eq(VideoReview::getId, reviewId).eq(VideoReview::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        auditLogService.record("video", review.getId(), videoTarget(review), "confirm",
                oldStatus, review.getStatus(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnReview(Long reviewId, VideoReturnRequest request) {
        VideoReview review = requireReview(reviewId);
        ensureCanReturnReview(review);
        VideoReviewStatus status = VideoReviewStatus.of(review.getStatus());
        if (status == VideoReviewStatus.CONFIRMED) {
            throw new BizException("已确认视频不可退回");
        }
        if (!returnable(status)) {
            throw new BizException("当前状态不可退回");
        }
        String comment = requiredTrim(request.getComment(), "退回意见不能为空");
        String oldStatus = review.getStatus();
        review.setStatus(VideoReviewStatus.RETURNED.name());
        clearReviewOutcome(review);
        review.setLocked(0);
        // 原子条件更新守卫（37b 同款）：仅当状态未被并发改动（return/confirm/arbitrate）时才退回，防竞态覆盖。
        if (updateReviewReturned(review, oldStatus) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        auditLogService.record("video", review.getId(), videoTarget(review), "return",
                oldStatus, review.getStatus(), comment);
        notificationHelper.notifyReturnedToStudent(review.getStudentId(), "教学能力视频", "RETURN",
                "video_review", review.getId());
    }

    @Override
    public VideoPlaybackVO playback(Long reviewId) {
        VideoReview review = requireReview(reviewId);
        ensurePlayable(review);
        if (review.getVideoFileId() == null) {
            throw new BizException("视频文件不存在");
        }
        int expiry = Math.max(
                paramService.getInt("video.presign.expirySeconds", DEFAULT_PRESIGN_SECONDS),
                VideoMediaAcceptancePolicy.playbackCookieLifetimeSeconds(paramService));
        VideoPlaybackVO vo = new VideoPlaybackVO();
        vo.setUrl("/api/video/reviews/" + reviewId + "/content");
        vo.setExpirySeconds(expiry);
        UserContext.CurrentUser user = UserContext.get();
        String realName = user == null ? "未知用户" : user.getRealName();
        String username = user == null ? "unknown" : user.getUsername();
        vo.setWatermarkText((StringUtils.hasText(realName) ? realName : username) + " " + username + " "
                + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        vo.setIssuedAt(Instant.now().toEpochMilli());
        return vo;
    }

    @Override
    public Long playbackFileId(Long reviewId) {
        VideoReview review = requireReview(reviewId);
        ensurePlayable(review);
        if (review.getVideoFileId() == null) {
            throw new BizException("视频文件不存在");
        }
        return review.getVideoFileId();
    }

    @Override
    public List<VideoReviewTaskVO> tasks(Long reviewId) {
        VideoReview review = requireReview(reviewId);
        ensureCanWriteReview(review, readManagementPermission());
        return taskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                        .eq(VideoReviewTask::getVideoReviewId, reviewId)
                        .orderByAsc(VideoReviewTask::getCreatedAt))
                .stream()
                .map(task -> toTaskVO(task, true))
                .toList();
    }

    private VideoUploadSession currentOpenUploadSession(Long studentId, String assessmentYear) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getStudentId, studentId)
                .eq(VideoUploadSession::getAssessmentYear, assessmentYear)
                .in(VideoUploadSession::getStatus,
                        VideoUploadStatus.UPLOADING.name(), VideoUploadStatus.MERGING.name())
                .orderByDesc(VideoUploadSession::getSlotClaimed)
                .orderByDesc(VideoUploadSession::getCreatedAt)
                .last("LIMIT 1"));
    }

    private void validateResumableSession(VideoUploadSession session, VideoUploadInitRequest request) {
        String fileName = requiredTrim(request.getFileName(), "文件名不能为空");
        String contentType = normalizeContentType(request.getFileName(), request.getContentType());
        int totalChunks = totalParts(request.getSize(), request.getChunkSize());
        if (!Objects.equals(session.getFileName(), fileName)
                || !Objects.equals(session.getFileSize(), request.getSize())
                || !Objects.equals(session.getChunkSize(), request.getChunkSize())
                || !Objects.equals(session.getTotalChunks(), totalChunks)
                || !Objects.equals(session.getContentType(), contentType)) {
            throw new BizException("续传文件与原上传会话不一致");
        }
    }

    private VideoUploadSession updateResumeMetadata(VideoUploadSession session, Integer durationSeconds,
                                                    String upgradedFileHash) {
        boolean updateDuration = durationSeconds != null
                && !Objects.equals(durationSeconds, session.getDurationSeconds());
        boolean updateFileHash = StringUtils.hasText(upgradedFileHash)
                && !Objects.equals(upgradedFileHash, session.getFileMd5());
        if (!updateDuration && !updateFileHash) {
            return requireUploadingResumeSession(session);
        }
        LambdaUpdateWrapper<VideoUploadSession> update = uploadingResumeUpdate(session)
                .set(VideoUploadSession::getUpdatedAt, LocalDateTime.now());
        if (updateDuration) {
            update.set(VideoUploadSession::getDurationSeconds, durationSeconds);
        }
        if (updateFileHash) {
            update.set(VideoUploadSession::getFileMd5, upgradedFileHash);
        }
        transactionTemplate.execute(status -> sessionMapper.update(null, update));
        return requireUploadingResumeSession(session);
    }

    private VideoUploadSession updateDirectResume(VideoUploadSession session, MultipartUploadPlan plan,
                                                   Integer durationSeconds) {
        LambdaUpdateWrapper<VideoUploadSession> update = uploadingResumeUpdate(session)
                .set(VideoUploadSession::getPresignExpiresAt,
                        LocalDateTime.ofInstant(plan.expiresAt(), ZoneId.systemDefault()))
                .set(VideoUploadSession::getUploadedChunks, plan.uploadedParts().size())
                .set(VideoUploadSession::getUploadedBytes,
                        plan.uploadedParts().stream().mapToLong(MultipartUploadedPart::size).sum())
                .set(VideoUploadSession::getUpdatedAt, LocalDateTime.now());
        if (durationSeconds != null) {
            update.set(VideoUploadSession::getDurationSeconds, durationSeconds);
        }
        transactionTemplate.execute(status -> sessionMapper.update(null, update));
        return requireUploadingResumeSession(session);
    }

    private LambdaUpdateWrapper<VideoUploadSession> uploadingResumeUpdate(VideoUploadSession session) {
        return new LambdaUpdateWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getId, session.getId())
                .eq(VideoUploadSession::getUploadId, session.getUploadId())
                .eq(VideoUploadSession::getStatus, VideoUploadStatus.UPLOADING.name());
    }

    private VideoUploadSession requireUploadingResumeSession(VideoUploadSession attempted) {
        VideoUploadSession latest = sessionMapper.selectById(attempted.getId());
        if (latest == null || !Objects.equals(latest.getUploadId(), attempted.getUploadId())) {
            throw new BizException("上传会话不存在或已失效");
        }
        VideoUploadStatus status = VideoUploadStatus.of(latest.getStatus());
        if (status == VideoUploadStatus.UPLOADING) {
            return latest;
        }
        if (status == VideoUploadStatus.FAILED) {
            throw new BizException("上传会话已失效，请重新发起上传");
        }
        if (status == VideoUploadStatus.MERGING) {
            throw new BizException("视频正在定稿，请稍后刷新结果");
        }
        if (status == VideoUploadStatus.MERGED || status == VideoUploadStatus.VALIDATION_FAILED) {
            throw new BizException("视频已完成，请刷新结果");
        }
        throw new BizException("上传会话状态已变化，请刷新后重试");
    }

    private void failDirectSession(VideoUploadSession session) {
        VideoUploadSession failed = transactionTemplate.execute(status -> {
            VideoUploadSession locked = lockUploadSession(session.getUploadId());
            if (VideoUploadStatus.of(locked.getStatus()) != VideoUploadStatus.UPLOADING) {
                throw new BizException("上传会话状态已变化，请刷新后重试");
            }
            locked.setStatus(VideoUploadStatus.FAILED.name());
            if (sessionMapper.updateById(locked) != 1) {
                throw new BizException("上传会话状态已变化，请刷新后重试");
            }
            finalizationObjectLifecycleService.markFailed(locked.getUploadId());
            return locked;
        });
        try {
            if (failed != null && StringUtils.hasText(failed.getS3UploadId())
                    && StringUtils.hasText(failed.getObjectKey())) {
                multipartObjectService.abortUpload(
                        requireVideoObjectKey(failed.getObjectKey()), failed.getS3UploadId());
            }
        } finally {
            if (failed != null) {
                bestEffortCandidateCleanup(failed.getUploadId());
            }
        }
    }

    private DirectUploadStart startDirectUpload(Student student, VideoUploadInitRequest request,
                                                String assessmentYear, String fileHash) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String objectKey = VIDEO_BIZ_TYPE + "/" + UUID.randomUUID().toString().replace("-", "") + ".mp4";
        int totalParts = totalParts(request.getSize(), request.getChunkSize());
        MultipartUploadPlan plan = multipartObjectService.createUpload(objectKey, "video/mp4", Map.of(
                        "upload-id", uploadId,
                        "owner-id", String.valueOf(UserContext.getUserIdOrSystem()),
                        "expected-size", String.valueOf(request.getSize()),
                        "fingerprint", fileHash),
                request.getSize(), request.getChunkSize(), totalParts,
                minioProperties.getPresignExpirySeconds());
        try {
            VideoUploadSession session = transactionTemplate.execute(status -> createDirectSession(
                    student, request, assessmentYear, fileHash, uploadId, plan));
            return new DirectUploadStart(session, plan);
        } catch (RuntimeException e) {
            multipartObjectService.abortUpload(objectKey, plan.multipartUploadId());
            if (e instanceof DuplicateKeyException) {
                throw new BizException("该学生本年度已有视频正在上传，请刷新后继续原上传");
            }
            throw e;
        }
    }

    private VideoUploadSession createDirectSession(Student student, VideoUploadInitRequest request,
                                                    String assessmentYear, String fileHash, String uploadId,
                                                    MultipartUploadPlan uploadPlan) {
        VideoUploadSession session = new VideoUploadSession();
        session.setUploadId(uploadId);
        session.setUploadMode(PRESIGNED_MULTIPART_MODE);
        session.setS3UploadId(uploadPlan.multipartUploadId());
        session.setObjectKey(uploadPlan.objectKey());
        session.setPresignExpiresAt(LocalDateTime.ofInstant(uploadPlan.expiresAt(), ZoneId.systemDefault()));
        session.setSlotClaimed(1);
        session.setStudentId(student.getId());
        session.setCollegeId(student.getCollegeId());
        session.setAssessmentYear(assessmentYear);
        session.setFileMd5(fileHash);
        session.setFileName(requiredTrim(request.getFileName(), "文件名不能为空"));
        session.setFileSize(request.getSize());
        session.setContentType(normalizeContentType(request.getFileName(), request.getContentType()));
        session.setChunkSize(request.getChunkSize());
        session.setTotalChunks(totalParts(request.getSize(), request.getChunkSize()));
        session.setUploadedChunks(0);
        session.setUploadedBytes(0L);
        session.setDurationSeconds(request.getDurationSeconds());
        session.setStatus(VideoUploadStatus.UPLOADING.name());
        sessionMapper.insert(session);
        return session;
    }

    private record DirectUploadStart(VideoUploadSession session, MultipartUploadPlan plan) {
    }

    private VideoUploadInitVO toUploadInitVO(VideoUploadSession session, MultipartUploadPlan uploadPlan) {
        VideoUploadInitVO vo = new VideoUploadInitVO();
        vo.setUploadId(session.getUploadId());
        vo.setUploadMode(StringUtils.hasText(session.getUploadMode()) ? session.getUploadMode() : SERVER_CHUNK_MODE);
        vo.setPartSize(session.getChunkSize());
        vo.setInstantHit(false);
        vo.setStatus(session.getStatus());
        vo.setValidationMessage(session.getValidationMessage());
        vo.setFileId(session.getFileId());
        if (uploadPlan == null) {
            vo.setUploadedChunks(uploadedIndexes(session.getUploadId()));
            vo.setUploadedParts(List.of());
            vo.setParts(List.of());
            return vo;
        }
        vo.setUploadedChunks(uploadPlan.uploadedParts().stream()
                .map(part -> part.partNumber() - 1)
                .sorted()
                .toList());
        vo.setUploadedParts(uploadPlan.uploadedParts().stream().map(this::toUploadedPartVO).toList());
        vo.setParts(uploadPlan.parts().stream().map(part -> {
            VideoPresignedPartVO item = new VideoPresignedPartVO();
            item.setPartNumber(part.partNumber());
            item.setUrl(part.url());
            item.setExpiresAt(part.expiresAt());
            return item;
        }).toList());
        return vo;
    }

    private VideoUploadInitVO toMergingUploadInitVO(VideoUploadSession session) {
        List<MultipartUploadedPart> verifiedParts = directPartsFromDatabase(session.getUploadId());
        validateDirectParts(session, verifiedParts);
        VideoUploadInitVO vo = new VideoUploadInitVO();
        vo.setUploadId(session.getUploadId());
        vo.setUploadMode(PRESIGNED_MULTIPART_MODE);
        vo.setPartSize(session.getChunkSize());
        vo.setInstantHit(false);
        vo.setFileId(session.getFileId());
        vo.setUploadedChunks(verifiedParts.stream()
                .map(part -> part.partNumber() - 1)
                .toList());
        vo.setUploadedParts(verifiedParts.stream().map(this::toUploadedPartVO).toList());
        vo.setParts(List.of());
        vo.setStatus(session.getStatus());
        vo.setValidationMessage(session.getValidationMessage());
        return vo;
    }

    private VideoUploadedPartVO toUploadedPartVO(MultipartUploadedPart part) {
        VideoUploadedPartVO vo = new VideoUploadedPartVO();
        vo.setPartNumber(part.partNumber());
        vo.setEtag(part.eTag());
        vo.setSize(part.size());
        return vo;
    }

    private VideoReviewVO finalizeDirectUpload(String uploadId, Student student,
                                               MultipartObjectInfo objectInfo,
                                               VideoMediaInspection inspection,
                                               VideoFinalizeSingleFlight.Lease lease) {
        lease.renewOrThrow();
        Long reviewId = transactionTemplate.execute(status -> {
            VideoUploadSession locked = lockUploadSession(uploadId);
            VideoUploadStatus uploadStatus = VideoUploadStatus.of(locked.getStatus());
            if (uploadStatus == VideoUploadStatus.MERGED || uploadStatus == VideoUploadStatus.VALIDATION_FAILED) {
                VideoReview existing = existingReview(locked.getStudentId(), locked.getAssessmentYear());
                if (existing == null) {
                    throw new BizException("视频已定稿但评审记录不存在");
                }
                return existing.getId();
            }
            if (uploadStatus != VideoUploadStatus.MERGING) {
                throw new BizException("视频上传会话状态已变化，请刷新后重试");
            }
            requireFinalizationOwner(locked, lease);
            FileObject file = fileObjectMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                    .eq(FileObject::getBucket, objectInfo.bucket())
                    .eq(FileObject::getObjectKey, objectInfo.objectKey())
                    .last("LIMIT 1"));
            if (file == null) {
                file = registerComposedFile(locked, objectInfo.objectKey(), inspection);
            }
            finalizationObjectLifecycleService.markRegistered(
                    locked.getUploadId(), lease.generation(), objectInfo.objectKey(), file.getId());
            if (StringUtils.hasText(inspection.fingerprint())) {
                locked.setFileMd5(inspection.fingerprint());
            }
            locked.setDurationSeconds(inspection.durationSeconds());
            locked.setFileId(file.getId());
            VideoReview review = upsertReviewAfterValidation(student, locked.getAssessmentYear(), file,
                    inspection, false, locked.getUploadId());
            locked.setStatus(VideoReviewStatus.of(review.getStatus()) == VideoReviewStatus.VALIDATION_FAILED
                    ? VideoUploadStatus.VALIDATION_FAILED.name()
                    : VideoUploadStatus.MERGED.name());
            locked.setValidationMessage(review.getValidationMessage());
            sessionMapper.updateById(locked);
            lease.assertOwned();
            return review.getId();
        });
        if (reviewId == null) {
            throw new BizException("视频定稿事务未返回结果");
        }
        return detail(reviewId);
    }

    private void persistDirectParts(VideoUploadSession session, List<MultipartUploadedPart> parts) {
        for (MultipartUploadedPart part : parts) {
            int chunkIndex = part.partNumber() - 1;
            VideoUploadChunk chunk = chunkMapper.selectOne(new LambdaQueryWrapper<VideoUploadChunk>()
                    .eq(VideoUploadChunk::getUploadId, session.getUploadId())
                    .eq(VideoUploadChunk::getChunkIndex, chunkIndex)
                    .last("LIMIT 1"));
            if (chunk == null) {
                chunk = new VideoUploadChunk();
                chunk.setUploadId(session.getUploadId());
                chunk.setChunkIndex(chunkIndex);
                chunk.setPartNumber(part.partNumber());
                chunk.setEtag(part.eTag());
                chunk.setChunkSize(part.size());
                chunk.setObjectKey(session.getObjectKey());
                chunk.setUploadedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);
            } else {
                chunk.setPartNumber(part.partNumber());
                chunk.setEtag(part.eTag());
                chunk.setChunkSize(part.size());
                chunk.setObjectKey(session.getObjectKey());
                chunk.setUploadedAt(LocalDateTime.now());
                chunkMapper.updateById(chunk);
            }
        }
        sessionMapper.update(new LambdaUpdateWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, session.getUploadId())
                .set(VideoUploadSession::getUploadedChunks, parts.size())
                .set(VideoUploadSession::getUploadedBytes,
                        parts.stream().mapToLong(MultipartUploadedPart::size).sum()));
    }

    private List<MultipartUploadedPart> directPartsFromDatabase(String uploadId) {
        return chunks(uploadId).stream()
                .filter(chunk -> chunk.getPartNumber() != null && StringUtils.hasText(chunk.getEtag()))
                .map(chunk -> new MultipartUploadedPart(
                        chunk.getPartNumber(), chunk.getEtag(), chunk.getChunkSize() == null ? 0L : chunk.getChunkSize()))
                .sorted(Comparator.comparingInt(MultipartUploadedPart::partNumber))
                .toList();
    }

    private List<MultipartUploadedPart> verifyClientParts(List<MultipartCompletedPartRequest> requestedParts,
                                                          List<MultipartUploadedPart> storedParts) {
        Map<Integer, MultipartUploadedPart> stored = storedParts.stream().collect(Collectors.toMap(
                MultipartUploadedPart::partNumber, part -> part, (left, right) -> left, LinkedHashMap::new));
        if (requestedParts == null || requestedParts.size() != stored.size()) {
            throw new BizException("上传分片数量不一致");
        }
        Map<Integer, MultipartUploadedPart> verified = new LinkedHashMap<>();
        for (MultipartCompletedPartRequest requested : requestedParts) {
            if (requested == null || requested.getPartNumber() == null
                    || verified.containsKey(requested.getPartNumber())) {
                throw new BizException("上传分片序号重复或为空");
            }
            MultipartUploadedPart storedPart = stored.get(requested.getPartNumber());
            if (storedPart == null || !normalizeETag(storedPart.eTag()).equals(normalizeETag(requested.getEtag()))) {
                throw new BizException("上传分片ETag校验失败");
            }
            verified.put(requested.getPartNumber(), storedPart);
        }
        return verified.values().stream()
                .sorted(Comparator.comparingInt(MultipartUploadedPart::partNumber))
                .toList();
    }

    private void validateDirectParts(VideoUploadSession session, List<MultipartUploadedPart> parts) {
        if (parts.size() != session.getTotalChunks()) {
            throw new BizException("分片尚未全部上传");
        }
        long totalSize = 0L;
        for (int index = 0; index < parts.size(); index++) {
            MultipartUploadedPart part = parts.get(index);
            if (part.partNumber() != index + 1) {
                throw new BizException("上传分片序号必须连续");
            }
            long expectedSize = index == parts.size() - 1
                    ? session.getFileSize() - session.getChunkSize() * index
                    : session.getChunkSize();
            if (part.size() != expectedSize) {
                throw new BizException("上传分片大小不符合会话约束");
            }
            totalSize += part.size();
        }
        if (totalSize != session.getFileSize()) {
            throw new BizException("上传对象总大小不一致");
        }
    }

    private void validateCompletedVideoObject(VideoUploadSession session, MultipartObjectInfo objectInfo) {
        requireVideoObjectKey(objectInfo.objectKey());
        if (!minioProperties.getBucket().equals(objectInfo.bucket())
                || !session.getObjectKey().equals(objectInfo.objectKey())
                || objectInfo.size() != session.getFileSize()) {
            throw new DirectUploadTerminalException("定稿对象与上传会话不一致");
        }
        if (!"video/mp4".equals(normalizeContentType(session.getFileName(), objectInfo.contentType()))) {
            throw new DirectUploadTerminalException("定稿对象类型不合法");
        }
    }

    private void convergeFailedFinalization(VideoUploadSession session, String reason,
                                            VideoFinalizeSingleFlight.Lease lease,
                                            boolean clearDirectState, boolean cleanupObject) {
        Boolean changed = transactionTemplate.execute(status -> {
            VideoUploadSession locked = lockUploadSession(session.getUploadId());
            if (VideoUploadStatus.of(locked.getStatus()) != VideoUploadStatus.MERGING
                    || !Objects.equals(locked.getFinalizationToken(), lease.generation())) {
                return false;
            }
            lease.assertOwned();
            locked.setStatus(VideoUploadStatus.FAILED.name());
            locked.setValidationMessage(reason);
            if (clearDirectState) {
                locked.setS3UploadId(null);
                locked.setPresignExpiresAt(null);
                locked.setUploadedChunks(0);
                locked.setUploadedBytes(0L);
            }
            sessionMapper.updateById(locked);
            if (clearDirectState) {
                // MyBatis-Plus 默认忽略实体中的 null；失效直传会话必须显式清空上传凭据。
                sessionMapper.update(null, new LambdaUpdateWrapper<VideoUploadSession>()
                        .eq(VideoUploadSession::getUploadId, locked.getUploadId())
                        .eq(VideoUploadSession::getStatus, VideoUploadStatus.FAILED.name())
                        .eq(VideoUploadSession::getFinalizationToken, lease.generation())
                        .set(VideoUploadSession::getS3UploadId, null)
                        .set(VideoUploadSession::getPresignExpiresAt, null));
            }
            chunkMapper.update(null, new LambdaUpdateWrapper<VideoUploadChunk>()
                    .eq(VideoUploadChunk::getUploadId, locked.getUploadId())
                    .set(VideoUploadChunk::getUpdatedBy, UserContext.getUserIdOrSystem())
                    .set(VideoUploadChunk::getUpdatedAt, LocalDateTime.now())
                    .setSql("deleted = 1"));
            finalizationObjectLifecycleService.markFailed(locked.getUploadId());
            lease.assertOwned();
            return true;
        });
        if (!Boolean.TRUE.equals(changed) || !cleanupObject) {
            return;
        }
        bestEffortCandidateCleanup(session.getUploadId());
    }

    private Boolean finalObjectExists(String objectKey) {
        try {
            return multipartObjectService.findObject(objectKey).isPresent();
        } catch (RuntimeException lookupFailure) {
            log.warn("视频定稿异常后无法确认最终对象状态，保留会话供后续接管: objectKey={}, message={}",
                    objectKey, lookupFailure.getMessage());
            return null;
        }
    }

    private void requirePresignedSession(VideoUploadSession session) {
        if (!PRESIGNED_MULTIPART_MODE.equals(session.getUploadMode())
                || !StringUtils.hasText(session.getS3UploadId())) {
            throw new BizException("该会话不是浏览器直传会话");
        }
        requireVideoObjectKey(session.getObjectKey());
    }

    private String requireVideoObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith(VIDEO_BIZ_TYPE + "/")
                || objectKey.contains("..")) {
            throw new BizException("视频对象Key不合法");
        }
        return objectKey;
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

    private int totalParts(long fileSize, long partSize) {
        long total = (fileSize + partSize - 1) / partSize;
        if (total > MAX_DIRECT_PARTS) {
            throw new BizException("视频分片数量超过限制");
        }
        return (int) total;
    }

    private void validateDirectChunkPlan(Long fileSize, Long partSize) {
        if (fileSize == null || partSize == null || partSize <= 0 || partSize > MAX_DIRECT_PART_SIZE) {
            throw new BizException("分片大小不合法");
        }
        int total = totalParts(fileSize, partSize);
        if (total > 1 && partSize < MIN_COMPOSE_PART_SIZE) {
            throw new BizException("除最后一片外，直传分片不得小于5MiB");
        }
    }

    private VideoUploadSession lockUploadSession(String uploadId) {
        VideoUploadSession session = sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, uploadId)
                .last("FOR UPDATE"));
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "上传会话不存在");
        }
        return session;
    }

    private void requireFinalizationOwner(VideoUploadSession session,
                                          VideoFinalizeSingleFlight.Lease lease) {
        if (VideoUploadStatus.of(session.getStatus()) != VideoUploadStatus.MERGING
                || !Objects.equals(session.getFinalizationToken(), lease.generation())) {
            throw new BizException("视频定稿执行权已转移，本次结果已丢弃");
        }
        lease.assertOwned();
    }

    private void advanceFinalizationGeneration(VideoUploadSession session,
                                               VideoFinalizeSingleFlight.Lease lease) {
        long current = normalizedFinalizationGeneration(session);
        if (current == Long.MAX_VALUE) {
            throw new BizException("视频定稿世代已耗尽，请联系管理员");
        }
        long next = current + 1L;
        session.setFinalizationToken(next);
        lease.bindGeneration(next);
    }

    private boolean stillOwns(VideoFinalizeSingleFlight.Lease lease) {
        try {
            lease.assertOwned();
            return true;
        } catch (BizException ignored) {
            return false;
        }
    }

    private long normalizedFinalizationGeneration(VideoUploadSession session) {
        return session.getFinalizationToken() == null ? 0L : session.getFinalizationToken();
    }

    private void returnDirectSessionToUploading(
            String uploadId, VideoFinalizeSingleFlight.Lease lease) {
        Boolean changed = transactionTemplate.execute(status -> {
            VideoUploadSession locked = lockUploadSession(uploadId);
            if (VideoUploadStatus.of(locked.getStatus()) != VideoUploadStatus.MERGING
                    || !Objects.equals(locked.getFinalizationToken(), lease.generation())) {
                return false;
            }
            lease.assertOwned();
            locked.setStatus(VideoUploadStatus.UPLOADING.name());
            if (sessionMapper.updateById(locked) != 1) {
                return false;
            }
            finalizationObjectLifecycleService.retireActiveCandidates(uploadId);
            lease.assertOwned();
            return true;
        });
        if (Boolean.TRUE.equals(changed)) {
            bestEffortCandidateCleanup(uploadId);
        }
    }

    private String serverCandidateObjectKey(String uploadId, long generation) {
        return VIDEO_BIZ_TYPE + "/server-finalized/" + uploadId
                + "/g-" + generation + ".mp4";
    }

    private void bestEffortCandidateCleanup(String uploadId) {
        try {
            finalizationObjectReconciler.bestEffortPending(uploadId);
        } catch (RuntimeException cleanupFailure) {
            log.warn("视频定稿候选对象即时对账失败，持久任务将重试: uploadId={}, message={}",
                    uploadId, cleanupFailure.getMessage());
        }
    }

    private VideoUploadSession createSession(Student student, VideoUploadInitRequest request) {
        VideoUploadSession session = new VideoUploadSession();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
        session.setUploadMode(SERVER_CHUNK_MODE);
        session.setSlotClaimed(1);
        session.setStudentId(student.getId());
        session.setCollegeId(student.getCollegeId());
        session.setAssessmentYear(requiredTrim(request.getAssessmentYear(), "考核年度不能为空"));
        session.setFileMd5(requiredTrim(request.getFileMd5(), "文件MD5不能为空").toLowerCase(Locale.ROOT));
        session.setFileName(requiredTrim(request.getFileName(), "文件名不能为空"));
        session.setFileSize(request.getSize());
        session.setContentType(normalizeContentType(request.getFileName(), request.getContentType()));
        session.setChunkSize(request.getChunkSize());
        session.setTotalChunks((int) Math.ceil((double) request.getSize() / request.getChunkSize()));
        session.setUploadedChunks(0);
        session.setUploadedBytes(0L);
        session.setDurationSeconds(request.getDurationSeconds());
        session.setStatus(VideoUploadStatus.UPLOADING.name());
        sessionMapper.insert(session);
        return session;
    }

    private void validateUploadMeta(String filename, String contentType, long size) {
        if (size <= 0) {
            throw new BizException("视频文件不能为空");
        }
        long maxSize = maxVideoSize();
        if (size > maxSize) {
            throw new BizException("视频大小超过限制");
        }
        String normalized = normalizeContentType(filename, contentType);
        if (!"video/mp4".equals(normalized)) {
            throw new BizException("视频格式必须为MP4");
        }
    }

    private VideoReview upsertReviewAfterValidation(Student student, String year, FileObject file,
                                                     VideoMediaInspection inspection,
                                                     boolean instantHit, String currentUploadId) {
        // review 尚不存在时无行可锁，先锁学生作为 (student, year) 创建互斥点；存在时再锁 review，
        // 与 assign/评分等状态迁移共享同一行锁，杜绝整实体旧快照覆盖。
        student = lockStudentForVideo(student.getId());
        VideoReview review = existingReviewForUpdate(student.getId(), year);
        boolean returnedReupload = false;
        if (review == null) {
            review = new VideoReview();
            review.setStudentId(student.getId());
            review.setCollegeId(student.getCollegeId());
            review.setAssessmentYear(requiredTrim(year, "考核年度不能为空"));
        } else {
            VideoReviewStatus oldStatus = VideoReviewStatus.of(review.getStatus());
            ensureReuploadable(review);
            returnedReupload = oldStatus == VideoReviewStatus.RETURNED;
            if (returnedReupload) {
                resetReturnedReviewForReupload(review, currentUploadId);
            }
        }
        review.setVideoFileId(file.getId());
        review.setVideoFileName(file.getOriginalName());
        review.setFileMd5(file.getMd5());
        review.setDurationSeconds(inspection.durationSeconds());
        review.setStatus(VideoReviewStatus.VALIDATING.name());
        review.setLocked(0);
        validateMergedVideo(review, file, inspection, instantHit);
        if (review.getId() == null) {
            reviewMapper.insert(review);
        } else {
            if (returnedReupload) {
                updateReviewAfterReturnedReupload(review);
            } else {
                reviewMapper.updateById(review);
            }
        }
        if (returnedReupload && VideoReviewStatus.of(review.getStatus()) == VideoReviewStatus.WAIT_REVIEW) {
            notificationHelper.notifySubmitted(review.getCollegeId(), review.getStudentId(), "教学能力视频",
                    "SECOND_REVIEW", "video_review", review.getId());
        }
        return review;
    }

    private void ensureReviewReuploadable(Long studentId, String assessmentYear) {
        VideoReview existing = existingReview(studentId, assessmentYear);
        if (existing != null) {
            ensureReuploadable(existing);
        }
    }

    private void ensureReuploadable(VideoReview review) {
        VideoReviewStatus status = VideoReviewStatus.of(review.getStatus());
        Long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, review.getId()));
        if (status.locked() || !status.reuploadable()
                || (status != VideoReviewStatus.RETURNED && taskCount != null && taskCount > 0)) {
            throw new DirectUploadTerminalException("评审进行中不可重新上传");
        }
    }

    private static final class DirectUploadTerminalException extends BizException {

        private DirectUploadTerminalException(String message) {
            super(message);
        }
    }

    private static final class ServerChunkTerminalException extends BizException {

        private ServerChunkTerminalException(String message) {
            super(message);
        }
    }

    private void ensureCanReturnReview(VideoReview review) {
        if (UserContext.hasPermission("video:confirm")) {
            ensureCanWriteReview(review, "video:confirm");
            return;
        }
        if (UserContext.hasPermission("video:arbitrate")) {
            ensureCanWriteReview(review, "video:arbitrate");
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权退回该视频");
    }

    private boolean returnable(VideoReviewStatus status) {
        return status == VideoReviewStatus.WAIT_REVIEW
                || status == VideoReviewStatus.REVIEWING
                || status == VideoReviewStatus.NEED_REVIEW
                || status == VideoReviewStatus.REVIEW_COMPLETED;
    }

    private void resetReturnedReviewForReupload(VideoReview review, String currentUploadId) {
        archiveReviewTasks(review.getId());
        archiveUploadSessions(review.getStudentId(), review.getAssessmentYear(), currentUploadId);
        clearReviewOutcome(review);
        review.setLocked(0);
    }

    private void archiveReturnedUploadSessions(Long studentId, String assessmentYear, String currentUploadId) {
        VideoReview existing = existingReview(studentId, assessmentYear);
        if (existing != null && VideoReviewStatus.of(existing.getStatus()) == VideoReviewStatus.RETURNED) {
            archiveUploadSessions(studentId, requiredTrim(assessmentYear, "考核年度不能为空"), currentUploadId);
        }
    }

    private void clearReviewOutcome(VideoReview review) {
        review.setFinalScore(null);
        review.setFinalConclusion(null);
        review.setArbitrateReviewer(null);
        review.setArbitrateMode(null);
        review.setConfirmedBy(null);
        review.setConfirmedAt(null);
    }

    private int updateReviewReturned(VideoReview review, String oldStatus) {
        return reviewMapper.update(null, new LambdaUpdateWrapper<VideoReview>()
                .eq(VideoReview::getId, review.getId())
                .eq(VideoReview::getStatus, oldStatus)
                .set(VideoReview::getStatus, review.getStatus())
                .set(VideoReview::getLocked, review.getLocked())
                .set(VideoReview::getFinalScore, null)
                .set(VideoReview::getFinalConclusion, null)
                .set(VideoReview::getArbitrateReviewer, null)
                .set(VideoReview::getArbitrateMode, null)
                .set(VideoReview::getConfirmedBy, null)
                .set(VideoReview::getConfirmedAt, null));
    }

    private void updateReviewAfterReturnedReupload(VideoReview review) {
        reviewMapper.update(null, new LambdaUpdateWrapper<VideoReview>()
                .eq(VideoReview::getId, review.getId())
                .set(VideoReview::getVideoFileId, review.getVideoFileId())
                .set(VideoReview::getVideoFileName, review.getVideoFileName())
                .set(VideoReview::getFileMd5, review.getFileMd5())
                .set(VideoReview::getDurationSeconds, review.getDurationSeconds())
                .set(VideoReview::getFormatCheck, review.getFormatCheck())
                .set(VideoReview::getValidationMessage, review.getValidationMessage())
                .set(VideoReview::getStatus, review.getStatus())
                .set(VideoReview::getLocked, review.getLocked())
                .set(VideoReview::getFinalScore, null)
                .set(VideoReview::getFinalConclusion, null)
                .set(VideoReview::getArbitrateReviewer, null)
                .set(VideoReview::getArbitrateMode, null)
                .set(VideoReview::getConfirmedBy, null)
                .set(VideoReview::getConfirmedAt, null));
    }

    private void archiveReviewTasks(Long reviewId) {
        taskMapper.update(null, new LambdaUpdateWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, reviewId)
                .set(VideoReviewTask::getUpdatedBy, UserContext.getUserIdOrSystem())
                .set(VideoReviewTask::getUpdatedAt, LocalDateTime.now())
                .setSql("reviewer_id = id, deleted = 1"));
    }

    private void archiveUploadSessions(Long studentId, String assessmentYear, String currentUploadId) {
        LambdaQueryWrapper<VideoUploadSession> wrapper = new LambdaQueryWrapper<VideoUploadSession>()
                .select(VideoUploadSession::getUploadId)
                .eq(VideoUploadSession::getStudentId, studentId)
                .eq(VideoUploadSession::getAssessmentYear, assessmentYear);
        if (StringUtils.hasText(currentUploadId)) {
            wrapper.ne(VideoUploadSession::getUploadId, currentUploadId);
        }
        List<String> uploadIds = sessionMapper.selectList(wrapper).stream()
                .map(VideoUploadSession::getUploadId)
                .toList();
        if (uploadIds.isEmpty()) {
            return;
        }
        chunkMapper.update(null, new LambdaUpdateWrapper<VideoUploadChunk>()
                .in(VideoUploadChunk::getUploadId, uploadIds)
                .set(VideoUploadChunk::getUpdatedBy, UserContext.getUserIdOrSystem())
                .set(VideoUploadChunk::getUpdatedAt, LocalDateTime.now())
                .setSql("deleted = 1"));
        LambdaUpdateWrapper<VideoUploadSession> sessionUpdate = new LambdaUpdateWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getStudentId, studentId)
                .eq(VideoUploadSession::getAssessmentYear, assessmentYear)
                .set(VideoUploadSession::getUpdatedBy, UserContext.getUserIdOrSystem())
                .set(VideoUploadSession::getUpdatedAt, LocalDateTime.now())
                .setSql("deleted = 1");
        if (StringUtils.hasText(currentUploadId)) {
            sessionUpdate.ne(VideoUploadSession::getUploadId, currentUploadId);
        }
        sessionMapper.update(null, sessionUpdate);
    }

    private void validateMergedVideo(VideoReview review, FileObject file,
                                     VideoMediaInspection inspection, boolean instantHit) {
        String normalized = normalizeContentType(file.getOriginalName(), file.getContentType());
        VideoMediaAcceptancePolicy.Result acceptance = VideoMediaAcceptancePolicy.validate(
                normalized, file.getSize(), inspection, videoMediaProbe, paramService);
        if (!acceptance.accepted()) {
            review.setFormatCheck("FAIL");
            review.setStatus(VideoReviewStatus.VALIDATION_FAILED.name());
            review.setValidationMessage(acceptance.message());
            return;
        }
        review.setFormatCheck("PASS");
        review.setStatus(VideoReviewStatus.WAIT_REVIEW.name());
        review.setValidationMessage(instantHit ? "文件秒传命中，校验通过" : "校验通过");
    }

    private FileObject registerComposedFile(VideoUploadSession session, String objectKey,
                                            VideoMediaInspection inspection) {
        FileObject file = new FileObject();
        file.setOriginalName(session.getFileName());
        file.setStoredName(objectKey.substring(objectKey.indexOf('/') + 1));
        file.setBucket(minioProperties.getBucket());
        file.setObjectKey(objectKey);
        file.setSize(session.getFileSize());
        file.setContentType("video/mp4");
        file.setMd5(inspection.fingerprint());
        file.setBizType(VIDEO_BIZ_TYPE);
        file.setStatus("READY");
        boolean hashVerified = StringUtils.hasText(inspection.fingerprint());
        file.setChecksumAlgorithm(hashVerified ? VideoMediaProbe.CHECKSUM_ALGORITHM : null);
        file.setContentHashVerified(hashVerified ? 1 : 0);
        file.setMediaCodec(inspection.codec());
        file.setMediaValidationPolicyHash(inspection.policyHash());
        file.setMediaProbeVersion(inspection.probeVersion());
        file.setUploaderId(UserContext.getUserIdOrSystem());
        file.setUploadTime(LocalDateTime.now());
        fileObjectMapper.insert(file);
        return file;
    }

    private VerifiedInstantHit findVerifiedInstantHit(Student student, String fingerprint) {
        String currentPolicyHash = videoMediaProbe.currentPolicyHash();
        List<VideoReview> sources = reviewMapper.selectList(new LambdaQueryWrapper<VideoReview>()
                .eq(VideoReview::getStudentId, student.getId())
                .eq(VideoReview::getFileMd5, fingerprint)
                .eq(VideoReview::getFormatCheck, "PASS")
                .isNotNull(VideoReview::getDurationSeconds)
                .orderByDesc(VideoReview::getId));
        for (VideoReview source : sources) {
            if (source.getVideoFileId() == null) {
                continue;
            }
            FileObject file = fileObjectMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                    .eq(FileObject::getId, source.getVideoFileId())
                    .eq(FileObject::getBizType, VIDEO_BIZ_TYPE)
                    .eq(FileObject::getMd5, fingerprint)
                    .eq(FileObject::getUploaderId, UserContext.getUserIdOrSystem())
                    .eq(FileObject::getChecksumAlgorithm, VideoMediaProbe.CHECKSUM_ALGORITHM)
                    .eq(FileObject::getContentHashVerified, 1)
                    .eq(FileObject::getMediaValidationPolicyHash, currentPolicyHash)
                    .eq(FileObject::getMediaProbeVersion, videoMediaProbe.probeVersion())
                    .eq(FileObject::getStatus, "READY")
                    .last("LIMIT 1"));
            if (file == null || !StringUtils.hasText(file.getMediaCodec())
                    || !Objects.equals(file.getBucket(), minioProperties.getBucket())
                    || !StringUtils.hasText(file.getObjectKey())) {
                continue;
            }
            MultipartObjectInfo object = multipartObjectService.findObject(file.getObjectKey()).orElse(null);
            if (object != null && Objects.equals(file.getSize(), object.size())) {
                return new VerifiedInstantHit(file, source.getDurationSeconds());
            }
        }
        return null;
    }

    /**
     * 是否满足 MinIO 服务端合并前提：多分片且除最后一片外每片均 ≥{@link #MIN_COMPOSE_PART_SIZE}（5MiB）。
     * 满足→走 {@link #composeServerSide}（composeObject 快路径）；否则（单分片，或末片以外存在小片）回退
     * {@link #streamComposeForSmallChunks}。判据与 io.minio composeObject 的部件下限校验对齐——单分片与最后一片
     * 不受 5MiB 下限约束，故此处仅校验「非末片」分片的大小。
     */
    private boolean canServerSideCompose(List<VideoUploadChunk> sortedChunks) {
        if (sortedChunks.size() < 2) {
            return false;
        }
        for (int i = 0; i < sortedChunks.size() - 1; i++) {
            Long size = sortedChunks.get(i).getChunkSize();
            if (size == null || size < MIN_COMPOSE_PART_SIZE) {
                return false;
            }
        }
        return true;
    }

    private void requireServerChunkSources(List<VideoUploadChunk> chunks) {
        for (VideoUploadChunk chunk : chunks) {
            MultipartObjectInfo object = multipartObjectService.findObject(chunk.getObjectKey())
                    .orElseThrow(() -> new ServerChunkTerminalException(
                            "已上传分片不存在或不完整，请重新发起上传"));
            if (!Objects.equals(chunk.getChunkSize(), object.size())) {
                throw new ServerChunkTerminalException(
                        "已上传分片不存在或不完整，请重新发起上传");
            }
        }
    }

    private Boolean serverChunkSourcesMissing(List<VideoUploadChunk> chunks) {
        try {
            for (VideoUploadChunk chunk : chunks) {
                MultipartObjectInfo object = multipartObjectService.findObject(chunk.getObjectKey())
                        .orElse(null);
                if (object == null || !Objects.equals(chunk.getChunkSize(), object.size())) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException lookupFailure) {
            log.warn("视频合并异常后无法确认源分片状态，保留会话供后续接管: message={}",
                    lookupFailure.getMessage());
            return null;
        }
    }

    private Boolean finalServerObjectReady(String objectKey, long expectedSize) {
        try {
            MultipartObjectInfo object = multipartObjectService.findObject(objectKey).orElse(null);
            return object != null && object.size() == expectedSize;
        } catch (RuntimeException lookupFailure) {
            log.warn("视频合并异常后无法确认最终对象状态，保留会话供后续接管: objectKey={}, message={}",
                    objectKey, lookupFailure.getMessage());
            return null;
        }
    }

    /**
     * 快路径：MinIO 服务端合并（composeObject → 服务端 UploadPartCopy 拼接各分片），字节不经应用服务器。
     * 显式指定 content-type=video/mp4——分片以 application/octet-stream 存储，合并对象须回到视频类型供后续鉴权播放。
     */
    private void composeServerSide(List<VideoUploadChunk> sortedChunks, String objectKey) {
        List<ComposeSource> sources = sortedChunks.stream()
                .map(chunk -> ComposeSource.builder()
                        .bucket(minioProperties.getBucket())
                        .object(chunk.getObjectKey())
                        .build())
                .toList();
        try {
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .sources(sources)
                    .headers(Map.of("Content-Type", "video/mp4"))
                    .build());
        } catch (Exception e) {
            log.error("视频服务端合并失败", e);
            throw new BizException("视频合并失败，请稍后重试");
        }
    }

    private void streamComposeForSmallChunks(List<VideoUploadChunk> chunks, String objectKey, long fileSize) {
        try (InputStream in = concatenatedChunkStream(chunks)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(in, fileSize, -1)
                    .contentType("video/mp4")
                    .build());
        } catch (Exception ex) {
            log.error("视频流式合并失败", ex);
            throw new BizException("视频合并失败，请稍后重试");
        }
    }

    private InputStream concatenatedChunkStream(List<VideoUploadChunk> chunks) {
        return new InputStream() {
            private int index;
            private InputStream current;

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                int read = read(one, 0, 1);
                return read < 0 ? -1 : one[0] & 0xff;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                while (true) {
                    if (current == null && !openNext()) {
                        return -1;
                    }
                    int read = current.read(b, off, len);
                    if (read >= 0) {
                        return read;
                    }
                    current.close();
                    current = null;
                }
            }

            @Override
            public void close() throws IOException {
                if (current != null) {
                    current.close();
                }
            }

            private boolean openNext() throws IOException {
                if (index >= chunks.size()) {
                    return false;
                }
                VideoUploadChunk chunk = chunks.get(index++);
                try {
                    current = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(chunk.getObjectKey())
                            .build());
                } catch (Exception e) {
                    throw new IOException(e);
                }
                return true;
            }
        };
    }

    private void refreshSessionProgress(String uploadId) {
        List<VideoUploadChunk> chunks = chunks(uploadId);
        long bytes = chunks.stream().map(VideoUploadChunk::getChunkSize).filter(value -> value != null).mapToLong(Long::longValue).sum();
        sessionMapper.update(new LambdaUpdateWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, uploadId)
                .set(VideoUploadSession::getUploadedChunks, chunks.size())
                .set(VideoUploadSession::getUploadedBytes, bytes));
    }

    private void settleIfReady(VideoReview review) {
        int expected = paramService.getInt("video.reviewerCount", DEFAULT_REVIEWER_COUNT);
        // 锁定读（FOR UPDATE）计票：绕过本事务 REPEATABLE_READ 快照、读最新已提交行——配合 submitScore 对 review 行的
        // 行锁串行化，保证后提交的事务能看到先提交事务已落库的评审任务，实现「末分提交恰一次结算、不卡 REVIEWING」。
        // FOR UPDATE 必须是 SQL 的最后一段（MP 的 .last 会紧跟 WHERE、排在 ORDER BY 之前），故不在 wrapper 里加
        // ORDER BY，改在 Java 侧按 submit_time 升序（等价于原 ORDER BY，submitted=1 的任务 submit_time 恒非空）。
        List<VideoReviewTask> submitted = taskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, review.getId())
                .eq(VideoReviewTask::getReviewerRole, "REVIEWER")
                .eq(VideoReviewTask::getSubmitted, 1)
                .last("FOR UPDATE"));
        if (submitted.size() < expected) {
            return;
        }
        List<VideoReviewTask> initialReviews = submitted.stream()
                .sorted(Comparator.comparing(VideoReviewTask::getSubmitTime))
                .limit(expected)
                .toList();
        int threshold = paramService.getInt("video.diffThreshold", DEFAULT_DIFF_THRESHOLD);
        if (allPairDiffWithin(initialReviews, threshold) && sameConclusion(initialReviews)) {
            int sum = initialReviews.stream().map(VideoReviewTask::getScore).mapToInt(Integer::intValue).sum();
            int finalScore = Math.round(sum / (float) initialReviews.size());
            review.setFinalScore(finalScore);
            review.setFinalConclusion(conclusionByScore(finalScore));
            review.setStatus(VideoReviewStatus.REVIEW_COMPLETED.name());
            review.setLocked(1);
        } else {
            review.setStatus(VideoReviewStatus.NEED_REVIEW.name());
            review.setLocked(0);
        }
        // 原子条件更新：仅当仍为 REVIEWING 时写入结算态，防重复结算（与 37b 同款守卫，行锁+条件更新双保险）。
        if (reviewMapper.update(review, new LambdaUpdateWrapper<VideoReview>()
                .eq(VideoReview::getId, review.getId())
                .eq(VideoReview::getStatus, VideoReviewStatus.REVIEWING.name())) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
    }

    private void settleThirdExpert(VideoReview review) {
        List<VideoReviewTask> submitted = taskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, review.getId())
                .eq(VideoReviewTask::getSubmitted, 1));
        if (submitted.size() < 3) {
            throw new BizException("第三专家评分不足");
        }
        Pair best = bestPair(submitted);
        int finalScore = Math.round((best.left().getScore() + best.right().getScore()) / 2.0f);
        review.setArbitrateMode("thirdExpert");
        review.setArbitrateReviewer(best.thirdExpertId());
        review.setFinalScore(finalScore);
        review.setFinalConclusion(conclusionByScore(finalScore));
        review.setStatus(VideoReviewStatus.REVIEW_COMPLETED.name());
        review.setLocked(1);
        // 原子条件更新：仅当仍为 NEED_REVIEW 时写入，防并发/重复复评（37b 同款守卫）。两并发 thirdReview
        // 各插一条 THIRD_EXPERT 任务后争这条更新，仅一个命中 status='NEED_REVIEW'，另一个 0 行 → 抛异常
        // → @Transactional 回滚其任务插入，最终恰一次复评结算、恰一条第三专家任务。
        if (reviewMapper.update(review, new LambdaUpdateWrapper<VideoReview>()
                .eq(VideoReview::getId, review.getId())
                .eq(VideoReview::getStatus, VideoReviewStatus.NEED_REVIEW.name())) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
    }

    private Pair bestPair(List<VideoReviewTask> tasks) {
        Pair best = null;
        for (int i = 0; i < tasks.size(); i++) {
            for (int j = i + 1; j < tasks.size(); j++) {
                VideoReviewTask left = tasks.get(i);
                VideoReviewTask right = tasks.get(j);
                int diff = Math.abs(left.getScore() - right.getScore());
                if (best == null || diff < best.diff()) {
                    Long third = tasks.stream()
                            .filter(task -> "THIRD_EXPERT".equals(task.getReviewerRole()))
                            .map(VideoReviewTask::getReviewerId)
                            .findFirst()
                            .orElse(right.getReviewerId());
                    best = new Pair(left, right, diff, third);
                }
            }
        }
        if (best == null) {
            throw new BizException("复评分数不足");
        }
        return best;
    }

    private boolean allPairDiffWithin(List<VideoReviewTask> tasks, int threshold) {
        for (int i = 0; i < tasks.size(); i++) {
            for (int j = i + 1; j < tasks.size(); j++) {
                if (Math.abs(tasks.get(i).getScore() - tasks.get(j).getScore()) > threshold) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean sameConclusion(List<VideoReviewTask> tasks) {
        if (tasks.isEmpty()) {
            return false;
        }
        String first = normalizeConclusion(tasks.get(0).getConclusion());
        return tasks.stream().allMatch(task -> first.equals(normalizeConclusion(task.getConclusion())));
    }

    private void fillScore(VideoReviewTask task, VideoScoreRequest request) {
        task.setScore(request.getScore());
        task.setDimensionScoresJson(writeDimensions(validateDimensions(request.getDimensionScores())));
        task.setComment(trimToNull(request.getComment()));
        task.setConclusion(normalizeConclusion(request.getConclusion()));
    }

    private Map<String, Integer> validateDimensions(Map<String, Integer> scores) {
        Map<String, Integer> input = scores == null ? Map.of() : scores;
        List<SysDictItem> dimensions = dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, "video_score_dimension")
                .eq(SysDictItem::getStatus, 1)
                .orderByAsc(SysDictItem::getSort)
                .orderByAsc(SysDictItem::getId))
                .stream()
                .collect(Collectors.toMap(SysDictItem::getSort, item -> item,
                        (left, right) -> left, LinkedHashMap::new))
                .values()
                .stream()
                .limit(9)
                .toList();
        if (dimensions.isEmpty()) {
            return new LinkedHashMap<>(input);
        }
        Set<String> allowed = dimensions.stream().map(SysDictItem::getItemCode).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!input.keySet().containsAll(allowed)) {
            throw new BizException("维度评分不完整");
        }
        for (Map.Entry<String, Integer> entry : input.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new BizException("维度评分不在字典范围");
            }
            if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 100) {
                throw new BizException("维度评分不合法");
            }
        }
        return input.entrySet().stream()
                .filter(entry -> allowed.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private String writeDimensions(Map<String, Integer> scores) {
        try {
            return objectMapper.writeValueAsString(scores == null ? Map.of() : scores);
        } catch (Exception e) {
            throw new BizException("维度评分序列化失败");
        }
    }

    private Map<String, Integer> readDimensions(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String conclusionByScore(int score) {
        return score >= paramService.getInt("video.passLine", DEFAULT_PASS_LINE) ? "PASS" : "FAIL";
    }

    private String normalizeConclusion(String conclusion) {
        String value = requiredTrim(conclusion, "结论不能为空").toUpperCase(Locale.ROOT);
        if (!"PASS".equals(value) && !"FAIL".equals(value)) {
            throw new BizException("结论仅支持 PASS/FAIL");
        }
        return value;
    }

    private LambdaQueryWrapper<VideoReview> buildListWrapper(VideoQuery query) {
        VideoQuery q = query == null ? new VideoQuery() : query;
        LambdaQueryWrapper<VideoReview> wrapper = new LambdaQueryWrapper<VideoReview>()
                .orderByAsc(VideoReview::getAssessmentYear)
                .orderByAsc(VideoReview::getStudentId);
        if (q.getStudentId() != null) {
            wrapper.eq(VideoReview::getStudentId, q.getStudentId());
        }
        if (q.getCollegeId() != null) {
            wrapper.eq(VideoReview::getCollegeId, q.getCollegeId());
        }
        if (StringUtils.hasText(q.getAssessmentYear())) {
            wrapper.eq(VideoReview::getAssessmentYear, q.getAssessmentYear().trim());
        }
        if (StringUtils.hasText(q.getStatus())) {
            wrapper.eq(VideoReview::getStatus, q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String keyword = q.getKeyword().trim();
            wrapper.and(w -> w.like(VideoReview::getVideoFileName, keyword)
                    .or()
                    .like(VideoReview::getFileMd5, keyword));
        }
        return wrapper;
    }

    private List<VideoReviewVO> toVOList(List<VideoReview> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> reviewIds = records.stream().map(VideoReview::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> studentIds = records.stream().map(VideoReview::getStudentId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Student> students = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, item -> item));
        Map<Long, List<VideoReviewTask>> tasksByReview = taskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                        .in(VideoReviewTask::getVideoReviewId, reviewIds)
                        .orderByAsc(VideoReviewTask::getCreatedAt))
                .stream()
                .collect(Collectors.groupingBy(VideoReviewTask::getVideoReviewId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, SysUser> reviewers = reviewerMap(tasksByReview.values().stream().flatMap(List::stream).toList());
        return records.stream()
                .map(entity -> toVO(entity, students.get(entity.getStudentId()),
                        tasksByReview.getOrDefault(entity.getId(), List.of()), reviewers))
                .toList();
    }

    private List<VideoReviewTask> tasksForReview(Long reviewId) {
        return taskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, reviewId)
                .orderByAsc(VideoReviewTask::getCreatedAt));
    }

    private Map<Long, SysUser> reviewerMap(List<VideoReviewTask> tasks) {
        Set<Long> ids = tasks.stream().map(VideoReviewTask::getReviewerId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(SysUser::getId, item -> item));
    }

    private VideoReviewVO toVO(VideoReview entity) {
        Student student = studentMapper.selectById(entity.getStudentId());
        List<VideoReviewTask> tasks = tasksForReview(entity.getId());
        Map<Long, SysUser> reviewers = reviewerMap(tasks);
        return toVO(entity, student, tasks, reviewers);
    }

    private VideoReviewVO toVO(VideoReview entity, Student student, List<VideoReviewTask> tasks, Map<Long, SysUser> reviewers) {
        VideoReviewVO vo = new VideoReviewVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setStudentNo(student == null ? null : student.getStudentNo());
        vo.setStudentName(student == null ? null : student.getName());
        vo.setCollegeId(entity.getCollegeId());
        vo.setAssessmentYear(entity.getAssessmentYear());
        vo.setVideoFileId(entity.getVideoFileId());
        vo.setVideoFileName(entity.getVideoFileName());
        vo.setDurationSeconds(entity.getDurationSeconds());
        vo.setFormatCheck(entity.getFormatCheck());
        vo.setValidationMessage(entity.getValidationMessage());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(VideoReviewStatus.of(entity.getStatus()).label());
        vo.setFinalScore(entity.getFinalScore());
        vo.setFinalConclusion(entity.getFinalConclusion());
        vo.setArbitrateReviewer(entity.getArbitrateReviewer());
        vo.setArbitrateMode(entity.getArbitrateMode());
        vo.setConfirmedBy(entity.getConfirmedBy());
        vo.setConfirmedAt(entity.getConfirmedAt());
        vo.setLocked(entity.getLocked());
        boolean managementView = canViewSubmittedTasks(entity);
        Long currentUserId = UserContext.getUserId();
        vo.setTasks(tasks.stream()
                .filter(task -> managementView || (currentUserId != null && currentUserId.equals(task.getReviewerId())))
                .map(task -> toTaskVO(task, managementView, reviewers.get(task.getReviewerId())))
                .toList());
        return vo;
    }

    private record VerifiedInstantHit(FileObject file, Integer durationSeconds) {
    }

    private String videoTarget(VideoReview entity) {
        return entity.getId() + "/" + entity.getAssessmentYear() + "/" + entity.getStudentId() + "/"
                + entity.getVideoFileName();
    }

    private boolean canViewSubmittedTasks(VideoReview review) {
        return UserContext.hasPermission("video:assign")
                || UserContext.hasPermission("video:arbitrate")
                || UserContext.hasPermission("video:confirm")
                || VideoReviewStatus.of(review.getStatus()) == VideoReviewStatus.REVIEW_COMPLETED
                || VideoReviewStatus.of(review.getStatus()) == VideoReviewStatus.CONFIRMED;
    }

    private VideoReviewTaskVO toTaskVO(VideoReviewTask task, boolean revealScore) {
        SysUser reviewer = userMapper.selectById(task.getReviewerId());
        return toTaskVO(task, revealScore, reviewer);
    }

    private VideoReviewTaskVO toTaskVO(VideoReviewTask task, boolean revealScore, SysUser reviewer) {
        boolean owner = UserContext.getUserId() != null && UserContext.getUserId().equals(task.getReviewerId());
        boolean reveal = revealScore || owner;
        VideoReviewTaskVO vo = new VideoReviewTaskVO();
        vo.setId(task.getId());
        vo.setVideoReviewId(task.getVideoReviewId());
        vo.setReviewerId(task.getReviewerId());
        vo.setReviewerName(reviewer == null ? null : reviewer.getRealName());
        vo.setReviewerRole(task.getReviewerRole());
        vo.setSubmitted(task.getSubmitted());
        vo.setSubmitTime(task.getSubmitTime());
        if (reveal) {
            vo.setScore(task.getScore());
            vo.setDimensionScores(readDimensions(task.getDimensionScoresJson()));
            vo.setComment(task.getComment());
            vo.setConclusion(task.getConclusion());
        }
        return vo;
    }

    private ReviewerCandidateVO toReviewerCandidateVO(SysUser user) {
        ReviewerCandidateVO vo = new ReviewerCandidateVO();
        vo.setId(user.getId());
        vo.setRealName(user.getRealName());
        vo.setWorkNo(user.getWorkNo());
        return vo;
    }

    private void ensurePlayable(VideoReview review) {
        if (UserContext.hasPermission("video:score") && assignedToCurrentUser(review.getId())) {
            return;
        }
        ensureReadableReview(review);
    }

    private void ensureReadableReview(VideoReview review) {
        Student student = requireStudent(review.getStudentId());
        DataScopeContext.Scope scope = dataScopeService.resolve(readPermission());
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该视频");
        }
        if (scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && scope.getCollegeIds().contains(student.getCollegeId())) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.SELF
                && scope.getStudentId() != null
                && scope.getStudentId().equals(student.getId())) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.ASSIGNED && assignedToCurrentUser(review.getId())) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该视频");
    }

    private void ensureCanWriteReview(VideoReview review, String permissionCode) {
        ensureCanWriteStudent(requireStudent(review.getStudentId()), permissionCode);
    }

    private List<Long> resolveAssignReviewerIds(VideoAssignRequest request, VideoReview review) {
        if (request == null) {
            throw new BizException("指派参数不能为空");
        }
        boolean hasReviewerIds = request.getReviewerIds() != null && !request.getReviewerIds().isEmpty();
        boolean hasGroup = request.getGroupId() != null;
        if (hasReviewerIds == hasGroup) {
            throw new BizException("评审教师和评审组必须二选一");
        }
        if (hasReviewerIds) {
            return distinctReviewerIds(request.getReviewerIds());
        }
        ReviewerGroup group = groupMapper.selectById(request.getGroupId());
        if (group == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评审组不存在");
        }
        if (!review.getCollegeId().equals(group.getCollegeId())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "评审组不属于该视频学院");
        }
        if (!"ENABLED".equals(group.getStatus())) {
            throw new BizException("评审组已停用");
        }
        List<Long> memberIds = groupMemberMapper.selectList(new LambdaQueryWrapper<ReviewerGroupMember>()
                        .eq(ReviewerGroupMember::getGroupId, group.getId())
                        .orderByAsc(ReviewerGroupMember::getId))
                .stream()
                .map(ReviewerGroupMember::getReviewerUserId)
                .toList();
        return distinctReviewerIds(memberIds);
    }

    private List<Long> distinctReviewerIds(List<Long> reviewerIds) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(reviewerIds == null ? List.of() : reviewerIds));
        if (ids.isEmpty() || ids.stream().anyMatch(id -> id == null)) {
            throw new BizException("评审教师不能为空");
        }
        return ids;
    }

    private SysUser requireReviewerForReview(Long reviewerId, Long collegeId) {
        SysUser reviewer = requireUser(reviewerId);
        if (!"ENABLED".equals(reviewer.getStatus())) {
            throw new BizException("评审教师未启用");
        }
        if (!collegeId.equals(reviewer.getCollegeId())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "评审教师不属于该视频学院");
        }
        if (!roleMapper.selectCodesByUserId(reviewer.getId()).contains(REVIEW_TEACHER_ROLE)) {
            throw new BizException("评审教师必须具备 REVIEW_TEACHER 角色");
        }
        return reviewer;
    }

    private Set<Long> reviewerCandidateCollegeIds() {
        DataScopeContext.Scope scope = dataScopeService.resolve("video:assign");
        if (scope == null || scope.getScopeType() == DataScopeContext.ScopeType.NONE) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权查看评审教师候选人");
        }
        if (scope.allSchool()) {
            LinkedHashSet<Long> allSchool = new LinkedHashSet<>();
            allSchool.add(null);
            return allSchool;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE && !scope.getCollegeIds().isEmpty()) {
            return scope.getCollegeIds();
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权查看评审教师候选人");
    }

    private void ensureCanWriteStudent(Student student, String permissionCode) {
        DataScopeContext.Scope scope = dataScopeService.resolve(permissionCode);
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该视频");
        }
        if (scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && scope.getCollegeIds().contains(student.getCollegeId())) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.SELF
                && scope.getStudentId() != null
                && scope.getStudentId().equals(student.getId())) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.ASSIGNED
                && "video:score".equals(permissionCode)) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该视频");
    }

    private boolean assignedToCurrentUser(Long reviewId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, reviewId)
                .eq(VideoReviewTask::getReviewerId, userId));
        return count != null && count > 0;
    }

    private void ensureTaskOwner(VideoReviewTask task) {
        if (UserContext.getUserId() == null || !UserContext.getUserId().equals(task.getReviewerId())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "只能访问分配给本人的评审任务");
        }
    }

    private String readPermission() {
        if (UserContext.hasPermission("video:assign")) {
            return "video:assign";
        }
        if (UserContext.hasPermission("video:confirm")) {
            return "video:confirm";
        }
        if (UserContext.hasPermission("video:arbitrate")) {
            return "video:arbitrate";
        }
        if (UserContext.hasPermission("video:play")) {
            return "video:play";
        }
        return "video:upload";
    }

    private String readManagementPermission() {
        if (UserContext.hasPermission("video:arbitrate")) {
            return "video:arbitrate";
        }
        if (UserContext.hasPermission("video:confirm")) {
            return "video:confirm";
        }
        return "video:assign";
    }

    private VideoReview existingReview(Long studentId, String assessmentYear) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<VideoReview>()
                .eq(VideoReview::getStudentId, studentId)
                .eq(VideoReview::getAssessmentYear, requiredTrim(assessmentYear, "考核年度不能为空"))
                .last("LIMIT 1"));
    }

    private VideoReview existingReviewForUpdate(Long studentId, String assessmentYear) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<VideoReview>()
                .eq(VideoReview::getStudentId, studentId)
                .eq(VideoReview::getAssessmentYear, requiredTrim(assessmentYear, "考核年度不能为空"))
                // (student_id, assessment_year) 有唯一索引，无需 LIMIT；避免 MP 将
                // “LIMIT 1 FOR UPDATE” 重排为 MySQL 不接受的 “FOR UPDATE LIMIT 1”。
                .last("FOR UPDATE"));
    }

    private Student lockStudentForVideo(Long studentId) {
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getId, studentId)
                .last("FOR UPDATE"));
        if (student == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "学生不存在");
        }
        return student;
    }

    private List<Integer> uploadedIndexes(String uploadId) {
        return chunks(uploadId).stream().map(VideoUploadChunk::getChunkIndex).sorted().toList();
    }

    private List<VideoUploadChunk> chunks(String uploadId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<VideoUploadChunk>()
                .eq(VideoUploadChunk::getUploadId, uploadId)
                .orderByAsc(VideoUploadChunk::getChunkIndex));
    }

    private VideoUploadSession requireSession(String uploadId) {
        String id = requiredTrim(uploadId, "上传会话不能为空");
        VideoUploadSession session = sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, id)
                .last("LIMIT 1"));
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "上传会话不存在");
        }
        return session;
    }

    private VideoReview requireReview(Long id) {
        if (id == null) {
            throw new BizException("视频评审ID不能为空");
        }
        VideoReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "视频评审不存在");
        }
        return review;
    }

    /** 悲观锁读取 review 行（SELECT ... FOR UPDATE），用于串行化并发提交末分的结算事务（须在事务内调用）。 */
    private VideoReview lockReview(Long id) {
        if (id == null) {
            throw new BizException("视频评审ID不能为空");
        }
        VideoReview review = reviewMapper.selectOne(new LambdaQueryWrapper<VideoReview>()
                .eq(VideoReview::getId, id)
                .last("FOR UPDATE"));
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "视频评审不存在");
        }
        return review;
    }

    private VideoReviewTask requireTask(Long id) {
        if (id == null) {
            throw new BizException("评审任务ID不能为空");
        }
        VideoReviewTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评审任务不存在");
        }
        return task;
    }

    private Student requireStudent(Long id) {
        if (id == null) {
            throw new BizException("学生ID不能为空");
        }
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "学生不存在");
        }
        return student;
    }

    private SysUser requireUser(Long id) {
        if (id == null) {
            throw new BizException("用户ID不能为空");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        return user;
    }

    private long maxVideoSize() {
        return VideoMediaAcceptancePolicy.maxVideoSize(paramService);
    }

    private String normalizeContentType(String originalFilename, String contentType) {
        String type = StringUtils.hasText(contentType) ? contentType.trim().toLowerCase(Locale.ROOT) : "";
        if (StringUtils.hasText(type) && !"application/octet-stream".equals(type)) {
            return type;
        }
        String name = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp4")) {
            return "video/mp4";
        }
        return type;
    }

    private String requiredTrim(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Pair(VideoReviewTask left, VideoReviewTask right, int diff, Long thirdExpertId) {
    }
}
