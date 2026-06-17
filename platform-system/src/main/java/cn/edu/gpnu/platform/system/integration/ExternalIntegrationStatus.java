package cn.edu.gpnu.platform.system.integration;

public record ExternalIntegrationStatus(
        String code,
        boolean enabled,
        String provider,
        String endpoint,
        String message
) {

    public static ExternalIntegrationStatus disabled(String code) {
        return new ExternalIntegrationStatus(code, false, "noop", "", "外部接口未启用");
    }
}
