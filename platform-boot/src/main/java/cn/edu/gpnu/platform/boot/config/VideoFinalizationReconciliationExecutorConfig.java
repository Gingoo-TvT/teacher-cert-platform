package cn.edu.gpnu.platform.boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 启动对账必须离开 ApplicationReady 事件线程；单线程与调度侧防重入共同限制外部对象调用并发。
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class VideoFinalizationReconciliationExecutorConfig {

    @Bean(
            name = VideoFinalizationReconciliationScheduleConfig.RECONCILIATION_EXECUTOR_BEAN,
            destroyMethod = "shutdownNow")
    public ExecutorService videoFinalizationReconciliationExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "video-finalization-reconciliation");
            thread.setDaemon(true);
            return thread;
        });
    }
}
