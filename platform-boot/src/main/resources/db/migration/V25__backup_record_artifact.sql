-- Phase 41.2（P0-6 真备份）：backup_record 关联真实产物元数据。
-- 触发备份从"仅插一行 COMPLETED"改为 JDBC 逻辑导出→gzip→上传 MinIO，记录须承载可核验的产物信息。
ALTER TABLE backup_record
    ADD COLUMN byte_size   BIGINT      DEFAULT NULL COMMENT '备份产物字节数(gzip 后)',
    ADD COLUMN checksum    VARCHAR(80) DEFAULT NULL COMMENT '产物校验和(算法:hex，如 sha256:...)',
    ADD COLUMN table_count INT         DEFAULT NULL COMMENT '导出表数',
    ADD COLUMN row_count   BIGINT      DEFAULT NULL COMMENT '导出行数';
