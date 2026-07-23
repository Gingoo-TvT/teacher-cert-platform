ALTER TABLE file_object
    MODIFY COLUMN md5 VARCHAR(64) DEFAULT NULL
        COMMENT '内容摘要；视频为服务端SHA-256分片树指纹',
    ADD COLUMN checksum_algorithm VARCHAR(32) DEFAULT NULL
        COMMENT '服务端内容摘要算法；视频使用SHA256_TREE_V1' AFTER upload_metadata_hash,
    ADD COLUMN content_hash_verified TINYINT NOT NULL DEFAULT 0
        COMMENT '摘要是否由服务端读取对象内容后验证' AFTER checksum_algorithm,
    ADD KEY idx_file_verified_hash
        (biz_type, md5, uploader_id, content_hash_verified, status, deleted);

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
     0, NOW(), 0, NOW(), 0)
ON DUPLICATE KEY UPDATE
    param_value = VALUES(param_value),
    param_type = VALUES(param_type),
    param_group = VALUES(param_group),
    description = VALUES(description),
    editable = VALUES(editable),
    updated_at = NOW(),
    deleted = 0;
