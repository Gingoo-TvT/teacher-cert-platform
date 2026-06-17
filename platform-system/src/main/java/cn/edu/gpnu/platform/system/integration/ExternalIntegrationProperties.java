package cn.edu.gpnu.platform.system.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * M14 外部接口预留开关。默认全部关闭，关闭时不得改变一期运行行为。
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.integration")
public class ExternalIntegrationProperties {

    private final Identity identity = new Identity();
    private final Academic academic = new Academic();
    private final StudentRegistry studentRegistry = new StudentRegistry();
    private final ElectronicSignature electronicSignature = new ElectronicSignature();
    private final ElectronicLicense electronicLicense = new ElectronicLicense();
    private final SuperiorPlatform superiorPlatform = new SuperiorPlatform();

    @Data
    public static class Identity {
        private boolean enabled = false;
        private String provider = "local";
        private String casServerUrl = "";
        private String oauth2IssuerUri = "";
        private String oauth2ClientId = "";
    }

    @Data
    public static class Academic {
        private boolean enabled = false;
        private String endpoint = "";
    }

    @Data
    public static class StudentRegistry {
        private boolean enabled = false;
        private String endpoint = "";
    }

    @Data
    public static class ElectronicSignature {
        private boolean enabled = false;
        private String endpoint = "";
    }

    @Data
    public static class ElectronicLicense {
        private boolean enabled = false;
        private String endpoint = "";
    }

    @Data
    public static class SuperiorPlatform {
        private boolean enabled = false;
        private String endpoint = "";
    }
}
