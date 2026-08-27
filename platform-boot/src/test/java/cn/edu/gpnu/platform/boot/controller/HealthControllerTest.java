package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.boot.health.PlatformReadinessProbe;
import cn.edu.gpnu.platform.common.api.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void livenessStaysUpWhenReadinessIsDown() {
        PlatformReadinessProbe probe = mock(PlatformReadinessProbe.class);
        when(probe.check()).thenReturn(new PlatformReadinessProbe.Snapshot(false, Map.of("redis", "DOWN")));
        HealthController controller = new HealthController(probe);

        Result<Map<String, Object>> liveness = controller.liveness();
        ResponseEntity<Result<Map<String, Object>>> readiness = controller.readiness();

        assertThat(liveness.getData()).containsEntry("status", "UP");
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(readiness.getBody().getData())
                .containsEntry("status", "DOWN")
                .containsEntry("components", Map.of("redis", "DOWN"));
    }

    @Test
    void readinessReturnsOkWhenDependenciesAreUp() {
        PlatformReadinessProbe probe = mock(PlatformReadinessProbe.class);
        when(probe.check()).thenReturn(new PlatformReadinessProbe.Snapshot(true, Map.of("mysql", "UP")));
        HealthController controller = new HealthController(probe);

        ResponseEntity<Result<Map<String, Object>>> readiness = controller.readiness();

        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody().getData()).containsEntry("status", "UP");
    }
}
