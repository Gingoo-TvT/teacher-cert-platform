package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class VideoScoreRequest {

    @NotNull(message = "总分不能为空")
    @Min(value = 0, message = "总分不能小于0")
    @Max(value = 100, message = "总分不能大于100")
    private Integer score;

    private Map<String, Integer> dimensionScores;

    private String comment;

    @NotBlank(message = "结论不能为空")
    private String conclusion;
}
