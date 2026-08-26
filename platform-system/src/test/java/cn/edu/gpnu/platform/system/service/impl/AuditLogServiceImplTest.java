package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.support.AuditIp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceImplTest {

    @Test
    void mapperFailurePropagatesToTransactionalCaller() {
        SysAuditLogMapper mapper = mock(SysAuditLogMapper.class);
        AuditIp auditIp = mock(AuditIp.class);
        AuditLogServiceImpl service = new AuditLogServiceImpl(mapper, auditIp);
        IllegalStateException failure = new IllegalStateException("audit unavailable");
        when(auditIp.clientIp()).thenReturn("203.0.113.27");
        when(mapper.insert(any(SysAuditLog.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.record(new SysAuditLog())).isSameAs(failure);
        verify(mapper).insert(any(SysAuditLog.class));
    }

    @Test
    void zeroInsertedRowsIsTreatedAsAuditFailure() {
        SysAuditLogMapper mapper = mock(SysAuditLogMapper.class);
        AuditIp auditIp = mock(AuditIp.class);
        AuditLogServiceImpl service = new AuditLogServiceImpl(mapper, auditIp);
        when(auditIp.clientIp()).thenReturn("203.0.113.27");
        when(mapper.insert(any(SysAuditLog.class))).thenReturn(0);

        assertThatThrownBy(() -> service.record(new SysAuditLog()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审计日志写入未成功");
    }

    @Test
    void nullAuditEntryFailsClosed() {
        SysAuditLogMapper mapper = mock(SysAuditLogMapper.class);
        AuditIp auditIp = mock(AuditIp.class);
        AuditLogServiceImpl service = new AuditLogServiceImpl(mapper, auditIp);

        assertThatThrownBy(() -> service.record((SysAuditLog) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计日志不能为空");
    }
}
