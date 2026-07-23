package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.business.video.entity.VideoFinalizationObjectCandidate;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadSession;
import cn.edu.gpnu.platform.business.video.mapper.VideoFinalizationObjectCandidateMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadSessionMapper;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FAILED/失权候选对象的持久对账器。DB 领取与提交均为短事务，MinIO 调用严格位于事务外。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoFinalizationObjectReconciler {

    private static final int ERROR_LIMIT = 500;

    private final VideoFinalizationObjectCandidateMapper candidateMapper;
    private final VideoUploadSessionMapper sessionMapper;
    private final FileObjectMapper fileObjectMapper;
    private final VideoFinalizationObjectLifecycleService lifecycleService;
    private final VideoFinalizationObjectStore objectStore;
    private final TransactionTemplate transactionTemplate;

    /**
     * 终态/换代后立即尝试一次，但不标记 CLEANED；静默期后仍会再删并确认，覆盖迟到外部写。
     */
    public int bestEffortPending(String uploadId) {
        List<Long> ids = candidateMapper.selectList(
                        new LambdaQueryWrapper<VideoFinalizationObjectCandidate>()
                                .select(VideoFinalizationObjectCandidate::getId)
                                .eq(VideoFinalizationObjectCandidate::getUploadId, uploadId)
                                .eq(VideoFinalizationObjectCandidate::getState,
                                        VideoFinalizationObjectState.CLEANUP_PENDING.name())
                                .orderByAsc(VideoFinalizationObjectCandidate::getId)
                                .last("LIMIT " + lifecycleService.batchSize()))
                .stream()
                .map(VideoFinalizationObjectCandidate::getId)
                .toList();
        int attempted = 0;
        for (Long id : ids) {
            ClaimedCandidate claimed = transactionTemplate.execute(
                    status -> claim(id, LocalDateTime.now(), false));
            if (claimed == null) {
                continue;
            }
            attempted++;
            try {
                objectStore.remove(claimed.bucket(), claimed.objectKey());
                finishBestEffort(claimed, null);
            } catch (RuntimeException e) {
                finishBestEffort(claimed, message(e));
                log.warn("视频定稿候选对象即时删除失败，已保留持久重试: uploadId={}, generation={}, message={}",
                        claimed.uploadId(), claimed.generation(), message(e));
            }
        }
        return attempted;
    }

    public ReconcileResult reconcileDue() {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = lifecycleService.batchSize();
        // 正常失败重试与过期 claim 独占一批，历史 CLEANED 墓碑另取一批，避免墓碑挤占紧急清理。
        List<Long> ids = new ArrayList<>(
                candidateMapper.selectDueCleanupIds(now, batchSize));
        ids.addAll(candidateMapper.selectDueTombstoneIds(now, batchSize));
        int cleaned = 0;
        int failed = 0;
        for (Long id : ids) {
            ClaimedCandidate claimed = transactionTemplate.execute(
                    status -> claim(id, LocalDateTime.now(), true));
            if (claimed == null) {
                continue;
            }
            try {
                objectStore.remove(claimed.bucket(), claimed.objectKey());
                if (objectStore.exists(claimed.bucket(), claimed.objectKey())) {
                    throw new VideoProbeInfrastructureException("对象删除后仍可见");
                }
                if (finishCleaned(claimed)) {
                    cleaned++;
                }
            } catch (RuntimeException e) {
                failed++;
                finishFailure(claimed, message(e));
                log.warn("视频定稿候选对象持久清理失败: uploadId={}, generation={}, message={}",
                        claimed.uploadId(), claimed.generation(), message(e));
            }
        }
        if (!ids.isEmpty()) {
            log.info("视频定稿候选对象对账完成: scanned={}, cleaned={}, failed={}",
                    ids.size(), cleaned, failed);
        }
        return new ReconcileResult(ids.size(), cleaned, failed);
    }

    private ClaimedCandidate claim(Long id, LocalDateTime now, boolean enforceDue) {
        VideoFinalizationObjectCandidate snapshot = candidateMapper.selectById(id);
        if (snapshot == null) {
            return null;
        }
        VideoUploadSession session = lockSession(snapshot.getUploadId());
        VideoFinalizationObjectCandidate candidate = candidateMapper.selectForUpdate(id);
        if (candidate == null || !claimable(candidate, now, enforceDue)) {
            return null;
        }
        Protection protection = protection(candidate, session);
        if (protection.registeredFileId() != null) {
            markRegistered(candidate, protection.registeredFileId(), now);
            return null;
        }
        if (protection.protectedReason() != null) {
            postpone(candidate, now, protection.protectedReason());
            return null;
        }
        String claimOwner = UUID.randomUUID().toString().replace("-", "");
        candidate.setState(VideoFinalizationObjectState.CLEANING.name());
        candidate.setClaimOwner(claimOwner);
        candidate.setClaimExpiresAt(now.plusSeconds(lifecycleService.claimSeconds()));
        candidate.setUpdatedAt(now);
        candidateMapper.updateById(candidate);
        return new ClaimedCandidate(candidate.getId(), candidate.getUploadId(),
                candidate.getFinalizationGeneration(), candidate.getBucket(),
                candidate.getObjectKey(), claimOwner, enforceDue);
    }

    private boolean claimable(VideoFinalizationObjectCandidate candidate,
                              LocalDateTime now, boolean enforceDue) {
        if (!enforceDue) {
            return VideoFinalizationObjectState.CLEANUP_PENDING.name().equals(candidate.getState());
        }
        if (candidate.getCleanupNotBefore() == null
                || candidate.getCleanupNotBefore().isAfter(now)) {
            return false;
        }
        if (VideoFinalizationObjectState.CLEANUP_PENDING.name().equals(candidate.getState())) {
            return candidate.getNextRetryAt() != null
                    && !candidate.getNextRetryAt().isAfter(now);
        }
        if (VideoFinalizationObjectState.CLEANED.name().equals(candidate.getState())) {
            return candidate.getNextRetryAt() != null
                    && !candidate.getNextRetryAt().isAfter(now);
        }
        return VideoFinalizationObjectState.CLEANING.name().equals(candidate.getState())
                && candidate.getClaimExpiresAt() != null
                && !candidate.getClaimExpiresAt().isAfter(now);
    }

    private Protection protection(VideoFinalizationObjectCandidate candidate,
                                    VideoUploadSession session) {
        FileObject registered = fileObjectMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getBucket, candidate.getBucket())
                .eq(FileObject::getObjectKey, candidate.getObjectKey())
                .last("LIMIT 1"));
        if (registered != null) {
            return Protection.registered(registered.getId());
        }
        VideoFinalizationObjectCandidate protecting = candidateMapper.selectOne(
                new LambdaQueryWrapper<VideoFinalizationObjectCandidate>()
                        .eq(VideoFinalizationObjectCandidate::getBucket, candidate.getBucket())
                        .eq(VideoFinalizationObjectCandidate::getObjectKey, candidate.getObjectKey())
                        .ne(VideoFinalizationObjectCandidate::getId, candidate.getId())
                        .in(VideoFinalizationObjectCandidate::getState,
                                VideoFinalizationObjectState.ACTIVE.name(),
                                VideoFinalizationObjectState.REGISTERED.name())
                        .orderByDesc(VideoFinalizationObjectCandidate::getFinalizationGeneration)
                        .last("LIMIT 1"));
        if (protecting != null) {
            if (VideoFinalizationObjectState.REGISTERED.name().equals(protecting.getState())
                    && protecting.getRegisteredFileId() != null) {
                return Protection.registered(protecting.getRegisteredFileId());
            }
            return Protection.protectedBy("同一对象Key仍有活跃定稿世代");
        }
        if (session != null && Objects.equals(session.getObjectKey(), candidate.getObjectKey())
                && VideoUploadStatus.of(session.getStatus()) != VideoUploadStatus.FAILED) {
            return Protection.protectedBy("上传会话仍引用该候选对象");
        }
        return Protection.none();
    }

    private void finishBestEffort(ClaimedCandidate claimed, String error) {
        transactionTemplate.executeWithoutResult(status -> {
            lockSession(claimed.uploadId());
            VideoFinalizationObjectCandidate candidate = candidateMapper.selectForUpdate(claimed.id());
            if (!ownsClaim(candidate, claimed)) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            candidateMapper.update(null,
                    new LambdaUpdateWrapper<VideoFinalizationObjectCandidate>()
                            .eq(VideoFinalizationObjectCandidate::getId, claimed.id())
                            .eq(VideoFinalizationObjectCandidate::getState,
                                    VideoFinalizationObjectState.CLEANING.name())
                            .eq(VideoFinalizationObjectCandidate::getClaimOwner, claimed.claimOwner())
                            .set(VideoFinalizationObjectCandidate::getState,
                                    VideoFinalizationObjectState.CLEANUP_PENDING.name())
                            .set(VideoFinalizationObjectCandidate::getClaimOwner, null)
                            .set(VideoFinalizationObjectCandidate::getClaimExpiresAt, null)
                            .set(VideoFinalizationObjectCandidate::getLastAttemptAt, now)
                            .set(VideoFinalizationObjectCandidate::getLastError, truncate(error))
                            .set(VideoFinalizationObjectCandidate::getUpdatedAt, now)
                            .setSql("attempt_count = attempt_count + 1"));
        });
    }

    private boolean finishCleaned(ClaimedCandidate claimed) {
        Boolean changed = transactionTemplate.execute(status -> {
            VideoUploadSession session = lockSession(claimed.uploadId());
            VideoFinalizationObjectCandidate candidate = candidateMapper.selectForUpdate(claimed.id());
            if (!ownsClaim(candidate, claimed)) {
                return false;
            }
            LocalDateTime now = LocalDateTime.now();
            Protection protection = protection(candidate, session);
            if (protection.registeredFileId() != null) {
                markRegistered(candidate, protection.registeredFileId(), now);
                return false;
            }
            if (protection.protectedReason() != null) {
                postpone(candidate, now, protection.protectedReason());
                return false;
            }
            candidateMapper.update(null,
                    new LambdaUpdateWrapper<VideoFinalizationObjectCandidate>()
                            .eq(VideoFinalizationObjectCandidate::getId, claimed.id())
                            .eq(VideoFinalizationObjectCandidate::getState,
                                    VideoFinalizationObjectState.CLEANING.name())
                            .eq(VideoFinalizationObjectCandidate::getClaimOwner, claimed.claimOwner())
                            .set(VideoFinalizationObjectCandidate::getState,
                                    VideoFinalizationObjectState.CLEANED.name())
                            .set(VideoFinalizationObjectCandidate::getClaimOwner, null)
                            .set(VideoFinalizationObjectCandidate::getClaimExpiresAt, null)
                            .set(VideoFinalizationObjectCandidate::getNextRetryAt,
                                    now.plusSeconds(lifecycleService.tombstoneCheckSeconds()))
                            .set(VideoFinalizationObjectCandidate::getLastAttemptAt, now)
                            .set(VideoFinalizationObjectCandidate::getLastError, null)
                            .set(VideoFinalizationObjectCandidate::getCleanedAt, now)
                            .set(VideoFinalizationObjectCandidate::getUpdatedAt, now)
                            .setSql("attempt_count = attempt_count + 1"));
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    private void finishFailure(ClaimedCandidate claimed, String error) {
        transactionTemplate.executeWithoutResult(status -> {
            lockSession(claimed.uploadId());
            VideoFinalizationObjectCandidate candidate = candidateMapper.selectForUpdate(claimed.id());
            if (!ownsClaim(candidate, claimed)) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            candidateMapper.update(null,
                    new LambdaUpdateWrapper<VideoFinalizationObjectCandidate>()
                            .eq(VideoFinalizationObjectCandidate::getId, claimed.id())
                            .eq(VideoFinalizationObjectCandidate::getState,
                                    VideoFinalizationObjectState.CLEANING.name())
                            .eq(VideoFinalizationObjectCandidate::getClaimOwner, claimed.claimOwner())
                            .set(VideoFinalizationObjectCandidate::getState,
                                    VideoFinalizationObjectState.CLEANUP_PENDING.name())
                            .set(VideoFinalizationObjectCandidate::getClaimOwner, null)
                            .set(VideoFinalizationObjectCandidate::getClaimExpiresAt, null)
                            .set(VideoFinalizationObjectCandidate::getNextRetryAt,
                                    now.plusSeconds(lifecycleService.retrySeconds()))
                            .set(VideoFinalizationObjectCandidate::getLastAttemptAt, now)
                            .set(VideoFinalizationObjectCandidate::getLastError, truncate(error))
                            .set(VideoFinalizationObjectCandidate::getUpdatedAt, now)
                            .setSql("attempt_count = attempt_count + 1"));
        });
    }

    private void markRegistered(VideoFinalizationObjectCandidate candidate,
                                Long fileId, LocalDateTime now) {
        candidate.setState(VideoFinalizationObjectState.REGISTERED.name());
        candidate.setRegisteredFileId(fileId);
        candidate.setClaimOwner(null);
        candidate.setClaimExpiresAt(null);
        candidate.setLastError(null);
        candidate.setUpdatedAt(now);
        candidateMapper.updateById(candidate);
    }

    private void postpone(VideoFinalizationObjectCandidate candidate,
                          LocalDateTime now, String reason) {
        candidate.setState(VideoFinalizationObjectState.CLEANUP_PENDING.name());
        candidate.setClaimOwner(null);
        candidate.setClaimExpiresAt(null);
        candidate.setNextRetryAt(now.plusSeconds(lifecycleService.retrySeconds()));
        candidate.setLastError(truncate(reason));
        candidate.setUpdatedAt(now);
        candidateMapper.updateById(candidate);
    }

    private boolean ownsClaim(VideoFinalizationObjectCandidate candidate,
                              ClaimedCandidate claimed) {
        return candidate != null
                && VideoFinalizationObjectState.CLEANING.name().equals(candidate.getState())
                && Objects.equals(candidate.getClaimOwner(), claimed.claimOwner());
    }

    private VideoUploadSession lockSession(String uploadId) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, uploadId)
                .last("FOR UPDATE"));
    }

    private String message(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= ERROR_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_LIMIT);
    }

    private record ClaimedCandidate(
            Long id,
            String uploadId,
            Long generation,
            String bucket,
            String objectKey,
            String claimOwner,
            boolean finalPass) {
    }

    private record Protection(Long registeredFileId, String protectedReason) {

        private static Protection none() {
            return new Protection(null, null);
        }

        private static Protection registered(Long fileId) {
            return new Protection(fileId, null);
        }

        private static Protection protectedBy(String reason) {
            return new Protection(null, reason);
        }
    }

    public record ReconcileResult(int scanned, int cleaned, int failed) {
    }
}
