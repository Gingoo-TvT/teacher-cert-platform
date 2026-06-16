CREATE TABLE IF NOT EXISTS process_material (
    id                            BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    student_id                    BIGINT       NOT NULL COMMENT '学生ID',
    college_id                    BIGINT       NOT NULL COMMENT '所属学院ID，用于数据范围',
    assessment_year               VARCHAR(16)  NOT NULL COMMENT '考核年度（文本）',
    category                      VARCHAR(64)  NOT NULL COMMENT '材料类别字典编码',
    file_id                       BIGINT       NOT NULL COMMENT '文件ID',
    file_name                     VARCHAR(255) NOT NULL COMMENT '文件名',
    file_path                     VARCHAR(512) NOT NULL COMMENT '对象存储路径',
    file_size                     BIGINT                DEFAULT NULL COMMENT '文件大小(字节)',
    content_type                  VARCHAR(128)          DEFAULT NULL COMMENT 'MIME类型',
    uploader_id                   BIGINT       NOT NULL COMMENT '上传人',
    upload_time                   DATETIME     NOT NULL COMMENT '上传时间',
    first_review_status           VARCHAR(32)           DEFAULT NULL COMMENT '初审结论 PASS/REJECT/FAIL',
    first_reviewer_id             BIGINT                DEFAULT NULL COMMENT '初审人',
    first_review_time             DATETIME              DEFAULT NULL COMMENT '初审时间',
    first_review_comment          VARCHAR(500)          DEFAULT NULL COMMENT '初审意见',
    second_review_status          VARCHAR(32)           DEFAULT NULL COMMENT '复审结论 PASS/REJECT/FAIL',
    second_reviewer_id            BIGINT                DEFAULT NULL COMMENT '复审人',
    second_review_time            DATETIME              DEFAULT NULL COMMENT '复审时间',
    second_review_comment         VARCHAR(500)          DEFAULT NULL COMMENT '复审意见',
    status                        VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/FIRST_REVIEW/FIRST_REJECTED/SECOND_REVIEW/SECOND_REJECTED/PASSED/FAILED',
    locked                        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否锁定 1是0否',
    created_by                    BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at                    DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by                    BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at                    DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted                       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    KEY idx_material_student_year (student_id, assessment_year),
    KEY idx_material_college (college_id),
    KEY idx_material_category_status (category, status),
    KEY idx_material_file (file_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '过程性考核材料';

INSERT INTO sys_param
(id, param_key, param_value, param_type, param_group, description, editable, created_at, updated_at, deleted)
VALUES
(18, 'file.material.allowedTypes', 'application/pdf,image/jpeg,image/png', 'string', 'file',
 '过程性材料允许的MIME类型，逗号分隔', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    param_value = VALUES(param_value),
    param_type = VALUES(param_type),
    param_group = VALUES(param_group),
    description = VALUES(description),
    editable = VALUES(editable),
    updated_at = NOW(),
    deleted = 0;
