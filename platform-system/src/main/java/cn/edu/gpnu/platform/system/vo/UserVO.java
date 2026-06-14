package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String workNo;
    private String email;
    private String phone;
    private String status;
    private String userType;
    private Long collegeId;
    private String collegeName;
    private Long studentId;
    private LocalDateTime lastLoginAt;
    private Integer mustChangePwd;
    private List<RoleVO> roles = new ArrayList<>();
    private List<Long> dataScopeCollegeIds = new ArrayList<>();
    private List<Long> dataScopeMajorIds = new ArrayList<>();
}
