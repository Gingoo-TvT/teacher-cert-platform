package cn.edu.gpnu.platform.system.integration;

import java.util.Optional;

/**
 * 上级平台报送 SPI 预留。
 */
public interface SuperiorPlatformSpi {

    ExternalIntegrationStatus status();

    default Optional<String> submitCertificate(String certNo) {
        return Optional.empty();
    }
}
