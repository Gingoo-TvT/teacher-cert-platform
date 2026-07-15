package cn.edu.gpnu.platform.security.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MeVO {

    private Long id;
    private String username;
    private String realName;
    private String userType;
    private Long collegeId;
    private Long studentId;
    private Boolean mustChangePwd;
    private Boolean userManagementWritable;
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
}
