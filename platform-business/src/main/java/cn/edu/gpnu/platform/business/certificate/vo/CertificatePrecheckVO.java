package cn.edu.gpnu.platform.business.certificate.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CertificatePrecheckVO {

    private Long studentId;
    private String assessmentYear;
    private boolean passed;
    private List<String> missingItems = new ArrayList<>();
}
