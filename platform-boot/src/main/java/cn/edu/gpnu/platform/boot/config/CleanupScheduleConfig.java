package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.boot.mapper.FileObjectScanMapper;
import cn.edu.gpnu.platform.file.service.impl.FileMaintenanceService;
import cn.edu.gpnu.platform.system.service.ParamService;
import cn.edu.gpnu.platform.system.service.impl.RetentionCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 47（P1-9 定时清理）：调度层——保留期清理 + MinIO 未完成分片 abort + 孤儿 file_object 扫描（仅报告）。
 *
 * <p><b>默认关闭</b>（与 {@code BackupScheduleConfig} 同款门禁）：整个配置（含 {@link EnableScheduling}）
 * 由 {@code platform.cleanup.schedule.enabled} 门禁——{@code havingValue="true"} 且默认 matchIfMissing=false，
 * 故 dev/测试/未配置环境本 bean 不注册、定时框架不启用、{@code @Scheduled} 不触发（不扰动全部 IT）。
 * 仅 application-prod.yml 显式置 true 时生效。三个作业 cron 各自可配，错峰于全量备份(03:00)之后。
 *
 * <p>放在 platform-boot 而非某业务模块，是因三个作业跨模块编排（system 保留清理 + file MinIO 维护 +
 * boot 孤儿扫描），本模块是唯一同时可见三者的组合根。作业内均吞异常仅告警，避免调度线程被单次失败中断；
 * 阈值/保留期经 sys_param 可调（{@link RetentionCleanupService} 及本类 minio/orphan 参数）。
 * 孤儿扫描<b>只报告不删除</b>——自动删文件/行风险过高，交运维人工核查。
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "platform.cleanup.schedule", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CleanupScheduleConfig {

    private static final String KEY_MINIO_ABORT_DAYS = "cleanup.minio.abortIncompleteDays";
    private static final String KEY_ORPHAN_GRACE_HOURS = "cleanup.orphan.graceHours";
    private static final int DEFAULT_MINIO_ABORT_DAYS = 7;
    private static final int DEFAULT_ORPHAN_GRACE_HOURS = 24;
    private static final int ORPHAN_SAMPLE_LIMIT = 20;

    private final RetentionCleanupService retentionCleanupService;
    private final FileMaintenanceService fileMaintenanceService;
    private final FileObjectScanMapper fileObjectScanMapper;
    private final ParamService paramService;

    /** 保留期物理清理 audit_log / notification（默认每日 03:30，错峰于备份之后）。 */
    @Scheduled(cron = "${platform.cleanup.schedule.retention-cron:0 30 3 * * *}")
    public void scheduledRetentionPrune() {
        try {
            int audit = retentionCleanupService.pruneAuditLog();
            int notification = retentionCleanupService.pruneNotification();
            log.info("定时保留清理完成 audit_log={} notification={}", audit, notification);
        } catch (Exception e) {
            log.error("定时保留清理失败", e);
        }
    }

    /** 确保 MinIO 未完成分片 abort 生命周期规则（默认每日 03:45，实际 abort 由服务端按规则执行）。 */
    @Scheduled(cron = "${platform.cleanup.schedule.minio-cron:0 45 3 * * *}")
    public void scheduledMinioIncompleteAbort() {
        try {
            int days = paramService.getInt(KEY_MINIO_ABORT_DAYS, DEFAULT_MINIO_ABORT_DAYS);
            boolean ok = fileMaintenanceService.ensureAbortIncompleteMultipartLifecycle(days);
            log.info("定时 MinIO 未完成分片 abort 生命周期规则确保 ok={} days={}", ok, days);
        } catch (Exception e) {
            log.error("定时 MinIO 未完成分片清理失败", e);
        }
    }

    /** 孤儿 file_object 扫描——仅统计/取样上报，不删除（默认每日 04:00）。 */
    @Scheduled(cron = "${platform.cleanup.schedule.orphan-cron:0 0 4 * * *}")
    public void scheduledOrphanFileScan() {
        try {
            int graceHours = paramService.getInt(KEY_ORPHAN_GRACE_HOURS, DEFAULT_ORPHAN_GRACE_HOURS);
            LocalDateTime graceCutoff = LocalDateTime.now().minusHours(Math.max(0, graceHours));
            long orphans = fileObjectScanMapper.countOrphans(graceCutoff);
            if (orphans <= 0) {
                log.info("孤儿 file_object 扫描：无孤儿（grace={}h）", graceHours);
                return;
            }
            List<Long> sample = fileObjectScanMapper.sampleOrphanIds(graceCutoff, ORPHAN_SAMPLE_LIMIT);
            // 仅报告：不自动删对象/行，交运维核查（避免误删仍被引用/在途的对象）
            log.warn("孤儿 file_object 扫描：发现 {} 条无业务引用(grace={}h)，样例 id(≤{})={}；仅报告不删除，请运维核查",
                    orphans, graceHours, ORPHAN_SAMPLE_LIMIT, sample);
        } catch (Exception e) {
            log.error("孤儿 file_object 扫描失败", e);
        }
    }
}
