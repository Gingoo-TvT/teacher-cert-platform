ALTER TABLE file_object
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT '对象状态 INITIATING/UPLOADING/COMPLETING/READY/FAILED' AFTER biz_type,
    ADD COLUMN multipart_upload_id VARCHAR(255) DEFAULT NULL COMMENT 'S3 multipart uploadId，仅待定稿对象使用' AFTER status,
    ADD COLUMN upload_part_size BIGINT DEFAULT NULL COMMENT '直传分片大小' AFTER multipart_upload_id,
    ADD COLUMN upload_total_parts INT DEFAULT NULL COMMENT '直传总分片数' AFTER upload_part_size,
    ADD COLUMN upload_expires_at DATETIME DEFAULT NULL COMMENT '直传会话过期时间' AFTER upload_total_parts,
    ADD COLUMN upload_context_hash CHAR(64) DEFAULT NULL COMMENT 'owner之外的业务上下文SHA-256' AFTER upload_expires_at,
    ADD COLUMN upload_metadata_hash CHAR(64) DEFAULT NULL COMMENT '含绑定版本的完整业务元数据SHA-256' AFTER upload_context_hash,
    ADD COLUMN active_upload_key VARCHAR(192)
        GENERATED ALWAYS AS (
            CASE WHEN deleted = 0 AND upload_context_hash IS NOT NULL
                       AND status IN ('INITIATING', 'UPLOADING', 'COMPLETING')
                 THEN CONCAT(uploader_id, '-', biz_type, '-', upload_context_hash) END
        ) STORED COMMENT '同一owner/业务上下文仅一个活跃直传',
    ADD KEY idx_file_status (status),
    ADD KEY idx_file_object_key (bucket, object_key(191)),
    ADD UNIQUE KEY uk_file_active_upload (active_upload_key);

ALTER TABLE video_upload_session
    ADD COLUMN upload_mode VARCHAR(32) NOT NULL DEFAULT 'SERVER_CHUNK' COMMENT 'PRESIGNED_MULTIPART/SERVER_CHUNK' AFTER upload_id,
    ADD COLUMN s3_upload_id VARCHAR(255) DEFAULT NULL COMMENT 'S3 multipart uploadId' AFTER upload_mode,
    ADD COLUMN object_key VARCHAR(512) DEFAULT NULL COMMENT '直传最终对象Key' AFTER s3_upload_id,
    ADD COLUMN presign_expires_at DATETIME DEFAULT NULL COMMENT '本批预签名链接过期时间' AFTER object_key,
    ADD COLUMN slot_claimed TINYINT NOT NULL DEFAULT 0 COMMENT 'WS-3新会话是否占用学生年度上传槽位' AFTER presign_expires_at,
    ADD COLUMN active_slot_key VARCHAR(96)
        GENERATED ALWAYS AS (
            CASE WHEN deleted = 0 AND slot_claimed = 1 AND status IN ('UPLOADING', 'MERGING')
                 THEN CONCAT(student_id, '-', assessment_year) END
        ) STORED COMMENT 'WS-3活跃上传唯一槽位，历史会话为NULL',
    ADD UNIQUE KEY uk_video_active_upload_slot (active_slot_key);

ALTER TABLE video_upload_chunk
    MODIFY COLUMN chunk_md5 VARCHAR(64) DEFAULT NULL COMMENT '服务端回退分片摘要',
    ADD COLUMN part_number INT DEFAULT NULL COMMENT 'S3 partNumber，从1开始' AFTER chunk_index,
    ADD COLUMN etag VARCHAR(255) DEFAULT NULL COMMENT 'S3 分片ETag' AFTER part_number,
    ADD KEY idx_video_chunk_part (upload_id, part_number);

ALTER TABLE process_material
    ADD COLUMN active_file_key BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN deleted = 0 AND file_id > 0 THEN file_id END
        ) STORED COMMENT '未删真实附件唯一键，file_id=0占位及软删记录为NULL',
    ADD UNIQUE KEY uk_process_material_file (active_file_key);
