package cn.edu.gpnu.platform.system.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    private String passwordHash;
    private String realName;
    private String workNo;
    private String email;
    private String phone;
    private String status;
    private String userType;
    private Long collegeId;
    private Long studentId;
    private LocalDateTime lastLoginAt;
    private Integer mustChangePwd;
    private Integer failedLoginCount;
    private LocalDateTime lockedUntil;
}
