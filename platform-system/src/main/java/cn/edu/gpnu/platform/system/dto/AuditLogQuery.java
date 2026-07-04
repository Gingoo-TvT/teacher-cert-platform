package cn.edu.gpnu.platform.system.dto;

import lombok.Data;

@Data
public class AuditLogQuery {

    private String bizType;
    private Long bizId;
    private String operation;
    private Long operatorId;
    private Long studentId;
    private Long collegeId;
    private String batchNo;
    private String keyword;
    private String startTime;
    private String endTime;
    private Integer page;
    private Integer size;
}
