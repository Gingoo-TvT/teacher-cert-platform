package cn.edu.gpnu.platform.system.integration;

import java.util.Optional;

/**
 * 统一身份认证 SPI 预留：CAS/OAuth2 等后续二期开启后实现。
 */
public interface IdentityProviderSpi {

    ExternalIntegrationStatus status();

    default Optional<String> resolveExternalUser(String ticketOrToken) {
        return Optional.empty();
    }
}
