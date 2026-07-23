-- WS-3 第五轮：为每个定稿世代记录独立候选对象，并以持久状态机对账失败/失权对象。
-- SERVER_CHUNK 从本版本起使用 generation-specific object key；旧世代迟到写不会覆盖当前世代。
CREATE TABLE IF NOT EXISTS video_finalization_object_candidate (
    id                          BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    upload_id                   VARCHAR(64)   NOT NULL COMMENT '视频上传会话ID',
    finalization_generation     BIGINT        NOT NULL COMMENT '数据库永久定稿世代',
    upload_mode                 VARCHAR(32)   NOT NULL COMMENT 'PRESIGNED_MULTIPART/SERVER_CHUNK',
    bucket                      VARCHAR(128)  NOT NULL COMMENT '对象存储桶',
    object_key                  VARCHAR(512)  NOT NULL COMMENT '该世代候选对象Key',
    state                       VARCHAR(32)   NOT NULL COMMENT 'ACTIVE/CLEANUP_PENDING/CLEANING/REGISTERED/CLEANED(CLEANED为持续复查墓碑)',
    retired_at                  DATETIME(3)            DEFAULT NULL COMMENT '失权或终态时间',
    cleanup_not_before          DATETIME(3)            DEFAULT NULL COMMENT '静默期结束前不得最终确认清理',
    next_retry_at               DATETIME(3)            DEFAULT NULL COMMENT '下次持久清理时间',
    attempt_count               INT           NOT NULL DEFAULT 0 COMMENT '删除尝试次数',
    claim_owner                 VARCHAR(64)            DEFAULT NULL COMMENT '清理任务租约owner',
    claim_expires_at            DATETIME(3)            DEFAULT NULL COMMENT '清理任务租约失效时间',
    last_attempt_at             DATETIME(3)            DEFAULT NULL COMMENT '最近删除尝试时间',
    last_error                  VARCHAR(500)            DEFAULT NULL COMMENT '最近清理错误',
    registered_file_id          BIGINT                  DEFAULT NULL COMMENT '成功登记的file_object.id',
    cleaned_at                  DATETIME(3)            DEFAULT NULL COMMENT '最终确认对象不存在时间',
    created_by                  BIGINT                  DEFAULT NULL COMMENT '创建人',
    created_at                  DATETIME                DEFAULT NULL COMMENT '创建时间',
    updated_by                  BIGINT                  DEFAULT NULL COMMENT '更新人',
    updated_at                  DATETIME                DEFAULT NULL COMMENT '更新时间',
    deleted                     TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_video_final_candidate_generation (upload_id, finalization_generation),
    KEY idx_video_final_candidate_due
        (state, cleanup_not_before, next_retry_at, claim_expires_at, id),
    KEY idx_video_final_candidate_object (bucket, object_key(191), state),
    KEY idx_video_final_candidate_upload (upload_id, state)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频定稿世代候选对象与持久清理账本';

INSERT INTO sys_param
    (id, param_key, param_value, param_type, param_group, description, editable,
     created_by, created_at, updated_by, updated_at, deleted)
VALUES
    (320000000000000001, 'video.finalizationCleanupSafetySeconds', '60', 'int', 'video',
     '失权对象静默期在对象存储总调用超时之外追加的安全秒数', 1,
     0, NOW(), 0, NOW(), 0),
    (320000000000000002, 'video.finalizationCleanupRetrySeconds', '60', 'int', 'video',
     '视频定稿对象持久清理失败后的重试间隔秒数', 1,
     0, NOW(), 0, NOW(), 0),
    (320000000000000003, 'video.finalizationCleanupClaimSeconds', '1860', 'int', 'video',
     '视频定稿对象清理任务跨节点租约秒数（运行时不低于两次对象调用总上限加安全余量）', 1,
     0, NOW(), 0, NOW(), 0),
    (320000000000000004, 'video.finalizationCleanupBatchSize', '100', 'int', 'video',
     '视频定稿对象单轮持久对账最大候选数', 1,
     0, NOW(), 0, NOW(), 0),
    (320000000000000005, 'video.finalizationCleanupTombstoneCheckSeconds', '3600', 'int', 'video',
     '已确认不存在的定稿对象墓碑再次复查间隔秒数', 1,
     0, NOW(), 0, NOW(), 0)
ON DUPLICATE KEY UPDATE
    param_value = VALUES(param_value),
    param_type = VALUES(param_type),
    param_group = VALUES(param_group),
    description = VALUES(description),
    editable = VALUES(editable),
    updated_at = NOW(),
    deleted = 0;
