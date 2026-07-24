package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.service.RbacAuthorizationGuard;
import cn.edu.gpnu.platform.security.service.SecurityAdminServiceImpl;
import cn.edu.gpnu.platform.security.service.TokenRevocationService;
import cn.edu.gpnu.platform.system.dto.UserRoleAssignRequest;
import cn.edu.gpnu.platform.system.dto.UserSaveRequest;
import cn.edu.gpnu.platform.system.entity.SysRole;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import cn.edu.gpnu.platform.system.mapper.SysPermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysRolePermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserDataScopeMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import cn.edu.gpnu.platform.system.service.CollegeParentGuard;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SecurityAdminServiceImplTest {

    private static final long USER_ID = 1001L;
    private static final long COLLEGE_ID = 2001L;
    private static final long OTHER_COLLEGE_ID = 2002L;
    private static final long STUDENT_ID = 3001L;
    private static final long ROLE_ID = 4001L;

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "security-admin-test");
        assistant.setCurrentNamespace(SysUserMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysPermissionMapper permissionMapper;
    @Mock
    private SysRolePermissionMapper rolePermissionMapper;
    @Mock
    private SysUserDataScopeMapper userDataScopeMapper;
    @Mock
    private SysCollegeMapper collegeMapper;
    @Mock
    private SysMajorMapper majorMapper;
    @Mock
    private CollegeParentGuard collegeParentGuard;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenRevocationService tokenRevocationService;
    @Mock
    private Environment environment;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private RbacAuthorizationGuard authorizationGuard;

    private SecurityAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SecurityAdminServiceImpl(
                userMapper,
                roleMapper,
                userRoleMapper,
                permissionMapper,
                rolePermissionMapper,
                userDataScopeMapper,
                collegeMapper,
                majorMapper,
                collegeParentGuard,
                passwordEncoder,
                tokenRevocationService,
                environment,
                dataScopeService,
                authorizationGuard);
        ReflectionTestUtils.setField(service, "initialPassword", "Staff-Initial-2026!");
        when(dataScopeService.resolve("system:user:manage")).thenReturn(scope(DataScopeContext.ScopeType.SCHOOL));
    }

    @Test
    void createUserRejectsStudentAccounts() {
        UserSaveRequest request = request("student-account", "STUDENT", null, STUDENT_ID);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(BizException.class)
                .hasMessage("通用用户管理仅支持创建 STAFF 账号");

        verify(userMapper, never()).selectByUsername(any());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void createUserRejectsStaffStudentBinding() {
        UserSaveRequest request = request("staff-account", "STAFF", null, STUDENT_ID);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(BizException.class)
                .hasMessage("STAFF 账号不能关联学生档案");

        verify(userMapper, never()).selectByUsername(any());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void updateUserRejectsUserTypeChange() {
        when(userMapper.selectById(USER_ID)).thenReturn(studentUser());
        UserSaveRequest request = request("student-account", "STAFF", COLLEGE_ID, null);

        assertThatThrownBy(() -> service.updateUser(USER_ID, request))
                .isInstanceOf(BizException.class)
                .hasMessage("用户类型不可通过通用用户管理修改");

        verify(userMapper, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateStudentRejectsChangesToAccountBinding() {
        when(userMapper.selectById(USER_ID)).thenReturn(studentUser());

        UserSaveRequest changedUsername = request("changed-username", "STUDENT", COLLEGE_ID, STUDENT_ID);
        UserSaveRequest changedCollege = request("student-account", "STUDENT", OTHER_COLLEGE_ID, STUDENT_ID);
        UserSaveRequest changedStudent = request("student-account", "STUDENT", COLLEGE_ID, STUDENT_ID + 1);

        assertStudentBindingChangeRejected(changedUsername);
        assertStudentBindingChangeRejected(changedCollege);
        assertStudentBindingChangeRejected(changedStudent);
        verify(userMapper, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateStudentWritesOnlyEditableProfileFields() {
        SysUser student = studentUser();
        when(userMapper.selectById(USER_ID)).thenReturn(student);
        when(userMapper.selectByUsername(student.getUsername())).thenReturn(student);
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role("STUDENT"));

        UserSaveRequest request = request(student.getUsername(), "STUDENT", COLLEGE_ID, STUDENT_ID);
        request.setRealName("更新后的姓名");
        service.updateUser(USER_ID, request);

        ArgumentCaptor<LambdaUpdateWrapper<SysUser>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertThat(sqlSet)
                .contains("real_name", "status", "updated_by", "updated_at")
                .doesNotContain("username", "user_type", "college_id", "student_id", "password_hash");
    }

    @Test
    void updateStudentRejectsAnyRoleOtherThanTheSystemStudentRole() {
        when(userMapper.selectById(USER_ID)).thenReturn(studentUser());
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role("COLLEGE_CLERK"));

        assertThatThrownBy(() -> service.updateUser(
                USER_ID, request("student-account", "STUDENT", COLLEGE_ID, STUDENT_ID)))
                .isInstanceOf(BizException.class)
                .hasMessage("学生账号只能绑定系统 STUDENT 角色");

        verify(authorizationGuard, never()).assertCanSetUserAuthorization(any(), any(), any(), any(), any());
        verify(userMapper, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void assignStudentRolesRejectsMixedOrNonStudentRoles() {
        when(userMapper.selectById(USER_ID)).thenReturn(studentUser());
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role("SYS_ADMIN"));
        UserRoleAssignRequest request = new UserRoleAssignRequest();
        request.setRoleIds(List.of(ROLE_ID));

        assertThatThrownBy(() -> service.assignUserRoles(USER_ID, request))
                .isInstanceOf(BizException.class)
                .hasMessage("学生账号只能绑定系统 STUDENT 角色");

        verify(authorizationGuard, never()).assertCanSetUserAuthorization(any(), any(), any(), any(), any());
        verify(userRoleMapper, never()).deleteByUserId(anyLong());
    }

    @Test
    void nonSchoolScopeIsRejectedBeforeTargetUserLookup() {
        when(dataScopeService.resolve("system:user:manage")).thenReturn(scope(DataScopeContext.ScopeType.COLLEGE));

        assertThatThrownBy(() -> service.updateUser(USER_ID, request("staff-account", "STAFF", null, null)))
                .isInstanceOf(BizException.class)
                .hasMessage("用户管理写操作仅限校级权限");
        assertThatThrownBy(() -> service.resetPassword(USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("用户管理写操作仅限校级权限");

        verify(userMapper, never()).selectById(anyLong());
    }

    @Test
    void userAuthorizationWritesLockBeforeReadingThePermissionSnapshot() {
        when(dataScopeService.resolve("system:user:manage"))
                .thenReturn(scope(DataScopeContext.ScopeType.COLLEGE));

        assertThatThrownBy(() -> service.resetPassword(USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("用户管理写操作仅限校级权限");

        InOrder order = inOrder(authorizationGuard, dataScopeService);
        order.verify(authorizationGuard).lockAuthorizationState();
        order.verify(dataScopeService).resolve("system:user:manage");
    }

    private void assertStudentBindingChangeRejected(UserSaveRequest request) {
        assertThatThrownBy(() -> service.updateUser(USER_ID, request))
                .isInstanceOf(BizException.class)
                .hasMessage("学生账号的用户名、学院和学生绑定只能通过学生管理维护");
    }

    private UserSaveRequest request(String username, String userType, Long collegeId, Long studentId) {
        UserSaveRequest request = new UserSaveRequest();
        request.setUsername(username);
        request.setRealName("测试用户");
        request.setStatus("ENABLED");
        request.setUserType(userType);
        request.setCollegeId(collegeId);
        request.setStudentId(studentId);
        request.setRoleIds(List.of(ROLE_ID));
        return request;
    }

    private SysUser studentUser() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("student-account");
        user.setPasswordHash("old-password-hash");
        user.setRealName("测试学生");
        user.setStatus("ENABLED");
        user.setUserType("STUDENT");
        user.setCollegeId(COLLEGE_ID);
        user.setStudentId(STUDENT_ID);
        return user;
    }

    private SysRole role(String code) {
        SysRole role = new SysRole();
        role.setId(ROLE_ID);
        role.setCode(code);
        return role;
    }

    private DataScopeContext.Scope scope(DataScopeContext.ScopeType scopeType) {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(scopeType);
        return scope;
    }
}
