package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.boot.Phase00TargetGuardInitializer;
import cn.edu.gpnu.platform.security.service.TokenRevocationService;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RuntimeProfileGuardTest {

    private static final long ADMIN_ID = 800000000000003001L;

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "runtime-profile-guard-test");
        assistant.setCurrentNamespace(SysUserMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    private final RuntimeProfileGuard guard = new RuntimeProfileGuard();

    @Test
    void registeredGuardRejectsRealApplicationWithoutProfile() {
        assertThat(SpringFactoriesLoader
                .forDefaultResourceLocation(RuntimeProfileGuardTest.class.getClassLoader())
                .load(ApplicationContextInitializer.class))
                .as("Phase 0 写前目标护栏必须经 Spring factories 自动注册")
                .anyMatch(Phase00TargetGuardInitializer.class::isInstance);

        SpringApplication application = new SpringApplication(PlatformApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);

        assertThatThrownBy(() -> application.run(
                "--spring.config.name=profile-guard-empty",
                "--spring.profiles.active=",
                "--spring.main.banner-mode=off"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未显式激活运行 profile")
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=dev")
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=prod");
    }

    @Test
    void registeredGuardAllowsProductionLikeConfigAndRunsAdminBootstrap() {
        String bootstrapHash = new BCryptPasswordEncoder(10).encode("Admin-Prod-Bootstrap-2026!");
        SpringApplication application = new SpringApplication(ProductionBootstrapConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);

        try (ConfigurableApplicationContext context = application.run(productionArguments(
                bootstrapHash,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=",
                "prod-only-id-card-hmac-pepper-2026-keep-secret"))) {
            SysUserMapper userMapper = context.getBean(SysUserMapper.class);
            TokenRevocationService revocationService = context.getBean(TokenRevocationService.class);

            verify(userMapper).update(any(LambdaUpdateWrapper.class));
            verify(revocationService, times(2)).revoke(ADMIN_ID);
        }
    }

    @Test
    void devProfileAllowsLocalFixtures() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("spring.flyway.locations", "classpath:db/migration,classpath:db/testseed");
        environment.setProperty("platform.security.jwt.secret",
                "dev-only-insecure-jwt-secret-do-not-use-in-production-0123456789");

        assertThatCode(() -> guard.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void prodProfileAllowsOnlyProductionLikeConfiguration() {
        MockEnvironment environment = productionEnvironment();

        assertThatCode(() -> guard.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void prodProfileRejectsMissingIdCardSecrets() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("platform.security.id-card.encryption-key", "");
        environment.setProperty("platform.security.id-card.hmac-pepper", "");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("platform.security.id-card.encryption-key 未配置")
                .hasMessageContaining("platform.security.id-card.hmac-pepper 未配置");
    }

    @Test
    void prodProfileRejectsReusedEncryptionKeyAndHmacPepperWithoutLeakingValue() {
        String sharedSecret = "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=";
        assertReusedSecretsRejected(
                "platform.security.id-card.encryption-key",
                "platform.security.id-card.hmac-pepper",
                sharedSecret);
    }

    @Test
    void registeredProdGuardRejectsReusedSecretsBeforeApplicationContextStarts() {
        String sharedSecret = "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=";
        String bootstrapHash = new BCryptPasswordEncoder(10).encode("Admin-Prod-Bootstrap-2026!");
        SpringApplication application = new SpringApplication(ProductionBootstrapConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);

        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = application.run(productionArguments(
                    bootstrapHash,
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    sharedSecret,
                    sharedSecret))) {
                throw new AssertionError("复用 secret 时不应创建应用上下文");
            }
        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("platform.security.id-card.encryption-key")
                .hasMessageContaining("platform.security.id-card.hmac-pepper")
                .hasMessageNotContaining(sharedSecret);
    }

    @Test
    void prodProfileRejectsReusedHmacPepperAndJwtSecretWithoutLeakingValue() {
        String sharedSecret = "prod-only-shared-secret-value-at-least-sixty-four-characters-2026";
        assertReusedSecretsRejected(
                "platform.security.id-card.hmac-pepper",
                "platform.security.jwt.secret",
                sharedSecret);
    }

    @Test
    void prodProfileRejectsReusedEncryptionKeyAndJwtSecretWithoutLeakingValue() {
        String sharedSecret = "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=";
        assertReusedSecretsRejected(
                "platform.security.id-card.encryption-key",
                "platform.security.jwt.secret",
                sharedSecret);
    }

    @Test
    void prodProfileRejectsTestseedDemoAndKnownCredentialsTogether() {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty("spring.flyway.locations", "classpath:db/migration,classpath:db/testseed");
        environment.setProperty("platform.demo.enabled", "true");
        environment.setProperty("spring.datasource.username", "root");
        environment.setProperty("spring.datasource.password", "root123");
        environment.setProperty("spring.data.redis.password", "change-me-strong-redis-password");
        environment.setProperty("minio.access-key", "minioadmin");
        environment.setProperty("minio.secret-key", "minioadmin123");
        environment.setProperty("platform.security.jwt.secret",
                "dev-only-insecure-jwt-secret-do-not-use-in-production-0123456789");
        environment.setProperty("platform.security.id-card.encryption-key",
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
        environment.setProperty("platform.security.id-card.hmac-pepper",
                "dev-only-insecure-id-card-hmac-pepper-do-not-use-in-production");
        environment.setProperty("platform.security.initial-password", "ChangeMe123!");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db/testseed")
                .hasMessageContaining("platform.demo.enabled=true")
                .hasMessageContaining("spring.datasource.username")
                .hasMessageContaining("spring.data.redis.password")
                .hasMessageContaining("minio.secret-key")
                .hasMessageContaining("platform.security.jwt.secret")
                .hasMessageContaining("platform.security.id-card.encryption-key")
                .hasMessageContaining("platform.security.id-card.hmac-pepper")
                .hasMessageContaining("platform.security.initial-password");
    }

    @Test
    void devAndProdCannotBeActiveTogether() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev", "prod");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能同时激活");
    }

    private MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.flyway.locations", "classpath:db/migration");
        environment.setProperty("spring.datasource.username", "teacher_app");
        environment.setProperty("spring.datasource.password", "Db-Prod-Only-2026!");
        environment.setProperty("spring.data.redis.password", "Redis-Prod-Only-2026!");
        environment.setProperty("minio.access-key", "teacher-cert-minio-prod");
        environment.setProperty("minio.secret-key", "Minio-Prod-Only-2026!");
        environment.setProperty("platform.security.jwt.secret",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        environment.setProperty("platform.security.id-card.encryption-key",
                "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=");
        environment.setProperty("platform.security.id-card.hmac-pepper",
                "prod-only-id-card-hmac-pepper-2026-keep-secret");
        environment.setProperty("platform.security.initial-password", "Staff-Prod-Only-2026!");
        return environment;
    }

    private void assertReusedSecretsRejected(String firstKey, String secondKey, String sharedSecret) {
        MockEnvironment environment = productionEnvironment();
        environment.setProperty(firstKey, "  " + sharedSecret + "  ");
        environment.setProperty(secondKey, sharedSecret);

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(firstKey)
                .hasMessageContaining(secondKey)
                .hasMessageNotContaining(sharedSecret);
    }

    private String[] productionArguments(String bootstrapHash,
                                         String jwtSecret,
                                         String encryptionKey,
                                         String hmacPepper) {
        return new String[]{
                "--spring.config.name=profile-guard-empty",
                "--spring.profiles.active=prod",
                "--spring.main.banner-mode=off",
                "--spring.flyway.locations=classpath:db/migration",
                "--spring.datasource.username=teacher_app",
                "--spring.datasource.password=Db-Prod-Only-2026!",
                "--spring.data.redis.password=Redis-Prod-Only-2026!",
                "--minio.access-key=teacher-cert-minio-prod",
                "--minio.secret-key=Minio-Prod-Only-2026!",
                "--platform.security.jwt.secret=" + jwtSecret,
                "--platform.security.id-card.encryption-key=" + encryptionKey,
                "--platform.security.id-card.hmac-pepper=" + hmacPepper,
                "--platform.security.initial-password=Staff-Prod-Only-2026!",
                "--platform.security.admin.initial-password-hash=" + bootstrapHash
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class ProductionBootstrapConfiguration {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(10);
        }

        @Bean
        SysUserMapper userMapper(PasswordEncoder passwordEncoder) {
            SysUserMapper mapper = mock(SysUserMapper.class);
            SysUser admin = new SysUser();
            admin.setId(ADMIN_ID);
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("ChangeMe123!"));
            when(mapper.selectByUsername("admin")).thenReturn(admin);
            when(mapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
            return mapper;
        }

        @Bean
        TokenRevocationService tokenRevocationService() {
            return mock(TokenRevocationService.class);
        }

        @Bean
        AdminAccountInitializer adminAccountInitializer(
                SysUserMapper userMapper,
                PasswordEncoder passwordEncoder,
                TokenRevocationService tokenRevocationService,
                org.springframework.core.env.Environment environment) {
            return new AdminAccountInitializer(userMapper, environment, passwordEncoder, tokenRevocationService);
        }
    }
}
