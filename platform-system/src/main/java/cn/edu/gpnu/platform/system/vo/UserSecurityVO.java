package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class UserSecurityVO {

    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String status;
    private String userType;
    private Long collegeId;
    private Long studentId;
    private Integer mustChangePwd;
    private Integer failedLoginCount;
    private LocalDateTime lockedUntil;
    private Set<String> roles = new LinkedHashSet<>();
    private Set<String> permissions = new LinkedHashSet<>();
}
