package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.service.ParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Phase 47（P1-9 定时清理）：audit_log / notification 保留期物理清理。
 *
 * <p>两张表都只增不清（audit_log 追加写；notification 长期累积）。本服务按可配置保留窗口
 * （{@link ParamService} 读 sys_param，默认 audit_log 180 天、notification 90 天）分批物理删除过期行。
 * 分批（每条 SQL 带 LIMIT，循环直至删尽或触及 maxBatches 上限）以避免一次大清理长时间锁表。
 * 只删过期运营数据、不碰业务数据。方法可被定时器（{@code CleanupScheduleConfig}）或运维直接调用；
 * 编排与告警由调用方负责，与 {@code DatabaseBackupService} 一样做成无接口的具体服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionCleanupService {

    // 参数键（sys_param 可覆盖）与保守默认值
    static final String KEY_AUDIT_RETENTION_DAYS = "cleanup.auditLog.retentionDays";
    static final String KEY_NOTIFICATION_RETENTION_DAYS = "cleanup.notification.retentionDays";
    static final String KEY_BATCH_SIZE = "cleanup.prune.batchSize";
    static final String KEY_MAX_BATCHES = "cleanup.prune.maxBatches";
    static final int DEFAULT_AUDIT_RETENTION_DAYS = 180;
    static final int DEFAULT_NOTIFICATION_RETENTION_DAYS = 90;
    static final int DEFAULT_BATCH_SIZE = 1000;
    static final int DEFAULT_MAX_BATCHES = 500;

    private final SysAuditLogMapper sysAuditLogMapper;
    private final NotificationMapper notificationMapper;
    private final ParamService paramService;

    /** 物理清理过期审计日志（operate_time 早于 now-保留天数）。返回删除总行数。 */
    public int pruneAuditLog() {
        int days = retentionDays(KEY_AUDIT_RETENTION_DAYS, DEFAULT_AUDIT_RETENTION_DAYS);
        return pruneOlderThan("audit_log", days, sysAuditLogMapper::deletePhysicalOlderThan);
    }

    /** 物理清理过期站内信（created_at 早于 now-保留天数）。返回删除总行数。 */
    public int pruneNotification() {
        int days = retentionDays(KEY_NOTIFICATION_RETENTION_DAYS, DEFAULT_NOTIFICATION_RETENTION_DAYS);
        return pruneOlderThan("notification", days, notificationMapper::deletePhysicalOlderThan);
    }

    private int retentionDays(String key, int defaultDays) {
        int days = paramService.getInt(key, defaultDays);
        // 防御：非正保留期会清空全表，视为配置错误、回退默认（保守，绝不因误配清空整表）
        if (days <= 0) {
            log.warn("保留天数参数 {}={} 非法(<=0)，回退默认 {} 天", key, days, defaultDays);
            return defaultDays;
        }
        return days;
    }

    private int pruneOlderThan(String label, int retentionDays, BatchDeleter deleter) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int batchSize = Math.max(1, paramService.getInt(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE));
        int maxBatches = Math.max(1, paramService.getInt(KEY_MAX_BATCHES, DEFAULT_MAX_BATCHES));
        int total = 0;
        int batches = 0;
        while (batches < maxBatches) {
            int deleted = deleter.delete(cutoff, batchSize);
            total += deleted;
            batches++;
            if (deleted < batchSize) {
                break; // 已删尽
            }
        }
        if (batches >= maxBatches) {
            log.warn("{} 保留清理触及单次批次上限 {}（batchSize={}），本次删 {} 行；剩余留待下次调度",
                    label, maxBatches, batchSize, total);
        }
        if (total > 0) {
            log.info("{} 保留清理完成：保留 {} 天(cutoff={})，物理删除 {} 行（{} 批）",
                    label, retentionDays, cutoff, total, batches);
        }
        return total;
    }

    /** 分批物理删除函数：删 cutoff 之前的行、单次至多 batchSize 行、返回实删行数。 */
    @FunctionalInterface
    interface BatchDeleter {
        int delete(LocalDateTime cutoff, int batchSize);
    }
}
