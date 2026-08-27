package cn.edu.gpnu.platform.business.certificate.vo;

import lombok.Data;

@Data
public class CertificateVO {

    private Long id;
    private Long studentId;
    private Long collegeId;
    private String assessmentYear;
    private String certNo;
    private String studentNo;
    private String studentName;
    private String idCardType;
    private String idCardNo;
    private String educationLevel;
    private String trainingGoal;
    private String teachingSegment;
    private String teachingSubjectCode;
    private String teachingSubjectName;
    private String issuer;
    private String issueDate;
    private String validUntil;
    private String status;
    private String statusLabel;
    private String voidReason;
    private String reissueOriginCertNo;
    private String correctionReason;
    private String correctionRevision;
    private Integer locked;
}
