package cn.edu.gpnu.platform.boot;

import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase 0 正式 Maven JVM 的写前目标身份护栏。
 *
 * <p>外层 gate 的独立 preflight 能阻止已知错靶进入正式 {@code clean verify}；本 initializer
 * 在每个 Spring context refresh（包括 Flyway 和 MinIO initializer）之前，用同一 JVM 再做一次
 * MySQL/Redis/MinIO 只读身份核验，从而关闭两次 Maven 之间目标被替换后先写错环境的窗口。
 * 未设置候选 SHA 的普通离线单测保持无副作用。
 */
public final class Phase00TargetGuardInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String ATTESTATION_BEAN_NAME = "phase00TargetGuardAttestation";
    private static final Object ATTESTATION_MONITOR = new Object();
    private static final List<TargetBinding> TARGET_BINDINGS = List.of(
            new TargetBinding(
                    "spring.datasource.url", "SPRING_DATASOURCE_URL", false, false),
            new TargetBinding(
                    "spring.datasource.username",
                    "SPRING_DATASOURCE_USERNAME",
                    false,
                    false),
            new TargetBinding(
                    "spring.datasource.password",
                    "SPRING_DATASOURCE_PASSWORD",
                    true,
                    false),
            new TargetBinding(
                    "spring.data.redis.host", "SPRING_DATA_REDIS_HOST", false, false),
            new TargetBinding(
                    "spring.data.redis.port", "SPRING_DATA_REDIS_PORT", false, false),
            new TargetBinding(
                    "spring.data.redis.database",
                    "SPRING_DATA_REDIS_DATABASE",
                    false,
                    false),
            new TargetBinding(
                    "spring.data.redis.username",
                    "SPRING_DATA_REDIS_USERNAME",
                    false,
                    true),
            new TargetBinding(
                    "spring.data.redis.password",
                    "SPRING_DATA_REDIS_PASSWORD",
                    false,
                    true),
            new TargetBinding("minio.endpoint", "MINIO_ENDPOINT", false, false),
            new TargetBinding(
                    "minio.public-endpoint",
                    "MINIO_PUBLIC_ENDPOINT",
                    false,
                    false),
            new TargetBinding("minio.access-key", "MINIO_ACCESS_KEY", false, false),
            new TargetBinding("minio.secret-key", "MINIO_SECRET_KEY", false, false),
            new TargetBinding("minio.bucket", "MINIO_BUCKET", false, false));
    private static final Set<String> ALTERNATE_ROUTE_EXACT_NAMES = Set.of(
            relaxedName("spring.datasource.jndi-name"),
            relaxedName("spring.datasource.type"),
            relaxedName("spring.datasource.hikari.jdbc-url"),
            relaxedName("spring.datasource.hikari.username"),
            relaxedName("spring.datasource.hikari.password"),
            relaxedName("spring.datasource.hikari.catalog"),
            relaxedName("spring.datasource.hikari.schema"),
            relaxedName("spring.datasource.hikari.connection-init-sql"),
            relaxedName("spring.datasource.hikari.connection-test-query"),
            relaxedName("spring.datasource.hikari.driver-class-name"),
            relaxedName("spring.data.redis.url"),
            relaxedName("spring.redis.url"),
            relaxedName("spring.flyway.url"),
            relaxedName("spring.flyway.user"),
            relaxedName("spring.flyway.password"),
            relaxedName("spring.flyway.default-schema"),
            relaxedName("spring.flyway.schemas"),
            relaxedName("spring.flyway.init-sqls"),
            relaxedName("spring.flyway.driver-class-name"));
    private static final List<String> ALTERNATE_ROUTE_PREFIXES = List.of(
            relaxedName("spring.datasource.hikari.data-source"),
            relaxedName("spring.data.redis.sentinel"),
            relaxedName("spring.data.redis.cluster"),
            relaxedName("spring.redis.sentinel"),
            relaxedName("spring.redis.cluster"),
            relaxedName("spring.sql.init"));
    private static final Set<String> ALLOWED_FLYWAY_PROPERTY_NAMES = Set.of(
            relaxedName("spring.flyway.enabled"),
            relaxedName("spring.flyway.locations"),
            relaxedName("spring.flyway.baseline-on-migrate"),
            relaxedName("spring.flyway.encoding"));
    private static final List<String> ALTERNATE_ROUTE_QUERY_NAMES = List.of(
            "spring.datasource.jndi-name",
            "spring.datasource.type",
            "spring.datasource.hikari.jdbc-url",
            "spring.datasource.hikari.username",
            "spring.datasource.hikari.password",
            "spring.datasource.hikari.catalog",
            "spring.datasource.hikari.schema",
            "spring.datasource.hikari.connection-init-sql",
            "spring.datasource.hikari.connection-test-query",
            "spring.datasource.hikari.driver-class-name",
            "spring.datasource.hikari.data-source-class-name",
            "spring.datasource.hikari.data-source-j-n-d-i",
            "spring.data.redis.url",
            "spring.data.redis.sentinel.master",
            "spring.data.redis.sentinel.nodes",
            "spring.data.redis.cluster.nodes",
            "spring.redis.url",
            "spring.redis.sentinel.master",
            "spring.redis.sentinel.nodes",
            "spring.redis.cluster.nodes",
            "spring.flyway.url",
            "spring.flyway.user",
            "spring.flyway.password",
            "spring.flyway.default-schema",
            "spring.flyway.schemas",
            "spring.flyway.init-sqls",
            "spring.flyway.driver-class-name",
            "spring.flyway.jdbc-properties.sessionVariables");

    private static Phase00TargetPreflight.TargetAttestation firstAttestation;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String candidateSha = System.getenv("PHASE00_EXPECTED_CANDIDATE_SHA");
        if (!isFormalGateActive(candidateSha)) {
            return;
        }
        try {
            if (applicationContext.getBeanFactory().containsSingleton(ATTESTATION_BEAN_NAME)) {
                throw new IllegalStateException("Phase 0 target guard attestation 重复注册");
            }
            validateResolvedTargetConfiguration(
                    applicationContext.getEnvironment(), System.getenv());
            Phase00TargetPreflight.TargetAttestation attestation =
                    attestFormalContextTarget();
            applicationContext.getBeanFactory()
                    .registerSingleton(ATTESTATION_BEAN_NAME, attestation);
        } catch (Exception ex) {
            throw new ApplicationContextException(
                    "Phase 0 target guard 在 context refresh 前未能确认隔离目标", ex);
        }
    }

    /**
     * 校验 Spring 已完成优先级与 relaxed binding 后真正会交给 DataSource/Redis/MinIO 的配置，
     * 而不是只相信 gate 读取到的 canonical 环境变量。
     */
    static void validateResolvedTargetConfiguration(
            ConfigurableEnvironment environment, Map<String, String> canonicalEnvironment) {
        rejectAlternateRouting(environment);
        assertCanonicalMigrationConfiguration(environment);
        for (TargetBinding binding : TARGET_BINDINGS) {
            assertCanonicalBinding(environment, canonicalEnvironment, binding);
        }
    }

    static boolean isFormalGateActive(String candidateSha) {
        return candidateSha != null && !candidateSha.isBlank();
    }

    private static Phase00TargetPreflight.TargetAttestation attestFormalContextTarget()
            throws Exception {
        synchronized (ATTESTATION_MONITOR) {
            if (firstAttestation == null) {
                Phase00TargetPreflight.TargetAttestation freshAttestation =
                        Phase00TargetPreflight.attestBeforeContextRefresh();
                firstAttestation = freshAttestation;
                return freshAttestation;
            }
            return Phase00TargetPreflight.reattestBeforeContextRefresh(firstAttestation);
        }
    }

    private static void rejectAlternateRouting(ConfigurableEnvironment environment) {
        for (String propertyName : ALTERNATE_ROUTE_QUERY_NAMES) {
            if (environment.containsProperty(propertyName)) {
                throw new IllegalStateException(
                        "Phase 0 target guard 禁止备用连接路由: " + propertyName);
            }
        }
        for (var propertySource : ConfigurationPropertySources.get(environment)) {
            if (propertySource instanceof IterableConfigurationPropertySource iterable) {
                iterable.stream().forEach(propertyName ->
                        rejectAlternatePropertyName(propertyName.toString()));
            }
        }
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String propertyName : enumerable.getPropertyNames()) {
                rejectAlternatePropertyName(propertyName);
            }
        }
    }

    private static void rejectAlternatePropertyName(String propertyName) {
        String relaxed = relaxedName(propertyName);
        boolean forbiddenFlyway = relaxed.startsWith(relaxedName("spring.flyway"))
                && !ALLOWED_FLYWAY_PROPERTY_NAMES.contains(relaxed);
        boolean forbidden = forbiddenFlyway
                || ALTERNATE_ROUTE_EXACT_NAMES.contains(relaxed)
                || ALTERNATE_ROUTE_PREFIXES.stream().anyMatch(relaxed::startsWith);
        if (forbidden) {
            throw new IllegalStateException(
                    "Phase 0 target guard 禁止备用连接路由: " + propertyName);
        }
    }

    private static void assertCanonicalMigrationConfiguration(
            ConfigurableEnvironment environment) {
        assertExactProperty(environment, "spring.flyway.enabled", "true");
        assertExactProperty(
                environment,
                "spring.flyway.locations",
                "classpath:db/migration,classpath:db/testseed");
        assertExactProperty(environment, "spring.flyway.baseline-on-migrate", "true");
        assertExactProperty(environment, "spring.flyway.encoding", "UTF-8");
    }

    private static void assertExactProperty(
            ConfigurableEnvironment environment, String propertyName, String expected) {
        String resolved = environment.getProperty(propertyName);
        if (!expected.equals(resolved)) {
            throw new IllegalStateException(
                    "Phase 0 target guard 要求 " + propertyName + "=" + expected);
        }
    }

    private static void assertCanonicalBinding(
            ConfigurableEnvironment environment,
            Map<String, String> canonicalEnvironment,
            TargetBinding binding) {
        String canonical = canonicalEnvironment.get(binding.environmentVariable());
        String resolved = environment.getProperty(binding.propertyName());
        if (binding.optional()) {
            String optionalCanonical = canonical == null || canonical.isBlank() ? null : canonical;
            String optionalResolved = resolved == null || resolved.isBlank() ? null : resolved;
            if (!java.util.Objects.equals(optionalCanonical, optionalResolved)) {
                throw bindingMismatch(binding);
            }
            return;
        }
        if (canonical == null || (!binding.allowEmpty() && canonical.isBlank())) {
            throw new IllegalStateException(
                    binding.environmentVariable() + " 必须显式设置");
        }
        if (containsControlCharacter(canonical)
                || resolved == null
                || !resolved.equals(canonical)) {
            throw bindingMismatch(binding);
        }
    }

    private static IllegalStateException bindingMismatch(TargetBinding binding) {
        return new IllegalStateException(
                "Spring 实际解析的 " + binding.propertyName()
                        + " 与 canonical " + binding.environmentVariable() + " 不一致");
    }

    private static boolean containsControlCharacter(String value) {
        return value.indexOf('\u0000') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0;
    }

    private static String relaxedName(String propertyName) {
        StringBuilder normalized = new StringBuilder(propertyName.length());
        for (int index = 0; index < propertyName.length(); index++) {
            char current = propertyName.charAt(index);
            if (Character.isLetterOrDigit(current)) {
                normalized.append(Character.toLowerCase(current));
            }
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }

    private record TargetBinding(
            String propertyName,
            String environmentVariable,
            boolean allowEmpty,
            boolean optional) {
    }
}
