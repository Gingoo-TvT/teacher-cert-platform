package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.mapper.BackupRecordMapper;
import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.service.ParamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionCleanupServiceTest {

    @Mock
    private SysAuditLogMapper sysAuditLogMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private BackupRecordMapper backupRecordMapper;
    @Mock
    private ParamService paramService;

    @InjectMocks
    private RetentionCleanupService service;

    @Test
    void backupRecordCleanupUsesConfiguredFortyFiveDays() {
        when(paramService.getInt(
                RetentionCleanupService.KEY_BACKUP_RETENTION_DAYS,
                RetentionCleanupService.DEFAULT_BACKUP_RETENTION_DAYS)).thenReturn(45);
        when(paramService.getInt(
                RetentionCleanupService.KEY_BATCH_SIZE,
                RetentionCleanupService.DEFAULT_BATCH_SIZE)).thenReturn(1000);
        when(paramService.getInt(
                RetentionCleanupService.KEY_MAX_BATCHES,
                RetentionCleanupService.DEFAULT_MAX_BATCHES)).thenReturn(500);
        when(backupRecordMapper.deletePhysicalTerminalOlderThan(any(LocalDateTime.class), eq(1000)))
                .thenReturn(0);

        LocalDateTime earliest = LocalDateTime.now().minusDays(45);
        assertThat(service.pruneBackupRecord()).isZero();
        LocalDateTime latest = LocalDateTime.now().minusDays(45);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(backupRecordMapper).deletePhysicalTerminalOlderThan(cutoff.capture(), eq(1000));
        assertThat(cutoff.getValue()).isBetween(earliest, latest);
    }
}
