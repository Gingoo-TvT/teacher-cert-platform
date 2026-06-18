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
import cn.edu.gpnu.platform.business.video.dto.VideoUploadMergeRequest;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadChunk;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadSession;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadChunkMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadSessionMapper;
import cn.edu.gpnu.platform.business.video.service.VideoReviewService;
import cn.edu.gpnu.platform.business.video.support.VideoReviewStatus;
import cn.edu.gpnu.platform.business.video.support.VideoUploadStatus;
import cn.edu.gpnu.platform.business.video.vo.VideoPlaybackVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewTaskVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadInitVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadProgressVO;
import cn.edu.gpnu.platform.business.support.ReviewNotificationHelper;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoReviewServiceImpl implements VideoReviewService {

    private static final String VIDEO_BIZ_TYPE = "teaching-video";
    private static final String CHUNK_BIZ_TYPE = "video-chunk";
    private static final long DEFAULT_MAX_VIDEO_SIZE = 2_147_483_648L;
    private static final int DEFAULT_DURATION_TARGET = 900;
    private static final int DEFAULT_DURATION_TOLERANCE = 60;
    private static final int DEFAULT_PASS_LINE = 60;
    private static final int DEFAULT_DIFF_THRESHOLD = 12;
    private static final int DEFAULT_REVIEWER_COUNT = 2;
    private static final int DEFAULT_PRESIGN_SECONDS = 300;

    private final VideoUploadSessionMapper sessionMapper;
    private final VideoUploadChunkMapper chunkMapper;
    private final VideoReviewMapper reviewMapper;
    private final VideoReviewTaskMapper taskMapper;
    private final StudentMapper studentMapper;
    private final SysUserMapper userMapper;
    private final SysDictItemMapper dictItemMapper;
    private final FileObjectMapper fileObjectMapper;
    private final FileService fileService;
    private final DataScopeService dataScopeService;
    private final ParamService paramService;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final ObjectMapper objectMapper;
    private final ReviewNotificationHelper notificationHelper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoUploadInitVO initUpload(VideoUploadInitRequest request) {
        Student student = requireStudent(request.getStudentId());
        ensureCanWriteStudent(student, "video:upload");
        validateUploadMeta(request.getFileName(), request.getContentType(), request.getSize());
        ensureReviewReuploadable(student.getId(), request.getAssessmentYear());
        archiveReturnedUploadSessions(student.getId(), request.getAssessmentYear());
        FileObject existingFile = fileService.getByMd5(requiredTrim(request.getFileMd5(), "文件MD5不能为空"));
        if (existingFile != null) {
            VideoReview review = upsertReviewAfterValidation(student, request.getAssessmentYear(), existingFile,
                    request.getFileMd5(), request.getDurationSeconds(), true, null);
            VideoUploadInitVO vo = new VideoUploadInitVO();
            vo.setUploadId(null);
            vo.setInstantHit(true);
            vo.setFileId(existingFile.getId());
            vo.setReviewId(review.getId());
            vo.setUploadedChunks(List.of());
            vo.setStatus(review.getStatus());
            vo.setValidationMessage(review.getValidationMessage());
            return vo;
        }
        VideoUploadSession existing = sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getStudentId, student.getId())
                .eq(VideoUploadSession::getAssessmentYear, requiredTrim(request.getAssessmentYear(), "考核年度不能为空"))
                .eq(VideoUploadSession::getFileMd5, request.getFileMd5().trim())
                .eq(VideoUploadSession::getStatus, VideoUploadStatus.UPLOADING.name())
                .orderByDesc(VideoUploadSession::getCreatedAt)
                .last("LIMIT 1"));
        VideoUploadSession session = existing == null ? createSession(student, request) : existing;
        if (existing != null && request.getDurationSeconds() != null) {
            existing.setDurationSeconds(request.getDurationSeconds());
            sessionMapper.updateById(existing);
        }
        VideoUploadInitVO vo = new VideoUploadInitVO();
        vo.setUploadId(session.getUploadId());
        vo.setInstantHit(false);
        vo.setUploadedChunks(uploadedIndexes(session.getUploadId()));
        vo.setStatus(session.getStatus());
        vo.setValidationMessage(session.getValidationMessage());
        vo.setFileId(session.getFileId());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadChunk(String uploadId, Integer index, String md5, InputStream input, long size) {
        VideoUploadSession session = requireSession(uploadId);
        ensureCanWriteStudent(requireStudent(session.getStudentId()), "video:upload");
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
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(input, size, -1)
                    .contentType("application/octet-stream")
                    .build());
        } catch (Exception e) {
            throw new BizException("分片上传失败: " + e.getMessage());
        }
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoReviewVO merge(VideoUploadMergeRequest request) {
        VideoUploadSession session = requireSession(request.getUploadId());
        Student student = requireStudent(session.getStudentId());
        ensureCanWriteStudent(student, "video:upload");
        ensureReviewReuploadable(student.getId(), session.getAssessmentYear());
        List<VideoUploadChunk> chunks = chunks(session.getUploadId());
        if (chunks.size() != session.getTotalChunks()) {
            throw new BizException("分片尚未全部上传");
        }
        Integer duration = request.getDurationSeconds() == null ? session.getDurationSeconds() : request.getDurationSeconds();
        if (duration != null) {
            session.setDurationSeconds(duration);
        }
        String objectKey = VIDEO_BIZ_TYPE + "/" + UUID.randomUUID().toString().replace("-", "") + ".mp4";
        List<VideoUploadChunk> sortedChunks = chunks.stream()
                .sorted(Comparator.comparing(VideoUploadChunk::getChunkIndex))
                .toList();
        try {
            List<ComposeSource> sources = sortedChunks.stream()
                    .map(chunk -> ComposeSource.builder()
                            .bucket(minioProperties.getBucket())
                            .object(chunk.getObjectKey())
                            .build())
                    .toList();
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .sources(sources)
                    .build());
        } catch (Exception e) {
            streamComposeForSmallChunks(sortedChunks, objectKey, session.getFileSize());
        }
        FileObject file = registerComposedFile(session, objectKey);
        session.setFileId(file.getId());
        VideoReview review = upsertReviewAfterValidation(student, session.getAssessmentYear(), file,
                session.getFileMd5(), duration, false, session.getUploadId());
        session.setStatus(VideoReviewStatus.of(review.getStatus()) == VideoReviewStatus.VALIDATION_FAILED
                ? VideoUploadStatus.VALIDATION_FAILED.name()
                : VideoUploadStatus.MERGED.name());
        session.setValidationMessage(review.getValidationMessage());
        sessionMapper.updateById(session);
        return detail(review.getId());
    }

    @Override
    public VideoUploadProgressVO progress(String uploadId) {
        VideoUploadSession session = requireSession(uploadId);
        ensureCanWriteStudent(requireStudent(session.getStudentId()), "video:upload");
        VideoUploadProgressVO vo = new VideoUploadProgressVO();
        vo.setUploadId(session.getUploadId());
        vo.setStatus(session.getStatus());
        vo.setTotalChunks(session.getTotalChunks());
        vo.setUploadedChunks(session.getUploadedChunks());
        vo.setUploadedBytes(session.getUploadedBytes());
        vo.setUploadedChunkIndexes(uploadedIndexes(uploadId));
        vo.setFileId(session.getFileId());
        vo.setValidationMessage(session.getValidationMessage());
        return vo;
    }

    @Override
    public PageResult<VideoReviewVO> list(VideoQuery query) {
        List<VideoReview> records = selectReviews(query);
        return new PageResult<>(records.size(), records.stream().map(this::toVO).toList());
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
        VideoReview review = requireReview(reviewId);
        ensureCanWriteReview(review, "video:assign");
        VideoReviewStatus status = VideoReviewStatus.of(review.getStatus());
        if (status != VideoReviewStatus.WAIT_REVIEW && status != VideoReviewStatus.REVIEWING) {
            throw new BizException("当前状态不可分配评审教师");
        }
        int expected = paramService.getInt("video.reviewerCount", DEFAULT_REVIEWER_COUNT);
        List<Long> reviewerIds = new ArrayList<>(new LinkedHashSet<>(request.getReviewerIds()));
        if (reviewerIds.size() != expected) {
            throw new BizException("评审教师人数需等于系统参数 video.reviewerCount");
        }
        for (Long reviewerId : reviewerIds) {
            SysUser reviewer = requireUser(reviewerId);
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
    public PageResult<VideoReviewTaskVO> myTasks(String status) {
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
        List<VideoReviewTask> records = taskMapper.selectList(wrapper);
        return new PageResult<>(records.size(), records.stream().map(task -> toTaskVO(task, false)).toList());
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
        VideoReview review = requireReview(task.getVideoReviewId());
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
        reviewMapper.updateById(review);
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
        reviewMapper.updateById(review);
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
        updateReviewReturned(review);
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
        int expiry = paramService.getInt("video.presign.expirySeconds", DEFAULT_PRESIGN_SECONDS);
        VideoPlaybackVO vo = new VideoPlaybackVO();
        vo.setUrl(fileService.presignedGet(review.getVideoFileId(), expiry));
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

    private VideoUploadSession createSession(Student student, VideoUploadInitRequest request) {
        VideoUploadSession session = new VideoUploadSession();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
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

    private VideoReview upsertReviewAfterValidation(Student student, String year, FileObject file, String fileMd5,
                                                     Integer durationSeconds, boolean instantHit, String currentUploadId) {
        VideoReview review = existingReview(student.getId(), year);
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
        review.setFileMd5(fileMd5 == null ? file.getMd5() : fileMd5.toLowerCase(Locale.ROOT));
        review.setDurationSeconds(durationSeconds);
        review.setStatus(VideoReviewStatus.VALIDATING.name());
        review.setLocked(0);
        validateMergedVideo(review, file, durationSeconds, instantHit);
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
            throw new BizException("评审进行中不可重新上传");
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

    private void archiveReturnedUploadSessions(Long studentId, String assessmentYear) {
        VideoReview existing = existingReview(studentId, assessmentYear);
        if (existing != null && VideoReviewStatus.of(existing.getStatus()) == VideoReviewStatus.RETURNED) {
            archiveUploadSessions(studentId, requiredTrim(assessmentYear, "考核年度不能为空"), null);
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

    private void updateReviewReturned(VideoReview review) {
        reviewMapper.update(null, new LambdaUpdateWrapper<VideoReview>()
                .eq(VideoReview::getId, review.getId())
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

    private void validateMergedVideo(VideoReview review, FileObject file, Integer durationSeconds, boolean instantHit) {
        String normalized = normalizeContentType(file.getOriginalName(), file.getContentType());
        if (!"video/mp4".equals(normalized)) {
            review.setFormatCheck("FAIL");
            review.setStatus(VideoReviewStatus.VALIDATION_FAILED.name());
            review.setValidationMessage("视频格式必须为MP4");
            return;
        }
        if (file.getSize() != null && file.getSize() > maxVideoSize()) {
            review.setFormatCheck("FAIL");
            review.setStatus(VideoReviewStatus.VALIDATION_FAILED.name());
            review.setValidationMessage("视频大小超过限制");
            return;
        }
        if (durationSeconds == null) {
            review.setFormatCheck("FAIL");
            review.setStatus(VideoReviewStatus.VALIDATION_FAILED.name());
            review.setValidationMessage("视频时长不能为空");
            return;
        }
        int target = paramService.getInt("video.durationTarget", DEFAULT_DURATION_TARGET);
        int tolerance = paramService.getInt("video.durationTolerance", DEFAULT_DURATION_TOLERANCE);
        if (Math.abs(durationSeconds - target) > tolerance) {
            review.setFormatCheck("FAIL");
            review.setStatus(VideoReviewStatus.VALIDATION_FAILED.name());
            review.setValidationMessage("视频时长超出容差");
            return;
        }
        review.setFormatCheck("PASS");
        review.setStatus(VideoReviewStatus.WAIT_REVIEW.name());
        review.setValidationMessage(instantHit ? "MD5秒传命中，校验通过" : "校验通过");
    }

    private FileObject registerComposedFile(VideoUploadSession session, String objectKey) {
        FileObject file = new FileObject();
        file.setOriginalName(session.getFileName());
        file.setStoredName(objectKey.substring(objectKey.indexOf('/') + 1));
        file.setBucket(minioProperties.getBucket());
        file.setObjectKey(objectKey);
        file.setSize(session.getFileSize());
        file.setContentType("video/mp4");
        file.setMd5(session.getFileMd5());
        file.setBizType(VIDEO_BIZ_TYPE);
        file.setUploaderId(UserContext.getUserIdOrSystem());
        file.setUploadTime(LocalDateTime.now());
        fileObjectMapper.insert(file);
        return file;
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
            throw new BizException("视频合并失败: " + ex.getMessage());
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
        List<VideoReviewTask> submitted = taskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, review.getId())
                .eq(VideoReviewTask::getReviewerRole, "REVIEWER")
                .eq(VideoReviewTask::getSubmitted, 1)
                .orderByAsc(VideoReviewTask::getSubmitTime));
        if (submitted.size() < expected) {
            return;
        }
        List<VideoReviewTask> initialReviews = submitted.stream().limit(expected).toList();
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
        reviewMapper.updateById(review);
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
        reviewMapper.updateById(review);
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

    private List<VideoReview> selectReviews(VideoQuery query) {
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
        return reviewMapper.selectList(wrapper);
    }

    private VideoReviewVO toVO(VideoReview entity) {
        Student student = studentMapper.selectById(entity.getStudentId());
        VideoReviewVO vo = new VideoReviewVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setStudentNo(student == null ? null : student.getStudentNo());
        vo.setStudentName(student == null ? null : student.getName());
        vo.setCollegeId(entity.getCollegeId());
        vo.setAssessmentYear(entity.getAssessmentYear());
        vo.setVideoFileId(entity.getVideoFileId());
        vo.setVideoFileName(entity.getVideoFileName());
        vo.setFileMd5(entity.getFileMd5());
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
        List<VideoReviewTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, entity.getId())
                .orderByAsc(VideoReviewTask::getCreatedAt));
        boolean managementView = canViewSubmittedTasks(entity);
        Long currentUserId = UserContext.getUserId();
        vo.setTasks(tasks.stream()
                .filter(task -> managementView || (currentUserId != null && currentUserId.equals(task.getReviewerId())))
                .map(task -> toTaskVO(task, managementView))
                .toList());
        return vo;
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
        String value = paramService.getString("file.maxSize.video", String.valueOf(DEFAULT_MAX_VIDEO_SIZE));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_VIDEO_SIZE;
        }
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
