package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.service.RbacAuthorizationGuard;
import cn.edu.gpnu.platform.system.entity.SysRole;
import cn.edu.gpnu.platform.system.entity.SysRolePermission;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.entity.SysUserRole;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysRolePermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserDataScopeMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacAuthorizationGuardTest {

    private static final long OPERATOR_ID = 101L;
    private static final long TARGET_ID = 102L;
    private static final long ROLE_ID = 201L;
    private static final long OTHER_ROLE_ID = 202L;
    private static final long PERMISSION_ID = 301L;
    private static final long OTHER_PERMISSION_ID = 302L;
    private static final long COLLEGE_A = 401L;
    private static final long COLLEGE_B = 402L;
    private static final long COLLEGE_C = 403L;
    private static final long MAJOR_B = 501L;
    private static final long MAJOR_C = 502L;

    @Mock
    private SysRolePermissionMapper rolePermissionMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysUserDataScopeMapper userDataScopeMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysMajorMapper majorMapper;

    private RbacAuthorizationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RbacAuthorizationGuard(
                rolePermissionMapper,
                userRoleMapper,
                userDataScopeMapper,
                userMapper,
                roleMapper,
                majorMapper);

        UserContext.CurrentUser current = new UserContext.CurrentUser();
        current.setUserId(OPERATOR_ID);
        current.setPermissions(Set.of("system:role:manage"));
        UserContext.set(current);

        when(userMapper.selectById(OPERATOR_ID)).thenReturn(user(OPERATOR_ID, COLLEGE_A, "ENABLED"));
        when(userDataScopeMapper.selectCollegeIds(OPERATOR_ID)).thenReturn(List.of());
        when(userDataScopeMapper.selectMajorIds(OPERATOR_ID)).thenReturn(List.of());
        when(rolePermissionMapper.lockAuthorizationState()).thenReturn(PERMISSION_ID);
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void systemRoleManagementUsesExactDatabaseScopeAndIgnoresContextPermissionCodes() {
        when(rolePermissionMapper.selectScopeTypes(OPERATOR_ID, "system:role:manage"))
                .thenReturn(List.of("SCHOOL"));

        assertDenied(() -> guard.requireSystemRoleManagement());

        when(rolePermissionMapper.selectScopeTypes(OPERATOR_ID, "system:role:manage"))
                .thenReturn(List.of("SYSTEM"));
        assertThatCode(() -> guard.requireSystemRoleManagement()).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} dominates {1}: {2}")
    @MethodSource("scopeDominanceCases")
    void scopeDominanceMatchesTheExplicitPartialOrder(String operatorScope,
                                                       String requestedScope,
                                                       boolean allowed) {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, operatorScope)));

        if (allowed) {
            assertThatCode(() -> guard.assertCanGrantPermissions(Map.of(PERMISSION_ID, requestedScope)))
                    .doesNotThrowAnyException();
        } else {
            assertDenied(() -> guard.assertCanGrantPermissions(Map.of(PERMISSION_ID, requestedScope)));
        }
    }

    @Test
    void roleAssignmentInspectsPotentialPermissionsEvenWhenRoleIsDisabled() {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "SCHOOL")));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(0));
        when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "SYSTEM")));

        assertDenied(() -> guard.assertCanGrantRoles(List.of(ROLE_ID)));
        verify(rolePermissionMapper).selectByRoleId(ROLE_ID);
    }

    @Test
    void userAfterStateCannotExpandCollegeOrMajorBeyondTheOperatorRange() {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));
        when(userDataScopeMapper.selectCollegeIds(OPERATOR_ID)).thenReturn(List.of(COLLEGE_B));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(1));
        when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));
        when(majorMapper.selectCollegeIdForUpdate(MAJOR_B)).thenReturn(COLLEGE_B);
        when(majorMapper.selectCollegeIdForUpdate(MAJOR_C)).thenReturn(COLLEGE_C);

        assertThatCode(() -> guard.assertCanSetUserAuthorization(
                null, List.of(ROLE_ID), COLLEGE_B, List.of(COLLEGE_A), List.of(MAJOR_B)))
                .doesNotThrowAnyException();

        assertDenied(() -> guard.assertCanSetUserAuthorization(
                null, List.of(ROLE_ID), COLLEGE_C, List.of(), List.of()));
        assertDenied(() -> guard.assertCanSetUserAuthorization(
                null, List.of(ROLE_ID), COLLEGE_B, List.of(), List.of(MAJOR_C)));
        verify(majorMapper).selectCollegeIdForUpdate(MAJOR_B);
        verify(majorMapper).selectCollegeIdForUpdate(MAJOR_C);
    }

    @Test
    void explicitOperatorMajorAssignmentsRemainANarrowRange() {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));
        when(userDataScopeMapper.selectMajorIds(OPERATOR_ID)).thenReturn(List.of(MAJOR_B));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(1));
        when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));

        assertDenied(() -> guard.assertCanSetUserAuthorization(
                null, List.of(ROLE_ID), COLLEGE_A, List.of(), List.of(MAJOR_C)));
        assertDenied(() -> guard.assertCanSetUserAuthorization(
                null, List.of(ROLE_ID), COLLEGE_A, List.of(), List.of()));
        verify(majorMapper, never()).selectCollegeIdForUpdate(MAJOR_C);
    }

    @Test
    void schoolScopeDominatesCollegeWithoutConsultingConcreteRange() {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "SCHOOL")));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(1));
        when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));

        assertThatCode(() -> guard.assertCanSetUserAuthorization(
                null, List.of(ROLE_ID), COLLEGE_C, List.of(), List.of(MAJOR_C)))
                .doesNotThrowAnyException();
        verify(majorMapper, never()).selectById(MAJOR_C);
    }

    @Test
    void collegeScopeCanGrantSelfOnlyToAUserInItsConcreteCollegeRange() {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(1));
        when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "SELF")));

        assertThatCode(() -> guard.assertCanSetUserAuthorization(
                null, List.of(ROLE_ID), COLLEGE_A, List.of(), List.of()))
                .doesNotThrowAnyException();
        assertDenied(() -> guard.assertCanSetUserAuthorization(
                null, List.of(ROLE_ID), COLLEGE_B, List.of(), List.of()));
    }

    @Test
    void selfAndAssignedScopesCanDelegateOnlyToTheSameUser() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(1));
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, COLLEGE_A, "ENABLED"));
        when(userRoleMapper.selectRoleIds(OPERATOR_ID)).thenReturn(List.of());
        when(userRoleMapper.selectRoleIds(TARGET_ID)).thenReturn(List.of());
        when(userDataScopeMapper.selectCollegeIds(TARGET_ID)).thenReturn(List.of());
        when(userDataScopeMapper.selectMajorIds(TARGET_ID)).thenReturn(List.of());

        for (String scope : List.of("SELF", "ASSIGNED")) {
            when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                    .thenReturn(List.of(grant(PERMISSION_ID, scope)));
            when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                    .thenReturn(List.of(grant(PERMISSION_ID, scope)));

            assertThatCode(() -> guard.assertCanSetUserAuthorization(
                    OPERATOR_ID, List.of(ROLE_ID), COLLEGE_A, List.of(), List.of()))
                    .doesNotThrowAnyException();
            assertDenied(() -> guard.assertCanSetUserAuthorization(
                    TARGET_ID, List.of(ROLE_ID), COLLEGE_A, List.of(), List.of()));
        }
    }

    @Test
    void changingRolePermissionsChecksEveryExistingMembersConcreteRange() {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(1));
        when(rolePermissionMapper.selectByRoleId(ROLE_ID)).thenReturn(List.of());
        when(userRoleMapper.selectUserIdsByRoleId(ROLE_ID)).thenReturn(List.of(TARGET_ID));
        when(userMapper.selectBatchIds(List.of(TARGET_ID)))
                .thenReturn(List.of(user(TARGET_ID, COLLEGE_B, "ENABLED")));
        when(userDataScopeMapper.selectByUserIds(List.of(TARGET_ID))).thenReturn(List.of());
        when(userRoleMapper.selectByUserIdsExcludingRole(List.of(TARGET_ID), ROLE_ID))
                .thenReturn(List.of());

        assertDenied(() -> guard.assertCanSetRolePermissions(
                ROLE_ID, Map.of(PERMISSION_ID, "COLLEGE")));
        verify(userRoleMapper).selectUserIdsByRoleId(ROLE_ID);
        verify(userMapper).selectBatchIds(List.of(TARGET_ID));
        verify(userDataScopeMapper).selectByUserIds(List.of(TARGET_ID));
    }

    @Test
    void managingDisabledRoleChecksItsCurrentGrantAgainstMemberRange() {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(0));
        when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));
        when(userRoleMapper.selectUserIdsByRoleId(ROLE_ID)).thenReturn(List.of(TARGET_ID));
        when(userMapper.selectBatchIds(List.of(TARGET_ID)))
                .thenReturn(List.of(user(TARGET_ID, COLLEGE_B, "DISABLED")));
        when(userDataScopeMapper.selectByUserIds(List.of(TARGET_ID))).thenReturn(List.of());
        when(userRoleMapper.selectByUserIdsExcludingRole(List.of(TARGET_ID), ROLE_ID))
                .thenReturn(List.of());

        assertDenied(() -> guard.assertCanManageRole(ROLE_ID));
    }

    @Test
    void managingSharedRoleChecksEachMembersCompleteAuthorizationVector() {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(1));
        when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "COLLEGE")));
        when(userRoleMapper.selectUserIdsByRoleId(ROLE_ID)).thenReturn(List.of(TARGET_ID));
        when(userMapper.selectBatchIds(List.of(TARGET_ID)))
                .thenReturn(List.of(user(TARGET_ID, COLLEGE_A, "ENABLED")));
        when(userDataScopeMapper.selectByUserIds(List.of(TARGET_ID))).thenReturn(List.of());
        when(userRoleMapper.selectByUserIdsExcludingRole(List.of(TARGET_ID), ROLE_ID))
                .thenReturn(List.of(userRole(TARGET_ID, OTHER_ROLE_ID)));
        SysRolePermission incomparable = grant(OTHER_PERMISSION_ID, "SELF");
        incomparable.setRoleId(OTHER_ROLE_ID);
        when(rolePermissionMapper.selectByRoleIds(List.of(OTHER_ROLE_ID)))
                .thenReturn(List.of(incomparable));

        assertDenied(() -> guard.assertCanManageRole(ROLE_ID));
    }

    @Test
    void managingUserAllowsEqualAuthorizationButRejectsIncomparablePermission() {
        when(rolePermissionMapper.selectEffectiveByUserId(OPERATOR_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "SCHOOL")));
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, COLLEGE_B, "DISABLED"));
        when(userRoleMapper.selectRoleIds(TARGET_ID)).thenReturn(List.of(ROLE_ID));
        when(userDataScopeMapper.selectCollegeIds(TARGET_ID)).thenReturn(List.of());
        when(userDataScopeMapper.selectMajorIds(TARGET_ID)).thenReturn(List.of());
        when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                .thenReturn(List.of(grant(PERMISSION_ID, "SCHOOL")));

        assertThatCode(() -> guard.assertCanManageUser(TARGET_ID)).doesNotThrowAnyException();

        when(rolePermissionMapper.selectByRoleId(ROLE_ID))
                .thenReturn(List.of(grant(OTHER_PERMISSION_ID, "SELF")));
        assertDenied(() -> guard.assertCanManageUser(TARGET_ID));
    }

    private void assertDenied(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(403);
                    assertThat(exception.getMessage()).isEqualTo("当前操作无权限");
                    assertThat(exception.getMessage()).doesNotContain("system:");
                });
    }

    private static Stream<Arguments> scopeDominanceCases() {
        List<String> scopes = List.of("SYSTEM", "SCHOOL", "LOGIN_ALL", "COLLEGE", "SELF", "ASSIGNED", "NONE");
        Map<String, Set<String>> dominated = Map.of(
                "SYSTEM", Set.copyOf(scopes),
                "SCHOOL", Set.of("SCHOOL", "COLLEGE", "SELF", "ASSIGNED", "NONE"),
                "LOGIN_ALL", Set.of("LOGIN_ALL", "NONE"),
                "COLLEGE", Set.of("COLLEGE", "SELF", "NONE"),
                "SELF", Set.of("SELF", "NONE"),
                "ASSIGNED", Set.of("ASSIGNED", "NONE"),
                "NONE", Set.of("NONE"));
        return scopes.stream().flatMap(operator -> scopes.stream()
                .map(requested -> Arguments.of(operator, requested, dominated.get(operator).contains(requested))));
    }

    private SysRolePermission grant(long permissionId, String scopeType) {
        SysRolePermission relation = new SysRolePermission();
        relation.setPermissionId(permissionId);
        relation.setScopeType(scopeType);
        return relation;
    }

    private SysRole role(int status) {
        SysRole role = new SysRole();
        role.setId(ROLE_ID);
        role.setStatus(status);
        return role;
    }

    private SysUser user(long id, Long collegeId, String status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setCollegeId(collegeId);
        user.setStatus(status);
        return user;
    }

    private SysUserRole userRole(long userId, long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }
}
