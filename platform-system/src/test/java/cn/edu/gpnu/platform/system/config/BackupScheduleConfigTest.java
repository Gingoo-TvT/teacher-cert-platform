package cn.edu.gpnu.platform.system.config;

import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.observability.ScheduledJobMetrics;
import cn.edu.gpnu.platform.system.service.impl.DatabaseBackupService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupScheduleConfigTest {

    @Test
    void recordsSuccessAndFailureWithoutChangingSchedulerExceptionContract() {
        DatabaseBackupService backupService = mock(DatabaseBackupService.class);
        BackupRecord record = new BackupRecord();
        when(backupService.backup(any(), any(), any(), any()))
                .thenReturn(record)
                .thenThrow(new IllegalStateException("backup unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            BackupScheduleConfig config = new BackupScheduleConfig(
                    backupService, new ScheduledJobMetrics(registry));

            assertThatCode(config::scheduledFullBackup).doesNotThrowAnyException();
            assertThatCode(config::scheduledFullBackup).doesNotThrowAnyException();

            assertThat(registry.find("platform.scheduled.job.executions")
                    .tags("job", "database_backup", "outcome", "success").counter().count()).isEqualTo(1.0);
            assertThat(registry.find("platform.scheduled.job.executions")
                    .tags("job", "database_backup", "outcome", "failure").counter().count()).isEqualTo(1.0);
        } finally {
            registry.close();
        }
    }
}
