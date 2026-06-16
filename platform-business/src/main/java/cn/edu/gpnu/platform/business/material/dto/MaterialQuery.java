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
}
