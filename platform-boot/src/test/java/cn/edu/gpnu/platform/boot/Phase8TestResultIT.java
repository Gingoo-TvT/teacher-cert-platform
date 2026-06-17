package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionRequest;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionRequestMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.testresult.entity.AbilityTestResult;
import cn.edu.gpnu.platform.business.testresult.mapper.AbilityTestResultMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase8TestResultIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;
    private static final String SEGMENT = "junior_middle_school";
    private static final String SUBJECT_A = "comprehensive_quality_junior";
    private static final String SUBJECT_B = "education_knowledge_junior";
    private static final String SUBJECT_C = "subject_knowledge_junior";

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
    private StudentMapper studentMapper;

    @Autowired
    private ExemptionRequestMapper exemptionRequestMapper;

    @Autowired
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private AbilityTestResultMapper resultMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void resetSeedData() {
        cleanupGeneratedData();
        resetSeedStudents();
        ensureSecondCollegeStudent();
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
    }

    @Test
    void passedExemptionSubjectIsExcludedAndScoreTextRoundTrips() throws Exception {
        LoginResult auditor = readyLogin("test_college_auditor");
        String year = "P8-LINK";
        seedPassedExemption(9001L, COLLEGE_A, year, SUBJECT_B);

        JsonNode saved = importOk(auditor.accessToken(), 9001L, year, "000000000000123456789", "pending_confirm");
        assertThat(saved.asLong()).isPositive();

        JsonNode detail = json(exchange("/api/test/9001?year=" + year + "&segment=" + SEGMENT,
                HttpMethod.GET, auditor.accessToken(), null)).at("/data");
        assertThat(detail.at("/score").asText()).isEqualTo("000000000000123456789");
        assertThat(detail.at("/examSubjects").toString()).contains(SUBJECT_A);
        assertThat(detail.at("/examSubjects").toString()).contains(SUBJECT_C);
        assertThat(detail.at("/examSubjects").toString()).doesNotContain(SUBJECT_B);

        JsonNode validity = json(exchange("/api/test/9001/validity?year=" + year,
                HttpMethod.GET, auditor.accessToken(), null)).at("/data");
        assertThat(validity.at("/validForCertificate").asBoolean()).isFalse();

        String importYear = "P8-IMPORT";
        importOk(auditor.accessToken(), 9001L, importYear, "000000000000987654321", "qualified");
        JsonNode importedDetail = json(exchange("/api/test/9001?year=" + importYear + "&segment=" + SEGMENT,
                HttpMethod.GET, auditor.accessToken(), null)).at("/data");
        assertThat(importedDetail.at("/score").asText()).isEqualTo("000000000000987654321");
    }

    @Test
    void manualCreateAndUpdateEndpointsAreOffline() throws Exception {
        LoginResult auditor = readyLogin("test_college_auditor");
        String year = "P8-MANUAL-OFF";

        ResponseEntity<String> created = exchange("/api/test", HttpMethod.POST, auditor.accessToken(),
                saveBody(9001L, year, "88", "qualified"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(created).at("/code").asInt()).isNotEqualTo(0);

        ResponseEntity<String> updated = exchange("/api/test", HttpMethod.PUT, auditor.accessToken(),
                saveBody(9001L, year, "89", "unqualified"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(updated).at("/code").asInt()).isNotEqualTo(0);

        assertThat(resultMapper.selectCount(new LambdaQueryWrapper<AbilityTestResult>()
                .eq(AbilityTestResult::getStudentId, 9001L)
                .eq(AbilityTestResult::getAssessmentYear, year))).isZero();
    }

    @Test
    void confirmedResultLocksAndRejectsDirectConclusionChange() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String year = "P8-LOCK";
        long id = importOk(academic.accessToken(), 9001L, year, "85", "qualified").asLong();

        ResponseEntity<String> confirmed = exchange("/api/test/" + id + "/confirm", HttpMethod.POST,
                academic.accessToken(), Map.of());
        assertThat(json(confirmed).at("/code").asInt()).isEqualTo(0);

        ResponseEntity<String> changed = importRows(academic.accessToken(), 9001L, year, "85", "unqualified");
        assertThat(json(changed).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(changed).at("/msg").asText()).contains("已锁定");

        AbilityTestResult after = resultMapper.selectById(id);
        assertThat(after.getConclusion()).isEqualTo("qualified");
        assertThat(after.getLocked()).isEqualTo(1);
    }

    @Test
    void testExemptedConclusionDoesNotOverwriteProcessMaterialConclusion() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String year = "P8-INDEPENDENT";
        ProcessMaterial process = new ProcessMaterial();
        process.setId(980000000000000001L);
        process.setStudentId(9001L);
        process.setCollegeId(COLLEGE_A);
        process.setAssessmentYear(year);
        process.setCategory("education_internship_practice");
        process.setFileId(0L);
        process.setFileName("process.pdf");
        process.setFilePath("process.pdf");
        process.setUploaderId(0L);
        process.setUploadTime(LocalDateTime.now());
        process.setStatus("FAILED");
        process.setLocked(1);
        materialMapper.insert(process);

        importOk(academic.accessToken(), 9001L, year, "", "exempted");

        ProcessMaterial after = materialMapper.selectById(process.getId());
        assertThat(after.getStatus()).isEqualTo("FAILED");
        assertThat(after.getLocked()).isEqualTo(1);
    }

    @Test
    void testResultReadAndWriteDataScopeAreEnforced() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        String year = "P8-SCOPE";
        long aId = importOk(academic.accessToken(), 9001L, year, "76", "qualified").asLong();
        long bId = importOk(academic.accessToken(), 9002L, year, "77", "qualified").asLong();

        ResponseEntity<String> crossCollegeWrite = importRows(auditor.accessToken(), 9002L, "P8-CROSS", "88", "qualified");
        assertThat(json(crossCollegeWrite).at("/code").asInt()).isEqualTo(403);
        assertThat(resultMapper.selectCount(new LambdaQueryWrapper<AbilityTestResult>()
                .eq(AbilityTestResult::getStudentId, 9002L)
                .eq(AbilityTestResult::getAssessmentYear, "P8-CROSS"))).isZero();

        JsonNode clerkRecords = json(exchange("/api/test?assessmentYear=" + year,
                HttpMethod.GET, clerk.accessToken(), null)).at("/data/records");
        assertThat(clerkRecords.toString()).contains(String.valueOf(aId));
        assertThat(clerkRecords.toString()).doesNotContain(String.valueOf(bId));

        JsonNode studentRecords = json(exchange("/api/test?assessmentYear=" + year,
                HttpMethod.GET, studentA.accessToken(), null)).at("/data/records");
        assertThat(studentRecords.size()).isEqualTo(1);
        assertThat(studentRecords.at("/0/studentId").asLong()).isEqualTo(9001L);

        JsonNode studentBRecords = json(exchange("/api/test?assessmentYear=" + year,
                HttpMethod.GET, studentB.accessToken(), null)).at("/data/records");
        assertThat(studentBRecords.size()).isEqualTo(1);
        assertThat(studentBRecords.at("/0/studentId").asLong()).isEqualTo(9002L);
    }

    private JsonNode importOk(String token, long studentId, String year, String score, String conclusion) throws Exception {
        ResponseEntity<String> response = importRows(token, studentId, year, score, conclusion);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data/0");
    }

    private ResponseEntity<String> importRows(String token, long studentId, String year, String score, String conclusion) {
        return exchange("/api/test/import", HttpMethod.POST, token, Map.of(
                "rows", java.util.List.of(saveBody(studentId, year, score, conclusion))
        ));
    }

    private Map<String, Object> saveBody(long studentId, String year, String score, String conclusion) {
        return Map.of(
                "studentId", studentId,
                "assessmentYear", year,
                "teachingSegment", SEGMENT,
                "examOrgMode", "with_internship_practice",
                "score", score,
                "conclusion", conclusion
        );
    }

    private void seedPassedExemption(long studentId, long collegeId, String year, String subject) {
        ExemptionRequest request = new ExemptionRequest();
        request.setStudentId(studentId);
        request.setCollegeId(collegeId);
        request.setAssessmentYear(year);
        request.setTeachingSegment(SEGMENT);
        request.setSubject(subject);
        request.setSubjectLabel("免考科目");
        request.setBasis("policy_exemption");
        request.setBasisLabel("政策规定免考");
        request.setFinalStatus("PASSED");
        request.setIncludedInExam(0);
        request.setLocked(1);
        exemptionRequestMapper.insert(request);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
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
        JsonNode initialRoot = json(initial);
        if (initialRoot.at("/code").asInt() == 0) {
            JsonNode data = initialRoot.at("/data");
            return new LoginResult(
                    data.at("/accessToken").asText(),
                    data.at("/refreshToken").asText(),
                    data.at("/mustChangePwd").asBoolean()
            );
        }
        return login(username, CHANGED_PASSWORD);
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
                data.at("/mustChangePwd").asBoolean()
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

    private void changePassword(String accessToken, String oldPassword, String newPassword) {
        ResponseEntity<String> response = exchange("/api/auth/change-pwd", HttpMethod.POST, accessToken, Map.of(
                "oldPassword", oldPassword,
                "newPassword", newPassword
        ));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private void resetSeedStudents() {
        Student studentA = studentMapper.selectById(9001L);
        if (studentA != null) {
            studentA.setCollegeId(COLLEGE_A);
            studentA.setStatus("DRAFT");
            studentA.setLocked(0);
            studentMapper.updateById(studentA);
        }
        Student studentB = studentMapper.selectById(9002L);
        if (studentB != null) {
            studentB.setCollegeId(COLLEGE_B);
            studentB.setStatus("DRAFT");
            studentB.setLocked(0);
            studentMapper.updateById(studentB);
        }
    }

    private void ensureSecondCollegeStudent() {
        SysUser user = userMapper.selectByUsername("test_student_b");
        if (user == null) {
            user = new SysUser();
            user.setId(STUDENT_B_USER_ID);
            user.setUsername("test_student_b");
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setRealName("学生测试账号B");
        user.setStatus("ENABLED");
        user.setUserType("STUDENT");
        user.setCollegeId(COLLEGE_B);
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
        userRoleMapper.upsert(STUDENT_B_ROLE_ID, user.getId(), STUDENT_ROLE_ID, 0L);
    }

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM ability_test_result WHERE assessment_year LIKE 'P8%'");
        jdbcTemplate.update("DELETE FROM exemption_material WHERE exemption_request_id IN (SELECT id FROM exemption_request WHERE assessment_year LIKE 'P8%')");
        jdbcTemplate.update("DELETE FROM exemption_request WHERE assessment_year LIKE 'P8%'");
        jdbcTemplate.update("DELETE FROM process_material WHERE assessment_year LIKE 'P8%'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
