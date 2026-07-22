package cn.edu.gpnu.platform.business.material.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MaterialDirectUploadCompleteRequest {

    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    private Long materialId;

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "考核年度不能为空")
    @Size(max = 16, message = "考核年度长度不能超过16")
    private String assessmentYear;

    @NotBlank(message = "材料类别不能为空")
    @Size(max = 64, message = "材料类别长度不能超过64")
    private String category;

    @Valid
    @NotNull(message = "上传分片不能为空")
    @Size(max = 10000, message = "上传分片数量超过限制")
    private List<MaterialCompletedPartRequest> parts;
}
