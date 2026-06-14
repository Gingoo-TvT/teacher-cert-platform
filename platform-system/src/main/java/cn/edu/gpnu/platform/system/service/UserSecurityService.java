package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.system.vo.UserSecurityVO;

import java.time.LocalDateTime;
import java.util.Set;

public interface UserSecurityService {

    UserSecurityVO loadByUsername(String username);

    UserSecurityVO loadById(Long userId);

    Set<String> roleCodes(Long userId);

    Set<String> permissionCodes(Long userId);

    void markLoginSuccess(Long userId, LocalDateTime loginAt);

    void markLoginFailure(Long userId, int failedCount, LocalDateTime lockedUntil);

    void unlockAfterExpired(Long userId);

    void changePassword(Long userId, String passwordHash);
}
