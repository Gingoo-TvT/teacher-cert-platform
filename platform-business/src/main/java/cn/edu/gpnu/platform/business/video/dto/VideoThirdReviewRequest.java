package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class VideoThirdReviewRequest extends VideoScoreRequest {

    @NotNull(message = "第三专家不能为空")
    private Long reviewerId;
}
