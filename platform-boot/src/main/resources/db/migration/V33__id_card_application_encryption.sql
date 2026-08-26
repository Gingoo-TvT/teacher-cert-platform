-- WS-8：身份证件号码由应用层 AES-GCM 加密保存，查重键改为应用层 HMAC-SHA256。
--
-- 本文件只负责可由普通 Flyway（含 Phase41 独立恢复器）建立的结构。密钥相关的数据回填由
-- Spring 注册的 AFTER_MIGRATE callback 完成；因此恢复流程可先创建完整结构，再回放已经加密的备份数据。
-- MySQL DDL 会逐句提交，所有结构操作都先查询 information_schema，Flyway repair 后可安全重跑。

ALTER TABLE student
    MODIFY COLUMN id_card_no VARCHAR(255) NOT NULL COMMENT '身份证件号码（应用层加密文本）';

ALTER TABLE certificate
    MODIFY COLUMN id_card_no VARCHAR(255) DEFAULT NULL COMMENT '证件号码快照（应用层加密文本）';

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'student'
       AND column_name = 'id_card_hmac') = 0,
    'ALTER TABLE student ADD COLUMN id_card_hmac CHAR(64) DEFAULT NULL COMMENT ''未删学生证件号HMAC-SHA256查重键'' AFTER id_card_no',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'certificate'
       AND column_name = 'id_card_hmac') = 0,
    'ALTER TABLE certificate ADD COLUMN id_card_hmac CHAR(64) DEFAULT NULL COMMENT ''证书证件号快照HMAC-SHA256检索键'' AFTER id_card_no',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- V24 的唯一键直接暴露明文；先删除索引，再删除 STORED 生成列。
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'student'
       AND index_name = 'uk_student_idcard') > 0,
    'ALTER TABLE student DROP INDEX uk_student_idcard',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'student'
       AND column_name = 'idcard_key') > 0,
    'ALTER TABLE student DROP COLUMN idcard_key',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 明文列不再建立索引；精确查找统一走 HMAC。
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'student'
       AND index_name = 'idx_student_id_card_no') > 0,
    'ALTER TABLE student DROP INDEX idx_student_id_card_no',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'student'
       AND index_name = 'uk_student_idcard') = 0,
    'ALTER TABLE student ADD UNIQUE KEY uk_student_idcard (id_card_hmac)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'certificate'
       AND index_name = 'idx_certificate_id_card_hmac') = 0,
    'ALTER TABLE certificate ADD KEY idx_certificate_id_card_hmac (id_card_hmac)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
