package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectLifecycleService;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoFinalizationReconciliationScheduleConfigTest {

    @Mock
    private VideoFinalizationObjectLifecycleService lifecycleService;

    @Mock
    private VideoFinalizationObjectReconciler reconciler;

    @Test
    void productionScheduleRunsBackfillAndReconciliationForStartupAndPeriodicTriggers()
            throws Exception {
        VideoFinalizationReconciliationScheduleConfig scheduleConfig =
                new VideoFinalizationReconciliationScheduleConfig(
                        lifecycleService, reconciler, Runnable::run);
        when(lifecycleService.backfillLegacyCandidates()).thenReturn(2, 0);
        when(reconciler.reconcileDue()).thenReturn(
                new VideoFinalizationObjectReconciler.ReconcileResult(3, 2, 1),
                new VideoFinalizationObjectReconciler.ReconcileResult(0, 0, 0));

        scheduleConfig.reconcileOnStartup();
        scheduleConfig.reconcileOnSchedule();

        verify(lifecycleService, times(2)).backfillLegacyCandidates();
        verify(reconciler, times(2)).reconcileDue();
        Profile profile = VideoFinalizationReconciliationScheduleConfig.class
                .getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("prod");

        Method scheduledMethod = VideoFinalizationReconciliationScheduleConfig.class
                .getMethod("reconcileOnSchedule");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.scheduler()).isEqualTo(
                VideoFinalizationReconciliationScheduleConfig
                        .RECONCILIATION_TASK_SCHEDULER_BEAN);
    }

    @Test
    void startupListenerReturnsWithoutWaitingForObjectStoreReconciliation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(lifecycleService.backfillLegacyCandidates()).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("测试后台对账未按时释放");
            }
            return 0;
        });
        when(reconciler.reconcileDue()).thenReturn(
                new VideoFinalizationObjectReconciler.ReconcileResult(0, 0, 0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        VideoFinalizationReconciliationScheduleConfig scheduleConfig =
                new VideoFinalizationReconciliationScheduleConfig(
                        lifecycleService, reconciler, executor);
        try {
            long startedAt = System.nanoTime();
            scheduleConfig.reconcileOnStartup();
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofMillis(500));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

            // 后台任务仍阻塞时，周期触发必须立即跳过，不能再排队扩大积压。
            assertThatCode(scheduleConfig::reconcileOnSchedule)
                    .doesNotThrowAnyException();
            verify(lifecycleService, times(1)).backfillLegacyCandidates();

            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(
                    Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            verify(reconciler).reconcileDue();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
