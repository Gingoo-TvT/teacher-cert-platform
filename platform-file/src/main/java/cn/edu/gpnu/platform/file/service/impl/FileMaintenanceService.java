package cn.edu.gpnu.platform.file.service.impl;

import cn.edu.gpnu.platform.file.config.MinioProperties;
import io.minio.GetBucketLifecycleArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import io.minio.messages.AbortIncompleteMultipartUpload;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.LifecycleRule;
import io.minio.messages.RuleFilter;
import io.minio.messages.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
 * 实际 abort 由 MinIO 服务端按规则执行。MinIO 不可达或权限不足时吞异常仅告警、返回 false，绝不打断调度线程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMaintenanceService {

    /** 我们这条生命周期规则的稳定 id（用于幂等识别/更新，不误伤运维手工添加的其它规则） */
    static final String ABORT_RULE_ID = "tcp-abort-incomplete-multipart-uploads";

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
            log.warn("MinIO 桶 {} 未完成分片 abort 生命周期规则设置失败(稍后可重试): {}", bucket, e.getMessage());
            return false;
        }
    }

    private List<LifecycleRule> currentRules(String bucket) {
        try {
            LifecycleConfiguration cfg = minioClient.getBucketLifecycle(
                    GetBucketLifecycleArgs.builder().bucket(bucket).build());
            if (cfg != null && cfg.rules() != null) {
                return new ArrayList<>(cfg.rules());
            }
        } catch (Exception e) {
            // 无生命周期配置(NoSuchLifecycleConfiguration)或读取失败：按空处理，后续 set 新建即可
            log.debug("读取 MinIO 桶 {} 生命周期配置为空/失败: {}", bucket, e.getMessage());
        }
        return new ArrayList<>();
    }
}
