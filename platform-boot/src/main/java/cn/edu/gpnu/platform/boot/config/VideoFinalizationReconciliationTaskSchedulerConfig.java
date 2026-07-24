package cn.edu.gpnu.platform.boot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 视频终态对象对账的触发调度器。
 *
 * <p>生产环境同时显式保留名为 {@code taskScheduler} 的普通任务调度器，避免 Spring Boot 因发现自定义
 * {@link org.springframework.scheduling.TaskScheduler} 而回退后，让未指定 scheduler 的备份/清理任务误用
 * 视频专用线程。视频 cron 只在专用调度器上触发，再把实际对象工作提交给独立 worker executor。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class VideoFinalizationReconciliationTaskSchedulerConfig {

    static final String DEFAULT_TASK_SCHEDULER_BEAN = "taskScheduler";

    @Bean(name = DEFAULT_TASK_SCHEDULER_BEAN)
    public ThreadPoolTaskScheduler platformTaskScheduler() {
        return scheduler("platform-scheduling-", "平台普通定时任务");
    }

    @Bean(name =
            VideoFinalizationReconciliationScheduleConfig.RECONCILIATION_TASK_SCHEDULER_BEAN)
    public ThreadPoolTaskScheduler videoFinalizationReconciliationTaskScheduler() {
        return scheduler(
                "video-finalization-reconciliation-trigger-",
                "视频定稿对象对账触发");
    }

    private ThreadPoolTaskScheduler scheduler(String threadNamePrefix, String taskDescription) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setErrorHandler(error ->
                log.error("{}异常，后续周期将继续调度", taskDescription, error));
        return scheduler;
    }
}
