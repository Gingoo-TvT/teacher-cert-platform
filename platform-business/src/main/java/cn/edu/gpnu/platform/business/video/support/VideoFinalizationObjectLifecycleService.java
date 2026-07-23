package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.business.video.entity.VideoFinalizationObjectCandidate;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadSession;
import cn.edu.gpnu.platform.business.video.mapper.VideoFinalizationObjectCandidateMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadSessionMapper;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 在上传会话行锁事务内维护候选对象账本，使数据库状态和外部对象副作用可持续对账。
 */
@Service
@RequiredArgsConstructor
public class VideoFinalizationObjectLifecycleService {

    private static final String SAFETY_SECONDS_KEY = "video.finalizationCleanupSafetySeconds";
    private static final String RETRY_SECONDS_KEY = "video.finalizationCleanupRetrySeconds";
    private static final String CLAIM_SECONDS_KEY = "video.finalizationCleanupClaimSeconds";
    private static final String BATCH_SIZE_KEY = "video.finalizationCleanupBatchSize";
    private static final String TOMBSTONE_CHECK_SECONDS_KEY =
            "video.finalizationCleanupTombstoneCheckSeconds";
    private static final int DEFAULT_SAFETY_SECONDS = 60;
    private static final int DEFAULT_RETRY_SECONDS = 60;
    private static final int DEFAULT_CLAIM_SECONDS = 1860;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_TOMBSTONE_CHECK_SECONDS = 3600;

    private final VideoFinalizationObjectCandidateMapper candidateMapper;
    private final VideoUploadSessionMapper sessionMapper;
    private final MinioProperties minioProperties;
    private final ParamService paramService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 当前调用方已持有 session 行锁。先退休旧世代，再登记当前世代，避免覆盖旧 key 后失去清理事实。
     */
    public void activateCandidate(VideoUploadSession session, long previousGeneration,
                                  String previousObjectKey) {
        long generation = Objects.requireNonNull(session.getFinalizationToken(), "定稿世代不能为空");
        String objectKey = requiredObjectKey(session.getObjectKey());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cleanupNotBefore = cleanupNotBefore(now);

        retireActiveCandidates(session.getUploadId(), now, cleanupNotBefore);
        if (previousGeneration > 0 && StringUtils.hasText(previousObjectKey)) {
            ensureRetiredLegacyCandidate(
                    session, previousGeneration, previousObjectKey, now, cleanupNotBefore);
        }

        VideoFinalizationObjectCandidate existing = find(session.getUploadId(), generation);
        if (existing != null) {
            if (!VideoFinalizationObjectState.ACTIVE.name().equals(existing.getState())
                    || !objectKey.equals(existing.getObjectKey())) {
                throw new IllegalStateException("视频定稿候选世代已存在且状态不一致");
            }
            return;
        }
        candidateMapper.insert(candidate(
                session, generation, objectKey, VideoFinalizationObjectState.ACTIVE,
                null, null));
    }

    /**
     * FAILED 与候选清理事实同事务提交。即使当前对象尚不存在，也必须覆盖迟到的外部写。
     */
    public void markFailed(String uploadId) {
        retireActiveCandidates(uploadId);
    }

    /**
     * 当前定稿世代已结束但会话可能回到可重试态时，同样必须退休 ACTIVE 候选。
     */
    public void retireActiveCandidates(String uploadId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cleanupNotBefore = cleanupNotBefore(now);
        retireActiveCandidates(uploadId, now, cleanupNotBefore);
    }

