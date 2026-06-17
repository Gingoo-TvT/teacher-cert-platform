package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.system.support.AuditIp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;

    @Override
    public void record(SysAuditLog entry) {
        try {
            if (entry == null) {
                return;
            }
            if (entry.getOperateTime() == null) {
                entry.setOperateTime(LocalDateTime.now());
            }
            if (entry.getOperatorId() == null) {
                entry.setOperatorId(UserContext.getUserIdOrSystem());
            }
            if (!StringUtils.hasText(entry.getIp())) {
                entry.setIp(AuditIp.clientIp());
            }
            sysAuditLogMapper.insert(entry);
        } catch (Exception ex) {
            log.warn("Audit log write failed", ex);
        }
    }

    @Override
    public void record(String bizType, Long bizId, String target, String operation,
                       String oldStatus, String newStatus, String comment) {
        SysAuditLog entry = new SysAuditLog();
        entry.setBizType(bizType);
        entry.setBizId(bizId);
        entry.setTarget(target);
        entry.setOperation(operation);
        entry.setOldStatus(oldStatus);
        entry.setNewStatus(newStatus);
        entry.setComment(comment);
        record(entry);
    }
}
