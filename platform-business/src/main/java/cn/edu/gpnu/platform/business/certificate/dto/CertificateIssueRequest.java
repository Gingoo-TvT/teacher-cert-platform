package cn.edu.gpnu.platform.business.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CertificateIssueRequest {

    @NotBlank(message = "签发人不能为空")
    private String issuer;

    @NotBlank(message = "签发日期不能为空")
    private String issueDate;
}
