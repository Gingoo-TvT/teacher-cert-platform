package cn.edu.gpnu.platform.exchange.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class ExchangeQuery {

    private Long batchId;
    private String keyword;
    private String assessmentYear;
    private Long collegeId;
    private String internalMajorCode;
    private String className;
    private String trainingGoal;
    private String teachingSegment;
    private String auditStatus;
    private String certStatus;

    @JsonIgnore
    private String contentBaseUrl;
}
