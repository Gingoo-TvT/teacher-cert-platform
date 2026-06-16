package cn.edu.gpnu.platform.business.material.dto;

import lombok.Data;

import java.util.List;

@Data
public class MaterialBatchDownloadRequest {

    private String keyword;
    private String status;
    private Long collegeId;
    private Long studentId;
    private String assessmentYear;
    private String category;
    private List<Long> ids;
}
