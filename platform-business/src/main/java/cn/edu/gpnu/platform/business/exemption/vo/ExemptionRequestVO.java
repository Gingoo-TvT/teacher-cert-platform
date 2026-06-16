package cn.edu.gpnu.platform.business.exemption.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExemptionRequestVO {

    private Long id;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private Long collegeId;
    private String assessmentYear;
    private String teachingSegment;
    private String teachingSegmentLabel;
    private String subject;
    private String subjectLabel;
    private String basis;
    private String basisLabel;
    private String remark;
    private String finalStatus;
    private String statusLabel;
    private Integer includedInExam;
    private Integer locked;
    private String firstReviewStatus;
    private String firstReviewComment;
    private String secondReviewStatus;
    private String secondReviewComment;
    private LocalDateTime firstReviewTime;
    private LocalDateTime secondReviewTime;
    private List<ExemptionMaterialVO> materials;
}
