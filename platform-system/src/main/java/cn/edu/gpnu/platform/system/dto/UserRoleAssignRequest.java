package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserRoleAssignRequest {

    @NotNull(message = "角色不能为空")
    private List<Long> roleIds = new ArrayList<>();
}
