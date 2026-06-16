package cn.edu.gpnu.platform.business.training.vo;

import lombok.Data;

@Data
public class TrainingProfileVO {

    private Long id;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String identityType;
    private Long collegeId;
    private String assessmentYear;
    private String secondDisciplineCode;
    private String secondDisciplineName;
    private String internalMajorCode;
    private String internalMajorName;
    private String educationLevel;
    private String trainingGoal;
    private String internshipOrgMode;
    private String internshipLocation;
    private String teachingSegment;
    private Long teachingSubjectId;
    private String teachingSubjectCode;
    private String teachingSubjectName;
    private String interviewOrgMode;
    private String abilityTestConclusion;
    private String status;
    private String statusLabel;
    private Integer locked;
    private String firstReviewComment;
    private String secondReviewComment;
}
