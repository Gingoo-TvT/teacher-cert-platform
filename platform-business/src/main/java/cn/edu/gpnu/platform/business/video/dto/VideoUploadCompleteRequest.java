package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class VideoUploadCompleteRequest {

    @NotBlank(message = "上传会话不能为空")
    @Size(max = 64, message = "上传会话长度不合法")
    private String uploadId;

    @Min(value = 1, message = "视频时长必须大于0")
    @Max(value = 86400, message = "视频时长不能超过86400秒")
    private Integer durationSeconds;

    @Valid
    @NotEmpty(message = "上传分片不能为空")
    @Size(max = 10000, message = "上传分片数量超过限制")
    private List<MultipartCompletedPartRequest> parts;
}
