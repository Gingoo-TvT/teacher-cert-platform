package cn.edu.gpnu.platform.business.exemption.dto;

import lombok.Data;

@Data
public class ExemptionQuery {

    private String keyword;
    private String status;
    private Long collegeId;
    private Long studentId;
    private String assessmentYear;
    private String teachingSegment;
    private String subject;
}
