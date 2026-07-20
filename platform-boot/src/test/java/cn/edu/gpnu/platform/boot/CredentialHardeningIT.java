package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 本 IT 使用 testseed 与本地 MySQL/MinIO 凭据，必须明确归类为 dev 测试夹具；
// prod profile 的 fail-fast 由 CredentialHardeningTest 与 RuntimeProfileGuardTest 覆盖。
@ActiveProfiles("dev")
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CredentialHardeningIT {

    private static final String USERNAME = "test_ws2_bootstrap_admin";
    private static final String LEGACY_PUBLIC_PASSWORD = "ChangeMe123!";
    private static final String LEGACY_PUBLIC_HASH =
            "$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO";
    private static final String BOOTSTRAP_PASSWORD = "Admin-Bootstrap-2026!";
    private static final String BOOTSTRAP_HASH = new BCryptPasswordEncoder(10).encode(BOOTSTRAP_PASSWORD);

    @DynamicPropertySource
    static void credentialProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String override = System.getenv("SPRING_DATASOURCE_URL");
            return override == null || override.isBlank()
                    ? "jdbc:mysql://localhost:3306/teacher_cert?useUnicode=true&characterEncoding=utf8"
                    + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
                    : override;
        });
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "root123");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/testseed");
        registry.add("minio.endpoint", () -> "http://localhost:9000");
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin123");
        registry.add("minio.bucket", () -> "teacher-cert");
        registry.add("platform.security.jwt.secret",
                () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        registry.add("platform.security.initial-password", () -> "Staff-Initial-2026!");
        registry.add("platform.security.admin.username", () -> USERNAME);
        registry.add("platform.security.admin.initial-password-hash", () -> BOOTSTRAP_HASH);
        registry.add("platform.backup.schedule.enabled", () -> false);
        registry.add("platform.cleanup.schedule.enabled", () -> false);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void restoreTestBootstrapAccount() {
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUsername, USERNAME)
                .set(SysUser::getPasswordHash, LEGACY_PUBLIC_HASH)
                .set(SysUser::getStatus, "ENABLED")
                .set(SysUser::getMustChangePwd, 1)
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null));
    }

    @Test
    void configuredBootstrapStartsAndRejectsOldAdminPassword() throws Exception {
        ResponseEntity<String> oldLogin = login(LEGACY_PUBLIC_PASSWORD);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(oldLogin).at("/code").asInt()).isNotEqualTo(0);

        ResponseEntity<String> newLogin = login(BOOTSTRAP_PASSWORD);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(newLogin).at("/code").asInt()).isEqualTo(0);
        assertThat(json(newLogin).at("/data/mustChangePwd").asBoolean()).isTrue();

        SysUser admin = userMapper.selectByUsername(USERNAME);
        assertThat(admin).isNotNull();
        assertThat(passwordEncoder.matches(BOOTSTRAP_PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(LEGACY_PUBLIC_PASSWORD, admin.getPasswordHash())).isFalse();
    }

    private ResponseEntity<String> login(String password) throws Exception {
        JsonNode captcha = json(rest.getForEntity(url("/api/auth/captcha"), String.class)).at("/data");
        return rest.postForEntity(url("/api/auth/login"), Map.of(
                "username", USERNAME,
                "password", password,
                "captchaId", captcha.at("/captchaId").asText(),
                "captchaCode", captchaCode(captcha.at("/image").asText())
        ), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private String captchaCode(String image) {
        String svg = new String(Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1)),
                StandardCharsets.UTF_8);
        return svg.replaceAll("(?s).*<text[^>]*>([^<]+)</text>.*", "$1").trim();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
