package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 字典项保存入参。
 */
@Data
public class DictItemSaveRequest {

    // Phase 44（PG-M4 第二轮整改）：typeCode 会被拼进 Redis 键，字符集必须是后端硬约束（红线 R7），
    // 与前端 DictTypeDrawer 的 /^[A-Za-z0-9_]+$/ 同口径；service 层 normalizeTypeCode 仍会再校验一次，
    // 保证绕过 Bean Validation 的内部调用路径同样受约束。
    @NotBlank(message = "字典类型编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "字典类型编码仅支持英文、数字、下划线，且不超过64位")
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
