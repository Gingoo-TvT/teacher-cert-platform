package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MajorTrainingGoalSaveRequest {

    @NotEmpty(message = "培养目标不能为空")
    private List<String> trainingGoalCodes;
}