    /**
     * file_object 登记与候选 REGISTERED 同事务提交；直传多世代共用 key，故同 key 历史候选一并保护。
     */
    public void markRegistered(String uploadId, long generation, String objectKey, Long fileId) {
        LocalDateTime now = LocalDateTime.now();
        int changed = candidateMapper.update(null,
                new LambdaUpdateWrapper<VideoFinalizationObjectCandidate>()
                        .eq(VideoFinalizationObjectCandidate::getUploadId, uploadId)
                        .eq(VideoFinalizationObjectCandidate::getObjectKey, objectKey)
                        .in(VideoFinalizationObjectCandidate::getState,
                                VideoFinalizationObjectState.ACTIVE.name(),
                                VideoFinalizationObjectState.CLEANUP_PENDING.name(),
                                VideoFinalizationObjectState.CLEANING.name())
                        .set(VideoFinalizationObjectCandidate::getState,
                                VideoFinalizationObjectState.REGISTERED.name())
                        .set(VideoFinalizationObjectCandidate::getRegisteredFileId, fileId)
                        .set(VideoFinalizationObjectCandidate::getClaimOwner, null)
                        .set(VideoFinalizationObjectCandidate::getClaimExpiresAt, null)
                        .set(VideoFinalizationObjectCandidate::getLastError, null)
                        .set(VideoFinalizationObjectCandidate::getUpdatedAt, now));
        VideoFinalizationObjectCandidate current = find(uploadId, generation);
        if (changed < 1 || current == null
                || !VideoFinalizationObjectState.REGISTERED.name().equals(current.getState())) {
            throw new IllegalStateException("视频定稿候选登记状态提交失败");
        }
    }

    /**
     * V32 上线时补录旧 MERGING/FAILED 会话；发布协议要求迁移前已停全部旧节点和 worker。
     */
    public int backfillLegacyCandidates() {
        List<String> uploadIds = candidateMapper.selectLegacyUploadIds(batchSize());
        int inserted = 0;
        for (String uploadId : uploadIds) {
            Boolean changed = transactionTemplate.execute(status -> backfillOne(uploadId));
            if (Boolean.TRUE.equals(changed)) {
                inserted++;
            }
        }
        return inserted;
    }

    public int retrySeconds() {
        return positiveParam(RETRY_SECONDS_KEY, DEFAULT_RETRY_SECONDS);
    }

