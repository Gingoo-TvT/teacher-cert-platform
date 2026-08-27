package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用本机未监听端口模拟依赖中断，验证 liveness 与 readiness 的运行期分离。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.connect-timeout=200ms",
        "spring.data.redis.timeout=200ms",
        "minio.endpoint=http://127.0.0.1:1",
        "minio.public-endpoint=http://127.0.0.1:1",
        "minio.connection-timeout-seconds=1",
        "minio.read-timeout-seconds=1",
        "minio.call-timeout-seconds=1",
        "minio.bucket=teacher-cert-ws11-unavailable",
        "platform.observability.prometheus.username=ws11-prometheus",
        "platform.observability.prometheus.password=ws11-prometheus-password"
})
class Ws11HealthDependencyDownIT {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unavailableRedisAndMinioKeepLivenessUpButReadinessDown() throws Exception {
        ResponseEntity<String> liveness = rest.getForEntity(url("/api/health/liveness"), String.class);
        ResponseEntity<String> readiness = rest.getForEntity(url("/api/health/readiness"), String.class);

        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(liveness.getBody()).at("/data/status").asText()).isEqualTo("UP");
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode data = objectMapper.readTree(readiness.getBody()).at("/data");
        assertThat(data.at("/status").asText()).isEqualTo("DOWN");
        assertThat(data.at("/components/mysql").asText()).isEqualTo("UP");
        assertThat(data.at("/components/redis").asText()).isEqualTo("DOWN");
        assertThat(data.at("/components/minio").asText()).isEqualTo("DOWN");
        assertThat(data.at("/components/migration").asText()).isEqualTo("UP");

        ResponseEntity<String> metrics = rest.withBasicAuth("ws11-prometheus", "ws11-prometheus-password")
                .getForEntity(url("/actuator/prometheus"), String.class);
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).containsPattern(
                "platform_readiness_component\\{[^\\n]*component=\\\"redis\\\"[^\\n]*} 0\\.0");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
