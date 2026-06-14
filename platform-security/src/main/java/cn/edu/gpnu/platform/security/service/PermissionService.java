package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.context.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("pms")
public class PermissionService {

    public boolean has(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        return UserContext.hasPermission(code.trim());
    }
}
