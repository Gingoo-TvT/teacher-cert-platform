package cn.edu.gpnu.platform.business.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TrainingProfileSaveRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    private Long collegeId;

    @NotBlank(message = "考核年度不能为空")
    @Size(max = 16, message = "考核年度过长")
    private String assessmentYear;

    @NotBlank(message = "二级学科代码不能为空")
    @Size(max = 64, message = "二级学科代码过长")
    private String secondDisciplineCode;

    @NotBlank(message = "二级学科名称不能为空")
    @Size(max = 128, message = "二级学科名称过长")
    private String secondDisciplineName;

    @Size(max = 64, message = "校内专业代码过长")
    private String internalMajorCode;

    @Size(max = 128, message = "校内专业名称过长")
    private String internalMajorName;

    @NotBlank(message = "学历层次不能为空")
    private String educationLevel;

    @NotBlank(message = "培养目标不能为空")
    private String trainingGoal;

    @NotBlank(message = "实习组织方式不能为空")
    private String internshipOrgMode;

    @NotBlank(message = "实习地点不能为空")
    private String internshipLocation;

    @NotBlank(message = "任教学段不能为空")
    private String teachingSegment;

    @NotBlank(message = "任教学科不能为空")
    private String teachingSubjectCode;

    @NotBlank(message = "面试组织方式不能为空")
    private String interviewOrgMode;

    private String abilityTestConclusion;
}
