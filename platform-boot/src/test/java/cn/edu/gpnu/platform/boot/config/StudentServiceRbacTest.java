package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.business.student.dto.StudentSaveRequest;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.student.service.impl.StudentServiceImpl;
import cn.edu.gpnu.platform.business.student.support.BirthDateValidator;
import cn.edu.gpnu.platform.business.student.support.IdCardValidator;
import cn.edu.gpnu.platform.business.student.support.NameValidator;
import cn.edu.gpnu.platform.business.student.support.StudentStatus;
import cn.edu.gpnu.platform.business.support.ReviewNotificationHelper;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.service.RbacAuthorizationGuard;
import cn.edu.gpnu.platform.system.entity.SysRole;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserDataScopeMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.CollegeParentGuard;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class StudentServiceRbacTest {

    private static final long STUDENT_ID = 3101L;
    private static final long USER_ID = 3201L;
    private static final long STUDENT_ROLE_ID = 3301L;
    private static final long OTHER_ROLE_ID = 3302L;
    private static final long COLLEGE_A = 3401L;
    private static final long COLLEGE_B = 3402L;
    private static final long MAJOR_A = 3501L;

    static {
        initTableInfo(StudentMapper.class, Student.class, "student-rbac-test");
        initTableInfo(SysUserMapper.class, SysUser.class, "student-user-rbac-test");
        initTableInfo(SysRoleMapper.class, SysRole.class, "student-role-rbac-test");
    }

    @Mock
    private StudentMapper studentMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysUserDataScopeMapper userDataScopeMapper;
    @Mock
    private RbacAuthorizationGuard authorizationGuard;
    @Mock
    private CollegeParentGuard collegeParentGuard;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private ParamService paramService;
    @Mock
    private IdCardValidator idCardValidator;
    @Mock
    private BirthDateValidator birthDateValidator;
    @Mock
    private NameValidator nameValidator;
    @Mock
    private ReviewNotificationHelper notificationHelper;
    @Mock
    private AuditLogService auditLogService;

    private StudentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentServiceImpl(
                studentMapper,
                userMapper,
                roleMapper,
                userRoleMapper,
                userDataScopeMapper,
                authorizationGuard,
                collegeParentGuard,
                passwordEncoder,
                dataScopeService,
                paramService,
                idCardValidator,
                birthDateValidator,
                nameValidator,
                notificationHelper,
                auditLogService);
    }

    @Test
    void createLocksAuthorizationBeforeTheFirstDatabaseRead() {
        stubValidFill();
        when(studentMapper.insert(any(Student.class))).thenAnswer(invocation -> {
            invocation.<Student>getArgument(0).setId(STUDENT_ID);
            return 1;
        });
        when(paramService.getBoolean("student.autoCreateAccount", false)).thenReturn(false);

        service.create(request(COLLEGE_A));

        InOrder order = inOrder(authorizationGuard, studentMapper);
        order.verify(authorizationGuard).lockAuthorizationState();
        order.verify(studentMapper, org.mockito.Mockito.times(2))
                .selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    void updateAndDeleteLockAuthorizationBeforeLoadingTheStudent() {
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.update(STUDENT_ID, request(COLLEGE_A)))
                .isInstanceOf(BizException.class)
                .hasMessage("学生不存在");
        InOrder updateOrder = inOrder(authorizationGuard, studentMapper);
        updateOrder.verify(authorizationGuard).lockAuthorizationState();
        updateOrder.verify(studentMapper).selectById(STUDENT_ID);

        assertThatThrownBy(() -> service.delete(STUDENT_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("学生不存在");
        verify(authorizationGuard, org.mockito.Mockito.times(2)).lockAuthorizationState();
        verify(studentMapper, org.mockito.Mockito.times(2)).selectById(STUDENT_ID);
    }

    @Test
    void deleteRejectsABoundAccountWithAnyNonStudentRoleBeforeDeletingTheStudent() {
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student(COLLEGE_A));
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(studentUser(COLLEGE_A));
        when(userRoleMapper.selectRoleIds(USER_ID)).thenReturn(List.of(STUDENT_ROLE_ID, OTHER_ROLE_ID));
        stubActiveStudentRole();

        assertThatThrownBy(() -> service.delete(STUDENT_ID))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(403);
                    assertThat(exception.getMessage())
                            .isEqualTo("学生账号授权异常，不能通过学生管理停用");
                });

        verify(studentMapper, never()).deleteById(STUDENT_ID);
        verify(authorizationGuard, never()).assertCanManageUser(anyLong());
    }

    @Test
    void deleteChecksTheBoundStudentAccountsAuthorizationBeforeDisablingIt() {
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student(COLLEGE_A));
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(studentUser(COLLEGE_A));
        when(userRoleMapper.selectRoleIds(USER_ID)).thenReturn(List.of(STUDENT_ROLE_ID));
        stubActiveStudentRole();
        doThrow(new BizException(403, "当前操作无权限"))
                .when(authorizationGuard).assertCanManageUser(USER_ID);

        assertThatThrownBy(() -> service.delete(STUDENT_ID))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(403);
                    assertThat(exception.getMessage()).isEqualTo("当前操作无权限");
                });

        verify(authorizationGuard).assertCanManageUser(USER_ID);
        verify(studentMapper, never()).deleteById(STUDENT_ID);
    }

    @Test
    void newStudentAccountIsAuthorizedBeforeUserAndRoleWrites() {
        stubValidFill();
        stubActiveStudentRole();
        when(studentMapper.insert(any(Student.class))).thenAnswer(invocation -> {
            invocation.<Student>getArgument(0).setId(STUDENT_ID);
            return 1;
        });
        when(paramService.getBoolean("student.autoCreateAccount", false)).thenReturn(true);
        when(paramService.getString("student.defaultPwd", "random")).thenReturn("random");
        when(passwordEncoder.encode(anyString())).thenReturn("student-password-hash");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.<SysUser>getArgument(0).setId(USER_ID);
            return 1;
        });

        assertThat(service.create(request(COLLEGE_A))).isEqualTo(STUDENT_ID);

        verify(authorizationGuard).assertCanSetUserAuthorization(
                null, List.of(STUDENT_ROLE_ID), COLLEGE_A, List.of(), List.of());
        InOrder order = inOrder(authorizationGuard, userMapper, userRoleMapper);
        order.verify(authorizationGuard).assertCanSetUserAuthorization(
                null, List.of(STUDENT_ROLE_ID), COLLEGE_A, List.of(), List.of());
        order.verify(userMapper).insert(any(SysUser.class));
        order.verify(userRoleMapper).upsert(anyLong(), org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(STUDENT_ROLE_ID), org.mockito.ArgumentMatchers.eq(0L));
    }

    @Test
    void unchangedStudentAuthorizationIsNotRegrantedDuringProfileUpdate() {
        Student student = student(COLLEGE_A);
        SysUser account = studentUser(COLLEGE_A);
        stubValidUpdate(student, account);
        SysRole disabled = studentRole();
        disabled.setStatus(0);
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(disabled);
        when(userRoleMapper.selectRoleIds(USER_ID)).thenReturn(List.of(STUDENT_ROLE_ID));
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.update(STUDENT_ID, request(COLLEGE_A));

        verify(authorizationGuard, never()).assertCanSetUserAuthorization(any(), any(), any(), any(), any());
        verify(userDataScopeMapper, never()).selectCollegeIds(anyLong());
        verify(userDataScopeMapper, never()).selectMajorIds(anyLong());
        verify(userMapper).update(any(LambdaUpdateWrapper.class));
        verify(userRoleMapper, never()).upsert(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void missingStudentRoleIsAuthorizedBeforeItIsAssigned() {
        Student student = student(COLLEGE_A);
        SysUser account = studentUser(COLLEGE_A);
        stubValidUpdate(student, account);
        stubActiveStudentRole();
        when(userRoleMapper.selectRoleIds(USER_ID)).thenReturn(List.of());
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.update(STUDENT_ID, request(COLLEGE_A));

        InOrder order = inOrder(authorizationGuard, userMapper, userRoleMapper);
        order.verify(authorizationGuard).assertCanSetUserAuthorization(
                USER_ID, List.of(STUDENT_ROLE_ID), COLLEGE_A, List.of(), List.of());
        order.verify(userMapper).update(any(LambdaUpdateWrapper.class));
        order.verify(userRoleMapper).upsert(anyLong(), org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(STUDENT_ROLE_ID), org.mockito.ArgumentMatchers.eq(0L));
    }

    @Test
    void collegeMoveChecksMergedRoleAndConcreteAssignedRangeBeforeAccountUpdate() {
        Student student = student(COLLEGE_A);
        SysUser account = studentUser(COLLEGE_A);
        stubValidUpdate(student, account);
        stubActiveStudentRole();
        when(userRoleMapper.selectRoleIds(USER_ID)).thenReturn(List.of(STUDENT_ROLE_ID));
        when(userDataScopeMapper.selectCollegeIds(USER_ID)).thenReturn(List.of(COLLEGE_A));
        when(userDataScopeMapper.selectMajorIds(USER_ID)).thenReturn(List.of(MAJOR_A));
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.update(STUDENT_ID, request(COLLEGE_B));

        verify(authorizationGuard).assertCanSetUserAuthorization(
                USER_ID, List.of(STUDENT_ROLE_ID), COLLEGE_B, List.of(COLLEGE_A), List.of(MAJOR_A));
        InOrder order = inOrder(authorizationGuard, userMapper, userRoleMapper);
        order.verify(authorizationGuard).assertCanSetUserAuthorization(
                USER_ID, List.of(STUDENT_ROLE_ID), COLLEGE_B, List.of(COLLEGE_A), List.of(MAJOR_A));
        order.verify(userMapper).update(any(LambdaUpdateWrapper.class));
        verify(userRoleMapper, never()).upsert(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void studentAccountWithAnyAdditionalRoleIsRejectedBeforeRbacWrites() {
        Student student = student(COLLEGE_A);
        SysUser account = studentUser(COLLEGE_A);
        stubValidUpdate(student, account);
        stubActiveStudentRole();
        when(userRoleMapper.selectRoleIds(USER_ID)).thenReturn(List.of(STUDENT_ROLE_ID, OTHER_ROLE_ID));

        assertThatThrownBy(() -> service.update(STUDENT_ID, request(COLLEGE_B)))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(403);
                    assertThat(exception.getMessage()).isEqualTo("学生账号仅允许绑定系统 STUDENT 角色");
                });

        verify(authorizationGuard, never()).assertCanSetUserAuthorization(any(), any(), any(), any(), any());
        verify(userDataScopeMapper, never()).selectCollegeIds(anyLong());
        verify(userDataScopeMapper, never()).selectMajorIds(anyLong());
        verify(userMapper, never()).update(any(LambdaUpdateWrapper.class));
        verify(userRoleMapper, never()).upsert(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void missingSystemStudentRoleFailsClosed() {
        stubValidFill();
        when(studentMapper.insert(any(Student.class))).thenAnswer(invocation -> {
            invocation.<Student>getArgument(0).setId(STUDENT_ID);
            return 1;
        });
        when(paramService.getBoolean("student.autoCreateAccount", false)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(COLLEGE_A)))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(403);
                    assertThat(exception.getMessage()).isEqualTo("系统 STUDENT 角色不可用");
                });

        verify(authorizationGuard, never()).assertCanSetUserAuthorization(any(), any(), any(), any(), any());
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(userRoleMapper, never()).upsert(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void disabledStudentRoleStillPassesThroughThePotentialGrantCeiling() {
        stubValidFill();
        SysRole disabled = studentRole();
        disabled.setStatus(0);
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(disabled);
        when(studentMapper.insert(any(Student.class))).thenAnswer(invocation -> {
            invocation.<Student>getArgument(0).setId(STUDENT_ID);
            return 1;
        });
        when(paramService.getBoolean("student.autoCreateAccount", false)).thenReturn(true);
        when(paramService.getString("student.defaultPwd", "random")).thenReturn("random");
        when(passwordEncoder.encode(anyString())).thenReturn("student-password-hash");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.<SysUser>getArgument(0).setId(USER_ID);
            return 1;
        });

        service.create(request(COLLEGE_A));

        verify(authorizationGuard).assertCanSetUserAuthorization(
                null, List.of(STUDENT_ROLE_ID), COLLEGE_A, List.of(), List.of());
        verify(userRoleMapper).upsert(anyLong(), org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(STUDENT_ROLE_ID), org.mockito.ArgumentMatchers.eq(0L));
    }

    private void stubValidFill() {
        when(studentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(idCardValidator.validate("hm_travel_permit", "H12345678")).thenReturn("H12345678");
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.SCHOOL);
        when(dataScopeService.resolve("student:edit")).thenReturn(scope);
    }

    private void stubValidUpdate(Student student, SysUser account) {
        stubValidFill();
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account);
        when(userMapper.selectByUsername(account.getUsername())).thenReturn(account);
    }

    private void stubActiveStudentRole() {
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(studentRole());
    }

    private StudentSaveRequest request(long collegeId) {
        StudentSaveRequest request = new StudentSaveRequest();
        request.setStudentNo("WS13-STUDENT-001");
        request.setName("测试学生");
        request.setGender("female");
        request.setIdCardType("hm_travel_permit");
        request.setIdCardNo("H12345678");
        request.setBirthDate("2001/1/2");
        request.setIdentityType("normal_student");
        request.setCollegeId(collegeId);
        request.setGrade("2026");
        request.setClassName("WS13测试班");
        return request;
    }

    private Student student(long collegeId) {
        Student student = new Student();
        student.setId(STUDENT_ID);
        student.setStudentNo("WS13-STUDENT-001");
        student.setName("测试学生");
        student.setGender("female");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo("H12345678");
        student.setBirthDate("2001/1/2");
        student.setIdentityType("normal_student");
        student.setCollegeId(collegeId);
        student.setStatus(StudentStatus.DRAFT.name());
        student.setLocked(0);
        return student;
    }

    private SysUser studentUser(long collegeId) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("WS13-STUDENT-001");
        user.setUserType("STUDENT");
        user.setStudentId(STUDENT_ID);
        user.setCollegeId(collegeId);
        return user;
    }

    private SysRole studentRole() {
        SysRole role = new SysRole();
        role.setId(STUDENT_ROLE_ID);
        role.setCode("STUDENT");
        role.setStatus(1);
        return role;
    }

    private static void initTableInfo(Class<?> mapperType, Class<?> entityType, String resource) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), resource);
        assistant.setCurrentNamespace(mapperType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
