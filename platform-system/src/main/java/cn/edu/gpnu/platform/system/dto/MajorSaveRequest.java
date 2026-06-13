package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MajorSaveRequest {

    @NotNull(message = "学院ID不能为空")
    private Long collegeId;

    @NotBlank(message = "校内专业代码不能为空")
    @Size(max = 64, message = "校内专业代码长度不能超过64")
    private String internalMajorCode;

    @NotBlank(message = "校内专业名称不能为空")
    @Size(max = 128, message = "校内专业名称长度不能超过128")
    private String internalMajorName;

    @Size(max = 64, message = "二级学科代码长度不能超过64")
    private String secondDisciplineCode;

    @Size(max = 128, message = "二级学科名称长度不能超过128")
    private String secondDisciplineName;

    private Integer pilotScopeFlag;

    @Size(max = 16, message = "年度版本长度不能超过16")
    private String yearVersion;

    private Integer sort;
    private Integer status;
}
