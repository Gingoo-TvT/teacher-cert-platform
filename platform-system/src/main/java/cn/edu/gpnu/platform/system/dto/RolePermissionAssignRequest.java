package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RolePermissionAssignRequest {

    @NotNull(message = "权限不能为空")
    private List<Item> permissions = new ArrayList<>();

    @Data
    public static class Item {
        @NotNull(message = "权限ID不能为空")
        private Long permissionId;

        @NotBlank(message = "范围类型不能为空")
        private String scopeType;
    }
}
