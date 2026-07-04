package cn.edu.gpnu.platform.business.testresult.dto;

import lombok.Data;

@Data
public class AbilityTestQuery {

    private String keyword;
    private Long studentId;
    private Long collegeId;
    private String assessmentYear;
    private String conclusion;
    private String confirmStatus;
    // Phase 44e 真分页 rollout（P1-1）：page/size 随 DTO 由 Spring 隐式 query-param 绑定注入，
    // controller 的 list(AbilityTestQuery query) 是裸对象参数，无需另加 @RequestParam。
    private Integer page;
    private Integer size;
}
