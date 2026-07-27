package cn.edu.gpnu.platform.boot;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 0 写前目标护栏的纯内存反例；不得触发任何外部连接。
 */
class Phase00TargetGuardInitializerTest {

    private static final Map<String, String> CANONICAL_ENVIRONMENT = Map.ofEntries(
            Map.entry(
                    "SPRING_DATASOURCE_URL",
                    "jdbc:mysql://127.0.0.1:33061/phase00_fresh"),
            Map.entry("SPRING_DATASOURCE_USERNAME", "phase00"),
            Map.entry("SPRING_DATASOURCE_PASSWORD", "mysql-secret"),
            Map.entry("SPRING_DATA_REDIS_HOST", "127.0.0.1"),
            Map.entry("SPRING_DATA_REDIS_PORT", "36379"),
            Map.entry("SPRING_DATA_REDIS_DATABASE", "15"),
            Map.entry("MINIO_ENDPOINT", "http://127.0.0.1:39000"),
            Map.entry("MINIO_PUBLIC_ENDPOINT", "http://127.0.0.1:39000"),
            Map.entry("MINIO_ACCESS_KEY", "phase00-access"),
            Map.entry("MINIO_SECRET_KEY", "minio-secret"),
            Map.entry("MINIO_BUCKET", "phase00-fresh"));

    @Test
    void matchingResolvedConfigurationIsAcceptedWithoutExternalConnections() {
        assertThatCode(() -> Phase00TargetGuardInitializer
                .validateResolvedTargetConfiguration(
                        matchingEnvironment(), CANONICAL_ENVIRONMENT))
                .doesNotThrowAnyException();
    }

