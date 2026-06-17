-- =============================================================
-- V21 测试结果只确认：撤销手工录入/编辑授权。
-- 保留 sys_permission.test:edit 权限点定义，用于历史审计/外键追溯；运行期不再授权。
-- =============================================================

UPDATE sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
   SET rp.deleted = 1,
       rp.updated_by = 0,
       rp.updated_at = NOW()
 WHERE p.code = 'test:edit'
   AND rp.deleted = 0;

-- 导入与确认仍按 WP-A 矩阵归 ACADEMIC_ADMIN / COLLEGE_AUDITOR。
INSERT INTO sys_role_permission
(id, role_id, permission_id, scope_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT seed.id, r.id, p.id, seed.scope_type, 0, NOW(), 0, NOW(), 0
FROM (
    SELECT 210000000000001001 AS id, 'COLLEGE_AUDITOR' AS role_code, 'test:import' AS permission_code, 'COLLEGE' AS scope_type
    UNION ALL SELECT 210000000000001002, 'COLLEGE_AUDITOR', 'test:confirm', 'COLLEGE'
    UNION ALL SELECT 210000000000001003, 'ACADEMIC_ADMIN', 'test:import', 'SCHOOL'
    UNION ALL SELECT 210000000000001004, 'ACADEMIC_ADMIN', 'test:confirm', 'SCHOOL'
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
