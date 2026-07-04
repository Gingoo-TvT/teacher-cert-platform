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
    // Phase 44e-rollout（P1-1 真分页）：随查询参数经 Spring 隐式对象绑定（?page=&size=）注入，见 ExemptionServiceImpl.list。
    private Integer page;
    private Integer size;
}
