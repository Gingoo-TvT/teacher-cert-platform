package cn.edu.gpnu.platform.business.material.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProcessStatusVO {

    private Long studentId;
    private String assessmentYear;
    private boolean qualified;
    private List<CategoryStatus> categories;

    @Data
    public static class CategoryStatus {
        private String category;
        private String categoryLabel;
        private boolean passed;
        private long totalCount;
        private long passedCount;
        private long failedCount;
    }
}
