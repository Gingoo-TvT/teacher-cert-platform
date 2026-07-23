package cn.edu.gpnu.platform.business.video.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 视频定稿外部对象的持久世代账本。数据库状态决定候选对象可登记、可清理或已完成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_finalization_object_candidate")
public class VideoFinalizationObjectCandidate extends BaseEntity {

    private String uploadId;
    private Long finalizationGeneration;
    private String uploadMode;
    private String bucket;
    private String objectKey;
    private String state;
    private LocalDateTime retiredAt;
    private LocalDateTime cleanupNotBefore;
    private LocalDateTime nextRetryAt;
    private Integer attemptCount;
    private String claimOwner;
    private LocalDateTime claimExpiresAt;
    private LocalDateTime lastAttemptAt;
    private String lastError;
    private Long registeredFileId;
    private LocalDateTime cleanedAt;
}
