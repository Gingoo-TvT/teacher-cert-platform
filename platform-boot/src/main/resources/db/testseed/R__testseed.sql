-- =============================================================
-- R__testseed 测试专用种子（仅 dev/test 加载：spring.flyway.locations 含 classpath:db/testseed）
-- Phase 52（种子测试/生产分离）：将原本混入 V8/V9/V10/V12 的“测试专用”数据抽出到本可重复迁移。
--   · 版本化迁移 V1..V26 先执行：V8-V12 仍插入测试行 → V26 统一删除（所有 profile）。
--   · 随后执行本可重复迁移：仅 dev/test（加载 db/testseed）重新插入，生产（仅 db/migration）保持干净。
-- 幂等：全部 INSERT ... ON DUPLICATE KEY UPDATE，可安全重跑（校验和变更时 Flyway 会重放）。
-- 主键 id 与原 V8/V9/V10/V12 完全一致，保证 IT（引用学院 201/202、学生 9001/9002、test_* 账号）可解析。
-- 状态对齐 V8 原始种子：test_* 账号 must_change_pwd=1、密码哈希 = bcrypt("ChangeMe123!")、ENABLED。
-- 注意：除业务合同所需的最小参考项外，本文件仅“搬运”既有测试种子（每角色 mock 数据属后续阶段）。
-- =============================================================

