package cn.edu.gpnu.platform.business.material.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MaterialCompletedPartRequest {

    @NotNull(message = "分片序号不能为空")
    @Min(value = 1, message = "分片序号必须从1开始")
    private Integer partNumber;

    @NotBlank(message = "分片ETag不能为空")
    @Size(max = 255, message = "分片ETag长度不合法")
    private String etag;
}
