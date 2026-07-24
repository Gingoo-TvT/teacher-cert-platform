package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectLifecycleService;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectReconciler;
import cn.edu.gpnu.platform.system.config.BackupScheduleConfig;
import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.service.impl.DatabaseBackupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoFinalizationSchedulingIsolationTest {

    @Test
    @Timeout(15)
    void blockedDefaultBackupDoesNotBlockDedicatedReconciliationTriggers() throws Exception {
        DatabaseBackupService backupService = mock(DatabaseBackupService.class);
        VideoFinalizationObjectLifecycleService lifecycleService =
                mock(VideoFinalizationObjectLifecycleService.class);
        VideoFinalizationObjectReconciler reconciler =
                mock(VideoFinalizationObjectReconciler.class);
        AtomicBoolean backupBlocked = new AtomicBoolean();
        CountDownLatch backupEntered = new CountDownLatch(1);
        CountDownLatch releaseBackup = new CountDownLatch(1);
        CountDownLatch submittedWhileBackupBlocked = new CountDownLatch(2);
        List<String> triggerThreads = new CopyOnWriteArrayList<>();

        BackupRecord completed = new BackupRecord();
        completed.setStatus("COMPLETED");
        doAnswer(invocation -> {
            backupBlocked.set(true);
            backupEntered.countDown();
            if (!releaseBackup.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("测试备份阻塞未按时释放");
            }
            backupBlocked.set(false);
            return completed;
        }).when(backupService).backup(any(), any(), any(), any());
        when(lifecycleService.backfillLegacyCandidates()).thenReturn(0);
        when(reconciler.reconcileDue()).thenReturn(
                new VideoFinalizationObjectReconciler.ReconcileResult(0, 0, 0));

        Executor recordingWorker = command -> {
            if (backupBlocked.get()) {
                triggerThreads.add(Thread.currentThread().getName());
                submittedWhileBackupBlocked.countDown();
            }
            command.run();
        };

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.getEnvironment().setActiveProfiles("prod");
            TestPropertyValues.of(
                    "platform.backup.schedule.enabled=true",
                    "platform.backup.schedule.cron=*/1 * * * * *",
                    "platform.video.finalization-reconciliation.cron=*/1 * * * * *")
                    .applyTo(context);
            context.registerBean(DatabaseBackupService.class, () -> backupService);
            context.registerBean(
                    VideoFinalizationObjectLifecycleService.class, () -> lifecycleService);
            context.registerBean(VideoFinalizationObjectReconciler.class, () -> reconciler);
            context.registerBean(
                    VideoFinalizationReconciliationScheduleConfig.RECONCILIATION_EXECUTOR_BEAN,
                    Executor.class,
                    () -> recordingWorker);
            context.register(
                    VideoFinalizationReconciliationTaskSchedulerConfig.class,
                    VideoFinalizationReconciliationScheduleConfig.class,
                    BackupScheduleConfig.class);
            context.refresh();

            assertThat(backupEntered.await(4, TimeUnit.SECONDS)).isTrue();
            assertThat(submittedWhileBackupBlocked.await(4, TimeUnit.SECONDS)).isTrue();
            assertThat(releaseBackup.getCount()).isEqualTo(1L);
            assertThat(triggerThreads)
                    .hasSizeGreaterThanOrEqualTo(2)
                    .allMatch(name ->
                            name.startsWith("video-finalization-reconciliation-trigger-"));

            ThreadPoolTaskScheduler defaultScheduler = context.getBean(
                    VideoFinalizationReconciliationTaskSchedulerConfig
                            .DEFAULT_TASK_SCHEDULER_BEAN,
                    ThreadPoolTaskScheduler.class);
            ThreadPoolTaskScheduler reconciliationScheduler = context.getBean(
                    VideoFinalizationReconciliationScheduleConfig
                            .RECONCILIATION_TASK_SCHEDULER_BEAN,
                    ThreadPoolTaskScheduler.class);
            assertThat(reconciliationScheduler).isNotSameAs(defaultScheduler);
            assertThat(defaultScheduler.getPoolSize()).isEqualTo(1);
            assertThat(reconciliationScheduler.getPoolSize()).isEqualTo(1);
        } finally {
            backupBlocked.set(false);
            releaseBackup.countDown();
            context.close();
        }
    }
}
