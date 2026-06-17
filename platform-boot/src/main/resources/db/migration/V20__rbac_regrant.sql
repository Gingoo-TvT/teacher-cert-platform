-- =============================================================
-- V20 RBAC 基座重定义：收紧学院教务员、扩展学院负责人、合并证书签发到教务处
-- 幂等可重跑；不修改 V1-V19 历史种子。
-- =============================================================

-- 先收回本轮重定义角色的旧授权，随后按新矩阵恢复。
UPDATE sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
   SET rp.deleted = 1,
       rp.updated_by = 0,
       rp.updated_at = NOW()
 WHERE r.code IN ('COLLEGE_CLERK', 'COLLEGE_AUDITOR', 'SYS_ADMIN', 'CERT_ISSUER')
   AND rp.deleted = 0;

-- COLLEGE_CLERK：只读 + 初审 + 只读导出/批量下载。
INSERT INTO sys_role_permission
(id, role_id, permission_id, scope_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT seed.id, r.id, p.id, seed.scope_type, 0, NOW(), 0, NOW(), 0
FROM (
    SELECT 200000000000001001 AS id, 'COLLEGE_CLERK' AS role_code, 'audit:view' AS permission_code, 'COLLEGE' AS scope_type
    UNION ALL SELECT 200000000000001002, 'COLLEGE_CLERK', 'dict:view', 'LOGIN_ALL'
    UNION ALL SELECT 200000000000001003, 'COLLEGE_CLERK', 'student:view', 'COLLEGE'
    UNION ALL SELECT 200000000000001004, 'COLLEGE_CLERK', 'info:firstReview', 'COLLEGE'
    UNION ALL SELECT 200000000000001005, 'COLLEGE_CLERK', 'student:export', 'COLLEGE'
    UNION ALL SELECT 200000000000001006, 'COLLEGE_CLERK', 'material:view', 'COLLEGE'
    UNION ALL SELECT 200000000000001007, 'COLLEGE_CLERK', 'material:firstReview', 'COLLEGE'
    UNION ALL SELECT 200000000000001008, 'COLLEGE_CLERK', 'material:batchDownload', 'COLLEGE'
    UNION ALL SELECT 200000000000001009, 'COLLEGE_CLERK', 'exemption:firstReview', 'COLLEGE'
    UNION ALL SELECT 200000000000001010, 'COLLEGE_CLERK', 'video:play', 'COLLEGE'
    UNION ALL SELECT 200000000000001011, 'COLLEGE_CLERK', 'cert:view', 'COLLEGE'
    UNION ALL SELECT 200000000000001012, 'COLLEGE_CLERK', 'exchange:export:standard', 'COLLEGE'
    UNION ALL SELECT 200000000000001013, 'COLLEGE_CLERK', 'exchange:export:full', 'COLLEGE'
    UNION ALL SELECT 200000000000001014, 'COLLEGE_CLERK', 'stats:view', 'COLLEGE'
    UNION ALL SELECT 200000000000001015, 'COLLEGE_CLERK', 'notice:view', 'LOGIN_ALL'
) seed
JOIN sys_role r ON r.code = seed.role_code AND r.deleted = 0
JOIN sys_permission p ON p.code = seed.permission_code AND p.deleted = 0
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    permission_id = VALUES(permission_id),
    scope_type = VALUES(scope_type),
    updated_by = VALUES(updated_by),
    updated_at = NOW(),
    deleted = 0;

-- COLLEGE_AUDITOR：学院负责人承接学院侧录入/导入/预校验、复审、视频分配/仲裁等动作权。
INSERT INTO sys_role_permission
(id, role_id, permission_id, scope_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT seed.id, r.id, p.id, seed.scope_type, 0, NOW(), 0, NOW(), 0
FROM (
    SELECT 200000000000002001 AS id, 'COLLEGE_AUDITOR' AS role_code, 'audit:view' AS permission_code, 'COLLEGE' AS scope_type
    UNION ALL SELECT 200000000000002002, 'COLLEGE_AUDITOR', 'dict:view', 'LOGIN_ALL'
    UNION ALL SELECT 200000000000002003, 'COLLEGE_AUDITOR', 'student:view', 'COLLEGE'
    UNION ALL SELECT 200000000000002004, 'COLLEGE_AUDITOR', 'student:import', 'COLLEGE'
    UNION ALL SELECT 200000000000002005, 'COLLEGE_AUDITOR', 'student:edit', 'COLLEGE'
    UNION ALL SELECT 200000000000002006, 'COLLEGE_AUDITOR', 'student:export', 'COLLEGE'
    UNION ALL SELECT 200000000000002007, 'COLLEGE_AUDITOR', 'training:edit', 'COLLEGE'
    UNION ALL SELECT 200000000000002008, 'COLLEGE_AUDITOR', 'info:secondReview', 'COLLEGE'
    UNION ALL SELECT 200000000000002009, 'COLLEGE_AUDITOR', 'material:view', 'COLLEGE'
    UNION ALL SELECT 200000000000002010, 'COLLEGE_AUDITOR', 'material:secondReview', 'COLLEGE'
    UNION ALL SELECT 200000000000002011, 'COLLEGE_AUDITOR', 'material:batchDownload', 'COLLEGE'
    UNION ALL SELECT 200000000000002012, 'COLLEGE_AUDITOR', 'exemption:secondReview', 'COLLEGE'
    UNION ALL SELECT 200000000000002013, 'COLLEGE_AUDITOR', 'video:assign', 'COLLEGE'
    UNION ALL SELECT 200000000000002014, 'COLLEGE_AUDITOR', 'video:arbitrate', 'COLLEGE'
    UNION ALL SELECT 200000000000002015, 'COLLEGE_AUDITOR', 'video:confirm', 'COLLEGE'
    UNION ALL SELECT 200000000000002016, 'COLLEGE_AUDITOR', 'video:play', 'COLLEGE'
    UNION ALL SELECT 200000000000002017, 'COLLEGE_AUDITOR', 'test:edit', 'COLLEGE'
    UNION ALL SELECT 200000000000002018, 'COLLEGE_AUDITOR', 'test:import', 'COLLEGE'
    UNION ALL SELECT 200000000000002019, 'COLLEGE_AUDITOR', 'cert:view', 'COLLEGE'
    UNION ALL SELECT 200000000000002020, 'COLLEGE_AUDITOR', 'exchange:template', 'COLLEGE'
    UNION ALL SELECT 200000000000002021, 'COLLEGE_AUDITOR', 'exchange:import', 'COLLEGE'
    UNION ALL SELECT 200000000000002022, 'COLLEGE_AUDITOR', 'exchange:prevalidate', 'COLLEGE'
    UNION ALL SELECT 200000000000002023, 'COLLEGE_AUDITOR', 'exchange:export:standard', 'COLLEGE'
    UNION ALL SELECT 200000000000002024, 'COLLEGE_AUDITOR', 'exchange:export:full', 'COLLEGE'
    UNION ALL SELECT 200000000000002025, 'COLLEGE_AUDITOR', 'stats:view', 'COLLEGE'
    UNION ALL SELECT 200000000000002026, 'COLLEGE_AUDITOR', 'notice:view', 'LOGIN_ALL'
) seed
JOIN sys_role r ON r.code = seed.role_code AND r.deleted = 0
JOIN sys_permission p ON p.code = seed.permission_code AND p.deleted = 0
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    permission_id = VALUES(permission_id),
    scope_type = VALUES(scope_type),
    updated_by = VALUES(updated_by),
    updated_at = NOW(),
    deleted = 0;

-- ACADEMIC_ADMIN：保留既有授权，新增证书签发。
INSERT INTO sys_role_permission
(id, role_id, permission_id, scope_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT 200000000000003001, r.id, p.id, 'SCHOOL', 0, NOW(), 0, NOW(), 0
FROM sys_role r
JOIN sys_permission p ON p.code = 'cert:issue' AND p.deleted = 0
WHERE r.code = 'ACADEMIC_ADMIN'
  AND r.deleted = 0
ON DUPLICATE KEY UPDATE
    scope_type = VALUES(scope_type),
    updated_by = VALUES(updated_by),
    updated_at = NOW(),
    deleted = 0;

-- SYS_ADMIN：超级管理员授予当前 sys_permission 全表所有权限。
INSERT INTO sys_role_permission
(id, role_id, permission_id, scope_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT 2000000000001000000 + ROW_NUMBER() OVER (ORDER BY p.id),
       r.id,
       p.id,
       'SYSTEM',
       0,
       NOW(),
       0,
       NOW(),
       0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
WHERE r.code = 'SYS_ADMIN'
  AND r.deleted = 0
ON DUPLICATE KEY UPDATE
    scope_type = VALUES(scope_type),
    updated_by = VALUES(updated_by),
    updated_at = NOW(),
    deleted = 0;

-- 删除 CERT_ISSUER 角色授权，并停用历史测试账号。
UPDATE sys_user_role ur
JOIN sys_role r ON r.id = ur.role_id
   SET ur.deleted = 1,
       ur.updated_by = 0,
       ur.updated_at = NOW()
 WHERE r.code = 'CERT_ISSUER'
   AND ur.deleted = 0;

UPDATE sys_user
   SET status = 'DISABLED',
       updated_by = 0,
       updated_at = NOW()
 WHERE username = 'test_cert_issuer'
   AND deleted = 0;

UPDATE sys_role
   SET status = 0,
       deleted = 1,
       updated_by = 0,
       updated_at = NOW()
 WHERE code = 'CERT_ISSUER';
