package cn.edu.gpnu.platform.exchange.dto;

import lombok.Data;

@Data
public class ExchangeQuery {

    private String keyword;
    private String assessmentYear;
    private Long collegeId;
    private String internalMajorCode;
    private String className;
    private String trainingGoal;
    private String teachingSegment;
    private String auditStatus;
    private String certStatus;
}
