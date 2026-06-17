INSERT INTO sys_role_permission
(id, role_id, permission_id, scope_type, created_at, updated_at, deleted)
SELECT seed.id, r.id, p.id, seed.scope_type, NOW(), NOW(), 0
FROM (
    SELECT 160000000000001001 AS id, 'STUDENT' AS role_code, 'cert:view' AS permission_code, 'SELF' AS scope_type
) seed
JOIN sys_role r ON r.code = seed.role_code AND r.deleted = 0
JOIN sys_permission p ON p.code = seed.permission_code AND p.deleted = 0
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    permission_id = VALUES(permission_id),
    scope_type = VALUES(scope_type),
    updated_at = NOW(),
    deleted = 0;
