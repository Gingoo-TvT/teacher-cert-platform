CREATE TABLE IF NOT EXISTS ability_test_result (
    id                   BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    student_id           BIGINT       NOT NULL COMMENT '学生ID',
    college_id           BIGINT       NOT NULL COMMENT '所属学院ID，用于数据范围',
    assessment_year      VARCHAR(16)  NOT NULL COMMENT '考核年度（文本）',
    exam_org_mode        VARCHAR(64)  NOT NULL COMMENT '考试组织方式字典编码',
    exam_subjects        JSON                  DEFAULT NULL COMMENT '应考科目JSON快照',
    score                VARCHAR(64)           DEFAULT NULL COMMENT '成绩（文本，保留前导零/长串）',
    conclusion           VARCHAR(32)  NOT NULL DEFAULT 'pending_confirm' COMMENT '结论 qualified/unqualified/exempted/pending_confirm',
    exemption_relation   JSON                  DEFAULT NULL COMMENT '免考联动关系JSON快照',
    confirm_status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '确认状态 PENDING/CONFIRMED',
    locked               TINYINT      NOT NULL DEFAULT 0 COMMENT '是否锁定 1是0否',
    created_by           BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at           DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by           BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at           DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_ability_test_student_year (student_id, assessment_year, deleted),
    KEY idx_ability_test_college (college_id),
    KEY idx_ability_test_year (assessment_year),
    KEY idx_ability_test_confirm (confirm_status),
    KEY idx_ability_test_conclusion (conclusion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教师职业能力测试结果';

INSERT INTO sys_dict_type
(id, type_code, type_name, description, sort, status, created_at, updated_at, deleted)
VALUES
(140000000000000001, 'exam_org_mode', '测试组织方式', '教师职业能力测试组织方式', 180, 1, NOW(), NOW(), 0)
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
(140000000000001001, 'exam_org_mode', 'with_internship_practice', '结合教育实习实践环节一并考核', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(140000000000001002, 'exam_org_mode', 'separate_interview', '单独面试', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    item_value = VALUES(item_value),
    parent_code = VALUES(parent_code),
    sort = VALUES(sort),
    status = VALUES(status),
    year_version = VALUES(year_version),
    ext_json = VALUES(ext_json),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO sys_permission
(id, code, name, type, parent_id, path, sort, status, created_at, updated_at, deleted)
VALUES
(140000000000002001, 'material:view', '材料查看', 'button', NULL, NULL, 265, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    type = VALUES(type),
    parent_id = VALUES(parent_id),
    path = VALUES(path),
    sort = VALUES(sort),
    status = VALUES(status),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO sys_role_permission
(id, role_id, permission_id, scope_type, created_at, updated_at, deleted)
SELECT seed.id, r.id, p.id, seed.scope_type, NOW(), NOW(), 0
FROM (
    SELECT 140000000000003001 AS id, 'STUDENT' AS role_code, 'material:view' AS permission_code, 'SELF' AS scope_type
    UNION ALL SELECT 140000000000003002, 'COLLEGE_CLERK', 'material:view', 'COLLEGE'
    UNION ALL SELECT 140000000000003003, 'COLLEGE_AUDITOR', 'material:view', 'COLLEGE'
    UNION ALL SELECT 140000000000003004, 'ACADEMIC_ADMIN', 'material:view', 'SCHOOL'
) seed
JOIN sys_role r ON r.code = seed.role_code AND r.deleted = 0
JOIN sys_permission p ON p.code = seed.permission_code AND p.deleted = 0
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    permission_id = VALUES(permission_id),
    scope_type = VALUES(scope_type),
    updated_at = NOW(),
    deleted = 0;
