package cn.edu.gpnu.platform.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 配置（application.yml: minio.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private String endpoint;
    private String publicEndpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String region = "us-east-1";
    private int presignExpirySeconds = 900;
    private boolean directUploadEnabled = true;
    private int directUploadInitTimeoutSeconds = 120;
    private int directUploadCompleteTimeoutSeconds = 300;
    private int connectionTimeoutSeconds = 10;
    private int readTimeoutSeconds = 30;

    public String browserEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}
