package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 字典类型保存入参。
 */
@Data
public class DictTypeSaveRequest {

    // Phase 44（PG-M4 第二轮整改）：typeCode 会被拼进 Redis 键，字符集必须是后端硬约束（红线 R7），
    // 与前端 DictTypeDrawer 的 /^[A-Za-z0-9_]+$/ 同口径；service 层仍会再校验并以 Locale.ROOT 转为小写
    // canonical identity，保证绕过 Bean Validation 的内部调用路径与大小写别名同样受约束。
    @NotBlank(message = "字典类型编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "字典类型编码仅支持英文、数字、下划线，且不超过64位")
    private String typeCode;

    @NotBlank(message = "字典类型名称不能为空")
    private String typeName;

    private String description;
    private Integer sort;
    private Integer status;
}
