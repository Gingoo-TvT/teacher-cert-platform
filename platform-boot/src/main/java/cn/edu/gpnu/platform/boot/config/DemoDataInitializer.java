package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.business.video.support.VideoMediaAcceptancePolicy;
import cn.edu.gpnu.platform.business.video.support.VideoMediaInspection;
import cn.edu.gpnu.platform.business.video.support.VideoMediaProbe;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.system.service.ParamService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 * MinIO bucket 已确保存在，可安全引用基础种子并写对象。两步均<b>幂等且失败关闭</b>：
 * <ol>
 *   <li>按样例原始 SHA-256 派生不可变 object key：缺失则上传并读回验证，同 key 内容漂移时失败关闭；
 *       旧固定 key 保留，避免对象覆盖与数据库事务之间出现可见性裂缝；</li>
 *   <li>用受信 {@link VideoMediaProbe} 探测已对账的视频对象，把实际大小、服务端指纹、时长和验证元数据
 *       注入 {@code db/demo/demo-data.sql} 后再执行。</li>
 * </ol>
 * 内容版本对象在事务前只会新增，不会覆盖旧引用；SQL 失败最多留下无引用对象，数据库仍指向旧版本。
 * 多实例写入同一审核内容是等价幂等写，实体引用的可见性切换由单个数据库事务完成。
 * 任一对象无法确认一致、媒体探测或 SQL 失败时，实体 SQL 不执行或事务回滚，并终止 demo-enabled 应用启动。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_SQL = "db/demo/demo-data.sql";
    private static final String DEMO_TOKEN_PREFIX = "{{DEMO_";
    private static final long EXPECTED_VIDEO_SIZE = 1_605_702L;
    private static final String EXPECTED_VIDEO_SHA256 =
            "0a15c2e382438f3fbbd1e9b5302514ad35da07b33b44f7eb61fbb5c8b31d611d";
    private static final long EXPECTED_MATERIAL_SIZE = 659L;
    private static final String EXPECTED_MATERIAL_SHA256 =
            "53aece50aefaf8236338f0ddada8469e54d1422d26da0a975a2f81c873ba9268";
    private static final long EXPECTED_IMAGE_SIZE = 99L;
    private static final String EXPECTED_IMAGE_SHA256 =
            "c589a04293ea064c50a46e8fec832e6920b28ea3cf3718aed18b929f9fb631ee";
    private static final String VIDEO_OBJECT_KEY =
            "teaching-video/demo/" + EXPECTED_VIDEO_SHA256 + ".mp4";
    private static final String MATERIAL_OBJECT_KEY =
            "process-material/demo/" + EXPECTED_MATERIAL_SHA256 + ".pdf";
    private static final String IMAGE_OBJECT_KEY =
            "process-material/demo/" + EXPECTED_IMAGE_SHA256 + ".png";
    private static final String EXEMPTION_OBJECT_KEY =
            "exemption-material/demo/" + EXPECTED_MATERIAL_SHA256 + ".pdf";
    private static final String EXPECTED_VIDEO_FINGERPRINT =
            "ee0f34bb9c2cdaa567cb1195b957f28d56c6279f972b9b3afe1c4b235ab21f86";
    private static final int EXPECTED_VIDEO_DURATION_SECONDS = 900;
    private static final int EXPECTED_VIDEO_FRAME_COUNT = 900;
    private static final String EXPECTED_VIDEO_CODEC = "H264";
    private static final long MAX_DEMO_RESOURCE_BYTES = 16L * 1024 * 1024;
    /** 本地样例资源 -> MinIO object key -> content-type（object key 与 demo-data.sql 的 file_path 一一对应）。 */
    private static final List<SampleObject> SAMPLE_OBJECTS = List.of(
            new SampleObject(
                    "db/demo/sample-video.mp4",
                    VIDEO_OBJECT_KEY,
                    "video/mp4",
                    true,
                    EXPECTED_VIDEO_SIZE,
                    EXPECTED_VIDEO_SHA256),
            new SampleObject("db/demo/sample-material.pdf",
                    MATERIAL_OBJECT_KEY, "application/pdf", false,
                    EXPECTED_MATERIAL_SIZE, EXPECTED_MATERIAL_SHA256),
            new SampleObject("db/demo/sample-image.png",
                    IMAGE_OBJECT_KEY, "image/png", false,
                    EXPECTED_IMAGE_SIZE, EXPECTED_IMAGE_SHA256),
            new SampleObject("db/demo/sample-material.pdf",
                    EXEMPTION_OBJECT_KEY, "application/pdf", false,
                    EXPECTED_MATERIAL_SIZE, EXPECTED_MATERIAL_SHA256));

    private final DataSource dataSource;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final VideoMediaProbe videoMediaProbe;
    private final ParamService paramService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[demo] platform.demo.enabled=true —— 开始装载各角色 demo/mock 数据（TEST/DEV 专用）");
        try {
            ReconcileSummary summary = reconcileSampleObjects(minioProperties.getBucket());
            seedEntities(summary);
            log.info("[demo] 演示数据装载完成：内容版本对象新建 {}、一致跳过 {}，实体 SQL 执行=成功",
                    summary.created(), summary.unchanged());
        } catch (Exception e) {
            log.error("[demo] 演示数据初始化失败，启动已终止（category={}）",
                    e.getClass().getSimpleName());
            // 不挂原始 cause，避免对象存储/数据库异常消息经启动栈泄露端点、路径或 SQL。
            throw new IllegalStateException("演示数据初始化失败，已终止启动");
        }
    }

    /**
     * 对账全部演示对象，并在对象一致后用生产媒体探测器生成视频元数据单一真源。
     */
    ReconcileSummary reconcileSampleObjects(String bucket) throws Exception {
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException("演示对象桶配置为空");
        }
        int created = 0;
        int unchanged = 0;
        PreparedSample video = null;
        List<PreparedSample> preparedSamples = new ArrayList<>();
        Map<String, DemoObjectManifest> objectManifests = new LinkedHashMap<>();
        for (SampleObject sample : SAMPLE_OBJECTS) {
            preparedSamples.add(prepare(sample));
        }
        for (PreparedSample prepared : preparedSamples) {
            SampleObject sample = prepared.sample();
            ObjectState before = objectState(bucket, prepared);
            if (before == ObjectState.MATCH) {
                unchanged++;
                log.info("[demo] MinIO 内容版本对象一致，跳过：{}/{}", bucket, sample.objectKey());
            } else if (before == ObjectState.MISSING) {
                putObject(bucket, prepared);
                ObjectState after = objectState(bucket, prepared);
                if (after != ObjectState.MATCH) {
                    throw new IllegalStateException("演示对象写入后无法确认一致");
                }
                created++;
                log.info("[demo] MinIO 内容版本对象已新建并验证：{}/{}", bucket, sample.objectKey());
            } else {
                // object key 由审核资源的原始 SHA-256 派生；同 key 内容不同表示受管命名空间被污染。
                throw new IllegalStateException("演示内容版本对象与已审核资源不一致");
            }
            if (sample.video()) {
                video = prepared;
            }
            objectManifests.put(sample.objectKey(), new DemoObjectManifest(
                    sample.objectKey(),
                    sample.contentType(),
                    prepared.bytes().length,
                    HexFormat.of().formatHex(prepared.md5())));
        }
        if (video == null) {
            throw new IllegalStateException("演示视频资源未配置");
        }
        DemoVideoManifest manifest = inspectVideo(bucket, video);
        for (PreparedSample prepared : preparedSamples) {
            if (objectState(bucket, prepared) != ObjectState.MATCH) {
                throw new IllegalStateException("媒体探测后演示对象发生变化");
            }
        }
        return new ReconcileSummary(
                manifest, Map.copyOf(objectManifests), created, unchanged);
    }

    private PreparedSample prepare(SampleObject sample) throws IOException {
        byte[] bytes;
        try (InputStream input = new ClassPathResource(sample.resourcePath()).getInputStream()) {
            bytes = input.readNBytes(Math.toIntExact(MAX_DEMO_RESOURCE_BYTES + 1));
        }
        if (bytes.length == 0 || bytes.length > MAX_DEMO_RESOURCE_BYTES) {
            throw new IllegalStateException("演示样例资源大小不合法");
        }
        byte[] sha256 = digest("SHA-256", bytes);
        if (bytes.length != sample.expectedSize()
                || !sample.expectedSha256().equals(HexFormat.of().formatHex(sha256))) {
            throw new IllegalStateException("演示样例资源与已审核 manifest 不一致");
        }
        return new PreparedSample(sample, bytes, sha256, digest("MD5", bytes));
    }

    private ObjectState objectState(String bucket, PreparedSample prepared) throws Exception {
        SampleObject sample = prepared.sample();
        StatObjectResponse stat;
        try {
            stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(sample.objectKey())
                    .build());
        } catch (ErrorResponseException e) {
            if (isMissingObject(e)) {
                return ObjectState.MISSING;
            }
            throw e;
        }
        if (stat.size() != prepared.bytes().length
                || !sample.contentType().equalsIgnoreCase(stat.contentType())) {
            return ObjectState.MISMATCH;
        }
        DigestResult actual;
        try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(sample.objectKey())
                .build())) {
            actual = sha256(input);
        }
        return actual.size() == prepared.bytes().length
                && MessageDigest.isEqual(actual.digest(), prepared.sha256())
                ? ObjectState.MATCH
                : ObjectState.MISMATCH;
    }

    private boolean isMissingObject(ErrorResponseException exception) {
        if (exception.errorResponse() == null) {
            return false;
        }
        String code = exception.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
    }

    private void putObject(String bucket, PreparedSample prepared) throws Exception {
        SampleObject sample = prepared.sample();
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(sample.objectKey())
                .stream(new ByteArrayInputStream(prepared.bytes()), prepared.bytes().length, -1)
                .contentType(sample.contentType())
                .build());
    }

    private DemoVideoManifest inspectVideo(String bucket, PreparedSample video) {
        VideoMediaInspection inspection = videoMediaProbe.inspect(
                video.sample().objectKey(), video.bytes().length, EXPECTED_VIDEO_FINGERPRINT);
        if (!inspection.valid()
                || !EXPECTED_VIDEO_FINGERPRINT.equals(inspection.fingerprint())
                || !Integer.valueOf(EXPECTED_VIDEO_DURATION_SECONDS)
                        .equals(inspection.durationSeconds())
                || !EXPECTED_VIDEO_CODEC.equals(inspection.codec())
                || !Integer.valueOf(EXPECTED_VIDEO_FRAME_COUNT).equals(inspection.frameCount())
                || !isLowerHex64(inspection.policyHash())
                || !StringUtils.hasText(inspection.probeVersion())
                || !inspection.probeVersion().equals(videoMediaProbe.probeVersion())
                || !inspection.policyHash().equals(videoMediaProbe.currentPolicyHash())) {
            throw new IllegalStateException("演示视频未通过受信媒体探测");
        }
        VideoMediaAcceptancePolicy.Result acceptance = VideoMediaAcceptancePolicy.validate(
                video.sample().contentType(),
                (long) video.bytes().length,
                inspection,
                videoMediaProbe,
                paramService);
        if (!acceptance.accepted()) {
            throw new IllegalStateException("演示视频不满足当前业务媒体策略");
        }
        return new DemoVideoManifest(
                bucket,
                video.sample().objectKey(),
                video.sample().contentType(),
                video.bytes().length,
                inspection.fingerprint().toLowerCase(Locale.ROOT),
                inspection.durationSeconds(),
                VideoMediaProbe.CHECKSUM_ALGORITHM,
                inspection.codec(),
                inspection.policyHash().toLowerCase(Locale.ROOT),
                inspection.probeVersion());
    }

    private boolean isLowerHex64(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    /** 幂等执行 demo-data.sql（ON DUPLICATE KEY UPDATE，可重跑）。 */
    void seedEntities(ReconcileSummary summary) {
        try {
            String renderedSql = renderDemoSql(summary);
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ByteArrayResource(renderedSql.getBytes(StandardCharsets.UTF_8)));
            populator.setSqlScriptEncoding("UTF-8");
            populator.setSeparator(";");
            populator.setContinueOnError(false);
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> populator.execute(dataSource));
            log.info("[demo] 已执行演示实体脚本 {}", DEMO_SQL);
        } catch (Exception e) {
            log.error("[demo] 执行演示实体脚本 {} 失败（category={}）",
                    DEMO_SQL, e.getClass().getSimpleName());
            throw new IllegalStateException("演示实体 SQL 执行失败");
        }
    }

    String renderDemoSql(ReconcileSummary summary) throws IOException {
        DemoVideoManifest manifest = summary.videoManifest();
        DemoObjectManifest material = requiredObject(summary, MATERIAL_OBJECT_KEY);
        DemoObjectManifest image = requiredObject(summary, IMAGE_OBJECT_KEY);
        DemoObjectManifest exemption = requiredObject(summary, EXEMPTION_OBJECT_KEY);
        String sql = new ClassPathResource(DEMO_SQL).getContentAsString(StandardCharsets.UTF_8);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("{{DEMO_BUCKET}}", sqlLiteral(manifest.bucket()));
        values.put("{{DEMO_VIDEO_OBJECT_KEY}}", sqlLiteral(manifest.objectKey()));
        values.put("{{DEMO_VIDEO_CONTENT_TYPE}}", sqlLiteral(manifest.contentType()));
        values.put("{{DEMO_VIDEO_SIZE}}", Long.toString(manifest.size()));
        values.put("{{DEMO_VIDEO_FINGERPRINT}}", sqlLiteral(manifest.fingerprint()));
        values.put("{{DEMO_VIDEO_DURATION_SECONDS}}", Integer.toString(manifest.durationSeconds()));
        values.put("{{DEMO_VIDEO_CHECKSUM_ALGORITHM}}", sqlLiteral(manifest.checksumAlgorithm()));
        values.put("{{DEMO_VIDEO_CODEC}}", sqlLiteral(manifest.codec()));
        values.put("{{DEMO_VIDEO_POLICY_HASH}}", sqlLiteral(manifest.policyHash()));
        values.put("{{DEMO_VIDEO_PROBE_VERSION}}", sqlLiteral(manifest.probeVersion()));
        addObjectTokens(values, "MATERIAL", material);
        addObjectTokens(values, "IMAGE", image);
        addObjectTokens(values, "EXEMPTION", exemption);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!sql.contains(entry.getKey())) {
                throw new IllegalStateException(
                        "演示 SQL 缺少必需元数据占位符 " + entry.getKey());
            }
            sql = sql.replace(entry.getKey(), entry.getValue());
        }
        if (sql.contains(DEMO_TOKEN_PREFIX)) {
            throw new IllegalStateException("演示 SQL 存在未解析元数据占位符");
        }
        return sql;
    }

    private DemoObjectManifest requiredObject(ReconcileSummary summary, String objectKey) {
        DemoObjectManifest manifest = summary.objectManifests().get(objectKey);
        if (manifest == null) {
            throw new IllegalStateException("演示对象 manifest 缺失");
        }
        return manifest;
    }

    private void addObjectTokens(
            Map<String, String> values, String prefix, DemoObjectManifest manifest) {
        values.put("{{DEMO_" + prefix + "_OBJECT_KEY}}", sqlLiteral(manifest.objectKey()));
        values.put("{{DEMO_" + prefix + "_CONTENT_TYPE}}", sqlLiteral(manifest.contentType()));
        values.put("{{DEMO_" + prefix + "_SIZE}}", Long.toString(manifest.size()));
        values.put("{{DEMO_" + prefix + "_MD5}}", sqlLiteral(manifest.md5()));
    }

    private String sqlLiteral(String value) {
        if (value == null) {
            throw new IllegalArgumentException("演示 SQL 元数据不能为空");
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private byte[] digest(String algorithm, byte[] bytes) {
        return digest(algorithm).digest(bytes);
    }

    private DigestResult sha256(InputStream input) throws IOException {
        MessageDigest digest = digest("SHA-256");
        long size = 0L;
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read == 0) {
                continue;
            }
            size += read;
            digest.update(buffer, 0, read);
        }
        return new DigestResult(size, digest.digest());
    }

    private MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM不支持摘要算法", e);
        }
    }

    private record SampleObject(
            String resourcePath,
            String objectKey,
            String contentType,
            boolean video,
            long expectedSize,
            String expectedSha256) {
    }

    private record PreparedSample(
            SampleObject sample,
            byte[] bytes,
            byte[] sha256,
            byte[] md5) {
    }

    private record DigestResult(long size, byte[] digest) {
    }

    private enum ObjectState {
        MISSING,
        MATCH,
        MISMATCH
    }

    record DemoVideoManifest(
            String bucket,
            String objectKey,
            String contentType,
            long size,
            String fingerprint,
            int durationSeconds,
            String checksumAlgorithm,
            String codec,
            String policyHash,
            String probeVersion) {
    }

    record DemoObjectManifest(
            String objectKey,
            String contentType,
            long size,
            String md5) {
    }

    record ReconcileSummary(
            DemoVideoManifest videoManifest,
            Map<String, DemoObjectManifest> objectManifests,
            int created,
            int unchanged) {
    }

}
