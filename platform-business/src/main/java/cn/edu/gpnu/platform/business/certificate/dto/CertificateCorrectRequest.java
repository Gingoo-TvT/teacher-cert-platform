package cn.edu.gpnu.platform.business.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CertificateCorrectRequest {

    private String certNo;
    private String validUntil;
    private String teachingSubjectCode;
    private String teachingSubjectName;
    private String teachingSegment;
    private String trainingGoal;

    @NotBlank(message = "更正原因不能为空")
    private String reason;
}
