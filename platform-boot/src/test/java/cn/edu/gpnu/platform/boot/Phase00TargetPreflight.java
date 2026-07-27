package cn.edu.gpnu.platform.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 真实依赖的写前只读身份预检。
 *
 * <p>类名故意不以 {@code Test}/{@code IT} 结尾，普通 Surefire/Failsafe 生命周期不会自动发现它。
 * Phase 0 gate 在正式 {@code clean verify} 前用 {@code -Dit.test=Phase00TargetPreflight}
 * 单独执行；任何身份不符都会在 Flyway 或 MinIO bucket initializer 启动前失败。
 */
class Phase00TargetPreflight {

    private static final int IDENTITY_SCHEMA_VERSION = 5;
    private static final int PROVISIONING_OBJECT_SCHEMA_VERSION = 2;
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);
    private static final Path PREFLIGHT_IDENTITY_PATH =
            Path.of("target", "phase00-target-preflight.json").toAbsolutePath().normalize();
    private static final Pattern MYSQL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern REDIS_RUN_ID = Pattern.compile("^[0-9a-fA-F]{40}$");
    private static final Pattern REDIS_CLIENT_DB = Pattern.compile("(?:^|\\s)db=(\\d+)(?:\\s|$)");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Timeout(value = 45)
    void attestConfiguredTargetsBeforeAnyApplicationContextWrite() throws Exception {
        writeIdentity(attestBeforeContextRefresh(), PREFLIGHT_IDENTITY_PATH);
    }

    /**
     * 供正式 Spring JVM 的 {@link Phase00TargetGuardInitializer} 在 context refresh / Flyway
     * 之前复用。返回值只在全部输入校验和三类只读身份校验均成功后创建。
     */
    static TargetAttestation attestBeforeContextRefresh() throws Exception {
        return new Phase00TargetPreflight().attestConfiguredTargets(null);
    }

    /**
     * 同一个正式 Failsafe JVM 的后续 Spring Context 必须重验实际服务身份，但不能再次要求
     * MySQL/Redis/MinIO 为空：第一个 Context 已经执行 Flyway 和验收写入。freshness 只允许
     * 从该 JVM 第一次 refresh 前的 attestation 继承。
     */
    static TargetAttestation reattestBeforeContextRefresh(TargetAttestation firstAttestation)
            throws Exception {
        if (firstAttestation == null) {
            throw new IllegalArgumentException("首次 Phase 0 target attestation 不能为空");
        }
        return new Phase00TargetPreflight().attestConfiguredTargets(firstAttestation);
    }

    private TargetAttestation attestConfiguredTargets(TargetAttestation firstAttestation)
            throws Exception {
        Inputs inputs = readAndValidateInputs();
        boolean requireFreshness = firstAttestation == null;

        MysqlIdentity mysql = readMysqlIdentity(inputs, requireFreshness);
        RedisIdentity redis = readRedisIdentity(inputs, requireFreshness);
        MinioIdentity minio = readMinioIdentity(inputs, requireFreshness);

        assertThat(mysql.database()).isEqualTo(inputs.schema());
        assertThat(mysql.version()).matches("^8\\..+");
        assertThat(mysql.serverUuid()).isEqualTo(inputs.expectedMysqlServerUuid());
        assertThat(redis.version()).matches("^7\\..+");
        assertThat(redis.runId()).isEqualTo(inputs.expectedRedisRunId());
        assertThat(redis.database()).isEqualTo(inputs.redisDatabase());

        if (firstAttestation == null) {
            validateFreshness(
                    mysql.tableCountBefore(),
                    redis.databaseSizeBefore(),
                    minio.objectCountBefore(),
                    minio.unexpectedObjectCountBefore());
        }

        TargetAttestation attestation = new TargetAttestation(
                inputs.candidateSha(),
                inputs.runContext(),
                mysql.database(),
                mysql.version(),
                mysql.serverUuid(),
                redis.version(),
                redis.runId(),
                redis.database(),
                minio.endpoint(),
                minio.bucket(),
                minio.identityObject(),
                minio.identitySha256(),
                minio.identityNonce(),
                minio.identityIssuedAt(),
                firstAttestation == null
                        ? mysql.tableCountBefore()
                        : firstAttestation.mysqlTableCountBefore(),
                firstAttestation == null
                        ? redis.databaseSizeBefore()
                        : firstAttestation.redisDatabaseSizeBefore(),
                firstAttestation == null
                        ? minio.objectCountBefore()
                        : firstAttestation.minioObjectCountBefore(),
                firstAttestation == null
                        ? minio.unexpectedObjectCountBefore()
                        : firstAttestation.minioUnexpectedObjectCountBefore());
        if (firstAttestation != null) {
            assertSameTarget(firstAttestation, attestation);
        }
        return attestation;
    }

    static void validateFreshness(
            long mysqlTableCountBefore,
            long redisDatabaseSizeBefore,
            long minioObjectCountBefore,
            long minioUnexpectedObjectCountBefore) {
        if (mysqlTableCountBefore != 0L) {
            throw new IllegalStateException(
                    "Phase 0 MySQL schema 在首次 context refresh 前必须为空");
        }
        if (redisDatabaseSizeBefore != 0L) {
            throw new IllegalStateException(
                    "Phase 0 Redis 选定 DB 在首次 context refresh 前必须为空");
        }
        if (minioObjectCountBefore != 1L) {
            throw new IllegalStateException(
                    "Phase 0 MinIO bucket 在首次 context refresh 前只能有 provisioning identity");
        }
        if (minioUnexpectedObjectCountBefore != 0L) {
            throw new IllegalStateException(
                    "Phase 0 MinIO bucket 在首次 context refresh 前不得有其它对象");
        }
    }

    private Inputs readAndValidateInputs() {
        String candidateSha = requiredEnvironment("PHASE00_EXPECTED_CANDIDATE_SHA")
                .toLowerCase(java.util.Locale.ROOT);
        assertThat(candidateSha).matches("^[0-9a-f]{40}$");
        String runContext = requiredEnvironment("PHASE00_RUN_CONTEXT");
        assertThat(runContext).hasSizeBetween(1, 256);

        String schema = requiredEnvironment("PHASE00_TARGET_SCHEMA");
        assertThat(schema).matches("^[A-Za-z0-9_]{1,64}$");
        String jdbcUrl = requiredEnvironment("SPRING_DATASOURCE_URL");
        String mysqlUsername = requiredEnvironment("SPRING_DATASOURCE_USERNAME");
        String mysqlPassword = requiredEnvironmentAllowEmpty("SPRING_DATASOURCE_PASSWORD");
        String expectedMysqlServerUuid = requiredEnvironment("PHASE00_EXPECTED_MYSQL_SERVER_UUID")
                .toLowerCase(java.util.Locale.ROOT);
        assertThat(expectedMysqlServerUuid).matches(MYSQL_UUID);

        String redisHost = requiredEnvironment("SPRING_DATA_REDIS_HOST");
        int redisPort = parsePort(requiredEnvironment("SPRING_DATA_REDIS_PORT"),
                "SPRING_DATA_REDIS_PORT");
        int redisDatabase = parseNonNegativeInt(
                requiredEnvironment("SPRING_DATA_REDIS_DATABASE"),
                "SPRING_DATA_REDIS_DATABASE");
        String redisUsername = optionalEnvironment("SPRING_DATA_REDIS_USERNAME");
        String redisPassword = optionalEnvironment("SPRING_DATA_REDIS_PASSWORD");
        if (redisUsername != null && redisPassword == null) {
            throw new IllegalStateException(
                    "SPRING_DATA_REDIS_USERNAME 存在时必须同时设置 SPRING_DATA_REDIS_PASSWORD");
        }
        String expectedRedisRunId = requiredEnvironment("PHASE00_EXPECTED_REDIS_RUN_ID")
                .toLowerCase(java.util.Locale.ROOT);
        assertThat(expectedRedisRunId).matches(REDIS_RUN_ID);

        String minioEndpoint = canonicalEndpoint(requiredEnvironment("MINIO_ENDPOINT"));
        String minioAccessKey = requiredEnvironment("MINIO_ACCESS_KEY");
        String minioSecretKey = requiredEnvironment("MINIO_SECRET_KEY");
        String minioBucket = requiredEnvironment("MINIO_BUCKET");
        assertThat(minioBucket).matches("^[A-Za-z0-9][A-Za-z0-9.-]{1,62}$");
        String identityObject = requiredEnvironment("PHASE00_MINIO_IDENTITY_OBJECT");
        assertThat(identityObject)
                .startsWith(".phase00-target/")
                .doesNotContain("..", "\\", "%");
        String identitySha256 = requiredEnvironment("PHASE00_EXPECTED_MINIO_IDENTITY_SHA256")
                .toLowerCase(java.util.Locale.ROOT);
        String identityNonce = requiredEnvironment("PHASE00_EXPECTED_MINIO_IDENTITY_NONCE")
                .toLowerCase(java.util.Locale.ROOT);
        assertThat(identitySha256).matches(SHA256);
        assertThat(identityNonce).matches(SHA256);
        String identityIssuedAt =
                requiredEnvironment("PHASE00_EXPECTED_MINIO_IDENTITY_ISSUED_AT");
        assertThat(Instant.parse(identityIssuedAt).toString()).isEqualTo(identityIssuedAt);

        return new Inputs(
                candidateSha,
                runContext,
                schema,
                jdbcUrl,
                mysqlUsername,
                mysqlPassword,
                expectedMysqlServerUuid,
                redisHost,
                redisPort,
                redisDatabase,
                redisUsername,
                redisPassword,
                expectedRedisRunId,
                minioEndpoint,
                minioAccessKey,
                minioSecretKey,
                minioBucket,
                identityObject,
                identitySha256,
                identityNonce,
                identityIssuedAt);
    }

    private MysqlIdentity readMysqlIdentity(Inputs inputs, boolean requireFreshness)
            throws Exception {
        DriverManager.setLoginTimeout((int) IO_TIMEOUT.toSeconds());
        Properties properties = new Properties();
        properties.setProperty("user", inputs.mysqlUsername());
        properties.setProperty("password", inputs.mysqlPassword());
        properties.setProperty("connectTimeout", Long.toString(IO_TIMEOUT.toMillis()));
        properties.setProperty("socketTimeout", Long.toString(IO_TIMEOUT.toMillis()));
        try (Connection connection = DriverManager.getConnection(inputs.jdbcUrl(), properties)) {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout((int) IO_TIMEOUT.toSeconds());
                String sql = requireFreshness
                        ? """
                        SELECT DATABASE() AS database_name,
                               VERSION() AS server_version,
                               @@GLOBAL.server_uuid AS server_uuid,
                               (
                                   SELECT COUNT(*)
                                   FROM information_schema.tables
                                   WHERE table_schema = DATABASE()
                               ) AS table_count_before
                        """
                        : """
                        SELECT DATABASE() AS database_name,
                               VERSION() AS server_version,
                               @@GLOBAL.server_uuid AS server_uuid
                        """;
                try (ResultSet result = statement.executeQuery(sql)) {
                    assertThat(result.next()).isTrue();
                    long tableCountBefore = requireFreshness
                            ? result.getLong("table_count_before")
                            : -1L;
                    if (requireFreshness) {
                        assertThat(result.wasNull())
                                .as("MySQL information_schema table count 不能为空")
                                .isFalse();
                    }
                    MysqlIdentity identity = new MysqlIdentity(
                            requiredText(result.getString("database_name"), "MySQL DATABASE()"),
                            requiredText(result.getString("server_version"), "MySQL VERSION()"),
                            requiredText(result.getString("server_uuid"), "MySQL server_uuid")
                                    .toLowerCase(java.util.Locale.ROOT),
                            tableCountBefore);
                    assertThat(result.next()).isFalse();
                    return identity;
                }
            }
        }
    }

    private RedisIdentity readRedisIdentity(Inputs inputs, boolean requireFreshness) {
        RedisURI.Builder uri = RedisURI.Builder.redis(inputs.redisHost(), inputs.redisPort())
                .withDatabase(inputs.redisDatabase())
                .withTimeout(IO_TIMEOUT);
        if (inputs.redisUsername() != null) {
            uri.withAuthentication(inputs.redisUsername(), inputs.redisPassword().toCharArray());
        } else if (inputs.redisPassword() != null) {
            uri.withPassword(inputs.redisPassword().toCharArray());
        }

        RedisClient client = RedisClient.create(uri.build());
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(IO_TIMEOUT)
                        .build())
                .build());
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            Map<String, String> info = parseRedisInfo(commands.info("server"));
            String clientInfo = requiredText(commands.clientInfo(), "Redis CLIENT INFO");
            Matcher matcher = REDIS_CLIENT_DB.matcher(clientInfo);
            if (!matcher.find()) {
                throw new IllegalStateException("Redis CLIENT INFO 缺少实际 db");
            }
            long databaseSize = -1L;
            if (requireFreshness) {
                Long measuredSize = commands.dbsize();
                if (measuredSize == null || measuredSize < 0) {
                    throw new IllegalStateException("Redis DBSIZE 返回非法结果");
                }
                databaseSize = measuredSize;
            }
            return new RedisIdentity(
                    requiredText(info.get("redis_version"), "Redis redis_version"),
                    requiredText(info.get("run_id"), "Redis run_id")
                            .toLowerCase(java.util.Locale.ROOT),
                    parseNonNegativeInt(matcher.group(1), "Redis CLIENT INFO db"),
                    databaseSize);
        } finally {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(5));
        }
    }

    private MinioIdentity readMinioIdentity(Inputs inputs, boolean requireFreshness)
            throws Exception {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(IO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(IO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(IO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(IO_TIMEOUT.plusSeconds(5).toMillis(), TimeUnit.MILLISECONDS)
                .build();
        try (MinioClient client = MinioClient.builder()
                .endpoint(inputs.minioEndpoint())
                .credentials(inputs.minioAccessKey(), inputs.minioSecretKey())
                .httpClient(httpClient)
                .build()) {
            long objectCount = -1L;
            long unexpectedObjectCount = -1L;
            if (requireFreshness) {
                objectCount = 0L;
                unexpectedObjectCount = 0L;
                for (var listed : client.listObjects(ListObjectsArgs.builder()
                        .bucket(inputs.minioBucket())
                        .recursive(true)
                        .includeVersions(true)
                        .build())) {
                    var item = listed.get();
                    String objectName = requiredText(
                            item.objectName(), "MinIO listed object name");
                    objectCount++;
                    if (!objectName.equals(inputs.identityObject())
                            || item.isDir()
                            || item.isDeleteMarker()) {
                        unexpectedObjectCount++;
                        throw new IllegalStateException(
                                "Phase 0 MinIO bucket 在首次 refresh 前存在非 identity 对象");
                    }
                    if (objectCount > 1L) {
                        throw new IllegalStateException(
                                "Phase 0 MinIO bucket 在首次 refresh 前存在多个对象");
                    }
                }
            }

            try (GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                        .bucket(inputs.minioBucket())
                        .object(inputs.identityObject())
                        .build())) {
                byte[] content = response.readNBytes(4097);
                if (content.length > 4096) {
                    throw new IllegalStateException(
                            "MinIO provisioning identity object 超过 4096 bytes");
                }
                String actualSha256 = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(content));
                assertThat(actualSha256).isEqualTo(inputs.identitySha256());
                validateProvisioningObject(content, inputs);

                return new MinioIdentity(
                        inputs.minioEndpoint(),
                        inputs.minioBucket(),
                        inputs.identityObject(),
                        actualSha256,
                        inputs.identityNonce(),
                        inputs.identityIssuedAt(),
                        objectCount,
                        unexpectedObjectCount);
            }
        } finally {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    private void validateProvisioningObject(byte[] content, Inputs inputs) throws Exception {
        JsonNode provisioning = OBJECT_MAPPER.readTree(content);
        assertThat(provisioning.isObject()).isTrue();
        Set<String> fields = new java.util.HashSet<>();
        provisioning.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
                "schemaVersion",
                "candidateSha",
                "runContext",
                "identityNonce",
                "identityIssuedAt");
        assertThat(provisioning.path("schemaVersion").asInt())
                .isEqualTo(PROVISIONING_OBJECT_SCHEMA_VERSION);
        assertThat(provisioning.path("candidateSha").asText())
                .isEqualTo(inputs.candidateSha());
        assertThat(provisioning.path("runContext").asText()).isEqualTo(inputs.runContext());
        assertThat(provisioning.path("identityNonce").asText())
                .isEqualTo(inputs.identityNonce());
        assertThat(provisioning.path("identityIssuedAt").asText())
                .isEqualTo(inputs.identityIssuedAt());
    }

    private void writeIdentity(TargetAttestation attestation, Path destination) throws Exception {
        Map<String, Object> root = identityDocument(
                Phase00TargetPreflight.class.getName(), attestation);

        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(
                destination.getFileName() + ".tmp-" + Long.toUnsignedString(System.nanoTime()));
        byte[] json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        try {
            Files.write(temporary, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Map<String, Object> identityDocument(
            String producerSuite, TargetAttestation attestation) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", IDENTITY_SCHEMA_VERSION);
        root.put("producerSuite", producerSuite);
        root.put("candidateSha", attestation.candidateSha());
        root.put("runContext", attestation.runContext());

        Map<String, Object> freshnessJson = new LinkedHashMap<>();
        freshnessJson.put("mysqlTableCountBefore", attestation.mysqlTableCountBefore());
        freshnessJson.put("redisDatabaseSizeBefore", attestation.redisDatabaseSizeBefore());
        freshnessJson.put("minioObjectCountBefore", attestation.minioObjectCountBefore());
        freshnessJson.put(
                "minioUnexpectedObjectCountBefore",
                attestation.minioUnexpectedObjectCountBefore());
        root.put("freshness", freshnessJson);

        Map<String, Object> mysqlJson = new LinkedHashMap<>();
        mysqlJson.put("database", attestation.mysqlDatabase());
        mysqlJson.put("version", attestation.mysqlVersion());
        mysqlJson.put("serverUuid", attestation.mysqlServerUuid());
        root.put("mysql", mysqlJson);

        Map<String, Object> redisJson = new LinkedHashMap<>();
        redisJson.put("version", attestation.redisVersion());
        redisJson.put("runId", attestation.redisRunId());
        redisJson.put("database", attestation.redisDatabase());
        root.put("redis", redisJson);

        Map<String, Object> minioJson = new LinkedHashMap<>();
        minioJson.put("endpoint", attestation.minioEndpoint());
        minioJson.put("bucket", attestation.minioBucket());
        minioJson.put("identityObject", attestation.minioIdentityObject());
        minioJson.put("identitySha256", attestation.minioIdentitySha256());
        minioJson.put("identityNonce", attestation.minioIdentityNonce());
        minioJson.put("identityIssuedAt", attestation.minioIdentityIssuedAt());
        root.put("minio", minioJson);

        return root;
    }

    private void assertSameTarget(
            TargetAttestation firstAttestation, TargetAttestation currentAttestation) {
        assertThat(currentAttestation.candidateSha())
                .as("后续 Context 的 candidate SHA 必须与首次 attestation 一致")
                .isEqualTo(firstAttestation.candidateSha());
        assertThat(currentAttestation.runContext())
                .as("后续 Context 的 runContext 必须与首次 attestation 一致")
                .isEqualTo(firstAttestation.runContext());
        assertThat(currentAttestation.mysqlDatabase())
                .isEqualTo(firstAttestation.mysqlDatabase());
        assertThat(currentAttestation.mysqlVersion())
                .isEqualTo(firstAttestation.mysqlVersion());
        assertThat(currentAttestation.mysqlServerUuid())
                .isEqualTo(firstAttestation.mysqlServerUuid());
        assertThat(currentAttestation.redisVersion())
                .isEqualTo(firstAttestation.redisVersion());
        assertThat(currentAttestation.redisRunId())
                .isEqualTo(firstAttestation.redisRunId());
        assertThat(currentAttestation.redisDatabase())
                .isEqualTo(firstAttestation.redisDatabase());
        assertThat(currentAttestation.minioEndpoint())
                .isEqualTo(firstAttestation.minioEndpoint());
        assertThat(currentAttestation.minioBucket())
                .isEqualTo(firstAttestation.minioBucket());
        assertThat(currentAttestation.minioIdentityObject())
                .isEqualTo(firstAttestation.minioIdentityObject());
        assertThat(currentAttestation.minioIdentitySha256())
                .isEqualTo(firstAttestation.minioIdentitySha256());
        assertThat(currentAttestation.minioIdentityNonce())
                .isEqualTo(firstAttestation.minioIdentityNonce());
        assertThat(currentAttestation.minioIdentityIssuedAt())
                .isEqualTo(firstAttestation.minioIdentityIssuedAt());
    }

    static Map<String, String> parseRedisInfo(String value) {
        String text = requiredRedisInfoPayload(value);
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String line : text.split("\\r?\\n")) {
            String cleanLine = cleanText(line, "Redis INFO server 行");
            if (cleanLine.isEmpty() || cleanLine.startsWith("#")) {
                continue;
            }
            int separator = cleanLine.indexOf(':');
            if (separator <= 0) {
                throw new IllegalStateException("Redis INFO server 行格式非法");
            }
            String key = cleanText(
                    cleanLine.substring(0, separator), "Redis INFO server key");
            String parsedValue = cleanText(
                    cleanLine.substring(separator + 1), "Redis INFO server value");
            if (parsed.putIfAbsent(key, parsedValue) != null) {
                throw new IllegalStateException("Redis INFO server 包含重复 key");
            }
        }
        return parsed;
    }

    /**
     * Redis INFO 是 CRLF/LF 分隔的协议载荷，不能套用仅允许单行值的 requiredText。
     * 整段只做空值与 NUL 防护，拆行后再由 cleanText 拒绝行内控制字符。
     */
    private static String requiredRedisInfoPayload(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Redis INFO server 不能为空");
        }
        if (value.indexOf('\u0000') >= 0) {
            throw new IllegalStateException("Redis INFO server 含控制字符");
        }
        return value;
    }

    private String canonicalEndpoint(String raw) {
        URI uri = URI.create(requiredText(raw, "MinIO endpoint"));
        String scheme = uri.getScheme() == null
                ? ""
                : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalStateException("MinIO endpoint 只允许 http/https");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("MinIO endpoint 不允许凭据、query 或 fragment");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalStateException("MinIO endpoint 缺少 host");
        }
        if (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) {
            throw new IllegalStateException("MinIO endpoint 不允许 path");
        }
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        int port = uri.getPort() >= 0 ? uri.getPort() : (scheme.equals("https") ? 443 : 80);
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("MinIO endpoint port 非法");
        }
        return scheme + "://" + host + ":" + port;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 不能为空");
        }
        return requiredText(value, name);
    }

    private String requiredEnvironmentAllowEmpty(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException(name + " 必须显式设置");
        }
        return cleanText(value, name);
    }

    private String optionalEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, name);
    }

    private String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " 不能为空");
        }
        return cleanText(value.trim(), label);
    }

    private static String cleanText(String value, String label) {
        if (value.indexOf('\u0000') >= 0 || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            throw new IllegalStateException(label + " 含控制字符");
        }
        return value;
    }

    private int parsePort(String value, String label) {
        int port = parseNonNegativeInt(value, label);
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(label + " 必须在 1..65535");
        }
        return port;
    }

    private int parseNonNegativeInt(String value, String label) {
        if (!value.matches("^[0-9]+$")) {
            throw new IllegalStateException(label + " 必须是非负整数");
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalStateException(label + " 必须是非负整数");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(label + " 超出整数范围", ex);
        }
    }

    private record Inputs(
            String candidateSha,
            String runContext,
            String schema,
            String jdbcUrl,
            String mysqlUsername,
            String mysqlPassword,
            String expectedMysqlServerUuid,
            String redisHost,
            int redisPort,
            int redisDatabase,
            String redisUsername,
            String redisPassword,
            String expectedRedisRunId,
            String minioEndpoint,
            String minioAccessKey,
            String minioSecretKey,
            String minioBucket,
            String identityObject,
            String identitySha256,
            String identityNonce,
            String identityIssuedAt) {
    }

    record TargetAttestation(
            String candidateSha,
            String runContext,
            String mysqlDatabase,
            String mysqlVersion,
            String mysqlServerUuid,
            String redisVersion,
            String redisRunId,
            int redisDatabase,
            String minioEndpoint,
            String minioBucket,
            String minioIdentityObject,
            String minioIdentitySha256,
            String minioIdentityNonce,
            String minioIdentityIssuedAt,
            long mysqlTableCountBefore,
            long redisDatabaseSizeBefore,
            long minioObjectCountBefore,
            long minioUnexpectedObjectCountBefore) {
    }

    private record MysqlIdentity(
            String database, String version, String serverUuid, long tableCountBefore) {
    }

    private record RedisIdentity(
            String version, String runId, int database, long databaseSizeBefore) {
    }

    private record MinioIdentity(
            String endpoint,
            String bucket,
            String identityObject,
            String identitySha256,
            String identityNonce,
            String identityIssuedAt,
            long objectCountBefore,
            long unexpectedObjectCountBefore) {
    }
}
