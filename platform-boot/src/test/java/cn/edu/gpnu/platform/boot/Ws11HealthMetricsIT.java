package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.observability.prometheus.username=ws11-prometheus",
        "platform.observability.prometheus.password=ws11-prometheus-password",
        "platform.security.jwt.access-ttl-seconds=1"
})
class Ws11HealthMetricsIT {

    private static final String METRICS_USERNAME = "ws11-prometheus";
    private static final String METRICS_PASSWORD = "ws11-prometheus-password";

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void realDependenciesAreReportedReadyWhileLegacyEndpointRemainsLiveness() throws Exception {
        ResponseEntity<String> legacy = rest.getForEntity(url("/api/health"), String.class);
        ResponseEntity<String> liveness = rest.getForEntity(url("/api/health/liveness"), String.class);
        ResponseEntity<String> readiness = rest.getForEntity(url("/api/health/readiness"), String.class);

        assertThat(legacy.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode readyData = objectMapper.readTree(readiness.getBody()).at("/data");
        assertThat(readyData.at("/status").asText()).isEqualTo("UP");
        assertThat(readyData.at("/components/mysql").asText()).isEqualTo("UP");
        assertThat(readyData.at("/components/redis").asText()).isEqualTo("UP");
        assertThat(readyData.at("/components/minio").asText()).isEqualTo("UP");
        assertThat(readyData.at("/components/migration").asText()).isEqualTo("UP");
    }

    @Test
    void prometheusUsesIndependentMachineIdentityBeforeAndAfterBusinessTokenTtl() throws Exception {
        rest.getForEntity(url("/api/health/readiness"), String.class);
        ResponseEntity<String> anonymous = rest.getForEntity(url("/actuator/prometheus"), String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> wrong = rest.withBasicAuth(METRICS_USERNAME, "wrong-password")
                .getForEntity(url("/actuator/prometheus"), String.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> metrics = scrapeMetrics();

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody())
                .contains("http_server_requests_seconds")
                .contains("hikaricp_connections")
                .contains("platform_readiness_component")
                .contains("platform_scheduled_job_executions_total")
                .contains("platform_scheduled_job_duration_seconds")
                .contains("platform_scheduled_job_last_success_timestamp_seconds");

        Thread.sleep(Duration.ofMillis(1100).toMillis());
        assertThat(scrapeMetrics().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> scrapeMetrics() {
        return rest.withBasicAuth(METRICS_USERNAME, METRICS_PASSWORD)
                .exchange(url("/actuator/prometheus"), HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
