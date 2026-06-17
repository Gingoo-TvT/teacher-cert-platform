package cn.edu.gpnu.platform.system.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 一期只注册空实现。后续真实适配以 @ConditionalOnProperty 替换这些 SPI。
 */
@Configuration
public class NoopExternalIntegrationConfig {

    @Bean
    public IdentityProviderSpi identityProviderSpi() {
        return () -> ExternalIntegrationStatus.disabled("identity");
    }

    @Bean
    public AcademicSystemSpi academicSystemSpi() {
        return () -> ExternalIntegrationStatus.disabled("academic");
    }

    @Bean
    public StudentRegistrySpi studentRegistrySpi() {
        return () -> ExternalIntegrationStatus.disabled("studentRegistry");
    }

    @Bean
    public ElectronicSignatureSpi electronicSignatureSpi() {
        return () -> ExternalIntegrationStatus.disabled("electronicSignature");
    }

    @Bean
    public ElectronicLicenseSpi electronicLicenseSpi() {
        return () -> ExternalIntegrationStatus.disabled("electronicLicense");
    }

    @Bean
    public SuperiorPlatformSpi superiorPlatformSpi() {
        return () -> ExternalIntegrationStatus.disabled("superiorPlatform");
    }
}
