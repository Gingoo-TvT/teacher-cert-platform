package cn.edu.gpnu.platform.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 身份证件号码应用层保护配置。
 *
 * <p>加密密钥与 HMAC pepper 必须是两个独立 secret，且只允许通过外部配置注入。
 */
@Data
@ConfigurationProperties(prefix = "platform.security.id-card")
public class IdCardProtectionProperties {

    /** Base64 编码的 32 字节 AES-256 密钥。 */
    private String encryptionKey;

    /** 至少 32 个 UTF-8 字节的 HMAC pepper。 */
    private String hmacPepper;
}
