package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoUploadInitRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "考核年度不能为空")
    private String assessmentYear;

    @NotBlank(message = "文件MD5不能为空")
    private String fileMd5;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    private String contentType;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件不能为空")
    private Long size;

    @NotNull(message = "分片大小不能为空")
    @Min(value = 1, message = "分片大小必须大于0")
    private Long chunkSize;

    private Integer durationSeconds;
}
