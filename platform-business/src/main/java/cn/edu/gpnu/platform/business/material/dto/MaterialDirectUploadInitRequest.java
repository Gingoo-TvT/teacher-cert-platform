package cn.edu.gpnu.platform.business.material.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MaterialDirectUploadInitRequest {

    private Long materialId;

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "考核年度不能为空")
    @Size(max = 16, message = "考核年度长度不能超过16")
    private String assessmentYear;

    @NotBlank(message = "材料类别不能为空")
    @Size(max = 64, message = "材料类别长度不能超过64")
    private String category;

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过255")
    private String fileName;

    @NotBlank(message = "文件类型不能为空")
    @Size(max = 128, message = "文件类型长度不能超过128")
    private String contentType;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件不能为空")
    private Long size;

    @NotBlank(message = "文件摘要不能为空")
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "文件摘要格式不合法")
    private String fileHash;

    @NotNull(message = "分片大小不能为空")
    @Min(value = 1, message = "分片大小必须大于0")
    private Long partSize;
}
