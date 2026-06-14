package cn.edu.gpnu.platform.security.vo;

import lombok.Data;

@Data
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private Boolean mustChangePwd;
    private MeVO user;
}
