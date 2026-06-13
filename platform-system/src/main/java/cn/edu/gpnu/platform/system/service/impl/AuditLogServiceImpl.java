package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        sysAuditLogMapper.insert(log);
    }
}