    public int claimSeconds() {
        int configured = positiveParam(CLAIM_SECONDS_KEY, DEFAULT_CLAIM_SECONDS);
        long minimum = Math.addExact(
                Math.multiplyExact((long) minioProperties.getCallTimeoutSeconds(), 2L),
                positiveParam(SAFETY_SECONDS_KEY, DEFAULT_SAFETY_SECONDS));
        // 一轮对账最多连续执行 remove + exists 两次对象调用；租约不得在任一调用仍在飞时过期。
        // 极端运维参数组合采用 int 上限而不是让定时对账永久报错停摆。
        int safeMinimum = minimum >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) minimum;
        return Math.max(configured, safeMinimum);
    }

    public int batchSize() {
        return positiveParam(BATCH_SIZE_KEY, DEFAULT_BATCH_SIZE);
    }

    public int tombstoneCheckSeconds() {
        return positiveParam(
                TOMBSTONE_CHECK_SECONDS_KEY, DEFAULT_TOMBSTONE_CHECK_SECONDS);
    }

    private boolean backfillOne(String uploadId) {
        VideoUploadSession session = lockSession(uploadId);
        if (session == null || !StringUtils.hasText(session.getObjectKey())
                || find(session.getUploadId(), normalizedGeneration(session)) != null) {
            return false;
        }
        VideoUploadStatus status = VideoUploadStatus.of(session.getStatus());
        if (status != VideoUploadStatus.MERGING && status != VideoUploadStatus.FAILED) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        VideoFinalizationObjectState state = status == VideoUploadStatus.MERGING
                ? VideoFinalizationObjectState.ACTIVE
                : VideoFinalizationObjectState.CLEANUP_PENDING;
        LocalDateTime cleanupNotBefore =
                state == VideoFinalizationObjectState.CLEANUP_PENDING ? cleanupNotBefore(now) : null;
        candidateMapper.insert(candidate(
                session, normalizedGeneration(session), session.getObjectKey(), state,
                state == VideoFinalizationObjectState.CLEANUP_PENDING ? now : null,
                cleanupNotBefore));
        return true;
    }

    private void retireActiveCandidates(String uploadId, LocalDateTime now,
                                        LocalDateTime cleanupNotBefore) {
        candidateMapper.update(null,
                new LambdaUpdateWrapper<VideoFinalizationObjectCandidate>()
                        .eq(VideoFinalizationObjectCandidate::getUploadId, uploadId)
                        .eq(VideoFinalizationObjectCandidate::getState,
                                VideoFinalizationObjectState.ACTIVE.name())
                        .set(VideoFinalizationObjectCandidate::getState,
                                VideoFinalizationObjectState.CLEANUP_PENDING.name())
                        .set(VideoFinalizationObjectCandidate::getRetiredAt, now)
                        .set(VideoFinalizationObjectCandidate::getCleanupNotBefore, cleanupNotBefore)
                        .set(VideoFinalizationObjectCandidate::getNextRetryAt, cleanupNotBefore)
                        .set(VideoFinalizationObjectCandidate::getClaimOwner, null)
                        .set(VideoFinalizationObjectCandidate::getClaimExpiresAt, null)
                        .set(VideoFinalizationObjectCandidate::getUpdatedAt, now));
    }

    private void ensureRetiredLegacyCandidate(
            VideoUploadSession session, long generation, String objectKey,
            LocalDateTime retiredAt, LocalDateTime cleanupNotBefore) {
        if (find(session.getUploadId(), generation) != null) {
            return;
        }
        candidateMapper.insert(candidate(
                session, generation, objectKey, VideoFinalizationObjectState.CLEANUP_PENDING,
                retiredAt, cleanupNotBefore));
    }

    private VideoFinalizationObjectCandidate candidate(
            VideoUploadSession session, long generation, String objectKey,
            VideoFinalizationObjectState state, LocalDateTime retiredAt,
            LocalDateTime cleanupNotBefore) {
        VideoFinalizationObjectCandidate candidate = new VideoFinalizationObjectCandidate();
        candidate.setUploadId(session.getUploadId());
        candidate.setFinalizationGeneration(generation);
        candidate.setUploadMode(StringUtils.hasText(session.getUploadMode())
                ? session.getUploadMode() : "SERVER_CHUNK");
        candidate.setBucket(minioProperties.getBucket());
        candidate.setObjectKey(requiredObjectKey(objectKey));
        candidate.setState(state.name());
        candidate.setRetiredAt(retiredAt);
        candidate.setCleanupNotBefore(cleanupNotBefore);
        candidate.setNextRetryAt(cleanupNotBefore);
        candidate.setAttemptCount(0);
        return candidate;
    }

    private VideoFinalizationObjectCandidate find(String uploadId, long generation) {
        return candidateMapper.selectOne(
                new LambdaQueryWrapper<VideoFinalizationObjectCandidate>()
                        .eq(VideoFinalizationObjectCandidate::getUploadId, uploadId)
                        .eq(VideoFinalizationObjectCandidate::getFinalizationGeneration, generation)
                        .last("LIMIT 1"));
    }

    private VideoUploadSession lockSession(String uploadId) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, uploadId)
                .last("FOR UPDATE"));
    }

    private LocalDateTime cleanupNotBefore(LocalDateTime now) {
        long delaySeconds = Math.addExact(
                (long) minioProperties.getCallTimeoutSeconds(),
                positiveParam(SAFETY_SECONDS_KEY, DEFAULT_SAFETY_SECONDS));
        return now.plusSeconds(delaySeconds);
    }

    private int positiveParam(String key, int fallback) {
        return Math.max(1, paramService.getInt(key, fallback));
    }

    private long normalizedGeneration(VideoUploadSession session) {
        return session.getFinalizationToken() == null ? 0L : session.getFinalizationToken();
    }

    private String requiredObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalStateException("视频定稿候选对象Key不能为空");
        }
        return objectKey;
    }
}