    @Test
    void dottedLowercaseAliasCannotOverrideCanonicalDatasource() {
        MockEnvironment environment = matchingEnvironment();
        environment.getPropertySources().addFirst(systemEnvironment(
                "dotted-datasource-alias",
                "spring.datasource.url",
                "jdbc:mysql://127.0.0.1:33061/wrong_schema"));

        assertThatThrownBy(() -> Phase00TargetGuardInitializer
                .validateResolvedTargetConfiguration(environment, CANONICAL_ENVIRONMENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url")
                .hasMessageContaining("SPRING_DATASOURCE_URL");
    }

    @Test
    void lowercaseUnderscoreAliasCannotOverrideCanonicalRedisTarget() {
        MockEnvironment environment = matchingEnvironment();
        environment.getPropertySources().addFirst(systemEnvironment(
                "lowercase-redis-alias",
                "spring_data_redis_host",
                "shared-redis.internal"));

        assertThatThrownBy(() -> Phase00TargetGuardInitializer
                .validateResolvedTargetConfiguration(environment, CANONICAL_ENVIRONMENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.redis.host")
                .hasMessageContaining("SPRING_DATA_REDIS_HOST");
    }

    @Test
    void resolvedMinioEndpointAndBucketMustBothMatchCanonicalEnvironment() {
        MockEnvironment wrongEndpoint = matchingEnvironment();
        wrongEndpoint.setProperty("minio.endpoint", "http://127.0.0.1:49000");
        assertThatThrownBy(() -> Phase00TargetGuardInitializer
                .validateResolvedTargetConfiguration(wrongEndpoint, CANONICAL_ENVIRONMENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.endpoint");

        MockEnvironment wrongBucket = matchingEnvironment();
        wrongBucket.setProperty("minio.bucket", "shared-bucket");
        assertThatThrownBy(() -> Phase00TargetGuardInitializer
                .validateResolvedTargetConfiguration(wrongBucket, CANONICAL_ENVIRONMENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.bucket");

        MockEnvironment wrongPublicEndpoint = matchingEnvironment();
        wrongPublicEndpoint.setProperty(
                "minio.public-endpoint", "http://127.0.0.1:59000");
        assertThatThrownBy(() -> Phase00TargetGuardInitializer
                .validateResolvedTargetConfiguration(
                        wrongPublicEndpoint, CANONICAL_ENVIRONMENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.public-endpoint");
    }

    @Test
    void alternateDatasourceAndRedisRoutesFailClosedAcrossRelaxedAliases() {
        for (String alternateProperty : List.of(
                "spring.datasource.jndi-name",
                "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.username",
                "spring.datasource.hikari.connection-test-query",
                "spring.datasource.hikari.dataSourceClassName",
                "spring.datasource.hikari.data-source-j-n-d-i",
                "spring.datasource.hikari.data-source-properties.serverName",
                "spring.data.redis.url",
                "spring_data_redis_sentinel_nodes",
                "SPRING_DATA_REDIS_CLUSTER_NODES",
                "spring_redis_cluster_nodes",
                "spring.flyway.url",
                "SPRING_FLYWAY_SCHEMAS",
                "spring_flyway_init_sqls",
                "spring.flyway.driverClassName",
                "spring.flyway.jdbc-properties.sessionVariables",
                "spring.flyway.placeholders.targetSchema",
                "spring.sql.init.mode",
                "SPRING_SQL_INIT_SCHEMA_LOCATIONS")) {
            MockEnvironment environment = matchingEnvironment();
            environment.getPropertySources().addFirst(systemEnvironment(
                    "alternate-route", alternateProperty, "alternate-target"));

            assertThatThrownBy(() -> Phase00TargetGuardInitializer
                    .validateResolvedTargetConfiguration(environment, CANONICAL_ENVIRONMENT))
                    .as("alternate route %s", alternateProperty)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("禁止备用连接路由");
        }

        MockEnvironment wrongLocations = matchingEnvironment();
        wrongLocations.setProperty(
                "spring.flyway.locations", "file:C:/outside/migrations");
        assertThatThrownBy(() -> Phase00TargetGuardInitializer
                .validateResolvedTargetConfiguration(
                        wrongLocations, CANONICAL_ENVIRONMENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.locations");
    }

    @Test
    void ordinaryRunWithoutCandidateDoesNotActivateTargetGuard() {
        assertThat(Phase00TargetGuardInitializer.isFormalGateActive(null)).isFalse();
        assertThat(Phase00TargetGuardInitializer.isFormalGateActive(" ")).isFalse();
        assertThat(Phase00TargetGuardInitializer.isFormalGateActive(
                "0123456789abcdef0123456789abcdef01234567")).isTrue();
    }

    @Test
    void freshnessRequiresExactlyZeroZeroOneZero() {
        assertThatCode(() -> Phase00TargetPreflight.validateFreshness(0L, 0L, 1L, 0L))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Phase00TargetPreflight.validateFreshness(1L, 0L, 1L, 0L))
                .hasMessageContaining("MySQL schema");
        assertThatThrownBy(() -> Phase00TargetPreflight.validateFreshness(0L, 1L, 1L, 0L))
                .hasMessageContaining("Redis");
        assertThatThrownBy(() -> Phase00TargetPreflight.validateFreshness(0L, 0L, 0L, 0L))
                .hasMessageContaining("provisioning identity");
        assertThatThrownBy(() -> Phase00TargetPreflight.validateFreshness(0L, 0L, 2L, 1L))
                .hasMessageContaining("provisioning identity");
        assertThatThrownBy(() -> Phase00TargetPreflight.validateFreshness(0L, 0L, 1L, 1L))
                .hasMessageContaining("其它对象");
    }

    @Test
    void evidenceSchemaFourContainsExactFreshnessAndFingerprintKeys() {
        String identityNonce =
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String rawDeploymentId = "123e4567-e89b-12d3-a456-426614174000";
        String instanceFingerprintSha256 =
                Phase00TargetPreflight.deriveMinioInstanceFingerprint(
                        List.of(rawDeploymentId), identityNonce);
        assertThat(instanceFingerprintSha256)
                .isEqualTo("7fe3df67cf424bb6bf35a90cd42f348da3d7855a20b0f55f9d3b612cdaea2c1e")
                .matches("^[0-9a-f]{64}$");
        assertThat(Phase00TargetPreflight.deriveMinioInstanceFingerprint(
                List.of("223e4567-e89b-12d3-a456-426614174000"), identityNonce))
                .isNotEqualTo(instanceFingerprintSha256);

        for (List<String> invalidHeaders : List.of(
                List.<String>of(),
                List.of(rawDeploymentId, rawDeploymentId),
                List.of(rawDeploymentId.toUpperCase(java.util.Locale.ROOT)),
                List.of("minio-secret-password"),
                List.of(" " + rawDeploymentId),
                List.of(rawDeploymentId + "\npassword=reflected"))) {
            assertThatThrownBy(() -> Phase00TargetPreflight
                    .deriveMinioInstanceFingerprint(invalidHeaders, identityNonce))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("MinIO deployment identity header 必须是单个 canonical UUID")
                    .hasMessageNotContaining("minio-secret-password")
                    .hasMessageNotContaining("password=reflected");
        }
        assertThatThrownBy(() -> Phase00TargetPreflight.deriveMinioInstanceFingerprint(
                List.of(rawDeploymentId), "not-a-nonce"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MinIO instance fingerprint nonce 非法");

        Phase00TargetPreflight.TargetAttestation attestation =
                new Phase00TargetPreflight.TargetAttestation(
                        "0123456789abcdef0123456789abcdef01234567",
                        "phase00-run",
                        "phase00_fresh",
                        "8.0.46",
                        "12345678-1234-1234-1234-123456789abc",
                        "7.4.9",
                        "0123456789abcdef0123456789abcdef01234567",
                        15,
                        "http://127.0.0.1:39000",
                        "phase00-fresh",
                        ".phase00-target/identity.json",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        identityNonce,
                        "2026-07-27T00:00:00Z",
                        instanceFingerprintSha256,
                        0L,
                        0L,
                        1L,
                        0L);

        Map<String, Object> document = Phase00TargetPreflight.identityDocument(
                "test-producer", attestation);

        assertThat(document).containsOnlyKeys(
                "schemaVersion",
                "producerSuite",
                "candidateSha",
                "runContext",
                "freshness",
                "mysql",
                "redis",
                "minio");
        assertThat(document.get("schemaVersion")).isEqualTo(4);
        assertThat(document.get("freshness")).isInstanceOf(Map.class);
        Map<?, ?> freshness = (Map<?, ?>) document.get("freshness");
        assertThat(freshness.keySet().stream().map(String::valueOf).toList())
                .containsExactlyInAnyOrder(
                "mysqlTableCountBefore",
                "redisDatabaseSizeBefore",
                "minioObjectCountBefore",
                "minioUnexpectedObjectCountBefore");
        assertThat(freshness.get("mysqlTableCountBefore")).isEqualTo(0L);
        assertThat(freshness.get("redisDatabaseSizeBefore")).isEqualTo(0L);
        assertThat(freshness.get("minioObjectCountBefore")).isEqualTo(1L);
        assertThat(freshness.get("minioUnexpectedObjectCountBefore")).isEqualTo(0L);
        assertThat(document.get("minio")).isInstanceOf(Map.class);
        Map<?, ?> minio = (Map<?, ?>) document.get("minio");
        assertThat(minio.keySet().stream().map(String::valueOf).toList())
                .containsExactlyInAnyOrder(
                        "endpoint",
                        "bucket",
                        "identityObject",
                        "identitySha256",
                        "identityNonce",
                        "identityIssuedAt",
                        "instanceFingerprintSha256");
        assertThat(minio.containsKey("server")).isFalse();
        assertThat(minio.containsKey("deploymentId")).isFalse();
        assertThat(minio.containsValue(rawDeploymentId)).isFalse();
        assertThat(minio.get("instanceFingerprintSha256"))
                .isEqualTo(instanceFingerprintSha256);
    }

    private MockEnvironment matchingEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(
                "spring.datasource.url",
                CANONICAL_ENVIRONMENT.get("SPRING_DATASOURCE_URL"));
        environment.setProperty(
                "spring.datasource.username",
                CANONICAL_ENVIRONMENT.get("SPRING_DATASOURCE_USERNAME"));
        environment.setProperty(
                "spring.datasource.password",
                CANONICAL_ENVIRONMENT.get("SPRING_DATASOURCE_PASSWORD"));
        environment.setProperty(
                "spring.data.redis.host",
                CANONICAL_ENVIRONMENT.get("SPRING_DATA_REDIS_HOST"));
        environment.setProperty(
                "spring.data.redis.port",
                CANONICAL_ENVIRONMENT.get("SPRING_DATA_REDIS_PORT"));
        environment.setProperty(
                "spring.data.redis.database",
                CANONICAL_ENVIRONMENT.get("SPRING_DATA_REDIS_DATABASE"));
        environment.setProperty(
                "minio.endpoint",
                CANONICAL_ENVIRONMENT.get("MINIO_ENDPOINT"));
        environment.setProperty(
                "minio.public-endpoint",
                CANONICAL_ENVIRONMENT.get("MINIO_PUBLIC_ENDPOINT"));
        environment.setProperty(
                "minio.access-key",
                CANONICAL_ENVIRONMENT.get("MINIO_ACCESS_KEY"));
        environment.setProperty(
                "minio.secret-key",
                CANONICAL_ENVIRONMENT.get("MINIO_SECRET_KEY"));
        environment.setProperty(
                "minio.bucket",
                CANONICAL_ENVIRONMENT.get("MINIO_BUCKET"));
        environment.setProperty("spring.flyway.enabled", "true");
        environment.setProperty(
                "spring.flyway.locations",
                "classpath:db/migration,classpath:db/testseed");
        environment.setProperty("spring.flyway.baseline-on-migrate", "true");
        environment.setProperty("spring.flyway.encoding", "UTF-8");
        return environment;
    }

    private SystemEnvironmentPropertySource systemEnvironment(
            String name, String propertyName, String value) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(propertyName, value);
        return new SystemEnvironmentPropertySource(name, values);
    }
}
