-- WS-14 复核整改：数据库备份对象与终态记录共用一个可管理的正整数保留窗口。
-- 幂等升级时保留学校已经配置的 param_value，只补齐参数元数据与 active 状态。
INSERT INTO sys_param
    (id, param_key, param_value, param_type, param_group, description, editable,
     created_by, created_at, updated_by, updated_at, deleted)
VALUES
    (340000000000000001, 'cleanup.backup.retentionDays', '30', 'int', 'global',
     '数据库备份对象与终态记录共享保留天数（必须大于0）', 1,
     0, NOW(), 0, NOW(), 0)
ON DUPLICATE KEY UPDATE
    param_type = VALUES(param_type),
    param_group = VALUES(param_group),
    description = VALUES(description),
    editable = VALUES(editable),
    updated_at = NOW(),
    deleted = 0;
