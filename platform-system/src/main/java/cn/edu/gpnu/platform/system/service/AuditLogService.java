package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.system.entity.SysAuditLog;

/**
 * 审计日志写入。
 */
public interface AuditLogService {

    void record(SysAuditLog log);

    void record(String bizType, Long bizId, String target, String operation,
                String oldStatus, String newStatus, String comment);
}
