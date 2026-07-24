package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.service.RbacAuthorizationGuard;
import cn.edu.gpnu.platform.security.service.SecurityAdminServiceImpl;
import cn.edu.gpnu.platform.security.service.TokenRevocationService;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysPermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.impl.UserSecurityServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CredentialHardeningTest {

    private static final String LEGACY_PUBLIC_PASSWORD = "ChangeMe123!";
    private static final String STRONG_STAFF_PASSWORD = "Staff-Initial-2026!";
    private static final long ADMIN_ID = 800000000000003001L;

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "credential-test");
        assistant.setCurrentNamespace(SysUserMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Test
    void prodRejectsMissingPublicAndWeakStaffInitialPasswords() {
        assertStaffContextFails(null, "STAFF 初始口令未配置");
        assertStaffContextFails(LEGACY_PUBLIC_PASSWORD, "STAFF_INITIAL_PASSWORD 不安全");
        assertStaffContextFails("change-me-strong-staff-initial-password", "STAFF_INITIAL_PASSWORD 不安全");
        assertStaffContextFails("weak", "STAFF_INITIAL_PASSWORD 不安全");
    }

    @Test
    void prodAcceptsExplicitStrongStaffInitialPassword() {
        prodRunner(StaffOnlyConfiguration.class)
                .withPropertyValues("platform.security.initial-password=" + STRONG_STAFF_PASSWORD)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void prodRejectsMissingMalformedAndPublicAdminHashes() {
        assertAdminContextFails(null, "生产环境未配置 ADMIN_INITIAL_PASSWORD_HASH");
        assertAdminContextFails("not-a-bcrypt-hash", "必须是合法 BCrypt 哈希");

        String publicHash = new BCryptPasswordEncoder(10).encode(LEGACY_PUBLIC_PASSWORD);
        assertAdminContextFails(publicHash, "不能仍对应公开默认口令");
    }

    @Test
    void seedAdminIsBootstrappedOnceAndExistingPasswordIsPreservedOnRestart() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String bootstrapHash = encoder.encode("Admin-Bootstrap-2026!");

        prodRunner(AdminOnlyConfiguration.class)
                .withPropertyValues("platform.security.admin.initial-password-hash=" + bootstrapHash)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SysUserMapper userMapper = context.getBean(SysUserMapper.class);
                    TokenRevocationService revocationService = context.getBean(TokenRevocationService.class);

                    @SuppressWarnings("unchecked")
                    ArgumentCaptor<LambdaUpdateWrapper<SysUser>> updateCaptor =
                            ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
                    verify(userMapper).update(updateCaptor.capture());
                    assertThat(updateCaptor.getValue().getParamNameValuePairs()).containsValue(bootstrapHash);
                    verify(revocationService, times(2)).revoke(ADMIN_ID);
                    assertThat(encoder.matches(LEGACY_PUBLIC_PASSWORD, bootstrapHash)).isFalse();
                });

        prodRunner(AdminOnlyConfiguration.class)
                .withPropertyValues(
                        "platform.security.admin.initial-password-hash=" + bootstrapHash,
                        "test.admin.current-password=Already-Changed-2026!")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SysUserMapper userMapper = context.getBean(SysUserMapper.class);
                    TokenRevocationService revocationService = context.getBean(TokenRevocationService.class);
                    verify(userMapper, never()).update(any(LambdaUpdateWrapper.class));
                    verify(revocationService, never()).revoke(any());
                });
    }

    @Test
    void postCasRevocationFailureIsRetriedWhenBootstrapHashIsAlreadyStored() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String bootstrapHash = encoder.encode("Admin-Bootstrap-2026!");
        SysUser admin = user(ADMIN_ID, "STAFF", encoder.encode(LEGACY_PUBLIC_PASSWORD));
        admin.setUsername("admin");
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectByUsername("admin")).thenReturn(admin);
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        TokenRevocationService firstAttemptRevocation = mock(TokenRevocationService.class);
        doNothing().doThrow(new IllegalStateException("redis unavailable"))
                .when(firstAttemptRevocation).revoke(ADMIN_ID);
        AdminAccountInitializer firstAttempt = initializer(
                userMapper, encoder, firstAttemptRevocation, bootstrapHash);

        assertThatThrownBy(firstAttempt::bootstrapAdmin)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
        verify(userMapper).update(any(LambdaUpdateWrapper.class));
        verify(firstAttemptRevocation, times(2)).revoke(ADMIN_ID);

        // CAS 已提交但第二次 revoke 失败：下一次启动看到精确 bootstrap 哈希时补偿撤销，不再改库。
        admin.setPasswordHash(bootstrapHash);
        TokenRevocationService recoveryRevocation = mock(TokenRevocationService.class);
        AdminAccountInitializer recovery = initializer(
                userMapper, encoder, recoveryRevocation, bootstrapHash);

        recovery.bootstrapAdmin();

        verify(recoveryRevocation).revoke(ADMIN_ID);
        verify(userMapper, times(1)).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void studentResetUsesOldPasswordHashAsCompareAndSetGuard() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenRevocationService revocationService = mock(TokenRevocationService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        RbacAuthorizationGuard authorizationGuard = mock(RbacAuthorizationGuard.class);
        SysUser student = user(21L, "STUDENT", "old-student-hash");
        DataScopeContext.Scope schoolScope = new DataScopeContext.Scope();
        schoolScope.setScopeType(DataScopeContext.ScopeType.SYSTEM);

        when(userMapper.selectById(student.getId())).thenReturn(student);
        when(passwordEncoder.encode(anyString())).thenReturn("new-student-hash");
        when(dataScopeService.resolve("system:user:manage")).thenReturn(schoolScope);
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(0);

        SecurityAdminServiceImpl service = new SecurityAdminServiceImpl(
                userMapper, null, null, null, null, null, null, null, null,
                passwordEncoder, revocationService, null, dataScopeService, authorizationGuard);

        assertThatThrownBy(() -> service.resetPassword(student.getId()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("并发冲突");

        ArgumentCaptor<LambdaUpdateWrapper<SysUser>> updateCaptor = updateCaptor();
        verify(userMapper).update(updateCaptor.capture());
        LambdaUpdateWrapper<SysUser> update = updateCaptor.getValue();
        assertThat(update.getSqlSegment()).contains("id", "user_type", "password_hash");
        assertThat(update.getParamNameValuePairs().values())
                .contains(student.getId(), "STUDENT", "old-student-hash", "new-student-hash");
        verify(revocationService, never()).revoke(any());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void selfServicePasswordChangeRejectsStalePasswordHash() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser user = user(22L, "STAFF", "current-hash");
        when(userMapper.selectById(user.getId())).thenReturn(user);
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(0);
        UserSecurityServiceImpl service = userSecurityService(userMapper);

        assertThatThrownBy(() -> service.changePassword(user.getId(), "stale-hash", "new-hash"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("其他操作更新");

        ArgumentCaptor<LambdaUpdateWrapper<SysUser>> updateCaptor = updateCaptor();
        verify(userMapper).update(updateCaptor.capture());
        LambdaUpdateWrapper<SysUser> update = updateCaptor.getValue();
        assertThat(update.getSqlSegment()).contains("id", "password_hash", "status");
        assertThat(update.getSqlSet()).doesNotContain("status");
        assertThat(update.getParamNameValuePairs().values())
                .contains(user.getId(), "stale-hash", "new-hash", "ENABLED");
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void loginFailureUpdatesOnlySecurityFieldsWithoutWholeEntityWrite() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser user = user(23L, "STAFF", "current-hash");
        user.setStatus("LOCKED");
        user.setFailedLoginCount(5);
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(30);
        user.setLockedUntil(lockedUntil);
        when(userMapper.recordLoginFailure(user.getId(), 5, lockedUntil)).thenReturn(1);
        when(userMapper.selectById(user.getId())).thenReturn(user);
        UserSecurityServiceImpl service = userSecurityService(userMapper);

        assertThat(service.markLoginFailure(user.getId(), 5, lockedUntil)).isTrue();

        verify(userMapper).recordLoginFailure(user.getId(), 5, lockedUntil);
        verify(userMapper, never()).update(any(LambdaUpdateWrapper.class));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    private UserSecurityServiceImpl userSecurityService(SysUserMapper userMapper) {
        return new UserSecurityServiceImpl(userMapper, mock(SysRoleMapper.class), mock(SysPermissionMapper.class));
    }

    private AdminAccountInitializer initializer(
            SysUserMapper userMapper,
            PasswordEncoder passwordEncoder,
            TokenRevocationService tokenRevocationService,
            String bootstrapHash) {
        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userMapper, mock(Environment.class), passwordEncoder, tokenRevocationService);
        ReflectionTestUtils.setField(initializer, "adminInitialPasswordHash", bootstrapHash);
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        return initializer;
    }

    private SysUser user(long id, String userType, String passwordHash) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUserType(userType);
        user.setPasswordHash(passwordHash);
        return user;
    }

    private ArgumentCaptor<LambdaUpdateWrapper<SysUser>> updateCaptor() {
        return ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }

    private void assertStaffContextFails(String password, String expectedMessage) {
        ApplicationContextRunner runner = prodRunner(StaffOnlyConfiguration.class);
        if (password != null) {
            runner = runner.withPropertyValues("platform.security.initial-password=" + password);
        }
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(expectedMessage);
        });
    }

    private void assertAdminContextFails(String hash, String expectedMessage) {
        ApplicationContextRunner runner = prodRunner(AdminOnlyConfiguration.class);
        if (hash != null) {
            runner = runner.withPropertyValues("platform.security.admin.initial-password-hash=" + hash);
        }
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(expectedMessage);
        });
    }

    private ApplicationContextRunner prodRunner(Class<?> configuration) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(configuration);
    }

    @Configuration(proxyBeanMethods = false)
    static class StaffOnlyConfiguration {

        @Bean
        SecurityAdminServiceImpl securityAdminService(Environment environment) {
            return new SecurityAdminServiceImpl(
                    null, null, null, null, null, null, null, null, null,
                    new BCryptPasswordEncoder(10), null, environment, null, null);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AdminOnlyConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(10);
        }

        @Bean
        SysUserMapper userMapper(PasswordEncoder passwordEncoder, Environment environment) {
            SysUserMapper mapper = mock(SysUserMapper.class);
            SysUser admin = new SysUser();
            admin.setId(ADMIN_ID);
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode(environment.getProperty(
                    "test.admin.current-password", LEGACY_PUBLIC_PASSWORD)));
            when(mapper.selectByUsername("admin")).thenReturn(admin);
            when(mapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
            return mapper;
        }

        @Bean
        TokenRevocationService tokenRevocationService() {
            return mock(TokenRevocationService.class);
        }

        @Bean
        AdminAccountInitializer adminAccountInitializer(
                SysUserMapper userMapper,
                Environment environment,
                PasswordEncoder passwordEncoder,
                TokenRevocationService tokenRevocationService) {
            return new AdminAccountInitializer(userMapper, environment, passwordEncoder, tokenRevocationService);
        }
    }
}
