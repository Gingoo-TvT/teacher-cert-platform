package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysPermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.impl.UserSecurityServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class UserSecurityConcurrencyTest {

    private static final long USER_ID = 5101L;
    private static final String CURRENT_PASSWORD_HASH = "current-password-hash";

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "user-security-test");
        assistant.setCurrentNamespace(SysUserMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Test
    void loginFailureMapperAtomicallyIncrementsEnabledAccountUsingOldCount() throws Exception {
        Method method = SysUserMapper.class.getMethod(
                "recordLoginFailure", Long.class, int.class, LocalDateTime.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql)
                .contains("failed_login_count + 1 >= #{lockThreshold}")
                .contains("failed_login_count = failed_login_count + 1")
                .contains("status = 'ENABLED'")
                .contains("deleted = 0")
                .contains("updated_by = 0");
        assertThat(sql.indexOf("SET status"))
                .isLessThan(sql.indexOf("locked_until = CASE"));
        assertThat(sql.indexOf("locked_until = CASE"))
                .isLessThan(sql.indexOf("failed_login_count = failed_login_count + 1"));
    }

    @Test
    void loginFailureRejectsNonPositiveThresholdAndExpiredDeadline() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        UserSecurityServiceImpl service = service(userMapper);

        assertThatThrownBy(() -> service.markLoginFailure(USER_ID, 0, LocalDateTime.now().plusMinutes(5)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("参数不合法");
        assertThatThrownBy(() -> service.markLoginFailure(USER_ID, 5, LocalDateTime.now().minusSeconds(1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("参数不合法");

        verify(userMapper, never()).recordLoginFailure(any(), any(Integer.class), any(LocalDateTime.class));
    }

    @Test
    void loginSuccessRequiresSameEnabledPasswordSnapshot() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser user = enabledUser();
        LocalDateTime loginAt = LocalDateTime.of(2026, 7, 15, 16, 0);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service(userMapper).markLoginSuccess(USER_ID, CURRENT_PASSWORD_HASH, loginAt))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("并发冲突");

        ArgumentCaptor<LambdaUpdateWrapper<SysUser>> captor = updateCaptor();
        verify(userMapper).update(captor.capture());
        LambdaUpdateWrapper<SysUser> update = captor.getValue();
        assertThat(update.getSqlSegment()).contains("id", "status", "password_hash");
        assertThat(update.getParamNameValuePairs().values())
                .contains(USER_ID, "ENABLED", CURRENT_PASSWORD_HASH, loginAt, 0L);
        assertThat(update.getSqlSet()).doesNotContain("status", "password_hash");
    }

    @Test
    void expiredUnlockRequiresSameLockedStatusAndDeadline() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(1);
        SysUser user = enabledUser();
        user.setStatus("LOCKED");
        user.setLockedUntil(expiredAt);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service(userMapper).unlockAfterExpired(USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("并发冲突");

        ArgumentCaptor<LambdaUpdateWrapper<SysUser>> captor = updateCaptor();
        verify(userMapper).update(captor.capture());
        LambdaUpdateWrapper<SysUser> update = captor.getValue();
        assertThat(update.getSqlSegment()).contains("id", "status", "locked_until", "<=");
        assertThat(update.getParamNameValuePairs().values())
                .contains(USER_ID, "LOCKED", expiredAt, "ENABLED", 0L);
    }

    @Test
    void activeOrDeadlineLessLockCannotBeUnlocked() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser activeLock = enabledUser();
        activeLock.setStatus("LOCKED");
        activeLock.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        when(userMapper.selectById(USER_ID)).thenReturn(activeLock);

        assertThatThrownBy(() -> service(userMapper).unlockAfterExpired(USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尚未过期");

        activeLock.setLockedUntil(null);
        assertThatThrownBy(() -> service(userMapper).unlockAfterExpired(USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尚未过期");

        verify(userMapper, never()).update(any(LambdaUpdateWrapper.class));
    }

    private UserSecurityServiceImpl service(SysUserMapper userMapper) {
        return new UserSecurityServiceImpl(
                userMapper, mock(SysRoleMapper.class), mock(SysPermissionMapper.class));
    }

    private SysUser enabledUser() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setStatus("ENABLED");
        user.setPasswordHash(CURRENT_PASSWORD_HASH);
        user.setFailedLoginCount(0);
        return user;
    }

    private ArgumentCaptor<LambdaUpdateWrapper<SysUser>> updateCaptor() {
        return ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }
}
