package cn.edu.gpnu.platform.business.certificate.dto;

import lombok.Data;

@Data
public class CertificateQuery {

    private String keyword;
    private Long studentId;
    private Long collegeId;
    private String assessmentYear;
    private String status;
}
