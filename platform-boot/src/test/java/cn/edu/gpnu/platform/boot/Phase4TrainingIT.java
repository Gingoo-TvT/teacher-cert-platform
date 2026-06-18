package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
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
class Phase4TrainingIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;
    private static final String YEAR = "P4-2026";
    private static final String NEXT_YEAR = "P4-2027";

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
    private TrainingProfileMapper trainingProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void resetSeedUsers() {
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
    void majorCodeAndLinkValidationRejectInvalidProfiles() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        long graduateId = createStudent(academic.accessToken(), student(uniqueNo("P4GRAD"), "研究生甲",
                "education_master", COLLEGE_A));
        assertSaveFails(academic.accessToken(), training(graduateId, COLLEGE_A, YEAR, "030101", "法学",
                null, null, "master", "primary_school_teacher", "primary_secondary_school",
                "primary_school", "ps_chinese"), "专业代码不符合规则");
        long validGraduate = save(academic.accessToken(), training(graduateId, COLLEGE_A, YEAR, "045101",
                "教育管理", null, null, "master", "primary_school_teacher", "primary_secondary_school",
                "primary_school", "ps_chinese"));
        assertThat(validGraduate).isPositive();

        long normalId = createStudent(academic.accessToken(), student(uniqueNo("P4NORMAL"), "普通生甲",
                "normal_student", COLLEGE_A));
        assertSaveFails(academic.accessToken(), training(normalId, COLLEGE_A, YEAR, "050101", "汉语言文学",
                "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor", "junior_middle_school_teacher",
                "primary_secondary_school", "junior_middle_school", "语文ABC"), "任教学科不属于当前学段");
        assertSaveFails(academic.accessToken(), training(normalId, COLLEGE_A, YEAR, "050101", "汉语言文学",
                "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor", "junior_middle_school_teacher",
                "primary_secondary_school", "secondary_vocational_school", "sv_cat_finance_commerce"),
                "任教学段不在培养目标允许范围");
        long vocationalId = createStudent(academic.accessToken(), student(uniqueNo("P4CAT"), "类别节点",
                "normal_student", COLLEGE_A));
        assertSaveFails(academic.accessToken(), training(vocationalId, COLLEGE_A, YEAR, "045120", "职业技术教育",
                "P4_VOC_A", "职业技术教育专业", "bachelor", "secondary_vocational_school_teacher",
                "other", "secondary_vocational_school", "sv_cat_finance_commerce"),
                "任教学科类别节点不可选择");
        assertSaveFails(academic.accessToken(), training(normalId, COLLEGE_A, YEAR, "050101", "汉语言文学",
                "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor", "primary_school_teacher",
                "enterprise_vocational_education", "primary_school", "ps_chinese"), "实习地点不在培养目标允许范围");
        assertSaveFails(academic.accessToken(), training(normalId, COLLEGE_A, YEAR, "050101", "汉语言文学",
                "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor", "primary_school_teacher",
                "overseas_chinese_international_education", "primary_school", "ps_chinese"), "海外实习地点仅汉语国际教育专业可选");
    }

    @Test
    void multiGoalSaveAndTwoLevelReviewPass() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        JsonNode options = json(exchange("/api/training/options?goal=primary_school_teacher&segment=primary_school",
                HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(options.at("/allowedSegments").toString()).contains("primary_school");
        assertThat(options.at("/subjects").toString()).contains("ps_chinese");
        assertThat(options.at("/subjects").toString()).doesNotContain("jms_chinese");

        long studentId = createStudent(academic.accessToken(), student(uniqueNo("P4FLOW"), "流程学生",
                "normal_student", COLLEGE_A));
        long profileId = save(academic.accessToken(), training(studentId, COLLEGE_A, YEAR,
                "050101", "汉语言文学", "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor",
                "junior_middle_school_teacher", "primary_secondary_school", "junior_middle_school", "jms_chinese"));

        JsonNode detail = json(exchange("/api/training/" + studentId + "?year=" + YEAR, HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(detail.at("/trainingGoal").asText()).isEqualTo("junior_middle_school_teacher");
        assertThat(detail.at("/teachingSubjectCode").asText()).isEqualTo("jms_chinese");

        ResponseEntity<String> submit = exchange("/api/training/" + profileId + "/submit", HttpMethod.POST, academic.accessToken(), Map.of());
        assertThat(json(submit).at("/code").asInt()).isEqualTo(0);
        LoginResult clerk = readyLogin("test_college_clerk");
        ResponseEntity<String> first = exchange("/api/training/" + profileId + "/first-review", HttpMethod.POST,
                clerk.accessToken(), Map.of("action", "PASS", "comment", "初审通过"));
        assertThat(json(first).at("/code").asInt()).isEqualTo(0);
        LoginResult auditor = readyLogin("test_college_auditor");
        ResponseEntity<String> second = exchange("/api/training/" + profileId + "/second-review", HttpMethod.POST,
                auditor.accessToken(), Map.of("action", "PASS", "comment", "复审通过"));
        assertThat(json(second).at("/code").asInt()).isEqualTo(0);

        TrainingProfile passed = trainingProfileMapper.selectById(profileId);
        assertThat(passed.getStatus()).isEqualTo("PASSED");
        assertThat(passed.getLocked()).isEqualTo(1);

        ResponseEntity<String> locked = exchange("/api/training", HttpMethod.POST, academic.accessToken(),
                training(studentId, COLLEGE_A, YEAR, "050101", "汉语言文学",
                        "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor",
                        "primary_school_teacher", "primary_secondary_school", "primary_school", "ps_chinese"));
        assertThat(json(locked).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(locked).at("/msg").asText()).contains("关键字段已锁定");
    }

    @Test
    void trainingProfileInReviewCannotBeSavedAgain() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        long studentId = createStudent(academic.accessToken(), student(uniqueNo("P4REVIEW"), "在审培养",
                "normal_student", COLLEGE_A));
        long profileId = save(academic.accessToken(), training(studentId, COLLEGE_A, YEAR,
                "050101", "汉语言文学", "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor",
                "primary_school_teacher", "primary_secondary_school", "primary_school", "ps_chinese"));
        ResponseEntity<String> submit = exchange("/api/training/" + profileId + "/submit", HttpMethod.POST,
                academic.accessToken(), Map.of());
        assertThat(json(submit).at("/code").asInt()).isEqualTo(0);

        ResponseEntity<String> saveWhileReview = exchange("/api/training", HttpMethod.POST, academic.accessToken(),
                training(studentId, COLLEGE_A, YEAR, "050101", "汉语言文学",
                        "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor",
                        "primary_school_teacher", "primary_secondary_school", "primary_school", "ps_chinese"));
        assertThat(json(saveWhileReview).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(saveWhileReview).at("/msg").asText()).contains("当前状态不可编辑");
    }

    @Test
    void writeScopeAndReadDataScopeUseTrainingProfileTable() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        long studentA = createStudent(academic.accessToken(), student(uniqueNo("P4A"), "学院甲培养",
                "normal_student", COLLEGE_A));
        long studentB = createStudent(academic.accessToken(), student(uniqueNo("P4B"), "学院乙培养",
                "normal_student", COLLEGE_B));

        save(academic.accessToken(), training(studentA, COLLEGE_A, YEAR, "050101", "汉语言文学",
                "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor", "primary_school_teacher",
                "primary_secondary_school", "primary_school", "ps_chinese"));
        save(academic.accessToken(), training(studentB, COLLEGE_B, YEAR, "070101", "数学与应用数学",
                "P4_NORMAL_B", "Phase4普通师范试点专业B", "bachelor", "junior_middle_school_teacher",
                "primary_secondary_school", "junior_middle_school", "jms_math"));

        ResponseEntity<String> cross = exchange("/api/training", HttpMethod.POST, auditor.accessToken(),
                training(studentB, COLLEGE_B, NEXT_YEAR, "070101", "数学与应用数学",
                        "P4_NORMAL_B", "Phase4普通师范试点专业B", "bachelor", "junior_middle_school_teacher",
                        "primary_secondary_school", "junior_middle_school", "jms_math"));
        assertThat(cross.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(cross).at("/code").asInt()).isEqualTo(403);
        assertThat(trainingProfileMapper.selectCount(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, studentB)
                .eq(TrainingProfile::getAssessmentYear, NEXT_YEAR))).isZero();
        ResponseEntity<String> crossUpdate = exchange("/api/training", HttpMethod.POST, auditor.accessToken(),
                training(studentA, COLLEGE_B, YEAR, "050101", "汉语言文学",
                        "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor", "primary_school_teacher",
                        "primary_secondary_school", "primary_school", "ps_chinese"));
        assertThat(json(crossUpdate).at("/code").asInt()).isEqualTo(403);
        TrainingProfile ownAfterCrossUpdate = trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, studentA)
                .eq(TrainingProfile::getAssessmentYear, YEAR)
                .last("LIMIT 1"));
        assertThat(ownAfterCrossUpdate.getCollegeId()).isEqualTo(COLLEGE_A);

        ResponseEntity<String> clerkList = exchange("/api/training?assessmentYear=" + YEAR, HttpMethod.GET, clerk.accessToken(), null);
        assertThat(json(clerkList).at("/code").asInt()).isEqualTo(0);
        JsonNode clerkRecords = json(clerkList).at("/data/records");
        assertThat(clerkRecords.toString()).contains("学院甲培养");
        assertThat(clerkRecords.toString()).doesNotContain("学院乙培养");

        LoginResult student = readyLogin("test_student");
        save(academic.accessToken(), training(9001L, COLLEGE_A, YEAR, "050101", "汉语言文学",
                "P4_NORMAL_A", "Phase4普通师范试点专业A", "bachelor", "primary_school_teacher",
                "primary_secondary_school", "primary_school", "ps_chinese"));
        ResponseEntity<String> studentList = exchange("/api/training?assessmentYear=" + YEAR, HttpMethod.GET, student.accessToken(), null);
        assertThat(json(studentList).at("/code").asInt()).isEqualTo(0);
        JsonNode studentRecords = json(studentList).at("/data/records");
        assertThat(studentRecords.size()).isEqualTo(1);
        assertThat(studentRecords.at("/0/studentId").asLong()).isEqualTo(9001L);
    }

    @Test
    void vocationalAndOverseasRestrictionsHavePositivePaths() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        long vocationalId = createStudent(academic.accessToken(), student(uniqueNo("P4VOC"), "职教学生",
                "normal_student", COLLEGE_A));
        long vocationalProfile = save(academic.accessToken(), training(vocationalId, COLLEGE_A, YEAR,
                "045120", "职业技术教育", "P4_VOC_A", "职业技术教育专业", "bachelor",
                "secondary_vocational_school_teacher", "other",
                "secondary_vocational_school", "sv_ecommerce"));
        assertThat(vocationalProfile).isPositive();

        long overseasId = createStudent(academic.accessToken(), student(uniqueNo("P4OVERSEA"), "海外学生",
                "normal_student", COLLEGE_A));
        long overseasProfile = save(academic.accessToken(), training(overseasId, COLLEGE_A, YEAR,
                "045300", "汉语国际教育", "P4_CHINESE_INTL_A", "汉语国际教育专业", "bachelor",
                "primary_school_teacher", "overseas_chinese_international_education",
                "primary_school", "ps_chinese"));
        assertThat(overseasProfile).isPositive();
    }

    private void assertSaveFails(String token, Map<String, Object> body, String message) throws Exception {
        ResponseEntity<String> response = exchange("/api/training", HttpMethod.POST, token, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(1000);
        assertThat(root.at("/msg").asText()).contains(message);
    }

    private long save(String token, Map<String, Object> body) throws Exception {
        ResponseEntity<String> response = exchange("/api/training", HttpMethod.POST, token, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data").asLong();
    }

    private long createStudent(String token, Map<String, Object> body) throws Exception {
        ResponseEntity<String> response = exchange("/api/student", HttpMethod.POST, token, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data").asLong();
    }

    private Map<String, Object> training(long studentId, long collegeId, String year, String secondCode, String secondName,
                                         String majorCode, String majorName, String educationLevel, String goal,
                                         String internshipLocation, String segment, String subjectCode) {
        return Map.ofEntries(
                Map.entry("studentId", studentId),
                Map.entry("collegeId", collegeId),
                Map.entry("assessmentYear", year),
                Map.entry("secondDisciplineCode", secondCode),
                Map.entry("secondDisciplineName", secondName),
                Map.entry("internalMajorCode", majorCode == null ? "" : majorCode),
                Map.entry("internalMajorName", majorName == null ? "" : majorName),
                Map.entry("educationLevel", educationLevel),
                Map.entry("trainingGoal", goal),
                Map.entry("internshipOrgMode", "school_organized"),
                Map.entry("internshipLocation", internshipLocation),
                Map.entry("teachingSegment", segment),
                Map.entry("teachingSubjectCode", subjectCode),
                Map.entry("interviewOrgMode", "separate_interview"),
                Map.entry("abilityTestConclusion", "qualified")
        );
    }

    private Map<String, Object> student(String studentNo, String name, String identityType, long collegeId) {
        return Map.ofEntries(
                Map.entry("studentNo", studentNo),
                Map.entry("name", name),
                Map.entry("gender", "female"),
                Map.entry("idCardType", "hm_travel_permit"),
                Map.entry("idCardNo", uniqueTravelPermit("P")),
                Map.entry("birthDate", "2001/01/02"),
                Map.entry("identityType", identityType),
                Map.entry("sourceProvince", "440000"),
                Map.entry("sourceCity", "440100"),
                Map.entry("sourceCounty", "440106"),
                Map.entry("sourceFull", "广东省/广州市/天河区"),
                Map.entry("collegeId", collegeId),
                Map.entry("grade", "2022"),
                Map.entry("className", "Phase4测试班")
        );
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
                    data.at("/mustChangePwd").asBoolean(),
                    data.at("/user/permissions").toString()
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

    private String uniqueNo(String prefix) {
        return prefix + System.nanoTime();
    }

    private String uniqueTravelPermit(String prefix) {
        return prefix + ("%08d").formatted(Math.floorMod(System.nanoTime(), 100000000));
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
            studentA.setIdentityType("normal_student");
            studentA.setStatus("DRAFT");
            studentA.setLocked(0);
            studentMapper.updateById(studentA);
        }
        Student studentB = studentMapper.selectById(9002L);
        if (studentB != null) {
            studentB.setCollegeId(COLLEGE_B);
            studentB.setIdentityType("normal_student");
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
        jdbcTemplate.update("DELETE FROM training_profile WHERE assessment_year LIKE 'P4%'");
        userMapper.delete(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "STUDENT")
                .likeRight(SysUser::getUsername, "P4"));
        studentMapper.delete(new LambdaQueryWrapper<Student>()
                .likeRight(Student::getStudentNo, "P4"));
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd, String permissions) {
    }
}
