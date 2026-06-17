package cn.edu.gpnu.platform.system.integration;

import java.util.Optional;

/**
 * 电子证照 SPI 预留。
 */
public interface ElectronicLicenseSpi {

    ExternalIntegrationStatus status();

    default Optional<String> publishLicense(String certNo) {
        return Optional.empty();
    }
}
