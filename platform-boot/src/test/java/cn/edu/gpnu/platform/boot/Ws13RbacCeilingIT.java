package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.security.service.RbacAuthorizationGuard;
import cn.edu.gpnu.platform.system.entity.SysPermission;
import cn.edu.gpnu.platform.system.entity.SysRole;
import cn.edu.gpnu.platform.system.entity.SysRolePermission;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.entity.SysUserDataScope;
import cn.edu.gpnu.platform.system.entity.SysUserRole;
import cn.edu.gpnu.platform.system.mapper.SysPermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysRolePermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserDataScopeMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=120"
})
class Ws13RbacCeilingIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";

    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long MAJOR_A = 810000000000000101L;
    private static final long MAJOR_B = 810000000000000201L;

    private static final long LOW_ROLE_ID = 913000000000000001L;
    private static final long PEER_ROLE_ID = 913000000000000002L;
    private static final long SCHOOL_ROLE_ADMIN_ROLE_ID = 913000000000000003L;
    private static final long TEMP_ROLE_ID = 913000000000000004L;

    private static final long LOW_USER_ID = 913000000000001001L;
    private static final long PEER_USER_ID = 913000000000001002L;
    private static final long TEMP_USER_ID = 913000000000001003L;
    private static final long HIGH_ADMIN_USER_ID = 913000000000001004L;

    private static final String LOW_ROLE_CODE = "WS13_LOW_ADMIN";
    private static final String PEER_ROLE_CODE = "WS13_PEER_ADMIN";
    private static final String SCHOOL_ROLE_ADMIN_CODE = "WS13_SCHOOL_ROLE_ADMIN";
    private static final String TEMP_ROLE_CODE = "WS13_TEMP_ROLE";
    private static final String LOW_USERNAME = "WS13_low_admin";
    private static final String PEER_USERNAME = "WS13_peer_admin";
    private static final String TEMP_USERNAME = "WS13_school_role_admin";
    private static final String HIGH_ADMIN_USERNAME = "WS13_high_admin";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysPermissionMapper permissionMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRolePermissionMapper rolePermissionMapper;

    @Autowired
    private SysUserDataScopeMapper userDataScopeMapper;

    @Autowired
    private RbacAuthorizationGuard authorizationGuard;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private SysRole sysAdminRole;
    private SysPermission userManagePermission;
    private SysPermission roleManagePermission;
    private SysPermission studentViewPermission;
    private SysPermission unheldPermission;
    private CoreRoleBaseline sysAdminRoleBaseline;

    @BeforeEach
    void setUpFixture() {
        cleanupFixture();
        resetSeedSysAdmin();

        sysAdminRole = requireRole("SYS_ADMIN");
        sysAdminRoleBaseline = captureCoreRole(sysAdminRole);
        userManagePermission = requirePermission("system:user:manage");
        roleManagePermission = requirePermission("system:role:manage");
        studentViewPermission = requirePermission("student:view");
        unheldPermission = requirePermission("cert:generate");

        seedRole(LOW_ROLE_ID, LOW_ROLE_CODE, "WS13低权管理员", 9131);
        seedRole(PEER_ROLE_ID, PEER_ROLE_CODE, "WS13同权管理员", 9132);
        seedRole(SCHOOL_ROLE_ADMIN_ROLE_ID, SCHOOL_ROLE_ADMIN_CODE, "WS13校级角色管理员", 9133);
        seedRole(TEMP_ROLE_ID, TEMP_ROLE_CODE, "WS13临时授权角色", 9134);

        seedLowPrivilegeGrants(LOW_ROLE_ID, 913000000000002000L);
        seedLowPrivilegeGrants(PEER_ROLE_ID, 913000000000002010L);
        seedRolePermission(913000000000002021L, SCHOOL_ROLE_ADMIN_ROLE_ID,
                roleManagePermission.getId(), "SCHOOL");

        String passwordHash = passwordEncoder.encode(INITIAL_PASSWORD);
        seedUser(LOW_USER_ID, LOW_USERNAME, "WS13低权管理员", "WS13_LOW", COLLEGE_A, passwordHash);
        seedUser(PEER_USER_ID, PEER_USERNAME, "WS13同权管理员", "WS13_PEER", COLLEGE_A, passwordHash);
        seedUser(TEMP_USER_ID, TEMP_USERNAME, "WS13校级角色管理员", "WS13_SCHOOL", COLLEGE_A, passwordHash);
        seedUser(HIGH_ADMIN_USER_ID, HIGH_ADMIN_USERNAME, "WS13高权管理员", "WS13_HIGH", null, passwordHash);

        seedUserRole(913000000000003001L, LOW_USER_ID, LOW_ROLE_ID);
        seedUserRole(913000000000003002L, PEER_USER_ID, PEER_ROLE_ID);
        seedUserRole(913000000000003003L, TEMP_USER_ID, SCHOOL_ROLE_ADMIN_ROLE_ID);
        seedUserRole(913000000000003004L, HIGH_ADMIN_USER_ID, sysAdminRole.getId());

        seedDataScope(913000000000004001L, LOW_USER_ID, COLLEGE_A, null);
        seedDataScope(913000000000004002L, LOW_USER_ID, null, MAJOR_A);
        seedDataScope(913000000000004003L, PEER_USER_ID, COLLEGE_A, null);
        seedDataScope(913000000000004004L, PEER_USER_ID, null, MAJOR_A);
    }

    @AfterEach
    void tearDownFixture() {
        try {
            restoreCoreRole(sysAdminRoleBaseline);
        } finally {
            try {
                cleanupFixture();
            } finally {
                resetSeedSysAdmin();
            }
        }
    }

    @Test
    void lowAdminCannotEscalateOrMutateHigherPrivilegeState() throws Exception {
        LoginResult low = readyLogin(LOW_USERNAME);
        assertThat(low.userManagementWritable()).isTrue();
        assertThat(low.roleManagementWritable()).isTrue();

        UserState ownAuthorization = userState(LOW_USER_ID);
        assertForbidden(exchange("/api/system/user/" + LOW_USER_ID + "/roles", HttpMethod.PUT,
                low.accessToken(), Map.of("roleIds", List.of(LOW_ROLE_ID, sysAdminRole.getId()))));
        assertThat(userState(LOW_USER_ID)).isEqualTo(ownAuthorization);

        RoleState lowRole = roleState(LOW_ROLE_ID);
        assertForbidden(exchange("/api/system/role/" + LOW_ROLE_ID + "/permissions", HttpMethod.PUT,
                low.accessToken(), permissionsBody(List.of(
                        grant(userManagePermission, "SCHOOL"),
                        grant(roleManagePermission, "SYSTEM"),
                        grant(studentViewPermission, "SYSTEM")
                ))));
        assertThat(roleState(LOW_ROLE_ID)).isEqualTo(lowRole);

        assertForbidden(exchange("/api/system/role/" + LOW_ROLE_ID + "/permissions", HttpMethod.PUT,
                low.accessToken(), permissionsBody(List.of(
                        grant(userManagePermission, "SCHOOL"),
                        grant(roleManagePermission, "SYSTEM"),
                        grant(studentViewPermission, "COLLEGE"),
                        grant(unheldPermission, "COLLEGE")
                ))));
        assertThat(roleState(LOW_ROLE_ID)).isEqualTo(lowRole);

        UserState higherAdmin = userState(HIGH_ADMIN_USER_ID);
        assertForbidden(exchange("/api/system/user/" + HIGH_ADMIN_USER_ID + "/reset-pwd", HttpMethod.PUT,
                low.accessToken(), null));
        assertThat(userState(HIGH_ADMIN_USER_ID)).isEqualTo(higherAdmin);

        assertForbidden(exchange("/api/system/user/" + HIGH_ADMIN_USER_ID, HttpMethod.PUT,
                low.accessToken(), Map.of(
                        "username", HIGH_ADMIN_USERNAME,
                        "realName", "WS13高权管理员",
                        "status", "DISABLED",
                        "userType", "STAFF",
                        "roleIds", List.of(sysAdminRole.getId())
                )));
        assertThat(userState(HIGH_ADMIN_USER_ID)).isEqualTo(higherAdmin);

        assertForbidden(exchange("/api/system/user/" + HIGH_ADMIN_USER_ID, HttpMethod.DELETE,
                low.accessToken(), null));
        assertThat(userState(HIGH_ADMIN_USER_ID)).isEqualTo(higherAdmin);

        RoleState protectedRole = roleState(sysAdminRole.getId());
        assertForbidden(exchange("/api/system/role/" + sysAdminRole.getId() + "/permissions", HttpMethod.PUT,
                low.accessToken(), Map.of("permissions", List.of())));
        assertThat(roleState(sysAdminRole.getId())).isEqualTo(protectedRole);

        assertForbidden(exchange("/api/system/role/" + sysAdminRole.getId(), HttpMethod.PUT,
                low.accessToken(), Map.of(
                        "code", sysAdminRole.getCode(),
                        "name", "WS13越权修改",
                        "description", "must remain unchanged",
                        "sort", sysAdminRole.getSort(),
                        "status", 0
                )));
        assertThat(roleState(sysAdminRole.getId())).isEqualTo(protectedRole);

        UserState ownScope = userState(LOW_USER_ID);
        assertForbidden(exchange("/api/system/user/" + LOW_USER_ID + "/data-scope", HttpMethod.PUT,
                low.accessToken(), Map.of(
                        "collegeIds", List.of(COLLEGE_A, COLLEGE_B),
                        "majorIds", List.of(MAJOR_A)
                )));
        assertThat(userState(LOW_USER_ID)).isEqualTo(ownScope);

        assertForbidden(exchange("/api/system/user/" + LOW_USER_ID + "/data-scope", HttpMethod.PUT,
                low.accessToken(), Map.of(
                        "collegeIds", List.of(COLLEGE_A),
                        "majorIds", List.of(MAJOR_A, MAJOR_B)
                )));
        assertThat(userState(LOW_USER_ID)).isEqualTo(ownScope);
    }

    @Test
    void authorizedDelegationAndEqualPrivilegeManagementRemainAvailable() throws Exception {
        LoginResult low = readyLogin(LOW_USERNAME);
        assertThat(low.userManagementWritable()).isTrue();
        assertThat(low.roleManagementWritable()).isTrue();
        assertOk(exchange("/api/system/permission/tree", HttpMethod.GET, low.accessToken(), null));

        RoleState peerRoleBefore = roleState(PEER_ROLE_ID);
        assertOk(exchange("/api/system/role/" + PEER_ROLE_ID + "/permissions", HttpMethod.PUT,
                low.accessToken(), permissionsBody(List.of(
                        grant(userManagePermission, "SCHOOL"),
                        grant(roleManagePermission, "SYSTEM"),
                        grant(studentViewPermission, "COLLEGE")
                ))));
        assertThat(roleState(PEER_ROLE_ID).permissions()).isEqualTo(peerRoleBefore.permissions());

        assertOk(exchange("/api/system/role/" + PEER_ROLE_ID, HttpMethod.PUT, low.accessToken(), Map.of(
                "code", PEER_ROLE_CODE,
                "name", "WS13同权管理员（已复核）",
                "description", "equal privilege role",
                "sort", peerRoleBefore.sort(),
                "status", peerRoleBefore.status()
        )));
        RoleState peerRoleAfter = roleState(PEER_ROLE_ID);
        assertThat(peerRoleAfter.name()).isEqualTo("WS13同权管理员（已复核）");
        assertThat(peerRoleAfter.permissions()).isEqualTo(peerRoleBefore.permissions());

        UserState peerBefore = userState(PEER_USER_ID);
        assertOk(exchange("/api/system/user/" + PEER_USER_ID + "/reset-pwd", HttpMethod.PUT,
                low.accessToken(), null));
        UserState peerAfter = userState(PEER_USER_ID);
        assertThat(peerAfter.passwordHash()).isNotEqualTo(peerBefore.passwordHash());
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD, peerAfter.passwordHash())).isTrue();
        assertThat(peerAfter.status()).isEqualTo(peerBefore.status());
        assertThat(peerAfter.roleIds()).isEqualTo(peerBefore.roleIds());
        assertThat(peerAfter.collegeIds()).isEqualTo(peerBefore.collegeIds());
        assertThat(peerAfter.majorIds()).isEqualTo(peerBefore.majorIds());

        LoginResult schoolRoleAdmin = readyLogin(TEMP_USERNAME);
        assertThat(schoolRoleAdmin.roleManagementWritable()).isFalse();
        RoleState tempRoleBefore = roleState(TEMP_ROLE_ID);
        assertForbidden(exchange("/api/system/role/" + TEMP_ROLE_ID, HttpMethod.PUT,
                schoolRoleAdmin.accessToken(), Map.of(
                        "code", TEMP_ROLE_CODE,
                        "name", "WS13校级越权修改",
                        "description", "must remain unchanged",
                        "sort", tempRoleBefore.sort(),
                        "status", tempRoleBefore.status()
                )));
        assertThat(roleState(TEMP_ROLE_ID)).isEqualTo(tempRoleBefore);

        LoginResult sysAdmin = readyLogin("test_sys_admin");
        assertThat(sysAdmin.userManagementWritable()).isTrue();
        assertThat(sysAdmin.roleManagementWritable()).isTrue();

        assertOk(exchange("/api/system/role/" + TEMP_ROLE_ID + "/permissions", HttpMethod.PUT,
                sysAdmin.accessToken(), permissionsBody(List.of(grant(userManagePermission, "SCHOOL")))));
        assertThat(roleState(TEMP_ROLE_ID).permissions())
                .containsExactly(new PermissionState(userManagePermission.getId(), "SCHOOL"));

        assertOk(exchange("/api/system/user/" + TEMP_USER_ID + "/roles", HttpMethod.PUT,
                sysAdmin.accessToken(), Map.of("roleIds", List.of(TEMP_ROLE_ID))));
        assertThat(userState(TEMP_USER_ID).roleIds()).containsExactly(TEMP_ROLE_ID);

        UserState lowBeforeReset = userState(LOW_USER_ID);
        assertOk(exchange("/api/system/user/" + LOW_USER_ID + "/reset-pwd", HttpMethod.PUT,
                sysAdmin.accessToken(), null));
        UserState lowAfterReset = userState(LOW_USER_ID);
        assertThat(lowAfterReset.passwordHash()).isNotEqualTo(lowBeforeReset.passwordHash());
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD, lowAfterReset.passwordHash())).isTrue();
        assertThat(lowAfterReset.mustChangePwd()).isEqualTo(1);
        assertThat(lowAfterReset.roleIds()).isEqualTo(lowBeforeReset.roleIds());
        assertThat(lowAfterReset.collegeIds()).isEqualTo(lowBeforeReset.collegeIds());
        assertThat(lowAfterReset.majorIds()).isEqualTo(lowBeforeReset.majorIds());
    }

    @Test
    void sharedRoleCannotBeChangedThroughAMemberWithHigherAuthorization() throws Exception {
        seedRolePermission(913000000000002099L, TEMP_ROLE_ID,
                studentViewPermission.getId(), "COLLEGE");
        seedUserRole(913000000000003099L, HIGH_ADMIN_USER_ID, TEMP_ROLE_ID);
        LoginResult low = readyLogin(LOW_USERNAME);
        RoleState before = roleState(TEMP_ROLE_ID);

        assertForbidden(exchange("/api/system/role/" + TEMP_ROLE_ID, HttpMethod.PUT,
                low.accessToken(), Map.of(
                        "code", TEMP_ROLE_CODE,
                        "name", "WS13共享角色越权修改",
                        "description", "must remain unchanged",
                        "sort", before.sort(),
                        "status", 0
                )));
        assertForbidden(exchange("/api/system/role/" + TEMP_ROLE_ID + "/permissions", HttpMethod.PUT,
                low.accessToken(), Map.of("permissions", List.of())));
        assertForbidden(exchange("/api/system/role/" + TEMP_ROLE_ID, HttpMethod.DELETE,
                low.accessToken(), null));

        assertThat(roleState(TEMP_ROLE_ID)).isEqualTo(before);
        assertThat(userState(HIGH_ADMIN_USER_ID).roleIds())
                .contains(sysAdminRole.getId(), TEMP_ROLE_ID);
    }

    @Test
    void authorizationMutationsSerializeOnOneDatabaseLock() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        authorizationGuard.lockAuthorizationState();
                        firstHasLock.countDown();
                        awaitLatch(releaseFirst);
                    }));
            assertThat(firstHasLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        secondStarted.countDown();
                        authorizationGuard.lockAuthorizationState();
                    }));
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out while holding the RBAC serialization lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while holding the RBAC serialization lock", exception);
        }
    }

    private void seedLowPrivilegeGrants(long roleId, long idBase) {
        seedRolePermission(idBase + 1, roleId, userManagePermission.getId(), "SCHOOL");
        seedRolePermission(idBase + 2, roleId, roleManagePermission.getId(), "SYSTEM");
        seedRolePermission(idBase + 3, roleId, studentViewPermission.getId(), "COLLEGE");
    }

    private void seedRole(long id, String code, String name, int sort) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName(name);
        role.setDescription("WS13 RBAC ceiling integration fixture");
        role.setSort(sort);
        role.setStatus(1);
        roleMapper.insert(role);
    }

    private void seedUser(long id, String username, String realName, String workNo,
                          Long collegeId, String passwordHash) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setRealName(realName);
        user.setWorkNo(workNo);
        user.setStatus("ENABLED");
        user.setUserType("STAFF");
        user.setCollegeId(collegeId);
        user.setStudentId(null);
        user.setMustChangePwd(1);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(null);
        userMapper.insert(user);
    }

    private void seedRolePermission(long id, long roleId, long permissionId, String scopeType) {
        SysRolePermission relation = new SysRolePermission();
        relation.setId(id);
        relation.setRoleId(roleId);
        relation.setPermissionId(permissionId);
        relation.setScopeType(scopeType);
        rolePermissionMapper.insert(relation);
    }

    private void seedUserRole(long id, long userId, long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setId(id);
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        userRoleMapper.insert(relation);
    }

    private void seedDataScope(long id, long userId, Long collegeId, Long majorId) {
        SysUserDataScope scope = new SysUserDataScope();
        scope.setId(id);
        scope.setUserId(userId);
        scope.setCollegeId(collegeId);
        scope.setMajorId(majorId);
        userDataScopeMapper.insert(scope);
    }

    private void cleanupFixture() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE operator_id IN (?, ?, ?, ?)",
                LOW_USER_ID, PEER_USER_ID, TEMP_USER_ID, HIGH_ADMIN_USER_ID);
        jdbcTemplate.update("""
                DELETE FROM audit_log
                 WHERE biz_id IN (?, ?, ?, ?, ?, ?, ?, ?)
                """, LOW_ROLE_ID, PEER_ROLE_ID, SCHOOL_ROLE_ADMIN_ROLE_ID, TEMP_ROLE_ID,
                LOW_USER_ID, PEER_USER_ID, TEMP_USER_ID, HIGH_ADMIN_USER_ID);
        jdbcTemplate.update("DELETE FROM sys_user_data_scope WHERE user_id IN (?, ?, ?, ?)",
                LOW_USER_ID, PEER_USER_ID, TEMP_USER_ID, HIGH_ADMIN_USER_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?, ?)",
                LOW_USER_ID, PEER_USER_ID, TEMP_USER_ID, HIGH_ADMIN_USER_ID);
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN (?, ?, ?, ?)",
                LOW_ROLE_ID, PEER_ROLE_ID, SCHOOL_ROLE_ADMIN_ROLE_ID, TEMP_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?, ?)",
                LOW_USER_ID, PEER_USER_ID, TEMP_USER_ID, HIGH_ADMIN_USER_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE id IN (?, ?, ?, ?)",
                LOW_ROLE_ID, PEER_ROLE_ID, SCHOOL_ROLE_ADMIN_ROLE_ID, TEMP_ROLE_ID);
    }

    private CoreRoleBaseline captureCoreRole(SysRole role) {
        List<CoreGrantBaseline> permissions = rolePermissionMapper.selectByRoleId(role.getId()).stream()
                .map(relation -> new CoreGrantBaseline(
                        relation.getId(), relation.getPermissionId(), relation.getScopeType(),
                        relation.getCreatedBy(), relation.getCreatedAt(), relation.getUpdatedBy(), relation.getUpdatedAt()))
                .toList();
        return new CoreRoleBaseline(
                role.getId(), role.getCode(), role.getName(), role.getDescription(), role.getSort(), role.getStatus(),
                role.getCreatedBy(), role.getCreatedAt(), role.getUpdatedBy(), role.getUpdatedAt(), permissions);
    }

    private void restoreCoreRole(CoreRoleBaseline baseline) {
        if (baseline == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE sys_role
                   SET code = ?, name = ?, description = ?, sort = ?, status = ?,
                       created_by = ?, created_at = ?, updated_by = ?, updated_at = ?, deleted = 0
                 WHERE id = ?
                """, baseline.code(), baseline.name(), baseline.description(), baseline.sort(), baseline.status(),
                baseline.createdBy(), baseline.createdAt(), baseline.updatedBy(), baseline.updatedAt(), baseline.id());
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", baseline.id());
        for (CoreGrantBaseline permission : baseline.permissions()) {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_permission
                        (id, role_id, permission_id, scope_type, created_by, created_at, updated_by, updated_at, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, permission.id(), baseline.id(), permission.permissionId(), permission.scopeType(),
                    permission.createdBy(), permission.createdAt(), permission.updatedBy(), permission.updatedAt());
        }
    }

    private void resetSeedSysAdmin() {
        SysUser user = userMapper.selectByUsername("test_sys_admin");
        if (user == null) {
            return;
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setStatus("ENABLED");
        user.setMustChangePwd(1);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getLastLoginAt, null));
    }

    private UserState userState(long userId) {
        SysUser user = userMapper.selectById(userId);
        assertThat(user).as("user %s must remain active", userId).isNotNull();
        return new UserState(
                user.getId(), user.getUsername(), user.getPasswordHash(), user.getRealName(), user.getWorkNo(),
                user.getStatus(), user.getUserType(), user.getCollegeId(), user.getStudentId(), user.getMustChangePwd(),
                List.copyOf(userRoleMapper.selectRoleIds(userId)),
                List.copyOf(userDataScopeMapper.selectCollegeIds(userId)),
                List.copyOf(userDataScopeMapper.selectMajorIds(userId)));
    }

    private RoleState roleState(long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        assertThat(role).as("role %s must remain active", roleId).isNotNull();
        List<PermissionState> permissions = rolePermissionMapper.selectByRoleId(roleId).stream()
                .map(relation -> new PermissionState(relation.getPermissionId(), relation.getScopeType()))
                .toList();
        return new RoleState(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getSort(), role.getStatus(), permissions);
    }

    private SysRole requireRole(String code) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, code)
                .last("LIMIT 1"));
        assertThat(role).as("required role %s", code).isNotNull();
        return role;
    }

    private SysPermission requirePermission(String code) {
        SysPermission permission = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getCode, code)
                .last("LIMIT 1"));
        assertThat(permission).as("required permission %s", code).isNotNull();
        return permission;
    }

    private GrantRequest grant(SysPermission permission, String scopeType) {
        return new GrantRequest(permission.getId(), scopeType);
    }

    private Map<String, Object> permissionsBody(List<GrantRequest> permissions) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (GrantRequest permission : permissions) {
            items.add(Map.of(
                    "permissionId", permission.permissionId(),
                    "scopeType", permission.scopeType()
            ));
        }
        return Map.of("permissions", items);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private void assertOk(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void assertForbidden(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(403);
    }

    private LoginResult readyLogin(String username) throws Exception {
        LoginResult result = loginFlexibly(username);
        if (result.mustChangePwd()) {
            changePassword(result.accessToken(), INITIAL_PASSWORD, CHANGED_PASSWORD);
            result = login(username, CHANGED_PASSWORD);
        }
        return result;
    }

    private LoginResult loginFlexibly(String username) throws Exception {
        ResponseEntity<String> initial = loginRaw(username, INITIAL_PASSWORD);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(initial);
        if (root.at("/code").asInt() == 0) {
            return loginResult(root.at("/data"));
        }
        return login(username, CHANGED_PASSWORD);
    }

    private LoginResult login(String username, String password) throws Exception {
        ResponseEntity<String> response = loginRaw(username, password);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return loginResult(root.at("/data"));
    }

    private LoginResult loginResult(JsonNode data) {
        JsonNode user = data.at("/user");
        assertThat(user.at("/userManagementWritable").isBoolean()).isTrue();
        assertThat(user.at("/roleManagementWritable").isBoolean()).isTrue();
        return new LoginResult(
                data.at("/accessToken").asText(),
                data.at("/refreshToken").asText(),
                data.at("/mustChangePwd").asBoolean(),
                user.at("/userManagementWritable").asBoolean(),
                user.at("/roleManagementWritable").asBoolean()
        );
    }

    private ResponseEntity<String> loginRaw(String username, String password) throws Exception {
        JsonNode captcha = json(rest.getForEntity(url("/api/auth/captcha"), String.class)).at("/data");
        return rest.postForEntity(url("/api/auth/login"), Map.of(
                "username", username,
                "password", password,
                "captchaId", captcha.at("/captchaId").asText(),
                "captchaCode", captchaCode(captcha.at("/image").asText())
        ), String.class);
    }

    private void changePassword(String accessToken, String oldPassword, String newPassword) throws Exception {
        assertOk(exchange("/api/auth/change-pwd", HttpMethod.POST, accessToken, Map.of(
                "oldPassword", oldPassword,
                "newPassword", newPassword
        )));
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private String captchaCode(String image) {
        String svg = new String(java.util.Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1)),
                java.nio.charset.StandardCharsets.UTF_8);
        return svg.replaceAll("(?s).*<text[^>]*>([^<]+)</text>.*", "$1").trim();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd,
                               boolean userManagementWritable, boolean roleManagementWritable) {
    }

    private record GrantRequest(Long permissionId, String scopeType) {
    }

    private record PermissionState(Long permissionId, String scopeType) {
    }

    private record RoleState(Long id, String code, String name, String description, Integer sort, Integer status,
                             List<PermissionState> permissions) {
    }

    private record UserState(Long id, String username, String passwordHash, String realName, String workNo,
                             String status, String userType, Long collegeId, Long studentId, Integer mustChangePwd,
                             List<Long> roleIds, List<Long> collegeIds, List<Long> majorIds) {
    }

    private record CoreGrantBaseline(Long id, Long permissionId, String scopeType,
                                     Long createdBy, LocalDateTime createdAt,
                                     Long updatedBy, LocalDateTime updatedAt) {
    }

    private record CoreRoleBaseline(Long id, String code, String name, String description, Integer sort, Integer status,
                                    Long createdBy, LocalDateTime createdAt,
                                    Long updatedBy, LocalDateTime updatedAt,
                                    List<CoreGrantBaseline> permissions) {
    }
}
