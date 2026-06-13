package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典类型保存入参。
 */
@Data
public class DictTypeSaveRequest {

    @NotBlank(message = "字典类型编码不能为空")
    private String typeCode;

    @NotBlank(message = "字典类型名称不能为空")
    private String typeName;

    private String description;
    private Integer sort;
    private Integer status;
}
