package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.boot.mapper.FileObjectScanMapper;
import cn.edu.gpnu.platform.file.service.impl.FileMaintenanceService;
import cn.edu.gpnu.platform.system.observability.ScheduledJobMetrics;
import cn.edu.gpnu.platform.system.service.ParamService;
import cn.edu.gpnu.platform.system.service.impl.RetentionCleanupService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleanupScheduleConfigTest {

    @Test
    void mapsCleanupServiceResultsToRealSchedulerOutcomes() {
        RetentionCleanupService retention = mock(RetentionCleanupService.class);
        FileMaintenanceService fileMaintenance = mock(FileMaintenanceService.class);
        FileObjectScanMapper fileMapper = mock(FileObjectScanMapper.class);
        ParamService params = mock(ParamService.class);
        when(retention.pruneAuditLog()).thenReturn(0);
        when(retention.pruneNotification()).thenReturn(0);
        when(params.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(fileMaintenance.ensureAbortIncompleteMultipartLifecycle(anyInt())).thenReturn(false);
        when(fileMapper.countOrphans(any())).thenReturn(0L);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            CleanupScheduleConfig config = new CleanupScheduleConfig(retention, fileMaintenance,
                    fileMapper, params, new ScheduledJobMetrics(registry));

            assertThatCode(config::scheduledRetentionPrune).doesNotThrowAnyException();
            verify(retention).pruneBackupRecord();
            assertThatCode(config::scheduledMinioIncompleteAbort).doesNotThrowAnyException();
            assertThatCode(config::scheduledOrphanFileScan).doesNotThrowAnyException();

            assertCounter(registry, "retention_prune", "success", 1.0);
            assertCounter(registry, "minio_incomplete_abort", "failure", 1.0);
            assertCounter(registry, "orphan_file_scan", "success", 1.0);
        } finally {
            registry.close();
        }
    }

    @Test
    void swallowedCleanupExceptionIsStillRecordedAsFailure() {
        RetentionCleanupService retention = mock(RetentionCleanupService.class);
        when(retention.pruneAuditLog()).thenThrow(new IllegalStateException("database unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            CleanupScheduleConfig config = new CleanupScheduleConfig(retention,
                    mock(FileMaintenanceService.class), mock(FileObjectScanMapper.class),
                    mock(ParamService.class), new ScheduledJobMetrics(registry));

            assertThatCode(config::scheduledRetentionPrune).doesNotThrowAnyException();
            assertCounter(registry, "retention_prune", "failure", 1.0);
        } finally {
            registry.close();
        }
    }

    @Test
    void minioScheduleEnsuresAbortAndBackupRetentionFromSharedCleanupParams() {
        RetentionCleanupService retention = mock(RetentionCleanupService.class);
        FileMaintenanceService fileMaintenance = mock(FileMaintenanceService.class);
        FileObjectScanMapper fileMapper = mock(FileObjectScanMapper.class);
        ParamService params = mock(ParamService.class);
        when(params.getInt("cleanup.minio.abortIncompleteDays", 7)).thenReturn(9);
        when(params.getInt("cleanup.backup.retentionDays", 30)).thenReturn(45);
        when(fileMaintenance.ensureAbortIncompleteMultipartLifecycle(9)).thenReturn(true);
        when(fileMaintenance.ensureBackupRetentionLifecycle("archive-bucket", "nightly/", 45))
                .thenReturn(true);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            CleanupScheduleConfig config = new CleanupScheduleConfig(retention, fileMaintenance,
                    fileMapper, params, new ScheduledJobMetrics(registry));
            ReflectionTestUtils.setField(config, "backupBucket", "archive-bucket");
            ReflectionTestUtils.setField(config, "backupPrefix", "nightly/");

            config.scheduledMinioIncompleteAbort();

            verify(params).getInt(eq("cleanup.minio.abortIncompleteDays"), eq(7));
            verify(params).getInt(eq("cleanup.backup.retentionDays"), eq(30));
            verify(fileMaintenance).ensureAbortIncompleteMultipartLifecycle(9);
            verify(fileMaintenance).ensureBackupRetentionLifecycle("archive-bucket", "nightly/", 45);
            assertCounter(registry, "minio_incomplete_abort", "success", 1.0);
        } finally {
            registry.close();
        }
    }

    private void assertCounter(SimpleMeterRegistry registry, String job, String outcome, double expected) {
        assertThat(registry.find("platform.scheduled.job.executions")
                .tags("job", job, "outcome", outcome).counter().count()).isEqualTo(expected);
    }
}
