package cn.edu.gpnu.platform.boot.health;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Compose 五秒健康检查以内的就绪探针预算。 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.readiness")
public class ReadinessProbeProperties {

    private Duration timeout = Duration.ofSeconds(3);
    private Duration minioConnectTimeout = Duration.ofSeconds(1);
    private Duration minioReadTimeout = Duration.ofSeconds(2);
    private Duration minioCallTimeout = Duration.ofSeconds(2);

    @PostConstruct
    void validate() {
        if (!positive(timeout) || timeout.compareTo(Duration.ofSeconds(5)) >= 0) {
            throw new IllegalStateException("readiness 总预算必须大于 0 且小于 5 秒");
        }
        if (!positive(minioConnectTimeout) || !positive(minioReadTimeout) || !positive(minioCallTimeout)
                || minioCallTimeout.compareTo(timeout) > 0) {
            throw new IllegalStateException("readiness MinIO 超时必须为正数，且调用超时不得超过总预算");
        }
    }

    private boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
