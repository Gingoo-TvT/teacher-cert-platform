package cn.edu.gpnu.platform.business.training.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("training_profile")
public class TrainingProfile extends BaseEntity {

    private Long studentId;
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
    private Integer locked;
    private Long firstReviewerId;
    private LocalDateTime firstReviewTime;
    private String firstReviewComment;
    private Long secondReviewerId;
    private LocalDateTime secondReviewTime;
    private String secondReviewComment;
}
