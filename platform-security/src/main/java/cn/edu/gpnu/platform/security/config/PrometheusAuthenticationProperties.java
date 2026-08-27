package cn.edu.gpnu.platform.security.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Prometheus 抓取使用的本地机器账号，不进入业务用户、JWT 或会话存储。 */
@Data
@ConfigurationProperties(prefix = "platform.observability.prometheus")
public class PrometheusAuthenticationProperties {

    private String username;
    private String password;

    @PostConstruct
    void validate() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Prometheus 抓取账号和密码必须显式配置");
        }
    }
}
