package cn.edu.gpnu.platform.business.testresult.vo;

import cn.edu.gpnu.platform.business.exemption.vo.ExamSubjectVO;
import lombok.Data;

import java.util.List;

@Data
public class AbilityTestResultVO {

    private Long id;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private Long collegeId;
    private String assessmentYear;
    private String teachingSegment;
    private String examOrgMode;
    private String examOrgModeLabel;
    private List<ExamSubjectVO> examSubjects;
    private String score;
    private String conclusion;
    private String conclusionLabel;
    private String confirmStatus;
    private String confirmStatusLabel;
    private Integer locked;
    private boolean validForCertificate;
    private String exemptionRelation;
}
