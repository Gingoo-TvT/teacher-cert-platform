-- =============================================================
-- V8 RBAC 种子：7 角色、52 权限点、§15.1 角色权限矩阵、测试账号
-- 权限点编码唯一来源：plan.md §15.1
-- 范围映射：本=SELF，院=COLLEGE，校=SCHOOL，系=SYSTEM，✓=LOGIN_ALL，分配=ASSIGNED
-- =============================================================

SET @rbac_seed_password_hash = '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO';

INSERT INTO sys_param
(id, param_key, param_value, param_type, param_group, description, editable, created_at, updated_at, deleted)
VALUES
(800000000000000101, 'login.lockThreshold', '5', 'int', 'security', '连续登录失败锁定阈值', 1, NOW(), NOW(), 0),
(800000000000000102, 'login.lockMinutes', '15', 'int', 'security', '登录失败锁定分钟数', 1, NOW(), NOW(), 0),
(800000000000000103, 'captcha.ttlSeconds', '120', 'int', 'security', '图形验证码有效秒数', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    param_value = VALUES(param_value),
    param_type = VALUES(param_type),
    param_group = VALUES(param_group),
    description = VALUES(description),
    editable = VALUES(editable),
    updated_at = NOW(),
    deleted = 0;

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

INSERT INTO sys_role
(id, code, name, description, sort, status, created_at, updated_at, deleted)
VALUES
(800000000000000001, 'STUDENT', '学生', '学生本人账号，仅可访问本人数据', 10, 1, NOW(), NOW(), 0),
(800000000000000002, 'COLLEGE_CLERK', '学院教务员', '学院教务员，负责本学院或授权专业初审与维护', 20, 1, NOW(), NOW(), 0),
(800000000000000003, 'COLLEGE_AUDITOR', '学院负责人', '学院负责人，负责本学院或授权专业复审与确认', 30, 1, NOW(), NOW(), 0),
(800000000000000004, 'REVIEW_TEACHER', '评审教师', '视频评审教师，仅处理分配给本人的评审任务', 40, 1, NOW(), NOW(), 0),
(800000000000000005, 'ACADEMIC_ADMIN', '教务处管理员', '教务处管理员，负责全校业务管理与审核', 50, 1, NOW(), NOW(), 0),
(800000000000000006, 'CERT_ISSUER', '证书签发人', '证书签发人，负责全校证书签发', 60, 1, NOW(), NOW(), 0),
(800000000000000007, 'SYS_ADMIN', '系统管理员', '系统管理员，负责系统级账号角色权限与基础配置', 70, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    sort = VALUES(sort),
    status = VALUES(status),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO sys_permission
(id, code, name, type, parent_id, path, sort, status, created_at, updated_at, deleted)
VALUES
(800000000000001001, 'system:user:manage', '系统用户管理', 'button', NULL, NULL, 10, 1, NOW(), NOW(), 0),
(800000000000001002, 'system:role:manage', '系统角色管理', 'button', NULL, NULL, 20, 1, NOW(), NOW(), 0),
(800000000000001003, 'system:perm:manage', '系统权限管理', 'button', NULL, NULL, 30, 1, NOW(), NOW(), 0),
(800000000000001004, 'system:param:manage', '系统参数管理', 'button', NULL, NULL, 40, 1, NOW(), NOW(), 0),
(800000000000001005, 'system:backup', '系统备份', 'button', NULL, NULL, 50, 1, NOW(), NOW(), 0),
(800000000000001006, 'audit:view', '审计日志查看', 'button', NULL, NULL, 60, 1, NOW(), NOW(), 0),
(800000000000001007, 'dict:view', '字典查看', 'button', NULL, NULL, 70, 1, NOW(), NOW(), 0),
(800000000000001008, 'dict:manage', '字典管理', 'button', NULL, NULL, 80, 1, NOW(), NOW(), 0),
(800000000000001009, 'region:manage', '区划管理', 'button', NULL, NULL, 90, 1, NOW(), NOW(), 0),
(800000000000001010, 'college:manage', '学院管理', 'button', NULL, NULL, 100, 1, NOW(), NOW(), 0),
(800000000000001011, 'major:manage', '专业管理', 'button', NULL, NULL, 110, 1, NOW(), NOW(), 0),
(800000000000001012, 'subject:manage', '任教学科管理', 'button', NULL, NULL, 120, 1, NOW(), NOW(), 0),
(800000000000001013, 'subject:import', '任教学科导入', 'button', NULL, NULL, 130, 1, NOW(), NOW(), 0),
(800000000000001014, 'student:view', '学生查看', 'button', NULL, NULL, 140, 1, NOW(), NOW(), 0),
(800000000000001015, 'student:import', '学生导入', 'button', NULL, NULL, 150, 1, NOW(), NOW(), 0),
(800000000000001016, 'student:edit', '学生编辑', 'button', NULL, NULL, 160, 1, NOW(), NOW(), 0),
(800000000000001017, 'student:confirm', '学生本人确认补充', 'button', NULL, NULL, 170, 1, NOW(), NOW(), 0),
(800000000000001018, 'info:firstReview', '基本培养信息初审', 'button', NULL, NULL, 180, 1, NOW(), NOW(), 0),
(800000000000001019, 'info:secondReview', '基本培养信息复审', 'button', NULL, NULL, 190, 1, NOW(), NOW(), 0),
(800000000000001020, 'student:export', '学生导出', 'button', NULL, NULL, 200, 1, NOW(), NOW(), 0),
(800000000000001021, 'training:edit', '培养信息编辑', 'button', NULL, NULL, 210, 1, NOW(), NOW(), 0),
(800000000000001022, 'training:confirm', '培养目标本人确认', 'button', NULL, NULL, 220, 1, NOW(), NOW(), 0),
(800000000000001023, 'material:upload', '材料本人上传替换', 'button', NULL, NULL, 230, 1, NOW(), NOW(), 0),
(800000000000001024, 'material:firstReview', '材料初审', 'button', NULL, NULL, 240, 1, NOW(), NOW(), 0),
(800000000000001025, 'material:secondReview', '材料复审', 'button', NULL, NULL, 250, 1, NOW(), NOW(), 0),
(800000000000001026, 'material:batchDownload', '材料批量下载', 'button', NULL, NULL, 260, 1, NOW(), NOW(), 0),
(800000000000001027, 'exemption:apply', '免考本人申请', 'button', NULL, NULL, 270, 1, NOW(), NOW(), 0),
(800000000000001028, 'exemption:firstReview', '免考初审', 'button', NULL, NULL, 280, 1, NOW(), NOW(), 0),
(800000000000001029, 'exemption:secondReview', '免考复审', 'button', NULL, NULL, 290, 1, NOW(), NOW(), 0),
(800000000000001030, 'video:upload', '视频本人上传', 'button', NULL, NULL, 300, 1, NOW(), NOW(), 0),
(800000000000001031, 'video:assign', '视频分配评审教师', 'button', NULL, NULL, 310, 1, NOW(), NOW(), 0),
(800000000000001032, 'video:score', '视频独立评分', 'button', NULL, NULL, 320, 1, NOW(), NOW(), 0),
(800000000000001033, 'video:arbitrate', '视频复评仲裁', 'button', NULL, NULL, 330, 1, NOW(), NOW(), 0),
(800000000000001034, 'video:confirm', '视频结果确认', 'button', NULL, NULL, 340, 1, NOW(), NOW(), 0),
(800000000000001035, 'video:play', '视频鉴权播放', 'button', NULL, NULL, 350, 1, NOW(), NOW(), 0),
(800000000000001036, 'test:edit', '测试结果编辑', 'button', NULL, NULL, 360, 1, NOW(), NOW(), 0),
(800000000000001037, 'test:import', '测试结果导入', 'button', NULL, NULL, 370, 1, NOW(), NOW(), 0),
(800000000000001038, 'test:confirm', '测试结果确认锁定', 'button', NULL, NULL, 380, 1, NOW(), NOW(), 0),
(800000000000001039, 'cert:generate', '证书编号生成', 'button', NULL, NULL, 390, 1, NOW(), NOW(), 0),
(800000000000001040, 'cert:correct', '证书更正', 'button', NULL, NULL, 400, 1, NOW(), NOW(), 0),
(800000000000001041, 'cert:void', '证书作废', 'button', NULL, NULL, 410, 1, NOW(), NOW(), 0),
(800000000000001042, 'cert:reissue', '证书重开', 'button', NULL, NULL, 420, 1, NOW(), NOW(), 0),
(800000000000001043, 'cert:issue', '证书签发', 'button', NULL, NULL, 430, 1, NOW(), NOW(), 0),
(800000000000001044, 'cert:view', '证书查看', 'button', NULL, NULL, 440, 1, NOW(), NOW(), 0),
(800000000000001045, 'exchange:template', '数据交换模板下载', 'button', NULL, NULL, 450, 1, NOW(), NOW(), 0),
(800000000000001046, 'exchange:import', '数据交换导入', 'button', NULL, NULL, 460, 1, NOW(), NOW(), 0),
(800000000000001047, 'exchange:prevalidate', '数据交换预校验', 'button', NULL, NULL, 470, 1, NOW(), NOW(), 0),
(800000000000001048, 'exchange:export:standard', '数据交换标准导出', 'button', NULL, NULL, 480, 1, NOW(), NOW(), 0),
(800000000000001049, 'exchange:export:full', '数据交换完整导出', 'button', NULL, NULL, 490, 1, NOW(), NOW(), 0),
(800000000000001050, 'exchange:export:sensitive', '数据交换敏感明文导出', 'button', NULL, NULL, 500, 1, NOW(), NOW(), 0),
(800000000000001051, 'stats:view', '统计查看', 'button', NULL, NULL, 510, 1, NOW(), NOW(), 0),
(800000000000001052, 'notice:view', '通知查看', 'button', NULL, NULL, 520, 1, NOW(), NOW(), 0)
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
    SELECT 800000000000002001 AS id, 'STUDENT' AS role_code, 'dict:view' AS permission_code, 'LOGIN_ALL' AS scope_type
    UNION ALL SELECT 800000000000002002, 'STUDENT', 'student:view', 'SELF'
    UNION ALL SELECT 800000000000002003, 'STUDENT', 'student:confirm', 'SELF'
    UNION ALL SELECT 800000000000002004, 'STUDENT', 'training:confirm', 'SELF'
    UNION ALL SELECT 800000000000002005, 'STUDENT', 'material:upload', 'SELF'
    UNION ALL SELECT 800000000000002006, 'STUDENT', 'exemption:apply', 'SELF'
    UNION ALL SELECT 800000000000002007, 'STUDENT', 'video:upload', 'SELF'
    UNION ALL SELECT 800000000000002008, 'STUDENT', 'video:play', 'SELF'
    UNION ALL SELECT 800000000000002009, 'STUDENT', 'notice:view', 'LOGIN_ALL'

    UNION ALL SELECT 800000000000002010, 'COLLEGE_CLERK', 'audit:view', 'COLLEGE'
    UNION ALL SELECT 800000000000002011, 'COLLEGE_CLERK', 'dict:view', 'LOGIN_ALL'
    UNION ALL SELECT 800000000000002012, 'COLLEGE_CLERK', 'student:view', 'COLLEGE'
    UNION ALL SELECT 800000000000002013, 'COLLEGE_CLERK', 'student:import', 'COLLEGE'
    UNION ALL SELECT 800000000000002014, 'COLLEGE_CLERK', 'student:edit', 'COLLEGE'
    UNION ALL SELECT 800000000000002015, 'COLLEGE_CLERK', 'info:firstReview', 'COLLEGE'
    UNION ALL SELECT 800000000000002016, 'COLLEGE_CLERK', 'student:export', 'COLLEGE'
    UNION ALL SELECT 800000000000002017, 'COLLEGE_CLERK', 'training:edit', 'COLLEGE'
    UNION ALL SELECT 800000000000002018, 'COLLEGE_CLERK', 'material:firstReview', 'COLLEGE'
    UNION ALL SELECT 800000000000002019, 'COLLEGE_CLERK', 'material:batchDownload', 'COLLEGE'
    UNION ALL SELECT 800000000000002020, 'COLLEGE_CLERK', 'exemption:firstReview', 'COLLEGE'
    UNION ALL SELECT 800000000000002021, 'COLLEGE_CLERK', 'video:assign', 'COLLEGE'
    UNION ALL SELECT 800000000000002022, 'COLLEGE_CLERK', 'video:play', 'COLLEGE'
    UNION ALL SELECT 800000000000002023, 'COLLEGE_CLERK', 'test:edit', 'COLLEGE'
    UNION ALL SELECT 800000000000002024, 'COLLEGE_CLERK', 'test:import', 'COLLEGE'
    UNION ALL SELECT 800000000000002025, 'COLLEGE_CLERK', 'cert:view', 'COLLEGE'
    UNION ALL SELECT 800000000000002026, 'COLLEGE_CLERK', 'exchange:template', 'COLLEGE'
    UNION ALL SELECT 800000000000002027, 'COLLEGE_CLERK', 'exchange:import', 'COLLEGE'
    UNION ALL SELECT 800000000000002028, 'COLLEGE_CLERK', 'exchange:prevalidate', 'COLLEGE'
    UNION ALL SELECT 800000000000002029, 'COLLEGE_CLERK', 'exchange:export:standard', 'COLLEGE'
    UNION ALL SELECT 800000000000002030, 'COLLEGE_CLERK', 'exchange:export:full', 'COLLEGE'
    UNION ALL SELECT 800000000000002031, 'COLLEGE_CLERK', 'stats:view', 'COLLEGE'
    UNION ALL SELECT 800000000000002032, 'COLLEGE_CLERK', 'notice:view', 'LOGIN_ALL'

    UNION ALL SELECT 800000000000002033, 'COLLEGE_AUDITOR', 'audit:view', 'COLLEGE'
    UNION ALL SELECT 800000000000002034, 'COLLEGE_AUDITOR', 'dict:view', 'LOGIN_ALL'
    UNION ALL SELECT 800000000000002035, 'COLLEGE_AUDITOR', 'student:view', 'COLLEGE'
    UNION ALL SELECT 800000000000002036, 'COLLEGE_AUDITOR', 'info:secondReview', 'COLLEGE'
    UNION ALL SELECT 800000000000002037, 'COLLEGE_AUDITOR', 'material:secondReview', 'COLLEGE'
    UNION ALL SELECT 800000000000002038, 'COLLEGE_AUDITOR', 'material:batchDownload', 'COLLEGE'
    UNION ALL SELECT 800000000000002039, 'COLLEGE_AUDITOR', 'exemption:secondReview', 'COLLEGE'
    UNION ALL SELECT 800000000000002040, 'COLLEGE_AUDITOR', 'video:assign', 'COLLEGE'
    UNION ALL SELECT 800000000000002041, 'COLLEGE_AUDITOR', 'video:arbitrate', 'COLLEGE'
    UNION ALL SELECT 800000000000002042, 'COLLEGE_AUDITOR', 'video:confirm', 'COLLEGE'
    UNION ALL SELECT 800000000000002043, 'COLLEGE_AUDITOR', 'video:play', 'COLLEGE'
    UNION ALL SELECT 800000000000002044, 'COLLEGE_AUDITOR', 'cert:view', 'COLLEGE'
    UNION ALL SELECT 800000000000002045, 'COLLEGE_AUDITOR', 'stats:view', 'COLLEGE'
    UNION ALL SELECT 800000000000002046, 'COLLEGE_AUDITOR', 'notice:view', 'LOGIN_ALL'

    UNION ALL SELECT 800000000000002047, 'REVIEW_TEACHER', 'dict:view', 'LOGIN_ALL'
    UNION ALL SELECT 800000000000002048, 'REVIEW_TEACHER', 'video:score', 'ASSIGNED'
    UNION ALL SELECT 800000000000002049, 'REVIEW_TEACHER', 'video:play', 'ASSIGNED'
    UNION ALL SELECT 800000000000002050, 'REVIEW_TEACHER', 'notice:view', 'LOGIN_ALL'

    UNION ALL SELECT 800000000000002051, 'ACADEMIC_ADMIN', 'system:param:manage', 'SCHOOL'
    UNION ALL SELECT 800000000000002052, 'ACADEMIC_ADMIN', 'audit:view', 'SCHOOL'
    UNION ALL SELECT 800000000000002053, 'ACADEMIC_ADMIN', 'dict:view', 'LOGIN_ALL'
    UNION ALL SELECT 800000000000002054, 'ACADEMIC_ADMIN', 'dict:manage', 'SCHOOL'
    UNION ALL SELECT 800000000000002055, 'ACADEMIC_ADMIN', 'region:manage', 'SCHOOL'
    UNION ALL SELECT 800000000000002056, 'ACADEMIC_ADMIN', 'college:manage', 'SCHOOL'
    UNION ALL SELECT 800000000000002057, 'ACADEMIC_ADMIN', 'major:manage', 'SCHOOL'
    UNION ALL SELECT 800000000000002058, 'ACADEMIC_ADMIN', 'subject:manage', 'SCHOOL'
    UNION ALL SELECT 800000000000002059, 'ACADEMIC_ADMIN', 'subject:import', 'SCHOOL'
    UNION ALL SELECT 800000000000002060, 'ACADEMIC_ADMIN', 'student:view', 'SCHOOL'
    UNION ALL SELECT 800000000000002061, 'ACADEMIC_ADMIN', 'student:import', 'SCHOOL'
    UNION ALL SELECT 800000000000002062, 'ACADEMIC_ADMIN', 'student:edit', 'SCHOOL'
    UNION ALL SELECT 800000000000002063, 'ACADEMIC_ADMIN', 'student:export', 'SCHOOL'
    UNION ALL SELECT 800000000000002064, 'ACADEMIC_ADMIN', 'training:edit', 'SCHOOL'
    UNION ALL SELECT 800000000000002065, 'ACADEMIC_ADMIN', 'material:batchDownload', 'SCHOOL'
    UNION ALL SELECT 800000000000002066, 'ACADEMIC_ADMIN', 'video:assign', 'SCHOOL'
    UNION ALL SELECT 800000000000002067, 'ACADEMIC_ADMIN', 'video:arbitrate', 'SCHOOL'
    UNION ALL SELECT 800000000000002068, 'ACADEMIC_ADMIN', 'video:play', 'SCHOOL'
    UNION ALL SELECT 800000000000002069, 'ACADEMIC_ADMIN', 'test:edit', 'SCHOOL'
    UNION ALL SELECT 800000000000002070, 'ACADEMIC_ADMIN', 'test:import', 'SCHOOL'
    UNION ALL SELECT 800000000000002071, 'ACADEMIC_ADMIN', 'test:confirm', 'SCHOOL'
    UNION ALL SELECT 800000000000002072, 'ACADEMIC_ADMIN', 'cert:generate', 'SCHOOL'
    UNION ALL SELECT 800000000000002073, 'ACADEMIC_ADMIN', 'cert:correct', 'SCHOOL'
    UNION ALL SELECT 800000000000002074, 'ACADEMIC_ADMIN', 'cert:void', 'SCHOOL'
    UNION ALL SELECT 800000000000002075, 'ACADEMIC_ADMIN', 'cert:reissue', 'SCHOOL'
    UNION ALL SELECT 800000000000002076, 'ACADEMIC_ADMIN', 'cert:view', 'SCHOOL'
    UNION ALL SELECT 800000000000002077, 'ACADEMIC_ADMIN', 'exchange:template', 'SCHOOL'
    UNION ALL SELECT 800000000000002078, 'ACADEMIC_ADMIN', 'exchange:import', 'SCHOOL'
    UNION ALL SELECT 800000000000002079, 'ACADEMIC_ADMIN', 'exchange:prevalidate', 'SCHOOL'
    UNION ALL SELECT 800000000000002080, 'ACADEMIC_ADMIN', 'exchange:export:standard', 'SCHOOL'
    UNION ALL SELECT 800000000000002081, 'ACADEMIC_ADMIN', 'exchange:export:full', 'SCHOOL'
    UNION ALL SELECT 800000000000002082, 'ACADEMIC_ADMIN', 'exchange:export:sensitive', 'SCHOOL'
    UNION ALL SELECT 800000000000002083, 'ACADEMIC_ADMIN', 'stats:view', 'SCHOOL'
    UNION ALL SELECT 800000000000002084, 'ACADEMIC_ADMIN', 'notice:view', 'LOGIN_ALL'

    UNION ALL SELECT 800000000000002085, 'CERT_ISSUER', 'dict:view', 'LOGIN_ALL'
    UNION ALL SELECT 800000000000002086, 'CERT_ISSUER', 'student:view', 'SCHOOL'
    UNION ALL SELECT 800000000000002087, 'CERT_ISSUER', 'cert:issue', 'SCHOOL'
    UNION ALL SELECT 800000000000002088, 'CERT_ISSUER', 'cert:view', 'SCHOOL'
    UNION ALL SELECT 800000000000002089, 'CERT_ISSUER', 'stats:view', 'SCHOOL'
    UNION ALL SELECT 800000000000002090, 'CERT_ISSUER', 'notice:view', 'LOGIN_ALL'

    UNION ALL SELECT 800000000000002091, 'SYS_ADMIN', 'system:user:manage', 'SYSTEM'
    UNION ALL SELECT 800000000000002092, 'SYS_ADMIN', 'system:role:manage', 'SYSTEM'
    UNION ALL SELECT 800000000000002093, 'SYS_ADMIN', 'system:perm:manage', 'SYSTEM'
    UNION ALL SELECT 800000000000002094, 'SYS_ADMIN', 'system:param:manage', 'SYSTEM'
    UNION ALL SELECT 800000000000002095, 'SYS_ADMIN', 'system:backup', 'SYSTEM'
    UNION ALL SELECT 800000000000002096, 'SYS_ADMIN', 'audit:view', 'SYSTEM'
    UNION ALL SELECT 800000000000002097, 'SYS_ADMIN', 'dict:view', 'LOGIN_ALL'
    UNION ALL SELECT 800000000000002098, 'SYS_ADMIN', 'dict:manage', 'SYSTEM'
    UNION ALL SELECT 800000000000002099, 'SYS_ADMIN', 'region:manage', 'SYSTEM'
    UNION ALL SELECT 800000000000002100, 'SYS_ADMIN', 'college:manage', 'SYSTEM'
    UNION ALL SELECT 800000000000002101, 'SYS_ADMIN', 'major:manage', 'SYSTEM'
    UNION ALL SELECT 800000000000002102, 'SYS_ADMIN', 'subject:manage', 'SYSTEM'
    UNION ALL SELECT 800000000000002103, 'SYS_ADMIN', 'subject:import', 'SYSTEM'
    UNION ALL SELECT 800000000000002104, 'SYS_ADMIN', 'notice:view', 'LOGIN_ALL'
) seed
JOIN sys_role r ON r.code = seed.role_code AND r.deleted = 0
JOIN sys_permission p ON p.code = seed.permission_code AND p.deleted = 0
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    permission_id = VALUES(permission_id),
    scope_type = VALUES(scope_type),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO sys_user
(id, username, password_hash, real_name, work_no, email, phone, status, user_type, college_id, student_id, last_login_at, must_change_pwd, failed_login_count, locked_until, created_at, updated_at, deleted)
VALUES
(800000000000003001, 'admin', @rbac_seed_password_hash, '系统超级管理员', 'RBAC_ADMIN', NULL, NULL, 'ENABLED', 'STAFF', NULL, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003002, 'test_student', @rbac_seed_password_hash, '学生测试账号', NULL, NULL, NULL, 'ENABLED', 'STUDENT', 800000000000000201, 9001, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003003, 'test_college_clerk', @rbac_seed_password_hash, '学院教务员测试账号', 'RBAC_CLERK', NULL, NULL, 'ENABLED', 'STAFF', 800000000000000201, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003004, 'test_college_auditor', @rbac_seed_password_hash, '学院负责人测试账号', 'RBAC_AUDITOR', NULL, NULL, 'ENABLED', 'STAFF', 800000000000000201, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003005, 'test_review_teacher', @rbac_seed_password_hash, '评审教师测试账号', 'RBAC_REVIEWER', NULL, NULL, 'ENABLED', 'STAFF', 800000000000000201, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003006, 'test_academic_admin', @rbac_seed_password_hash, '教务处管理员测试账号', 'RBAC_ACADEMIC', NULL, NULL, 'ENABLED', 'STAFF', NULL, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003007, 'test_cert_issuer', @rbac_seed_password_hash, '证书签发人测试账号', 'RBAC_ISSUER', NULL, NULL, 'ENABLED', 'STAFF', NULL, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0),
(800000000000003008, 'test_sys_admin', @rbac_seed_password_hash, '系统管理员测试账号', 'RBAC_SYSADMIN', NULL, NULL, 'ENABLED', 'STAFF', NULL, NULL, NULL, 1, 0, NULL, NOW(), NOW(), 0)
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

INSERT INTO sys_user_role
(id, user_id, role_id, created_at, updated_at, deleted)
SELECT seed.id, u.id, r.id, NOW(), NOW(), 0
FROM (
    SELECT 800000000000004001 AS id, 'admin' AS username, 'SYS_ADMIN' AS role_code
    UNION ALL SELECT 800000000000004002, 'test_student', 'STUDENT'
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
