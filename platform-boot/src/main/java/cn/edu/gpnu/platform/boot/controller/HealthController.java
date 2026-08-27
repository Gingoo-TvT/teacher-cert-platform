package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.boot.health.PlatformReadinessProbe;
import cn.edu.gpnu.platform.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查（验证统一响应/文档/跨域链路）。
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private static final String APP_NAME = "teacher-cert-platform";

    private final PlatformReadinessProbe readinessProbe;

    @Operation(summary = "存活探测")
    @GetMapping({"", "/liveness"})
    public Result<Map<String, Object>> liveness() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("app", APP_NAME);
        return Result.ok(data);
    }

    @Operation(summary = "就绪探测")
    @GetMapping("/readiness")
    public ResponseEntity<Result<Map<String, Object>>> readiness() {
        PlatformReadinessProbe.Snapshot snapshot = readinessProbe.check();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", snapshot.ready() ? "UP" : "DOWN");
        data.put("app", APP_NAME);
        data.put("components", snapshot.components());
        HttpStatus status = snapshot.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Result.ok(data));
    }
}
