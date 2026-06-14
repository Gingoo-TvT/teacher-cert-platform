package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleSaveRequest {

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过64")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 128, message = "角色名称长度不能超过128")
    private String name;

    @Size(max = 255, message = "说明长度不能超过255")
    private String description;

    private Integer sort;
    private Integer status;
}
