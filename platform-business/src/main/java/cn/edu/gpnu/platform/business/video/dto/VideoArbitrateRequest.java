package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoArbitrateRequest {

    @NotNull(message = "终分不能为空")
    @Min(value = 0, message = "终分不能小于0")
    @Max(value = 100, message = "终分不能大于100")
    private Integer finalScore;

    @NotBlank(message = "结论不能为空")
    private String conclusion;

    private String comment;
}
