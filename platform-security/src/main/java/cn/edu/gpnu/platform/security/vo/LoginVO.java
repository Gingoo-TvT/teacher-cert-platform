package cn.edu.gpnu.platform.security.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class LoginVO {

    private String accessToken;
    /** 仅供认证控制器写入 HttpOnly Cookie，禁止进入 JSON 响应。 */
    @JsonIgnore
    private String refreshToken;
    private Long expiresIn;
    private Boolean mustChangePwd;
    private MeVO user;
}
