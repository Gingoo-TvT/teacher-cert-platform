package cn.edu.gpnu.platform.system.integration;

import java.util.Optional;

/**
 * 电子签章 SPI 预留。
 */
public interface ElectronicSignatureSpi {

    ExternalIntegrationStatus status();

    default Optional<String> signCertificate(String certNo, byte[] payload) {
        return Optional.empty();
    }
}
