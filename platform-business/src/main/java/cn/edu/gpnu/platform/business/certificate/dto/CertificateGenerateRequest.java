package cn.edu.gpnu.platform.business.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CertificateGenerateRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "考核年度不能为空")
    private String assessmentYear;

    private String reissueOriginCertNo;
}
