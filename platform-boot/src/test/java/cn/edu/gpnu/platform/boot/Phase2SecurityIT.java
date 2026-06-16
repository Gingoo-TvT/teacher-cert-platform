package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.entity.SysUser;
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
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void resetSeedUsers() {
        cleanupPhase3GeneratedAccounts();
        ensureSecondCollegeStudent();
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_academic_admin", true);
        resetUser("test_cert_issuer", true);
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
                .doesNotContain("material:secondReview");
        ResponseEntity<String> clerkStudents = exchange("/api/phase2/probe/students", HttpMethod.GET, changedClerk.accessToken(), null);
        assertThat(clerkStudents.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(clerkStudents).at("/data").size()).isEqualTo(1);
        assertThat(json(clerkStudents).at("/data/0/collegeId").asLong()).isEqualTo(PHASE2_COLLEGE_A);
        assertThat(json(clerkStudents).at("/data").toString()).doesNotContain(String.valueOf(PHASE2_COLLEGE_B));

        LoginResult academic = login("test_academic_admin", INITIAL_PASSWORD);
        assertThat(academic.permissions().toString()).contains("cert:generate");
        ResponseEntity<String> allSchool = exchange("/api/phase2/probe/students", HttpMethod.GET, academic.accessToken(), null);
        assertThat(allSchool.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        changePassword(academic.accessToken(), INITIAL_PASSWORD, CHANGED_PASSWORD);
        LoginResult changedAcademic = login("test_academic_admin", CHANGED_PASSWORD);
        allSchool = exchange("/api/phase2/probe/students", HttpMethod.GET, changedAcademic.accessToken(), null);
        assertThat(allSchool.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(allSchool).at("/data").size()).isEqualTo(2);
        assertThat(json(allSchool).at("/data").toString()).contains(String.valueOf(PHASE2_COLLEGE_B));

        LoginResult issuer = login("test_cert_issuer", INITIAL_PASSWORD);
        assertThat(issuer.permissions().toString()).contains("cert:issue").doesNotContain("cert:generate");

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

    private void cleanupPhase3GeneratedAccounts() {
        userMapper.delete(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "STUDENT")
                .and(w -> w.likeRight(SysUser::getUsername, "P3")
                        .or()
                        .likeRight(SysUser::getUsername, "00P3")));
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd, String permissions) {
    }
}
