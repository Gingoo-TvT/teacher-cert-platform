CREATE TABLE IF NOT EXISTS student (
    id                   BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    student_no           VARCHAR(64)  NOT NULL COMMENT '学号（文本）',
    name                 VARCHAR(128) NOT NULL COMMENT '姓名',
    gender               VARCHAR(32)  NOT NULL COMMENT '性别字典编码',
    id_card_type         VARCHAR(64)  NOT NULL COMMENT '身份证件类型字典编码',
    id_card_no           VARCHAR(64)  NOT NULL COMMENT '身份证件号码（文本）',
    birth_date           VARCHAR(32)  NOT NULL COMMENT '出生日期（文本，保留录入格式）',
    identity_type        VARCHAR(64)  NOT NULL COMMENT '身份类型字典编码',
    source_province      VARCHAR(32)           DEFAULT NULL COMMENT '生源地省编码',
    source_city          VARCHAR(32)           DEFAULT NULL COMMENT '生源地市编码',
    source_county        VARCHAR(32)           DEFAULT NULL COMMENT '生源地区县编码',
    source_full          VARCHAR(255)          DEFAULT NULL COMMENT '生源地完整文本',
    college_id           BIGINT       NOT NULL COMMENT '所属学院ID',
    grade                VARCHAR(32)           DEFAULT NULL COMMENT '年级（文本）',
    class_name           VARCHAR(128)          DEFAULT NULL COMMENT '班级',
    status               VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/FIRST_REVIEW/FIRST_REJECTED/SECOND_REVIEW/SECOND_REJECTED/PASSED/FAILED',
    locked               TINYINT      NOT NULL DEFAULT 0 COMMENT '关键字段是否锁定 1是0否',
    first_reviewer_id    BIGINT                DEFAULT NULL COMMENT '初审人',
    first_review_time    DATETIME              DEFAULT NULL COMMENT '初审时间',
    first_review_comment VARCHAR(500)          DEFAULT NULL COMMENT '初审意见',
    second_reviewer_id   BIGINT                DEFAULT NULL COMMENT '复审人',
    second_review_time   DATETIME              DEFAULT NULL COMMENT '复审时间',
    second_review_comment VARCHAR(500)         DEFAULT NULL COMMENT '复审意见',
    created_by           BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at           DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by           BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at           DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_student_no (student_no),
    KEY idx_student_id_card_no (id_card_no),
    KEY idx_student_college (college_id),
    KEY idx_student_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生基本信息';

INSERT INTO student
(id, student_no, name, gender, id_card_type, id_card_no, birth_date, identity_type,
 source_province, source_city, source_county, source_full, college_id, grade, class_name,
 status, locked, created_at, updated_at, deleted)
VALUES
(9001, 'S00123', '学生测试账号', 'female', 'resident_id_card', '11010119900628002X', '1990/6/28', 'normal_student',
 '440000', '440100', '440106', '广东省/广州市/天河区', 800000000000000201, '2022', '师范1班', 'DRAFT', 0, NOW(), NOW(), 0),
(9002, 'S00124', '学生测试账号B', 'male', 'resident_id_card', '110101199107010019', '1991/7/1', 'normal_student',
 '440000', '440100', '440106', '广东省/广州市/天河区', 800000000000000202, '2022', '师范2班', 'DRAFT', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    student_no = VALUES(student_no),
    name = VALUES(name),
    gender = VALUES(gender),
    id_card_type = VALUES(id_card_type),
    id_card_no = VALUES(id_card_no),
    birth_date = VALUES(birth_date),
    identity_type = VALUES(identity_type),
    source_province = VALUES(source_province),
    source_city = VALUES(source_city),
    source_county = VALUES(source_county),
    source_full = VALUES(source_full),
    college_id = VALUES(college_id),
    grade = VALUES(grade),
    class_name = VALUES(class_name),
    updated_at = NOW(),
    deleted = 0;

UPDATE sys_user
   SET student_id = 9001,
       college_id = 800000000000000201,
       updated_at = NOW(),
       deleted = 0
 WHERE username = 'test_student';
