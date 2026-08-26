package cn.edu.gpnu.platform.boot.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * WS-10：运行 profile 必须显式选择，且生产环境不得混入测试种子或开发凭据。
 * 在 ConfigData 加载后、应用上下文创建前执行，避免无 profile 时先以晦涩的数据源错误失败。
 */
public final class RuntimeProfileGuard implements EnvironmentPostProcessor, Ordered {

    private static final String DEV_JWT_SECRET =
            "dev-only-insecure-jwt-secret-do-not-use-in-production-0123456789";
    private static final String EXAMPLE_JWT_SECRET =
            "change-me-at-least-64-characters-change-me-at-least-64-characters";
    private static final String DEV_ID_CARD_ENCRYPTION_KEY =
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String EXAMPLE_ID_CARD_ENCRYPTION_KEY =
            "REPLACE_WITH_BASE64_32_BYTE_KEY";
    private static final String DEV_ID_CARD_HMAC_PEPPER =
            "dev-only-insecure-id-card-hmac-pepper-do-not-use-in-production";
    private static final String EXAMPLE_ID_CARD_HMAC_PEPPER =
            "REPLACE_WITH_RANDOM_HMAC_PEPPER_AT_LEAST_32_BYTES";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        validate(environment);
    }

    static void validate(ConfigurableEnvironment environment) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean dev = activeProfiles.contains("dev");
        boolean prod = activeProfiles.contains("prod");
        if (!dev && !prod) {
            throw new IllegalStateException("未显式激活运行 profile：本地开发请设置 "
                    + "SPRING_PROFILES_ACTIVE=dev，生产请设置 SPRING_PROFILES_ACTIVE=prod");
        }
        if (dev && prod) {
            throw new IllegalStateException("dev 与 prod profile 不能同时激活");
        }
        if (dev) {
            return;
        }

        List<String> violations = new ArrayList<>();
        String flywayLocations = property(environment, "spring.flyway.locations", violations);
        if (StringUtils.hasText(flywayLocations)
                && flywayLocations.toLowerCase(Locale.ROOT).contains("db/testseed")) {
            violations.add("spring.flyway.locations 包含 db/testseed");
        }
        if (environment.getProperty("platform.demo.enabled", Boolean.class, false)) {
            violations.add("platform.demo.enabled=true");
        }

        rejectMissingOrKnownValue(environment, "spring.datasource.username", Set.of("root"), violations);
        rejectMissingOrKnownValue(environment, "spring.datasource.password",
                Set.of("root123", "change-me-strong-app-db-password"), violations);
        rejectMissingOrKnownValue(environment, "spring.data.redis.password",
                Set.of("change-me-strong-redis-password"), violations);
        rejectMissingOrKnownValue(environment, "minio.access-key",
                Set.of("minioadmin", "change-me-unique-minio-admin-user"), violations);
        rejectMissingOrKnownValue(environment, "minio.secret-key",
                Set.of("minioadmin123", "change-me-strong-minio-admin-password"), violations);
        String jwtSecret = rejectMissingOrKnownValue(environment, "platform.security.jwt.secret",
                Set.of(DEV_JWT_SECRET, EXAMPLE_JWT_SECRET), violations);
        String idCardEncryptionKey = rejectMissingOrKnownValue(
                environment,
                "platform.security.id-card.encryption-key",
                Set.of(DEV_ID_CARD_ENCRYPTION_KEY, EXAMPLE_ID_CARD_ENCRYPTION_KEY),
                violations);
        String idCardHmacPepper = rejectMissingOrKnownValue(
                environment,
                "platform.security.id-card.hmac-pepper",
                Set.of(DEV_ID_CARD_HMAC_PEPPER, EXAMPLE_ID_CARD_HMAC_PEPPER),
                violations);
        rejectSameSecret(
                "platform.security.id-card.encryption-key", idCardEncryptionKey,
                "platform.security.id-card.hmac-pepper", idCardHmacPepper,
                violations);
        rejectSameSecret(
                "platform.security.id-card.hmac-pepper", idCardHmacPepper,
                "platform.security.jwt.secret", jwtSecret,
                violations);
        rejectSameSecret(
                "platform.security.id-card.encryption-key", idCardEncryptionKey,
                "platform.security.jwt.secret", jwtSecret,
                violations);
        rejectMissingOrKnownValue(environment, "platform.security.initial-password",
                Set.of("ChangeMe123!", "change-me-strong-staff-initial-password"), violations);

        if (!violations.isEmpty()) {
            throw new IllegalStateException("prod profile 检测到开发、测试或示例配置："
                    + String.join("；", violations) + "。请注入真实生产配置后重启");
        }
    }

    private static String rejectMissingOrKnownValue(ConfigurableEnvironment environment,
                                                     String key,
                                                     Set<String> rejectedValues,
                                                     List<String> violations) {
        int violationCount = violations.size();
        String value = property(environment, key, violations);
        if (!StringUtils.hasText(value)) {
            if (violations.size() == violationCount) {
                violations.add(key + " 未配置");
            }
            return null;
        }
        String trimmedValue = value.trim();
        if (rejectedValues.contains(trimmedValue)) {
            violations.add(key + " 仍为开发或示例值");
        }
        return trimmedValue;
    }

    private static void rejectSameSecret(String firstKey,
                                         String firstValue,
                                         String secondKey,
                                         String secondValue,
                                         List<String> violations) {
        if (StringUtils.hasText(firstValue)
                && StringUtils.hasText(secondValue)
                && firstValue.equals(secondValue)) {
            violations.add(firstKey + " 与 " + secondKey + " 必须使用不同值");
        }
    }

    private static String property(ConfigurableEnvironment environment,
                                   String key,
                                   List<String> violations) {
        try {
            return environment.getProperty(key);
        } catch (IllegalArgumentException exception) {
            violations.add(key + " 含未解析占位符");
            return null;
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
