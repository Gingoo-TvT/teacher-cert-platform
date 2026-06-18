package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoReturnRequest {

    @NotBlank(message = "退回意见不能为空")
    private String comment;
}
