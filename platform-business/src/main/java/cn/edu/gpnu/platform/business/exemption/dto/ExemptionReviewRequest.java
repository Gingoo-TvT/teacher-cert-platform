package cn.edu.gpnu.platform.business.exemption.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExemptionReviewRequest {

    @NotBlank(message = "审核结论不能为空")
    private String action;

    private String comment;
}
