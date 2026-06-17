package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.system.support.AuditIp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;

    @Override
    public void record(SysAuditLog log) {
        if (log.getOperateTime() == null) {
            log.setOperateTime(LocalDateTime.now());
        }
        if (log.getOperatorId() == null) {
            log.setOperatorId(UserContext.getUserIdOrSystem());
        }
        if (!StringUtils.hasText(log.getIp())) {
            log.setIp(AuditIp.clientIp());
        }
        sysAuditLogMapper.insert(log);
    }

    @Override
    public void record(String bizType, Long bizId, String target, String operation,
                       String oldStatus, String newStatus, String comment) {
        SysAuditLog log = new SysAuditLog();
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setTarget(target);
        log.setOperation(operation);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setComment(comment);
        record(log);
    }
}
