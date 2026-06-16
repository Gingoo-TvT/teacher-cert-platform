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
}
