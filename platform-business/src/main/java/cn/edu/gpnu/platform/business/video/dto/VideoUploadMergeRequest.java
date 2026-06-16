package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoUploadMergeRequest {

    @NotBlank(message = "上传会话不能为空")
    private String uploadId;

    private Integer durationSeconds;
}
