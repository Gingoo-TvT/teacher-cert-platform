package cn.edu.gpnu.platform.business.testresult.vo;

import lombok.Data;

@Data
public class AbilityTestValidityVO {

    private Long studentId;
    private String assessmentYear;
    private String conclusion;
    private boolean validForCertificate;
    private String message;
}
