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
    public void markLoginSuccess(Long userId, LocalDateTime loginAt) {
        requireUser(userId);
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getLastLoginAt, loginAt)
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markLoginFailure(Long userId, int failedCount, LocalDateTime lockedUntil) {
        SysUser user = requireUser(userId);
        user.setFailedLoginCount(failedCount);
        user.setLockedUntil(lockedUntil);
        if (lockedUntil != null) {
            user.setStatus("LOCKED");
        }
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockAfterExpired(Long userId) {
        requireUser(userId);
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getStatus, "ENABLED")
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String passwordHash) {
        requireUser(userId);
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPasswordHash, passwordHash)
                .set(SysUser::getMustChangePwd, 0)
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getStatus, "ENABLED"));
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
