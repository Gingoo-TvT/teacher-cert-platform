package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

@Data
public class AuditLogVO {

    private Long id;
    private String bizType;
    private Long bizId;
    private String target;
    private Long operatorId;
    private String operatorName;
    private Long operatorCollegeId;
    private String operateTime;
    private String comment;
    private String oldStatus;
    private String newStatus;
    private String operation;
    private String ip;
}
