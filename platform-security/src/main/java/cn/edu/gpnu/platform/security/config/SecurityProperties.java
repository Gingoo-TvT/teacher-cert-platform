package cn.edu.gpnu.platform.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "platform.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private Login login = new Login();

    @Data
    public static class Jwt {
        private String secret;
        private long accessTtlSeconds = 3600;
        private long refreshTtlSeconds = 604800;
    }

    @Data
    public static class Login {
        private int lockThreshold = 5;
        private int lockMinutes = 15;
        private int captchaTtlSeconds = 120;
    }
}
