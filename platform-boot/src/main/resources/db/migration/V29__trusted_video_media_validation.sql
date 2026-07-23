-- MySQL DDL 会逐句提交。本迁移的 ADD COLUMN / ADD INDEX 均先查询 information_schema，
-- 使 V29 在前半 DDL 已落地、Flyway repair 后仍可安全收敛到同一目标结构。

ALTER TABLE file_object
    MODIFY COLUMN md5 VARCHAR(64) DEFAULT NULL
        COMMENT '内容摘要；视频为服务端SHA-256分片树指纹';

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'file_object'
       AND column_name = 'checksum_algorithm') = 0,
    'ALTER TABLE file_object ADD COLUMN checksum_algorithm VARCHAR(32) DEFAULT NULL COMMENT ''服务端内容摘要算法；视频使用SHA256_TREE_V1'' AFTER upload_metadata_hash',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'file_object'
       AND column_name = 'content_hash_verified') = 0,
    'ALTER TABLE file_object ADD COLUMN content_hash_verified TINYINT NOT NULL DEFAULT 0 COMMENT ''摘要是否由服务端读取对象内容后验证'' AFTER checksum_algorithm',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'file_object'
       AND column_name = 'media_codec') = 0,
    'ALTER TABLE file_object ADD COLUMN media_codec VARCHAR(32) DEFAULT NULL COMMENT ''服务端探测到的视频编码'' AFTER content_hash_verified',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'file_object'
       AND column_name = 'media_validation_policy_hash') = 0,
    'ALTER TABLE file_object ADD COLUMN media_validation_policy_hash CHAR(64) DEFAULT NULL COMMENT ''媒体验证规则快照SHA-256'' AFTER media_codec',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'file_object'
       AND column_name = 'media_probe_version') = 0,
    'ALTER TABLE file_object ADD COLUMN media_probe_version VARCHAR(32) DEFAULT NULL COMMENT ''媒体探测器版本'' AFTER media_validation_policy_hash',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'file_object'
       AND index_name = 'idx_file_verified_hash') = 0,
    'ALTER TABLE file_object ADD KEY idx_file_verified_hash (biz_type, md5, uploader_id, content_hash_verified, status, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE video_upload_session
    MODIFY COLUMN file_md5 VARCHAR(64) NOT NULL
        COMMENT '兼容字段名；上传中为客户端声明摘要，定稿后为服务端内容指纹';

ALTER TABLE video_review
    MODIFY COLUMN file_md5 VARCHAR(64) DEFAULT NULL
        COMMENT '服务端验真的视频内容指纹（仅内部使用）';

INSERT INTO sys_param
    (id, param_key, param_value, param_type, param_group, description, editable,
     created_by, created_at, updated_by, updated_at, deleted)
VALUES
    (290000000000000001, 'video.allowedCodecs', 'H264', 'string', 'video',
     '教学视频允许的服务端探测编码，多个值用逗号分隔', 1,
     0, NOW(), 0, NOW(), 0),
    (290000000000000002, 'video.timelineToleranceSeconds', '2', 'int', 'video',
     '视频头时长、样本时间线与样本时长和的最大允许差值（秒）', 1,
     0, NOW(), 0, NOW(), 0)
ON DUPLICATE KEY UPDATE
    param_value = VALUES(param_value),
    param_type = VALUES(param_type),
    param_group = VALUES(param_group),
    description = VALUES(description),
    editable = VALUES(editable),
    updated_at = NOW(),
    deleted = 0;
