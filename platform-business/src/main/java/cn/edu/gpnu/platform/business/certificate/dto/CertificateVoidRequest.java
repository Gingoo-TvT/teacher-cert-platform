package cn.edu.gpnu.platform.business.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CertificateVoidRequest {

    @NotBlank(message = "作废原因不能为空")
    private String reason;
}
