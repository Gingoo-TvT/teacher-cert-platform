package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.entity.SysPermission;
import cn.edu.gpnu.platform.system.entity.SysRole;
import cn.edu.gpnu.platform.system.entity.SysUser;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=2"
})
class Phase2SecurityIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long PHASE2_COLLEGE_A = 800000000000000201L;
    private static final long PHASE2_COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long PHASE2_STUDENT_B_USER_ID = 800000000000003009L;
    private static final long PHASE2_STUDENT_B_ROLE_ID = 800000000000004009L;

    // Phase 44e-contract（P1-1 真分页样例 · GET /api/system/user 分页×范围 IT 夹具）：
    // V8 种子里 system:user:manage 仅授予 SYS_ADMIN 且 scope_type=SYSTEM（全校范围，见 V8__rbac_seed.sql），
    // 现有测试账号中没有"学院范围（非全校）system:user:manage"组合可直接复用，故新建一个仅供本测试使用、
    // 独立 code 的角色 + 用户（不改动任何既有共享角色/账号），验证分页与既有数据范围叠加是否正确。
    private static final String P44E_ROLE_CODE = "P44E_COLLEGE_USER_ADMIN";
    private static final String P44E_ADMIN_USERNAME = "test_college_user_admin";
    private static final String P44E_LISTING_FIXTURE_PREFIX = "P44EU";
    private static final long P44E_ROLE_PERMISSION_LINK_ID = 900000000000000101L;
    private static final long P44E_USER_ROLE_LINK_ID = 900000000000000102L;
    private static final long P44E_USER_DATA_SCOPE_LINK_ID = 900000000000000103L;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysPermissionMapper permissionMapper;

    @Autowired
    private SysRolePermissionMapper rolePermissionMapper;

    @Autowired
    private SysUserDataScopeMapper userDataScopeMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void resetSeedUsers() {
        cleanupPhase3GeneratedAccounts();
        ensureSecondCollegeStudent();
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
        resetUser("test_sys_admin", true);
    }

    @Test
    void phase2AuthRbacAndDataScope() throws Exception {
        ResponseEntity<String> anonymous = rest.getForEntity(url("/api/auth/me"), String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        LoginResult student = login("test_student", INITIAL_PASSWORD);
        assertThat(student.mustChangePwd()).isTrue();
        assertThat(student.permissions().toString()).contains("student:view").doesNotContain("cert:generate");

        ResponseEntity<String> blocked = exchange("/api/phase2/probe/students", HttpMethod.GET, student.accessToken(), null);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(blocked).at("/code").asInt()).isEqualTo(403);

        changePassword(student.accessToken(), INITIAL_PASSWORD, CHANGED_PASSWORD);
        LoginResult changedStudent = login("test_student", CHANGED_PASSWORD);
        assertThat(changedStudent.mustChangePwd()).isFalse();

        ResponseEntity<String> ownStudents = exchange("/api/phase2/probe/students", HttpMethod.GET, changedStudent.accessToken(), null);
        assertThat(ownStudents.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(ownStudents).at("/data").size()).isEqualTo(1);
        assertThat(json(ownStudents).at("/data/0/studentId").asLong()).isEqualTo(9001L);
        assertThat(json(ownStudents).at("/data/0/collegeId").asLong()).isEqualTo(PHASE2_COLLEGE_A);

        ResponseEntity<String> otherStudent = exchange("/api/phase2/probe/students/9002", HttpMethod.GET, changedStudent.accessToken(), null);
        assertThat(otherStudent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(otherStudent).at("/code").asInt()).isEqualTo(403);

        ResponseEntity<String> certGenerate = exchange("/api/phase2/probe/cert/generate", HttpMethod.POST, changedStudent.accessToken(), Map.of());
        assertThat(certGenerate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        LoginResult clerk = login("test_college_clerk", INITIAL_PASSWORD);
        changePassword(clerk.accessToken(), INITIAL_PASSWORD, CHANGED_PASSWORD);
        LoginResult changedClerk = login("test_college_clerk", CHANGED_PASSWORD);
        assertThat(changedClerk.permissions().toString())
                .contains("material:firstReview")
                .contains("student:view", "exchange:export:standard", "exchange:export:full")
                .doesNotContain("material:secondReview", "student:edit", "exchange:import", "video:assign", "test:edit");
        ResponseEntity<String> clerkStudents = exchange("/api/phase2/probe/students", HttpMethod.GET, changedClerk.accessToken(), null);
        assertThat(clerkStudents.getStatusCode()).isEqualTo(HttpStatus.OK);
        // WS-1（审计#2）：按「本测试已知夹具」断言学院数据范围，而非全局条数——对共享库常驻的 demo 学生（同属学院A、
        // 合法在范围内）健壮，但不弱化被测的越权隔离语义。学院文员（学院A）数据范围 = 只见学院A、绝不见学院B：
        // ① 返回记录全部属学院A（无学院B泄漏）；② 含本测试学院A种子学生(9001)；③ 不含学院B种子学生(9002)。
        JsonNode clerkData = json(clerkStudents).at("/data");
        assertThat(collegeIdsOf(clerkData)).isNotEmpty().containsOnly(PHASE2_COLLEGE_A);
        assertThat(studentIdsOf(clerkData)).contains(9001L).doesNotContain(9002L);

        LoginResult auditor = login("test_college_auditor", INITIAL_PASSWORD);
        changePassword(auditor.accessToken(), INITIAL_PASSWORD, CHANGED_PASSWORD);
        LoginResult changedAuditor = login("test_college_auditor", CHANGED_PASSWORD);
        assertThat(changedAuditor.permissions().toString())
                .contains("info:secondReview", "material:secondReview", "exemption:secondReview",
                        "video:assign", "video:arbitrate", "test:import", "test:confirm",
                        "exchange:import", "exchange:prevalidate")
                .doesNotContain("info:firstReview", "test:edit");

        LoginResult academic = login("test_academic_admin", INITIAL_PASSWORD);
        assertThat(academic.permissions().toString())
                .contains("cert:generate", "cert:issue", "test:import", "test:confirm")
                .doesNotContain("test:edit");
        ResponseEntity<String> allSchool = exchange("/api/phase2/probe/students", HttpMethod.GET, academic.accessToken(), null);
        assertThat(allSchool.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        changePassword(academic.accessToken(), INITIAL_PASSWORD, CHANGED_PASSWORD);
        LoginResult changedAcademic = login("test_academic_admin", CHANGED_PASSWORD);
        allSchool = exchange("/api/phase2/probe/students", HttpMethod.GET, changedAcademic.accessToken(), null);
        assertThat(allSchool.getStatusCode()).isEqualTo(HttpStatus.OK);
        // WS-1（审计#2）：全校范围（教务处管理员）跨学院可见——按成员资格断言，而非全局条数（对常驻 demo 学生健壮）。
        // 语义 = 跨学院可见：既含学院A种子学生(9001) 又含学院B种子学生(9002)、且结果里出现学院B（与学院文员看不到B 成对照）。
        JsonNode schoolData = json(allSchool).at("/data");
        assertThat(studentIdsOf(schoolData)).contains(9001L, 9002L);
        assertThat(collegeIdsOf(schoolData)).contains(PHASE2_COLLEGE_B);

        LoginResult sysAdmin = login("test_sys_admin", INITIAL_PASSWORD);
        assertThat(sysAdmin.permissions().toString())
                .contains("system:user:manage", "cert:issue", "video:score", "exchange:import", "material:view");

        Thread.sleep(2500);
        ResponseEntity<String> expiredAccess = exchange("/api/auth/me", HttpMethod.GET, changedStudent.accessToken(), null);
        assertThat(expiredAccess.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json(expiredAccess).at("/code").asInt()).isEqualTo(401);

        LoginResult refreshed = refresh(changedStudent.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.permissions().toString()).contains("student:view");

        ResponseEntity<String> invalidRefresh = rest.postForEntity(
                url("/api/auth/refresh"),
                Map.of("refreshToken", changedStudent.accessToken()),
                String.class);
        assertThat(invalidRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(invalidRefresh).at("/code").asInt()).isEqualTo(401);
    }

    @Test
    void wrongPasswordLocksAccount() throws Exception {
        resetUser("test_sys_admin", false);
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = loginRaw("test_sys_admin", "BadPassword" + i);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(json(response).at("/code").asInt()).isEqualTo(1000);
        }
        ResponseEntity<String> locked = loginRaw("test_sys_admin", INITIAL_PASSWORD);
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(locked).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(locked).at("/msg").asText()).contains("锁定");
    }

    /**
     * Phase 44e-contract（P1-1 真分页样例 · GET /api/system/user）：验证 SecurityAdminServiceImpl.listUsers
     * 由「selectList 拼总数」改为 selectPage 真分页后，分页（page/size/total）与既有
     * {@code @DataScope(alias = "sys_user", permission = "system:user:manage")} 学院数据范围过滤仍正确叠加——
     * 即 COUNT 与数据两条 SQL 均经数据权限拦截器改写。由于现有测试账号里没有「学院范围（非全校）
     * system:user:manage」组合，先按既有 upsert 幂等模式建好本测试专用角色 + 用户（见
     * {@link #ensureCollegeScopedUserManager()}，不改动任何既有共享角色/账号）。
     */
    @Test
    void paginatedUserListIsScopedAndPagedForCollegeUser() throws Exception {
        userMapper.delete(new LambdaQueryWrapper<SysUser>().likeRight(SysUser::getUsername, P44E_LISTING_FIXTURE_PREFIX));
        ensureCollegeScopedUserManager();

        String prefix = P44E_LISTING_FIXTURE_PREFIX + System.nanoTime();
        seedSecurityListingUser(prefix + "A1", "分页甲", PHASE2_COLLEGE_A);
        seedSecurityListingUser(prefix + "A2", "分页乙", PHASE2_COLLEGE_A);
        seedSecurityListingUser(prefix + "A3", "分页丙", PHASE2_COLLEGE_A);
        // 同前缀但学院 B 的第 4 条：关键词能命中，但学院范围管理员的数据范围应把它排除在 total/records 之外。
        seedSecurityListingUser(prefix + "B1", "分页丁", PHASE2_COLLEGE_B);

        LoginResult scopedInitial = login(P44E_ADMIN_USERNAME, INITIAL_PASSWORD);
        changePassword(scopedInitial.accessToken(), INITIAL_PASSWORD, CHANGED_PASSWORD);
        LoginResult scoped = login(P44E_ADMIN_USERNAME, CHANGED_PASSWORD);

        JsonNode page1 = json(exchange("/api/system/user?keyword=" + prefix + "&page=1&size=2",
                HttpMethod.GET, scoped.accessToken(), null)).at("/data");
        assertThat(page1.at("/total").asLong()).isEqualTo(3);
        assertThat(page1.at("/records").size()).isEqualTo(2);
        assertThat(page1.at("/records").toString()).doesNotContain(String.valueOf(PHASE2_COLLEGE_B));

        JsonNode page2 = json(exchange("/api/system/user?keyword=" + prefix + "&page=2&size=2",
                HttpMethod.GET, scoped.accessToken(), null)).at("/data");
        assertThat(page2.at("/total").asLong()).isEqualTo(3);
        assertThat(page2.at("/records").size()).isEqualTo(1);
        assertThat(page2.at("/records").toString()).doesNotContain(String.valueOf(PHASE2_COLLEGE_B));

        assertThat(page1.at("/records/0/id").asLong()).isNotEqualTo(page2.at("/records/0/id").asLong());
    }

    private LoginResult login(String username, String password) throws Exception {
        ResponseEntity<String> response = loginRaw(username, password);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        JsonNode data = root.at("/data");
        return new LoginResult(
                data.at("/accessToken").asText(),
                data.at("/refreshToken").asText(),
                data.at("/mustChangePwd").asBoolean(),
                data.at("/user/permissions").toString()
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

    private LoginResult refresh(String refreshToken) throws Exception {
        ResponseEntity<String> response = rest.postForEntity(url("/api/auth/refresh"), Map.of("refreshToken", refreshToken), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = json(response).at("/data");
        return new LoginResult(
                data.at("/accessToken").asText(),
                data.at("/refreshToken").asText(),
                data.at("/mustChangePwd").asBoolean(),
                data.at("/user/permissions").toString()
        );
    }

    private void changePassword(String accessToken, String oldPassword, String newPassword) {
        ResponseEntity<String> response = exchange("/api/auth/change-pwd", HttpMethod.POST, accessToken, Map.of(
                "oldPassword", oldPassword,
                "newPassword", newPassword
        ));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
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

    private void resetUser(String username, boolean mustChangePwd) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            return;
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setStatus("ENABLED");
        user.setMustChangePwd(mustChangePwd ? 1 : 0);
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

    private void ensureSecondCollegeStudent() {
        SysUser user = userMapper.selectByUsername("test_student_b");
        if (user == null) {
            user = new SysUser();
            user.setId(PHASE2_STUDENT_B_USER_ID);
            user.setUsername("test_student_b");
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setRealName("学生测试账号B");
        user.setStatus("ENABLED");
        user.setUserType("STUDENT");
        user.setCollegeId(PHASE2_COLLEGE_B);
        user.setStudentId(9002L);
        user.setMustChangePwd(1);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(null);
        if (userMapper.selectByUsername("test_student_b") == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        userRoleMapper.upsert(PHASE2_STUDENT_B_ROLE_ID, user.getId(), STUDENT_ROLE_ID, 0L);
    }

    /**
     * Phase 44e-contract（P1-1 真分页样例 · GET /api/system/user 分页×范围 IT 所需夹具）：新建一个仅供本测试
     * 使用、独立 code 的角色（不复用/不修改任何既有共享角色如 COLLEGE_CLERK 的权限集，避免影响同库运行的
     * 其它 IT），赋予其既有 system:user:manage 权限（scope=COLLEGE），绑定新用户 test_college_user_admin
     * （学院=COLLEGE_A）。全程使用既有 upsert 幂等 mapper 方法（唯一键 on-duplicate-update），可安全跨测试
     * 重复调用；角色/用户均按"查到即复用、查不到才插入"处理，不产生重复行。
     */
    private void ensureCollegeScopedUserManager() {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, P44E_ROLE_CODE)
                .last("LIMIT 1"));
        if (role == null) {
            role = new SysRole();
            role.setCode(P44E_ROLE_CODE);
            role.setName("Phase44e学院范围用户管理员(测试)");
            role.setDescription("P1-1 真分页 IT 专用：学院范围 system:user:manage");
            role.setSort(999);
            role.setStatus(1);
            roleMapper.insert(role);
        }
        SysPermission permission = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getCode, "system:user:manage")
                .last("LIMIT 1"));
        assertThat(permission).isNotNull();
        rolePermissionMapper.upsert(P44E_ROLE_PERMISSION_LINK_ID, role.getId(), permission.getId(), "COLLEGE", 0L);

        SysUser admin = userMapper.selectByUsername(P44E_ADMIN_USERNAME);
        if (admin == null) {
            admin = new SysUser();
            admin.setUsername(P44E_ADMIN_USERNAME);
        }
        admin.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        admin.setRealName("学院范围用户管理员(测试)");
        admin.setStatus("ENABLED");
        admin.setUserType("STAFF");
        admin.setCollegeId(PHASE2_COLLEGE_A);
        admin.setMustChangePwd(1);
        admin.setFailedLoginCount(0);
        admin.setLockedUntil(null);
        admin.setLastLoginAt(null);
        if (userMapper.selectByUsername(P44E_ADMIN_USERNAME) == null) {
            userMapper.insert(admin);
        } else {
            userMapper.updateById(admin);
        }
        userRoleMapper.upsert(P44E_USER_ROLE_LINK_ID, admin.getId(), role.getId(), 0L);
        userDataScopeMapper.upsert(P44E_USER_DATA_SCOPE_LINK_ID, admin.getId(), PHASE2_COLLEGE_A, null, 0L);
    }

    // Phase 44e-contract：分页断言用的纯列表夹具（不登录、不需要角色），直接落库供 paginatedUserListIsScopedAndPagedForCollegeUser 查询。
    private void seedSecurityListingUser(String username, String realName, long collegeId) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setRealName(realName);
        user.setStatus("ENABLED");
        user.setUserType("STAFF");
        user.setCollegeId(collegeId);
        user.setMustChangePwd(1);
        user.setFailedLoginCount(0);
        userMapper.insert(user);
    }

    private void cleanupPhase3GeneratedAccounts() {
        userMapper.delete(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "STUDENT")
                .and(w -> w.likeRight(SysUser::getUsername, "P3")
                        .or()
                        .likeRight(SysUser::getUsername, "00P3")));
    }

    // WS-1：从 probe 返回的 /data 数组抽取 studentId / collegeId，供「按自身范围成员资格」断言（替代脆弱的全局条数）。
    private List<Long> studentIdsOf(JsonNode dataArray) {
        List<Long> ids = new ArrayList<>();
        dataArray.forEach(node -> ids.add(node.at("/studentId").asLong()));
        return ids;
    }

    private List<Long> collegeIdsOf(JsonNode dataArray) {
        List<Long> ids = new ArrayList<>();
        dataArray.forEach(node -> ids.add(node.at("/collegeId").asLong()));
        return ids;
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd, String permissions) {
    }
}
