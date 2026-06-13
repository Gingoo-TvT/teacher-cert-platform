package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典项保存入参。
 */
@Data
public class DictItemSaveRequest {

    @NotBlank(message = "字典类型编码不能为空")
    private String typeCode;

    @NotBlank(message = "字典项编码不能为空")
    private String itemCode;

    @NotBlank(message = "字典项值不能为空")
    private String itemValue;

    private String parentCode;
    private Integer sort;
    private Integer status;
    private String yearVersion;
    private String extJson;
}
