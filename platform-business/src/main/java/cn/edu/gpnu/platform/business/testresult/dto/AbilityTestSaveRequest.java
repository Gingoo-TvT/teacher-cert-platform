package cn.edu.gpnu.platform.business.testresult.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AbilityTestSaveRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "考核年度不能为空")
    @Size(max = 16, message = "考核年度过长")
    private String assessmentYear;

    @Size(max = 64, message = "任教学段过长")
    private String teachingSegment;

    @NotBlank(message = "考试组织方式不能为空")
    @Size(max = 64, message = "考试组织方式过长")
    private String examOrgMode;

    @Size(max = 64, message = "成绩文本过长")
    private String score;

    @NotBlank(message = "测试结论不能为空")
    @Size(max = 32, message = "测试结论过长")
    private String conclusion;
}
