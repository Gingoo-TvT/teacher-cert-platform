package cn.edu.gpnu.platform.file.service.impl;

import cn.edu.gpnu.platform.file.config.MinioProperties;
import io.minio.GetBucketLifecycleArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.messages.AbortIncompleteMultipartUpload;
import io.minio.messages.ErrorResponse;
import io.minio.messages.Expiration;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.LifecycleRule;
import io.minio.messages.NoncurrentVersionExpiration;
import io.minio.messages.RuleFilter;
import io.minio.messages.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 47（P1-9 定时清理）：MinIO 桶维护——未完成分片上传（incomplete multipart uploads）的清理。
 *
 * <p><b>为何用生命周期规则、而非应用侧遍历 abort</b>：MinIO Java SDK 8.5.x 的高层 {@link MinioClient}
 * 已<em>不再</em>暴露 {@code listIncompleteUploads}/{@code removeIncompleteUpload}（7.x 后移除；本仓库
 * 依赖 8.5.12，javap 核验其 public 方法确无此二者）。官方/S3 推荐做法即在桶上设置
 * {@code AbortIncompleteMultipartUpload} 生命周期规则，由 MinIO <b>服务端</b>自动 abort「初始化超过 N 天
 * 仍未完成」的分片上传——无需应用逐个遍历/删除，也不必自研一套列举分片的底层调用。
 *
 * <p>本服务只做一件事且幂等：<b>确保</b>该规则存在（保留桶上其它既有规则，仅新增/更新我们这条 id）。
 * 实际 abort 由 MinIO 服务端按规则执行。读取既有规则失败时采用 fail-closed：仅告警并返回 false，
 * 不调用整桶生命周期写入，也不打断调度线程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMaintenanceService {

    /** 我们这条生命周期规则的稳定 id（用于幂等识别/更新，不误伤运维手工添加的其它规则） */
    static final String ABORT_RULE_ID = "tcp-abort-incomplete-multipart-uploads";
    /** 备份产物保留规则的稳定 id；只替换本规则，保留桶上其它生命周期配置。 */
    static final String BACKUP_RETENTION_RULE_ID = "tcp-db-backup-retention";
    static final String DEFAULT_BACKUP_PREFIX = "db-backup/";
    private static final int DEFAULT_BACKUP_RETENTION_DAYS = 30;
    private static final int BACKUP_NONCURRENT_EXPIRATION_DAYS = 1;

    private final MinioClient minioClient;
    private final MinioProperties props;

    /**
     * 确保桶上存在「abort 初始化超过 days 天仍未完成的分片上传」生命周期规则。
     * 已存在且天数一致则跳过；否则保留其它既有规则、新增/更新本规则后整体下发。
     *
     * @param days 未完成分片初始化后保留的天数（超过则由服务端 abort），非正数回退 7
     * @return 是否成功确保（含幂等命中）；MinIO 异常时返回 false
     */
    public boolean ensureAbortIncompleteMultipartLifecycle(int days) {
        int abortDays = days > 0 ? days : 7;
        String bucket = props.getBucket();
        try {
            List<LifecycleRule> existing = currentRules(bucket);
            for (LifecycleRule r : existing) {
                if (ABORT_RULE_ID.equals(r.id())
                        && r.abortIncompleteMultipartUpload() != null
                        && r.abortIncompleteMultipartUpload().daysAfterInitiation() == abortDays) {
                    log.info("MinIO 桶 {} 已存在未完成分片 abort 规则(days={})，跳过", bucket, abortDays);
                    return true;
                }
            }
            List<LifecycleRule> rules = new ArrayList<>();
            for (LifecycleRule r : existing) {
                if (!ABORT_RULE_ID.equals(r.id())) {
                    rules.add(r); // 保留桶上其它既有规则
                }
            }
            rules.add(new LifecycleRule(
                    Status.ENABLED,
                    new AbortIncompleteMultipartUpload(abortDays),
                    null,                 // expiration：只 abort 残片，不删已完成对象
                    new RuleFilter(""),   // 空前缀 = 整桶
                    ABORT_RULE_ID,
                    null, null, null));
            minioClient.setBucketLifecycle(SetBucketLifecycleArgs.builder()
                    .bucket(bucket)
                    .config(new LifecycleConfiguration(rules))
                    .build());
            log.info("MinIO 桶 {} 已设置未完成分片 abort 生命周期规则：初始化超 {} 天自动 abort（服务端执行）",
                    bucket, abortDays);
            return true;
        } catch (Exception e) {
            // 仅记录固定白名单分类；禁止传入异常对象或服务端可控的 message/code/endpoint/path/trace。
            log.warn("MinIO 未完成分片 abort 生命周期规则确保失败(稍后可重试，category={})",
                    classifyLifecycleFailure(e));
            return false;
        }
    }

    /**
     * 确保备份目标桶上存在「指定前缀当前版本保留 N 天、非当前版本保留 1 天」规则。
     * 已有规则完全匹配时不写入；需要更新时只替换本服务管理的规则并保留其它规则。
     *
     * @param backupBucket 备份实际写入的桶；为空时复用业务桶
     * @param backupPrefix 备份实际写入的前缀；为空时使用 {@code db-backup/}
     * @param days 备份产物保留天数；非正数回退 30 天
     * @return 是否成功确保（含幂等命中）；读取或写入失败时返回 false
     */
    public boolean ensureBackupRetentionLifecycle(String backupBucket, String backupPrefix, int days) {
        String bucket = backupBucket == null || backupBucket.isBlank()
                ? props.getBucket()
                : backupBucket;
        String prefix = normalizePrefix(backupPrefix);
        int retentionDays = days > 0 ? days : DEFAULT_BACKUP_RETENTION_DAYS;
        try {
            List<LifecycleRule> existing = currentRules(bucket);
            LifecycleRule managedRule = null;
            int managedRuleCount = 0;
            for (LifecycleRule rule : existing) {
                if (BACKUP_RETENTION_RULE_ID.equals(rule.id())) {
                    managedRule = rule;
                    managedRuleCount++;
                }
            }
            if (managedRuleCount == 1 && matchesBackupRetention(managedRule, prefix, retentionDays)) {
                log.info("MinIO 桶 {} 已存在备份保留规则(prefix={}, days={})，跳过",
                        bucket, prefix, retentionDays);
                return true;
            }

            List<LifecycleRule> rules = new ArrayList<>();
            for (LifecycleRule rule : existing) {
                if (!BACKUP_RETENTION_RULE_ID.equals(rule.id())) {
                    rules.add(rule);
                }
            }
            rules.add(new LifecycleRule(
                    Status.ENABLED,
                    null,
                    new Expiration((java.time.ZonedDateTime) null, retentionDays, null),
                    new RuleFilter(prefix),
                    BACKUP_RETENTION_RULE_ID,
                    new NoncurrentVersionExpiration(BACKUP_NONCURRENT_EXPIRATION_DAYS),
                    null,
                    null));
            minioClient.setBucketLifecycle(SetBucketLifecycleArgs.builder()
                    .bucket(bucket)
                    .config(new LifecycleConfiguration(rules))
                    .build());
            log.info("MinIO 桶 {} 已设置备份保留生命周期规则：prefix={}，当前版本保留 {} 天，非当前版本保留 1 天",
                    bucket, prefix, retentionDays);
            return true;
        } catch (Exception e) {
            log.warn("MinIO 备份保留生命周期规则确保失败(稍后可重试，category={})",
                    classifyLifecycleFailure(e));
            return false;
        }
    }

    private static boolean matchesBackupRetention(LifecycleRule rule, String prefix, int days) {
        return rule != null
                && rule.status() == Status.ENABLED
                && rule.expiration() != null
                && Integer.valueOf(days).equals(rule.expiration().days())
                && rule.noncurrentVersionExpiration() != null
                && rule.noncurrentVersionExpiration().noncurrentDays()
                        == BACKUP_NONCURRENT_EXPIRATION_DAYS
                && rule.filter() != null
                && prefix.equals(rule.filter().prefix());
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return DEFAULT_BACKUP_PREFIX;
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private List<LifecycleRule> currentRules(String bucket) throws Exception {
        LifecycleConfiguration cfg = minioClient.getBucketLifecycle(
                GetBucketLifecycleArgs.builder().bucket(bucket).build());
        if (cfg == null) {
            // MinIO SDK 8.5.12 在服务端明确返回 NoSuchLifecycleConfiguration 时消费异常并返回 null。
            log.debug("MinIO 桶 {} 尚未配置生命周期规则，将创建托管规则", bucket);
            return new ArrayList<>();
        }
        List<LifecycleRule> rules = cfg.rules();
        if (rules == null || rules.isEmpty()) {
            throw new IllegalStateException("MinIO 生命周期配置读取结果不完整");
        }
        return new ArrayList<>(rules);
    }

    private static LifecycleFailureCategory classifyLifecycleFailure(Exception failure) {
        if (failure instanceof ErrorResponseException error) {
            ErrorResponse response = error.errorResponse();
            String code = response == null ? null : response.code();
            if ("AccessDenied".equals(code)) {
                return LifecycleFailureCategory.ACCESS_DENIED;
            }
            if ("NoSuchBucket".equals(code)) {
                return LifecycleFailureCategory.NO_SUCH_BUCKET;
            }
            if ("InternalError".equals(code)
                    || "ServiceUnavailable".equals(code)
                    || "SlowDown".equals(code)
                    || error.response() != null && error.response().code() >= 500) {
                return LifecycleFailureCategory.SERVER_ERROR;
            }
            return LifecycleFailureCategory.INVALID_RESPONSE;
        }
        if (failure instanceof ServerException) {
            return LifecycleFailureCategory.SERVER_ERROR;
        }
        if (failure instanceof XmlParserException) {
            return LifecycleFailureCategory.PARSE;
        }
        if (failure instanceof IOException || failure instanceof InsufficientDataException) {
            return LifecycleFailureCategory.TRANSPORT;
        }
        if (failure instanceof InvalidResponseException) {
            return LifecycleFailureCategory.INVALID_RESPONSE;
        }
        return LifecycleFailureCategory.INVALID_RESPONSE;
    }

    private enum LifecycleFailureCategory {
        ACCESS_DENIED,
        NO_SUCH_BUCKET,
        SERVER_ERROR,
        TRANSPORT,
        PARSE,
        INVALID_RESPONSE
    }
}
