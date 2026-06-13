package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TrainingGoalConfigSaveRequest {

    @NotBlank(message = "培养目标编码不能为空")
    @Size(max = 64, message = "培养目标编码长度不能超过64")
    private String trainingGoalCode;

    @NotBlank(message = "默认任教学段不能为空")
    @Size(max = 64, message = "默认任教学段长度不能超过64")
    private String defaultSegment;

    @NotEmpty(message = "允许任教学段不能为空")
    private List<String> allowedSegments;

    @NotBlank(message = "默认实习地点不能为空")
    @Size(max = 64, message = "默认实习地点长度不能超过64")
    private String defaultInternshipLocation;

    @NotEmpty(message = "允许实习地点不能为空")
    private List<String> allowedInternshipLocations;

    private Integer status;
}
