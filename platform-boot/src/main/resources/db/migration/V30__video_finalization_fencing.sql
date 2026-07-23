-- WS-3 第三轮：为可恢复定稿增加数据库 fencing token。
-- MySQL DDL 非事务，仍先查 information_schema，保证 repair 后可安全重跑。
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'video_upload_session'
       AND column_name = 'finalization_token') = 0,
    'ALTER TABLE video_upload_session ADD COLUMN finalization_token BIGINT DEFAULT NULL COMMENT ''定稿租约fencing token；仅当前token可提交结果'' AFTER validation_message',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
