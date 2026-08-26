package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.system.support.AuditIp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;
    private final AuditIp auditIp;

    @Override
    public void record(SysAuditLog entry) {
        if (entry == null) {
            throw new IllegalArgumentException("审计日志不能为空");
        }
        if (entry.getOperateTime() == null) {
            entry.setOperateTime(LocalDateTime.now());
        }
        if (entry.getOperatorId() == null) {
            entry.setOperatorId(UserContext.getUserIdOrSystem());
        }
        if (!StringUtils.hasText(entry.getIp())) {
            entry.setIp(auditIp.clientIp());
        }
        int inserted = sysAuditLogMapper.insert(entry);
        if (inserted != 1) {
            throw new IllegalStateException("审计日志写入未成功");
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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordRequiresNew(String bizType, Long bizId, String target, String operation,
                                  String oldStatus, String newStatus, String comment) {
        record(bizType, bizId, target, operation, oldStatus, newStatus, comment);
    }
}
