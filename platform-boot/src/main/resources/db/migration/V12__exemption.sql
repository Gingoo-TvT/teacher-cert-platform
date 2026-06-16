CREATE TABLE IF NOT EXISTS exemption_request (
    id                            BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    student_id                    BIGINT       NOT NULL COMMENT '学生ID',
    college_id                    BIGINT       NOT NULL COMMENT '所属学院ID，用于数据范围',
    assessment_year               VARCHAR(16)  NOT NULL COMMENT '考核年度（文本）',
    teaching_segment              VARCHAR(64)  NOT NULL COMMENT '任教学段字典编码',
    subject                       VARCHAR(128) NOT NULL COMMENT '免考科目字典编码',
    subject_label                 VARCHAR(128) NOT NULL COMMENT '免考科目名称快照',
    basis                         VARCHAR(128) NOT NULL COMMENT '免考依据字典编码',
    basis_label                   VARCHAR(128) NOT NULL COMMENT '免考依据名称快照',
    remark                        VARCHAR(500)          DEFAULT NULL COMMENT '申请说明',
    first_review_status           VARCHAR(32)           DEFAULT NULL COMMENT '初审结论 PASS/REJECT/FAIL',
    first_reviewer_id             BIGINT                DEFAULT NULL COMMENT '初审人',
    first_review_time             DATETIME              DEFAULT NULL COMMENT '初审时间',
    first_review_comment          VARCHAR(500)          DEFAULT NULL COMMENT '初审意见',
    second_review_status          VARCHAR(32)           DEFAULT NULL COMMENT '复审结论 PASS/REJECT/FAIL',
    second_reviewer_id            BIGINT                DEFAULT NULL COMMENT '复审人',
    second_review_time            DATETIME              DEFAULT NULL COMMENT '复审时间',
    second_review_comment         VARCHAR(500)          DEFAULT NULL COMMENT '复审意见',
    final_status                  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/FIRST_REVIEW/FIRST_REJECTED/SECOND_REVIEW/SECOND_REJECTED/PASSED/FAILED',
    included_in_exam              TINYINT      NOT NULL DEFAULT 1 COMMENT '是否仍纳入应考科目 1是0否',
    locked                        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否锁定 1是0否',
    created_by                    BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at                    DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by                    BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at                    DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted                       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_exemption_student_year_subject (student_id, assessment_year, subject, deleted),
    KEY idx_exemption_student_year (student_id, assessment_year),
    KEY idx_exemption_college (college_id),
    KEY idx_exemption_status (final_status),
    KEY idx_exemption_subject_status (subject, final_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '免考申请';

CREATE TABLE IF NOT EXISTS exemption_material (
    id                            BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    exemption_request_id          BIGINT       NOT NULL COMMENT '免考申请ID',
    student_id                    BIGINT       NOT NULL COMMENT '学生ID，用于本人数据范围',
    college_id                    BIGINT       NOT NULL COMMENT '所属学院ID，用于学院数据范围',
    file_id                       BIGINT       NOT NULL COMMENT '文件ID',
    file_name                     VARCHAR(255) NOT NULL COMMENT '文件名',
    file_path                     VARCHAR(512) NOT NULL COMMENT '对象存储路径',
    file_size                     BIGINT                DEFAULT NULL COMMENT '文件大小(字节)',
    content_type                  VARCHAR(128)          DEFAULT NULL COMMENT 'MIME类型',
    uploader_id                   BIGINT       NOT NULL COMMENT '上传人',
    upload_time                   DATETIME     NOT NULL COMMENT '上传时间',
    created_by                    BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at                    DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by                    BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at                    DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted                       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    KEY idx_exemption_material_request (exemption_request_id),
    KEY idx_exemption_material_student (student_id),
    KEY idx_exemption_material_college (college_id),
    KEY idx_exemption_material_file (file_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '免考佐证材料';

INSERT INTO sys_param
(id, param_key, param_value, param_type, param_group, description, editable, created_at, updated_at, deleted)
VALUES
(120000000000000101, 'file.exemption.allowedTypes', 'application/pdf,image/jpeg,image/png', 'string', 'file',
 '免考佐证允许的MIME类型，逗号分隔', 1, NOW(), NOW(), 0),
(120000000000000102, 'file.maxSize.exemption', '52428800', 'int', 'file',
 '免考佐证单附件最大字节数', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    param_value = VALUES(param_value),
    param_type = VALUES(param_type),
    param_group = VALUES(param_group),
    description = VALUES(description),
    editable = VALUES(editable),
    updated_at = NOW(),
    deleted = 0;

-- TODO: 待学校确认(确认单#12)：可免科目清单由学校后续模板导入；当前保留示例默认值供联调和反例测试。
INSERT INTO sys_dict_item
(id, type_code, item_code, item_value, parent_code, sort, status, year_version, ext_json, created_at, updated_at, deleted)
VALUES
(120000000000001001, 'exemption_subject', 'comprehensive_quality_kindergarten', '综合素质（幼儿园）', 'kindergarten', 1, 1, 'GLOBAL', JSON_OBJECT('segment', 'kindergarten'), NOW(), NOW(), 0),
(120000000000001002, 'exemption_subject', 'childcare_knowledge_ability', '保教知识与能力', 'kindergarten', 2, 1, 'GLOBAL', JSON_OBJECT('segment', 'kindergarten'), NOW(), NOW(), 0),
(120000000000001003, 'exemption_subject', 'comprehensive_quality_primary', '综合素质（小学）', 'primary_school', 3, 1, 'GLOBAL', JSON_OBJECT('segment', 'primary_school'), NOW(), NOW(), 0),
(120000000000001004, 'exemption_subject', 'education_teaching_knowledge_ability', '教育教学知识与能力', 'primary_school', 4, 1, 'GLOBAL', JSON_OBJECT('segment', 'primary_school'), NOW(), NOW(), 0),
(120000000000001005, 'exemption_subject', 'comprehensive_quality_junior', '综合素质（初中）', 'junior_middle_school', 5, 1, 'GLOBAL', JSON_OBJECT('segment', 'junior_middle_school'), NOW(), NOW(), 0),
(120000000000001006, 'exemption_subject', 'education_knowledge_junior', '教育知识与能力（初中）', 'junior_middle_school', 6, 1, 'GLOBAL', JSON_OBJECT('segment', 'junior_middle_school'), NOW(), NOW(), 0),
(120000000000001007, 'exemption_subject', 'subject_knowledge_junior', '学科知识与教学能力（初中）', 'junior_middle_school', 7, 1, 'GLOBAL', JSON_OBJECT('segment', 'junior_middle_school'), NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    item_value = VALUES(item_value),
    parent_code = VALUES(parent_code),
    sort = VALUES(sort),
    status = VALUES(status),
    year_version = VALUES(year_version),
    ext_json = VALUES(ext_json),
    updated_at = NOW(),
    deleted = 0;

-- TODO: 待学校确认(确认单#12)：免考依据由学校后续模板导入；当前保留示例默认值供联调和反例测试。
INSERT INTO sys_dict_item
(id, type_code, item_code, item_value, parent_code, sort, status, year_version, ext_json, created_at, updated_at, deleted)
VALUES
(120000000000002001, 'exemption_basis', 'policy_exemption', '政策规定免考', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(120000000000002002, 'exemption_basis', 'course_certificate', '课程或证书佐证', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    item_value = VALUES(item_value),
    sort = VALUES(sort),
    status = VALUES(status),
    year_version = VALUES(year_version),
    ext_json = VALUES(ext_json),
    updated_at = NOW(),
    deleted = 0;