-- ---- 测试学院（原 V8）：Phase2 权限测试学院 A/B ----
INSERT INTO sys_college
(id, code, name, sort, status, created_at, updated_at, deleted)
VALUES
(800000000000000201, 'PHASE2_COLLEGE_A', 'Phase2权限测试学院A', 901, 1, NOW(), NOW(), 0),
(800000000000000202, 'PHASE2_COLLEGE_B', 'Phase2权限测试学院B', 902, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    sort = VALUES(sort),
    status = VALUES(status),
    updated_at = NOW(),
    deleted = 0;

-- ---- 测试专业（原 V10）：均隶属测试学院 201/202 ----
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

-- ---- 测试专业培养目标（原 V10）：引用上述测试专业 ----
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

-- ---- 测试学生（原 V9）：9001/9002，隶属测试学院 201/202 ----
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

-- ---- 测试账号（原 V8 + WS-2 活体）：8 个 test_* 账号（admin 保留在 db/migration，未在此重建）----
-- password_hash = bcrypt("ChangeMe123!")（与 V8 @rbac_seed_password_hash 一致），must_change_pwd=1。
-- test_student 的 student_id=9001 / college_id=201 与 V8+V9 一致（无需额外 UPDATE）。
INSERT INTO sys_user
(id, username, password_hash, real_name, work_no, email, phone, status, user_type, college_id, student_id, last_login_at, must_change_pwd, failed_login_count, locked_until, created_at, updated_at, deleted)
VALUES
(800000000000003002, 'test_student', '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO', '学生测试账号', NULL, NULL, NULL, 'ENABLED', 'STUDENT', 800000000000000201, 9001, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003003, 'test_college_clerk', '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO', '学院教务员测试账号', 'RBAC_CLERK', NULL, NULL, 'ENABLED', 'STAFF', 800000000000000201, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003004, 'test_college_auditor', '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO', '学院负责人测试账号', 'RBAC_AUDITOR', NULL, NULL, 'ENABLED', 'STAFF', 800000000000000201, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003005, 'test_review_teacher', '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO', '评审教师测试账号', 'RBAC_REVIEWER', NULL, NULL, 'ENABLED', 'STAFF', 800000000000000201, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003006, 'test_academic_admin', '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO', '教务处管理员测试账号', 'RBAC_ACADEMIC', NULL, NULL, 'ENABLED', 'STAFF', NULL, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003007, 'test_cert_issuer', '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO', '证书签发人测试账号', 'RBAC_ISSUER', NULL, NULL, 'ENABLED', 'STAFF', NULL, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003008, 'test_sys_admin', '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO', '系统管理员测试账号', 'RBAC_SYSADMIN', NULL, NULL, 'ENABLED', 'STAFF', NULL, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000009001, 'test_ws2_bootstrap_admin', '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO', 'WS2凭据活体账号', 'WS2_BOOTSTRAP', NULL, NULL, 'ENABLED', 'STAFF', NULL, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    real_name = VALUES(real_name),
    work_no = VALUES(work_no),
    email = VALUES(email),
    phone = VALUES(phone),
    status = VALUES(status),
    user_type = VALUES(user_type),
    college_id = VALUES(college_id),
    student_id = VALUES(student_id),
    must_change_pwd = VALUES(must_change_pwd),
    failed_login_count = VALUES(failed_login_count),
    locked_until = VALUES(locked_until),
    updated_at = NOW(),
    deleted = 0;

-- ---- 测试账号-角色授权（原 V8）：test_* → 角色（admin 的 4001 保留在 db/migration）----
-- 说明：test_cert_issuer→CERT_ISSUER 的 4007 因 CERT_ISSUER 角色已于 V20 退役（deleted=1），
-- 下方 JOIN r.deleted=0 会自然跳过（与迁移后现状一致：该账号无有效 CERT_ISSUER 权限）；无 IT 依赖它。
INSERT INTO sys_user_role
(id, user_id, role_id, created_at, updated_at, deleted)
SELECT seed.id, u.id, r.id, NOW(), NOW(), 0
FROM (
    SELECT 800000000000004002 AS id, 'test_student' AS username, 'STUDENT' AS role_code
    UNION ALL SELECT 800000000000004003, 'test_college_clerk', 'COLLEGE_CLERK'
    UNION ALL SELECT 800000000000004004, 'test_college_auditor', 'COLLEGE_AUDITOR'
    UNION ALL SELECT 800000000000004005, 'test_review_teacher', 'REVIEW_TEACHER'
    UNION ALL SELECT 800000000000004006, 'test_academic_admin', 'ACADEMIC_ADMIN'
    UNION ALL SELECT 800000000000004007, 'test_cert_issuer', 'CERT_ISSUER'
    UNION ALL SELECT 800000000000004008, 'test_sys_admin', 'SYS_ADMIN'
) seed
JOIN sys_user u ON u.username = seed.username AND u.deleted = 0
JOIN sys_role r ON r.code = seed.role_code AND r.deleted = 0
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    role_id = VALUES(role_id),
    updated_at = NOW(),
    deleted = 0;

-- ---- 证书签发合同测试项：生产仍由学校在 cert_issuer 字典中维护，版本化迁移保持初始置空 ----
INSERT INTO sys_dict_item
(id, type_code, item_code, item_value, parent_code, sort, status, year_version, ext_json, created_at, updated_at, deleted)
VALUES
(120000000000003001, 'cert_issuer', 'test_school_principal', '校长', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    item_value = VALUES(item_value),
    sort = VALUES(sort),
    status = VALUES(status),
    year_version = VALUES(year_version),
    updated_at = NOW(),
    deleted = 0;

-- ---- 测试账号数据范围（原 V8）：学院教务员/负责人 → 测试学院 201 ----
INSERT INTO sys_user_data_scope
(id, user_id, college_id, major_id, created_at, updated_at, deleted)
SELECT seed.id, u.id, c.id, NULL, NOW(), NOW(), 0
FROM (
    SELECT 800000000000005001 AS id, 'test_college_clerk' AS username
    UNION ALL SELECT 800000000000005002, 'test_college_auditor'
) seed
JOIN sys_user u ON u.username = seed.username AND u.deleted = 0
JOIN sys_college c ON c.id = 800000000000000201 AND c.deleted = 0
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    college_id = VALUES(college_id),
    major_id = VALUES(major_id),
    updated_at = NOW(),
    deleted = 0;

-- ---- 免考科目占位示例（原 V12）：确认单#12 默认“学校维护、初始置空”；仅联调/反例测试使用 ----
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

-- ---- 免考依据占位示例（原 V12）：确认单#12 默认“学校维护、初始置空”；仅联调/反例测试使用 ----
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
