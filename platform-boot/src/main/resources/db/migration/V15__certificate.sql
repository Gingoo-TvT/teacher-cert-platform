CREATE TABLE IF NOT EXISTS certificate (
    id                          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    student_id                  BIGINT       NOT NULL COMMENT '学生ID',
    college_id                  BIGINT       NOT NULL COMMENT '所属学院ID，用于数据范围',
    assessment_year             VARCHAR(16)  NOT NULL COMMENT '考核年度（文本）',
    cert_no                     VARCHAR(18)           DEFAULT NULL COMMENT '18位证书编号（文本）',
    student_no                  VARCHAR(64)           DEFAULT NULL COMMENT '学号快照',
    student_name                VARCHAR(64)           DEFAULT NULL COMMENT '姓名快照',
    id_card_type                VARCHAR(64)           DEFAULT NULL COMMENT '证件类型快照',
    id_card_no                  VARCHAR(64)           DEFAULT NULL COMMENT '证件号码快照（文本）',
    education_level             VARCHAR(64)           DEFAULT NULL COMMENT '学历层次字典编码快照',
    training_goal               VARCHAR(255)          DEFAULT NULL COMMENT '培养目标快照',
    teaching_segment            VARCHAR(64)           DEFAULT NULL COMMENT '任教学段字典编码快照',
    teaching_subject_code       VARCHAR(64)           DEFAULT NULL COMMENT '任教学科编码快照',
    teaching_subject_name       VARCHAR(128)          DEFAULT NULL COMMENT '任教学科名称快照',
    issuer                      VARCHAR(64)           DEFAULT NULL COMMENT '签发人',
    issue_date                  VARCHAR(32)           DEFAULT NULL COMMENT '签发日期（文本）',
    valid_until                 VARCHAR(32)           DEFAULT NULL COMMENT '有效期限（文本）',
    status                      VARCHAR(32)  NOT NULL DEFAULT 'WAIT_GENERATE' COMMENT '证书状态',
    void_reason                 VARCHAR(500)          DEFAULT NULL COMMENT '作废原因',
    void_operator_id            BIGINT                DEFAULT NULL COMMENT '作废操作人',
    void_time                   DATETIME              DEFAULT NULL COMMENT '作废时间',
    reissue_origin_cert_no      VARCHAR(18)           DEFAULT NULL COMMENT '重开原证书号',
    correction_reason           VARCHAR(500)          DEFAULT NULL COMMENT '最近更正原因',
    locked                      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否锁定 1是0否',
    created_by                  BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at                  DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by                  BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at                  DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted                     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_certificate_cert_no (cert_no, deleted),
    KEY idx_certificate_student_year_active (student_id, assessment_year, deleted),
    KEY idx_certificate_college (college_id),
    KEY idx_certificate_student_year (student_id, assessment_year),
    KEY idx_certificate_status (status),
    KEY idx_certificate_origin (reissue_origin_cert_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教师职业能力证书';

CREATE TABLE IF NOT EXISTS cert_sequence (
    id             BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    scope_key      VARCHAR(128) NOT NULL COMMENT '序列作用域键',
    current_seq    INT          NOT NULL DEFAULT 0 COMMENT '当前已使用序号',
    created_by     BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at     DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by     BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at     DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_cert_sequence_scope (scope_key, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '证书编号序列';

INSERT INTO sys_dict_type
(id, type_code, type_name, description, sort, status, created_at, updated_at, deleted)
VALUES
(150000000000000001, 'certificate_status', '证书状态', '证书状态机C', 190, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    type_name = VALUES(type_name),
    description = VALUES(description),
    sort = VALUES(sort),
    status = VALUES(status),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO sys_dict_item
(id, type_code, item_code, item_value, parent_code, sort, status, year_version, ext_json, created_at, updated_at, deleted)
VALUES
(150000000000001001, 'certificate_status', 'WAIT_GENERATE', '待生成', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(150000000000001002, 'certificate_status', 'GENERATED', '已生成', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(150000000000001003, 'certificate_status', 'ISSUED', '已签发', NULL, 3, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(150000000000001004, 'certificate_status', 'EXPORTED', '已导出', NULL, 4, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(150000000000001005, 'certificate_status', 'ARCHIVED', '已归档', NULL, 5, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(150000000000001006, 'certificate_status', 'VOIDED', '已作废', NULL, 6, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(150000000000001007, 'certificate_status', 'REISSUED', '已重开', NULL, 7, 1, 'GLOBAL', NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    item_value = VALUES(item_value),
    parent_code = VALUES(parent_code),
    sort = VALUES(sort),
    status = VALUES(status),
    year_version = VALUES(year_version),
    ext_json = VALUES(ext_json),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO sys_param
(id, param_key, param_value, param_type, param_group, description, editable, created_at, updated_at, deleted)
VALUES
(150000000000002001, 'cert.seq.scope', 'SCHOOL_YEAR_SEGMENT', 'enum', 'cert',
 '证书序列作用域 SCHOOL_YEAR_SEGMENT/SCHOOL_YEAR，确认单#1=按学段', 1, NOW(), NOW(), 0),
(150000000000002002, 'cert.province.code', '44', 'string', 'cert',
 '省级行政区划代码，广东=44', 1, NOW(), NOW(), 0),
(150000000000002003, 'cert.school.code', '10588', 'string', 'cert',
 '高校代码，广东技术师范大学=10588', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    param_type = VALUES(param_type),
    param_group = VALUES(param_group),
    description = VALUES(description),
    editable = VALUES(editable),
    updated_at = NOW(),
    deleted = 0;
