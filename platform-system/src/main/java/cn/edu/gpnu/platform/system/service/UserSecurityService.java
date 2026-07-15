package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.system.vo.UserSecurityVO;

import java.time.LocalDateTime;
import java.util.Set;

public interface UserSecurityService {

    UserSecurityVO loadByUsername(String username);

    UserSecurityVO loadById(Long userId);

    Set<String> roleCodes(Long userId);

    Set<String> permissionCodes(Long userId);

    void markLoginSuccess(Long userId, String expectedPasswordHash, LocalDateTime loginAt);

    boolean markLoginFailure(Long userId, int lockThreshold, LocalDateTime lockedUntil);

    void unlockAfterExpired(Long userId);

    void changePassword(Long userId, String expectedPasswordHash, String newPasswordHash);
}
