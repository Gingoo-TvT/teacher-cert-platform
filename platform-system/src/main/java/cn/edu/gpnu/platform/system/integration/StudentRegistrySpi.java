package cn.edu.gpnu.platform.system.integration;

import java.util.Map;

/**
 * 学籍系统接口 SPI 预留。
 */
public interface StudentRegistrySpi {

    ExternalIntegrationStatus status();

    default Map<String, Object> fetchStudentProfile(String studentNo) {
        return Map.of();
    }
}
