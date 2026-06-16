package cn.edu.gpnu.platform.business.training.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrainingReviewRequest {

    @NotBlank(message = "审核结论不能为空")
    private String action;

    private String comment;
}
