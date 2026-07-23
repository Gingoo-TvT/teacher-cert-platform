package cn.edu.gpnu.platform.file.config;

import jakarta.annotation.PostConstruct;
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
    private int callTimeoutSeconds = 900;

    @PostConstruct
    public void validate() {
        if (presignExpirySeconds < 1
                || directUploadInitTimeoutSeconds < 1
                || directUploadCompleteTimeoutSeconds < 1
                || connectionTimeoutSeconds < 1
                || readTimeoutSeconds < 1
                || callTimeoutSeconds < 1) {
            throw new IllegalStateException("MinIO 超时与预签名有效期必须为正数");
        }
    }

    public String browserEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}
