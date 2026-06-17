package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SysParamUpdateRequest {

    @NotBlank(message = "参数值不能为空")
    private String paramValue;

    private String description;
}
