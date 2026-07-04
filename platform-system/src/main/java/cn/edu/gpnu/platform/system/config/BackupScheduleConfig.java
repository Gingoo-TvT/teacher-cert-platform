package cn.edu.gpnu.platform.system.config;

import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.service.impl.DatabaseBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Phase 41.2（P0-6 真备份）：定时全量备份。
 *
 * <p><b>默认关闭</b>：整个配置（含 {@link EnableScheduling}）由 {@code platform.backup.schedule.enabled}
 * 门禁——{@code havingValue="true"} 且 {@code matchIfMissing=false}，故 dev/测试/未配置环境下本 bean 不注册、
 * 定时框架不启用、{@code @Scheduled} 不触发（保证 90 IT 不被扰动）。仅 application-prod.yml 显式置 true 时生效。
 *
 * <p>cron 可配（{@code platform.backup.schedule.cron}），默认每日 03:00 全量备份，复用
 * {@link DatabaseBackupService#backup} 产出真实产物；作业内吞异常仅告警，避免调度线程被单次失败中断。
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "platform.backup.schedule", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BackupScheduleConfig {

    private final DatabaseBackupService databaseBackupService;

    @Scheduled(cron = "${platform.backup.schedule.cron:0 0 3 * * *}")
    public void scheduledFullBackup() {
        try {
            BackupRecord record = databaseBackupService.backup("full", "scheduled", "定时全量备份", 0L);
            log.info("定时备份完成 id={} size={} tables={} rows={}",
                    record.getId(), record.getByteSize(), record.getTableCount(), record.getRowCount());
        } catch (Exception e) {
            log.error("定时备份失败", e);
        }
    }
}
