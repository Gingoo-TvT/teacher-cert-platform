package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysPermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.UserSecurityService;
import cn.edu.gpnu.platform.system.vo.UserSecurityVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserSecurityServiceImpl implements UserSecurityService {

    private static final long SYSTEM_USER_ID = 0L;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    @Override
    public UserSecurityVO loadByUsername(String username) {
        SysUser user = userMapper.selectByUsername(username);
        return user == null ? null : toSecurityVO(user);
    }

    @Override
    public UserSecurityVO loadById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : toSecurityVO(user);
    }

    @Override
    public Set<String> roleCodes(Long userId) {
        return new LinkedHashSet<>(roleMapper.selectCodesByUserId(userId));
    }

    @Override
    public Set<String> permissionCodes(Long userId) {
        return new LinkedHashSet<>(permissionMapper.selectCodesByUserId(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markLoginSuccess(Long userId, String expectedPasswordHash, LocalDateTime loginAt) {
        requireUser(userId);
        int updated = userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getStatus, "ENABLED")
                .eq(SysUser::getPasswordHash, expectedPasswordHash)
                .set(SysUser::getLastLoginAt, loginAt)
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getUpdatedBy, SYSTEM_USER_ID)
                .set(SysUser::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException("登录成功状态更新发生并发冲突，请重新登录");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markLoginFailure(Long userId, int lockThreshold, LocalDateTime lockedUntil) {
        if (lockThreshold <= 0 || lockedUntil == null || !lockedUntil.isAfter(LocalDateTime.now())) {
            throw new BizException("登录锁定参数不合法");
        }
        if (userMapper.recordLoginFailure(userId, lockThreshold, lockedUntil) != 1) {
            throw new BizException("登录失败状态更新发生并发冲突，请重试");
        }
        return "LOCKED".equals(requireUser(userId).getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockAfterExpired(Long userId) {
        SysUser user = requireUser(userId);
        LocalDateTime expectedLockedUntil = user.getLockedUntil();
        LocalDateTime now = LocalDateTime.now();
        if (!"LOCKED".equals(user.getStatus())
                || expectedLockedUntil == null
                || expectedLockedUntil.isAfter(now)) {
            throw new BizException("账号锁定尚未过期或状态已变化");
        }
        int updated = userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getStatus, "LOCKED")
                .eq(SysUser::getLockedUntil, expectedLockedUntil)
                .le(SysUser::getLockedUntil, now)
                .set(SysUser::getStatus, "ENABLED")
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getUpdatedBy, SYSTEM_USER_ID)
                .set(SysUser::getUpdatedAt, now));
        if (updated != 1) {
            throw new BizException("账号解锁发生并发冲突，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String expectedPasswordHash, String newPasswordHash) {
        requireUser(userId);
        int updated = userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getPasswordHash, expectedPasswordHash)
                .eq(SysUser::getStatus, "ENABLED")
                .set(SysUser::getPasswordHash, newPasswordHash)
                .set(SysUser::getMustChangePwd, 0)
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getUpdatedBy, userId)
                .set(SysUser::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException("密码已被其他操作更新，请重新登录后重试");
        }
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private UserSecurityVO toSecurityVO(SysUser user) {
        UserSecurityVO vo = new UserSecurityVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setPasswordHash(user.getPasswordHash());
        vo.setRealName(user.getRealName());
        vo.setStatus(user.getStatus());
        vo.setUserType(user.getUserType());
        vo.setCollegeId(user.getCollegeId());
        vo.setStudentId(user.getStudentId());
        vo.setMustChangePwd(user.getMustChangePwd());
        vo.setFailedLoginCount(user.getFailedLoginCount());
        vo.setLockedUntil(user.getLockedUntil());
        vo.setRoles(roleCodes(user.getId()));
        vo.setPermissions(permissionCodes(user.getId()));
        return vo;
    }
}
