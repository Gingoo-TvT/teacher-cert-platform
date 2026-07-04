package cn.edu.gpnu.platform.business.material.dto;

import lombok.Data;

@Data
public class MaterialQuery {

    private String keyword;
    private String status;
    private Long collegeId;
    private Long studentId;
    private String assessmentYear;
    private String category;
    // P1-1 真分页 rollout（Phase 44e）：list() 走 selectPage，随 DTO 隐式绑定 ?page=&size=；
    // 其余调用方（如 batchDownload 经 toQuery 构造）不设置这两个字段，不影响其全量查询。
    private Integer page;
    private Integer size;
}
