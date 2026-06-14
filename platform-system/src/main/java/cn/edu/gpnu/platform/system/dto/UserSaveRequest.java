package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserSaveRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    private String username;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 128, message = "真实姓名长度不能超过128")
    private String realName;

    @Size(max = 64, message = "工号长度不能超过64")
    private String workNo;

    @Size(max = 128, message = "邮箱长度不能超过128")
    private String email;

    @Size(max = 32, message = "手机号长度不能超过32")
    private String phone;

    @NotBlank(message = "状态不能为空")
    private String status;

    @NotBlank(message = "用户类型不能为空")
    private String userType;

    private Long collegeId;

    private Long studentId;

    @NotNull(message = "角色不能为空")
    private List<Long> roleIds = new ArrayList<>();
}
