package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectLifecycleService;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizationObjectReconciler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频定稿对象是正确性关键的持久对账，不受普通保留期/孤儿报告作业开关影响。
 * 生产 profile 始终启用：启动时补录旧会话，随后每分钟处理失败清理与 CLEANED 墓碑复查。
 */
@Slf4j
@Configuration
@Profile("prod")
@EnableScheduling
public class VideoFinalizationReconciliationScheduleConfig {

    static final String RECONCILIATION_EXECUTOR_BEAN =
            "videoFinalizationReconciliationExecutor";
    static final String RECONCILIATION_TASK_SCHEDULER_BEAN =
            "videoFinalizationReconciliationTaskScheduler";

    private final VideoFinalizationObjectLifecycleService lifecycleService;
    private final VideoFinalizationObjectReconciler reconciler;
    private final Executor reconciliationExecutor;
    private final AtomicBoolean reconciliationRunning = new AtomicBoolean();

    public VideoFinalizationReconciliationScheduleConfig(
            VideoFinalizationObjectLifecycleService lifecycleService,
            VideoFinalizationObjectReconciler reconciler,
            @Qualifier(RECONCILIATION_EXECUTOR_BEAN) Executor reconciliationExecutor) {
        this.lifecycleService = lifecycleService;
        this.reconciler = reconciler;
        this.reconciliationExecutor = reconciliationExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        submitReconciliation("启动");
    }

    @Scheduled(
            cron = "${platform.video.finalization-reconciliation.cron:0 * * * * *}",
            scheduler = RECONCILIATION_TASK_SCHEDULER_BEAN)
    public void reconcileOnSchedule() {
        submitReconciliation("周期");
    }

    private void submitReconciliation(String trigger) {
        if (!reconciliationRunning.compareAndSet(false, true)) {
            log.info("视频定稿对象{}对账跳过：已有任务运行中", trigger);
            return;
        }
        try {
            reconciliationExecutor.execute(() -> {
                try {
                    runReconciliation(trigger);
                } finally {
                    reconciliationRunning.set(false);
                }
            });
        } catch (RuntimeException e) {
            reconciliationRunning.set(false);
            log.error("视频定稿对象{}对账提交失败，后续周期将继续重试", trigger, e);
        }
    }

    private void runReconciliation(String trigger) {
        try {
            int backfilled = lifecycleService.backfillLegacyCandidates();
            VideoFinalizationObjectReconciler.ReconcileResult result =
                    reconciler.reconcileDue();
            if ("启动".equals(trigger) || backfilled > 0 || result.scanned() > 0) {
                log.info("视频定稿对象{}对账完成 backfilled={} scanned={} cleaned={} failed={}",
                        trigger, backfilled, result.scanned(), result.cleaned(), result.failed());
            }
        } catch (Exception e) {
            log.error("视频定稿对象{}对账失败，后续周期将继续重试", trigger, e);
        }
    }
}
