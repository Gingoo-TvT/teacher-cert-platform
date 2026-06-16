CREATE TABLE IF NOT EXISTS training_profile (
    id                              BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    student_id                      BIGINT       NOT NULL COMMENT '学生ID',
    college_id                      BIGINT       NOT NULL COMMENT '所属学院ID，用于数据范围',
    assessment_year                 VARCHAR(16)  NOT NULL COMMENT '考核年度（文本）',
    second_discipline_code          VARCHAR(64)  NOT NULL COMMENT '二级学科（专业）代码（文本）',
    second_discipline_name          VARCHAR(128) NOT NULL COMMENT '二级学科（专业）名称',
    internal_major_code             VARCHAR(64)           DEFAULT NULL COMMENT '校内专业代码（文本）',
    internal_major_name             VARCHAR(128)          DEFAULT NULL COMMENT '校内专业名称',
    education_level                 VARCHAR(64)  NOT NULL COMMENT '学历层次字典编码',
    training_goal                   VARCHAR(64)  NOT NULL COMMENT '专业培养目标字典编码',
    internship_org_mode             VARCHAR(64)  NOT NULL COMMENT '教育实习实践组织方式字典编码',
    internship_location             VARCHAR(64)  NOT NULL COMMENT '教育实习实践地点字典编码',
    teaching_segment                VARCHAR(64)  NOT NULL COMMENT '任教学段字典编码',
    teaching_subject_id             BIGINT       NOT NULL COMMENT '任教学科标准库ID',
    teaching_subject_code           VARCHAR(128) NOT NULL COMMENT '任教学科编码（文本）',
    teaching_subject_name           VARCHAR(255) NOT NULL COMMENT '任教学科名称',
    interview_org_mode              VARCHAR(64)  NOT NULL COMMENT '面试考试组织方式字典编码',
    ability_test_conclusion         VARCHAR(64)           DEFAULT NULL COMMENT '测试结论字典编码',
    status                          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/FIRST_REVIEW/FIRST_REJECTED/SECOND_REVIEW/SECOND_REJECTED/PASSED/FAILED',
    locked                          TINYINT      NOT NULL DEFAULT 0 COMMENT '关键字段是否锁定 1是0否',
    first_reviewer_id               BIGINT                DEFAULT NULL COMMENT '初审人',
    first_review_time               DATETIME              DEFAULT NULL COMMENT '初审时间',
    first_review_comment            VARCHAR(500)          DEFAULT NULL COMMENT '初审意见',
    second_reviewer_id              BIGINT                DEFAULT NULL COMMENT '复审人',
    second_review_time              DATETIME              DEFAULT NULL COMMENT '复审时间',
    second_review_comment           VARCHAR(500)          DEFAULT NULL COMMENT '复审意见',
    created_by                      BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at                      DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by                      BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at                      DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted                         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_training_student_year (student_id, assessment_year),
    KEY idx_training_college (college_id),
    KEY idx_training_status (status),
    KEY idx_training_goal (training_goal),
    KEY idx_training_segment_subject (teaching_segment, teaching_subject_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '专业培养信息';

INSERT INTO sys_major
(id, college_id, internal_major_code, internal_major_name, second_discipline_code, second_discipline_name,
 pilot_scope_flag, year_version, sort, status, created_at, updated_at, deleted)
VALUES
(810000000000000101, 800000000000000201, 'P4_NORMAL_A', 'Phase4普通师范试点专业A', '050101', '汉语言文学', 1, 'GLOBAL', 901, 1, NOW(), NOW(), 0),
(810000000000000102, 800000000000000201, 'P4_VOC_A', '职业技术教育专业', '045120', '职业技术教育', 1, 'GLOBAL', 902, 1, NOW(), NOW(), 0),
(810000000000000103, 800000000000000201, 'P4_CHINESE_INTL_A', '汉语国际教育专业', '045300', '汉语国际教育', 1, 'GLOBAL', 903, 1, NOW(), NOW(), 0),
(810000000000000201, 800000000000000202, 'P4_NORMAL_B', 'Phase4普通师范试点专业B', '070101', '数学与应用数学', 1, 'GLOBAL', 904, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    college_id = VALUES(college_id),
    internal_major_name = VALUES(internal_major_name),
    second_discipline_code = VALUES(second_discipline_code),
    second_discipline_name = VALUES(second_discipline_name),
    pilot_scope_flag = VALUES(pilot_scope_flag),
    sort = VALUES(sort),
    status = VALUES(status),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO major_training_goal
(id, major_id, training_goal_code, sort, status, created_at, updated_at, deleted)
VALUES
(810000000000001101, 810000000000000101, 'primary_school_teacher', 1, 1, NOW(), NOW(), 0),
(810000000000001102, 810000000000000101, 'junior_middle_school_teacher', 2, 1, NOW(), NOW(), 0),
(810000000000001103, 810000000000000102, 'secondary_vocational_school_teacher', 1, 1, NOW(), NOW(), 0),
(810000000000001104, 810000000000000103, 'primary_school_teacher', 1, 1, NOW(), NOW(), 0),
(810000000000001105, 810000000000000201, 'junior_middle_school_teacher', 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    sort = VALUES(sort),
    status = VALUES(status),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO training_goal_config
(id, training_goal_code, default_segment, allowed_segments_json, default_internship_location,
 allowed_internship_locations_json, status, created_at, updated_at, deleted)
VALUES
(810000000000002001, 'kindergarten_teacher', 'kindergarten', JSON_ARRAY('kindergarten'), 'kindergarten',
 JSON_ARRAY('kindergarten', 'other'), 1, NOW(), NOW(), 0),
(810000000000002002, 'primary_school_teacher', 'primary_school', JSON_ARRAY('primary_school'), 'primary_secondary_school',
 JSON_ARRAY('primary_secondary_school', 'overseas_chinese_international_education', 'other'), 1, NOW(), NOW(), 0),
(810000000000002003, 'junior_middle_school_teacher', 'junior_middle_school', JSON_ARRAY('junior_middle_school'), 'primary_secondary_school',
 JSON_ARRAY('primary_secondary_school', 'other'), 1, NOW(), NOW(), 0),
(810000000000002004, 'senior_middle_school_teacher', 'senior_middle_school', JSON_ARRAY('senior_middle_school'), 'primary_secondary_school',
 JSON_ARRAY('primary_secondary_school', 'other'), 1, NOW(), NOW(), 0),
(810000000000002005, 'secondary_vocational_school_teacher', 'secondary_vocational_school', JSON_ARRAY('secondary_vocational_school'), 'enterprise_vocational_education',
 JSON_ARRAY('enterprise_vocational_education', 'primary_secondary_school', 'other'), 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    default_segment = VALUES(default_segment),
    allowed_segments_json = VALUES(allowed_segments_json),
    default_internship_location = VALUES(default_internship_location),
    allowed_internship_locations_json = VALUES(allowed_internship_locations_json),
    status = VALUES(status),
    updated_at = NOW(),
    deleted = 0;
