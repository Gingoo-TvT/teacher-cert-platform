package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.file.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * Phase 53（各角色 demo/mock 数据）：仅 TEST/DEV 使用的演示数据装载器。
 *
 * <p><b>默认关闭</b>（与 {@link CleanupScheduleConfig} / BackupScheduleConfig 同款门禁）：由
 * {@code platform.demo.enabled} 门禁——{@code havingValue="true"} 且默认 matchIfMissing=false，故未配置的
 * dev/测试环境本 bean 不注册、演示数据永不装载。<b>ITs 从不设置该开关</b>（config/ 测试资源显式激活 dev），因此
 * {@code mvn verify} 期间本 bean 不存在、演示数据不进库，基于基础种子精确计数的 IT 断言不受任何影响。
 * 启用方式：显式选择 dev 后传 {@code --platform.demo.enabled=true}，或使用
 * {@code SPRING_PROFILES_ACTIVE=dev,demo}（application-demo.yml）。
 *
 * <p>作为 {@link ApplicationRunner} 在上下文刷新之后运行——此时 Flyway 迁移 + db/testseed 已装载、
 * MinIO bucket 已由 {@code MinioConfig#ensureBucket} 确保存在，可安全引用基础种子并写对象。两步均<b>幂等</b>：
 * <ol>
 *   <li>上传 {@code db/demo/} 下的样例文件到 MinIO 固定 object key（先 statObject 探测，已存在则跳过）；</li>
 *   <li>执行 {@code db/demo/demo-data.sql}（全 INSERT ... ON DUPLICATE KEY UPDATE，固定 id 段，可重跑不重复）。</li>
 * </ol>
 * 任一步异常仅告警、不抛出，避免演示数据问题阻断应用启动（应用仍可用于其它测试）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_SQL = "db/demo/demo-data.sql";

    /** 本地样例资源 -> MinIO object key -> content-type（object key 与 demo-data.sql 的 file_path 一一对应）。 */
    private static final List<SampleObject> SAMPLE_OBJECTS = List.of(
            new SampleObject("db/demo/sample-video.mp4", "teaching-video/demo-teaching-video.mp4", "video/mp4"),
            new SampleObject("db/demo/sample-material.pdf", "process-material/demo-material.pdf", "application/pdf"),
            new SampleObject("db/demo/sample-image.png", "process-material/demo-material-image.png", "image/png"),
            new SampleObject("db/demo/sample-material.pdf", "exemption-material/demo-exemption.pdf", "application/pdf"));

    private final DataSource dataSource;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[demo] platform.demo.enabled=true —— 开始装载各角色 demo/mock 数据（TEST/DEV 专用）");
        int uploaded = uploadSampleObjects();
        boolean seeded = seedEntities();
        log.info("[demo] 演示数据装载完成：MinIO 新上传对象 {} 个（已存在则跳过），实体 SQL 执行={}", uploaded, seeded ? "成功" : "失败");
    }

    /** 幂等上传样例文件到 MinIO：先 statObject 探测，已存在则跳过；返回本次新上传的对象数。 */
    private int uploadSampleObjects() {
        String bucket = minioProperties.getBucket();
        int uploaded = 0;
        for (SampleObject obj : SAMPLE_OBJECTS) {
            try {
                if (objectExists(bucket, obj.objectKey())) {
                    log.info("[demo] MinIO 对象已存在，跳过：{}/{}", bucket, obj.objectKey());
                    continue;
                }
                byte[] bytes = new ClassPathResource(obj.resourcePath()).getInputStream().readAllBytes();
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(obj.objectKey())
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(obj.contentType())
                        .build());
                uploaded++;
                log.info("[demo] MinIO 已上传：{}/{}（{} bytes, {}）", bucket, obj.objectKey(), bytes.length, obj.contentType());
            } catch (Exception e) {
                log.error("[demo] MinIO 上传失败：{}/{} —— {}", bucket, obj.objectKey(), e.getMessage());
            }
        }
        return uploaded;
    }

    private boolean objectExists(String bucket, String objectKey) throws Exception {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (ErrorResponseException e) {
            // NoSuchKey -> 不存在（需上传）；其它错误码原样抛出交外层告警。
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw e;
        }
    }

    /** 幂等执行 demo-data.sql（ON DUPLICATE KEY UPDATE，可重跑）。 */
    private boolean seedEntities() {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource(DEMO_SQL));
            populator.setSqlScriptEncoding("UTF-8");
            populator.setSeparator(";");
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("[demo] 已执行演示实体脚本 {}", DEMO_SQL);
            return true;
        } catch (Exception e) {
            log.error("[demo] 执行演示实体脚本 {} 失败：{}", DEMO_SQL, e.getMessage(), e);
            return false;
        }
    }

    private record SampleObject(String resourcePath, String objectKey, String contentType) {
    }
}
