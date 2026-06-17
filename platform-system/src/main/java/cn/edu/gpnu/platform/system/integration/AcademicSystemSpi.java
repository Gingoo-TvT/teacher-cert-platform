package cn.edu.gpnu.platform.system.integration;

import java.util.Map;

/**
 * 教务系统接口 SPI 预留。
 */
public interface AcademicSystemSpi {

    ExternalIntegrationStatus status();

    default Map<String, Object> fetchTeachingPlan(String studentNo, String assessmentYear) {
        return Map.of();
    }
}
