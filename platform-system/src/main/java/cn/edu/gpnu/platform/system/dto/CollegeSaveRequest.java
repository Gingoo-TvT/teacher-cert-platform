package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CollegeSaveRequest {

    @NotBlank(message = "学院编码不能为空")
    @Size(max = 64, message = "学院编码长度不能超过64")
    private String code;

    @NotBlank(message = "学院名称不能为空")
    @Size(max = 128, message = "学院名称长度不能超过128")
    private String name;

    private Integer sort;
    private Integer status;
}
