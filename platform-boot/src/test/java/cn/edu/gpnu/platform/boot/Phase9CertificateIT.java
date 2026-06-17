package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.testresult.entity.AbilityTestResult;
import cn.edu.gpnu.platform.business.testresult.mapper.AbilityTestResultMapper;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.entity.TeachingSubject;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import cn.edu.gpnu.platform.system.mapper.TeachingSubjectMapper;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase9CertificateIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;
    private static final String SENIOR_SEGMENT = "senior_middle_school";
    private static final String VOCATIONAL_SEGMENT = "secondary_vocational_school";
    private static final String SENIOR_SUBJECT_CODE = "sms_chinese";
    private static final String VOCATIONAL_SUBJECT_CODE = "sv_ecommerce";
    private static final List<String> MATERIAL_CATEGORIES = List.of(
            "morality_teacher_ethics",
            "teacher_education_course",
            "education_internship_practice",
            "professional_ability_skill_training"
    );

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
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private AbilityTestResultMapper testResultMapper;

    @Autowired
    private VideoReviewMapper videoReviewMapper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private TeachingSubjectMapper teachingSubjectMapper;

    @Autowired
    private SysParamMapper paramMapper;

    @Autowired
    private SysAuditLogMapper auditLogMapper;

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
        resetParam("cert.seq.scope", "SCHOOL_YEAR_SEGMENT");
        resetParam("video.required", "true");
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_academic_admin", true);
    }

    @Test
    void certificateNumberSegmentsAndValidityRulesAreCorrect() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        long senior = seedEligibleStudent("P9EXS", COLLEGE_A, "2026", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        long vocational = seedEligibleStudent("P9EXV", COLLEGE_A, "2026", VOCATIONAL_SEGMENT, VOCATIONAL_SUBJECT_CODE, "电子商务");

        JsonNode seniorCert = generateOk(academic.accessToken(), senior, "2026");
        assertThat(seniorCert.at("/certNo").asText()).isEqualTo("202610588344400001");
        JsonNode vocationalCert = generateOk(academic.accessToken(), vocational, "2026");
        assertThat(vocationalCert.at("/certNo").asText()).isEqualTo("202610588344500001");

        JsonNode firstHalf = issueOk(academic.accessToken(), seniorCert.at("/id").asLong(), "校长", "2022/3/15");
        assertThat(firstHalf.at("/validUntil").asText()).isEqualTo("2025/6/30");
        JsonNode secondHalf = issueOk(academic.accessToken(), vocationalCert.at("/id").asLong(), "校长", "2022-09-01");
        assertThat(secondHalf.at("/validUntil").asText()).isEqualTo("2025/12/31");
    }

    @Test
    void schoolYearScopeCanBeSwitchedBySysParam() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        resetParam("cert.seq.scope", "SCHOOL_YEAR");
        long senior = seedEligibleStudent("P9SCS", COLLEGE_A, "2027", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        long vocational = seedEligibleStudent("P9SCV", COLLEGE_A, "2027", VOCATIONAL_SEGMENT, VOCATIONAL_SUBJECT_CODE, "电子商务");

        JsonNode seniorCert = generateOk(academic.accessToken(), senior, "2027");
        JsonNode vocationalCert = generateOk(academic.accessToken(), vocational, "2027");
        assertThat(seq(seniorCert.at("/certNo").asText())).isEqualTo(1);
        assertThat(seq(vocationalCert.at("/certNo").asText())).isEqualTo(2);
    }

    @Test
    void missingProcessMaterialBlocksGenerateWithExplicitMissingList() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        long studentId = seedEligibleStudent("P9MISS", COLLEGE_A, "2028", SENIOR_SEGMENT,
                SENIOR_SUBJECT_CODE, "语文", false);

        ResponseEntity<String> precheck = exchange("/api/cert/precheck/" + studentId + "?year=2028",
                HttpMethod.GET, academic.accessToken(), null);
        JsonNode precheckData = json(precheck).at("/data");
        assertThat(precheckData.at("/passed").asBoolean()).isFalse();
        assertThat(precheckData.at("/missingItems").toString()).contains("过程性考核");

        ResponseEntity<String> generated = exchange("/api/cert/generate", HttpMethod.POST, academic.accessToken(),
                Map.of("studentId", studentId, "assessmentYear", "2028"));
        JsonNode root = json(generated);
        assertThat(root.at("/code").asInt()).isEqualTo(1000);
        assertThat(root.at("/msg").asText()).contains("过程性考核");
    }

    @Test
    void concurrentGenerateUsesContinuousSequenceWithoutDuplicatesOrGaps() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String token = academic.accessToken();
        String year = "2029";
        List<Long> studentIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            studentIds.add(seedEligibleStudent("P9C" + i, COLLEGE_A, year, SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<String>> tasks = studentIds.stream()
                    .map(studentId -> (Callable<String>) () -> generateOk(token, studentId, year).at("/certNo").asText())
                    .toList();
            List<Future<String>> futures = executor.invokeAll(tasks);
            List<String> certNos = new ArrayList<>();
            for (Future<String> future : futures) {
                certNos.add(future.get());
            }
            assertThat(new HashSet<>(certNos)).hasSize(certNos.size());
            List<Integer> seqs = certNos.stream().map(this::seq).sorted().toList();
            assertThat(seqs).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 50).boxed().toList());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void lockedCertificateRejectsDirectCriticalChangeButCorrectionLeavesAudit() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        long studentId = seedEligibleStudent("P9LOCK", COLLEGE_A, "2030", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        long certId = generateOk(academic.accessToken(), studentId, "2030").at("/id").asLong();

        ResponseEntity<String> duplicateGenerate = exchange("/api/cert/generate", HttpMethod.POST, academic.accessToken(),
                Map.of("studentId", studentId, "assessmentYear", "2030"));
        assertThat(json(duplicateGenerate).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(duplicateGenerate).at("/msg").asText()).contains("已有未作废证书");

        ResponseEntity<String> corrected = exchange("/api/cert/" + certId + "/correct", HttpMethod.PUT,
                academic.accessToken(), Map.of(
                        "teachingSubjectCode", "sms_math",
                        "teachingSubjectName", "数学",
                        "validUntil", "2029/6/30",
                        "reason", "复核更正任教学科"
                ));
        assertThat(json(corrected).at("/code").asInt()).isEqualTo(0);
        Certificate after = certificateMapper.selectById(certId);
        assertThat(after.getTeachingSubjectCode()).isEqualTo("sms_math");
        assertThat(after.getValidUntil()).isEqualTo("2029/6/30");
        assertThat(auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "cert")
                .eq(SysAuditLog::getBizId, certId)
                .eq(SysAuditLog::getOperation, "correct"))).isGreaterThan(0);
    }

    @Test
    void voidAndReissueCreateNewCertificateLinkedToOriginal() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        long studentId = seedEligibleStudent("P9VOID", COLLEGE_A, "2031", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        JsonNode generated = generateOk(academic.accessToken(), studentId, "2031");
        long certId = generated.at("/id").asLong();
        String originalNo = generated.at("/certNo").asText();

        JsonNode voided = json(exchange("/api/cert/" + certId + "/void", HttpMethod.POST,
                academic.accessToken(), Map.of("reason", "编号作废重开"))).at("/data");
        assertThat(voided.at("/status").asText()).isEqualTo("VOIDED");

        JsonNode reissued = json(exchange("/api/cert/" + certId + "/reissue", HttpMethod.POST,
                academic.accessToken(), Map.of())).at("/data");
        assertThat(reissued.at("/id").asLong()).isNotEqualTo(certId);
        assertThat(reissued.at("/reissueOriginCertNo").asText()).isEqualTo(originalNo);
        assertThat(reissued.at("/status").asText()).isEqualTo("GENERATED");
        assertThat(certificateMapper.selectById(certId).getStatus()).isEqualTo("VOIDED");
    }

    @Test
    void certificateReadScopeAndSchoolOnlyWritesAreEnforced() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult clerk = readyLogin("test_college_clerk");
        long a = seedEligibleStudent("P9SCPA", COLLEGE_A, "2032", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        long b = seedEligibleStudent("P9SCPB", COLLEGE_B, "2032", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        JsonNode certA = generateOk(academic.accessToken(), a, "2032");
        JsonNode certB = generateOk(academic.accessToken(), b, "2032");
        bindStudentUser("test_student", a, COLLEGE_A);
        bindStudentUser("test_student_b", b, COLLEGE_B);
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");

        ResponseEntity<String> clerkGenerate = exchange("/api/cert/generate", HttpMethod.POST, clerk.accessToken(),
                Map.of("studentId", a, "assessmentYear", "2032"));
        assertThat(json(clerkGenerate).at("/code").asInt()).isEqualTo(403);

        JsonNode clerkRecords = json(exchange("/api/cert?assessmentYear=2032",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data/records");
        assertThat(clerkRecords.toString()).contains(certA.at("/certNo").asText());
        assertThat(clerkRecords.toString()).doesNotContain(certB.at("/certNo").asText());

        JsonNode studentARecords = json(exchange("/api/cert?assessmentYear=2032",
                HttpMethod.GET, studentA.accessToken(), null)).at("/data/records");
        assertThat(studentARecords.size()).isEqualTo(1);
        assertThat(studentARecords.at("/0/studentId").asLong()).isEqualTo(a);

        JsonNode studentBRecords = json(exchange("/api/cert?assessmentYear=2032",
                HttpMethod.GET, studentB.accessToken(), null)).at("/data/records");
        assertThat(studentBRecords.size()).isEqualTo(1);
        assertThat(studentBRecords.at("/0/studentId").asLong()).isEqualTo(b);
    }

    private JsonNode generateOk(String token, long studentId, String year) throws Exception {
        ResponseEntity<String> response = exchange("/api/cert/generate", HttpMethod.POST, token,
                Map.of("studentId", studentId, "assessmentYear", year));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data");
    }

    private JsonNode issueOk(String token, long certId, String issuer, String issueDate) throws Exception {
        ResponseEntity<String> response = exchange("/api/cert/" + certId + "/issue", HttpMethod.POST, token,
                Map.of("issuer", issuer, "issueDate", issueDate));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data");
    }

    private long seedEligibleStudent(String prefix, long collegeId, String year, String segment,
                                     String subjectCode, String subjectName) {
        return seedEligibleStudent(prefix, collegeId, year, segment, subjectCode, subjectName, true);
    }

    private long seedEligibleStudent(String prefix, long collegeId, String year, String segment,
                                     String subjectCode, String subjectName, boolean qualifiedMaterials) {
        long studentId = insertStudent(prefix, collegeId);
        insertTraining(studentId, collegeId, year, segment, subjectCode, subjectName);
        if (qualifiedMaterials) {
            insertMaterials(studentId, collegeId, year);
        }
        insertTestResult(studentId, collegeId, year);
        insertVideo(studentId, collegeId, year);
        return studentId;
    }

    private long insertStudent(String prefix, long collegeId) {
        Student student = new Student();
        student.setStudentNo(prefix + "-" + Math.floorMod(System.nanoTime(), 1_000_000_000L));
        student.setName("证书学生" + prefix);
        student.setGender("female");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo(uniqueTravelPermit(prefix.substring(0, 1)));
        student.setBirthDate("2001/1/2");
        student.setIdentityType("normal_student");
        student.setSourceProvince("440000");
        student.setSourceCity("440100");
        student.setSourceCounty("440106");
        student.setSourceFull("广东省/广州市/天河区");
        student.setCollegeId(collegeId);
        student.setGrade("2022");
        student.setClassName("Phase9测试班");
        student.setStatus("PASSED");
        student.setLocked(1);
        studentMapper.insert(student);
        return student.getId();
    }

    private void insertTraining(long studentId, long collegeId, String year, String segment,
                                String subjectCode, String subjectName) {
        TrainingProfile training = new TrainingProfile();
        training.setStudentId(studentId);
        training.setCollegeId(collegeId);
        training.setAssessmentYear(year);
        training.setSecondDisciplineCode("050101");
        training.setSecondDisciplineName("汉语言文学");
        training.setInternalMajorCode("P4_NORMAL_A");
        training.setInternalMajorName("Phase4普通师范试点专业A");
        training.setEducationLevel("bachelor");
        training.setTrainingGoal(VOCATIONAL_SEGMENT.equals(segment) ? "secondary_vocational_school_teacher" : "senior_middle_school_teacher");
        training.setInternshipOrgMode("school_organized");
        training.setInternshipLocation(VOCATIONAL_SEGMENT.equals(segment) ? "enterprise_vocational_education" : "primary_secondary_school");
        training.setTeachingSegment(segment);
        training.setTeachingSubjectId(requireSubjectId(subjectCode));
        training.setTeachingSubjectCode(subjectCode);
        training.setTeachingSubjectName(subjectName);
        training.setInterviewOrgMode("separate_interview");
        training.setAbilityTestConclusion("qualified");
        training.setStatus("PASSED");
        training.setLocked(1);
        trainingProfileMapper.insert(training);
    }

    private void insertMaterials(long studentId, long collegeId, String year) {
        for (String category : MATERIAL_CATEGORIES) {
            ProcessMaterial material = new ProcessMaterial();
            material.setStudentId(studentId);
            material.setCollegeId(collegeId);
            material.setAssessmentYear(year);
            material.setCategory(category);
            material.setFileId(0L);
            material.setFileName(category + ".pdf");
            material.setFilePath(category + ".pdf");
            material.setFileSize(12L);
            material.setContentType("application/pdf");
            material.setUploaderId(0L);
            material.setUploadTime(LocalDateTime.now());
            material.setStatus("PASSED");
            material.setLocked(1);
            materialMapper.insert(material);
        }
    }

    private void insertTestResult(long studentId, long collegeId, String year) {
        AbilityTestResult result = new AbilityTestResult();
        result.setStudentId(studentId);
        result.setCollegeId(collegeId);
        result.setAssessmentYear(year);
        result.setExamOrgMode("with_internship_practice");
        result.setExamSubjects("[]");
        result.setScore("85");
        result.setConclusion("qualified");
        result.setExemptionRelation("[]");
        result.setConfirmStatus("CONFIRMED");
        result.setLocked(1);
        testResultMapper.insert(result);
    }

    private void insertVideo(long studentId, long collegeId, String year) {
        VideoReview video = new VideoReview();
        video.setStudentId(studentId);
        video.setCollegeId(collegeId);
        video.setAssessmentYear(year);
        video.setVideoFileId(0L);
        video.setVideoFileName("lesson.mp4");
        video.setFileMd5("md5-" + studentId);
        video.setDurationSeconds(900);
        video.setFormatCheck("PASS");
        video.setStatus("CONFIRMED");
        video.setFinalScore(82);
        video.setFinalConclusion("PASS");
        video.setConfirmedBy(0L);
        video.setConfirmedAt(LocalDateTime.now());
        video.setLocked(1);
        videoReviewMapper.insert(video);
    }

    private long requireSubjectId(String subjectCode) {
        TeachingSubject subject = teachingSubjectMapper.selectOne(new LambdaQueryWrapper<TeachingSubject>()
                .eq(TeachingSubject::getSubjectCode, subjectCode)
                .eq(TeachingSubject::getStatus, 1)
                .last("LIMIT 1"));
        assertThat(subject).isNotNull();
        return subject.getId();
    }

    private int seq(String certNo) {
        return Integer.parseInt(certNo.substring(13));
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

    private String uniqueTravelPermit(String prefix) {
        String alpha = prefix.matches("[A-Za-z]") ? prefix.toUpperCase() : "P";
        return alpha + ("%08d").formatted(Math.floorMod(System.nanoTime(), 100000000));
    }

    private void resetUser(String username, boolean mustChangePwd) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            return;
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setStatus("ENABLED");
        if ("test_student".equals(username)) {
            user.setCollegeId(COLLEGE_A);
            user.setStudentId(9001L);
        }
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

    private void bindStudentUser(String username, long studentId, long collegeId) {
        SysUser user = userMapper.selectByUsername(username);
        assertThat(user).isNotNull();
        user.setStudentId(studentId);
        user.setCollegeId(collegeId);
        user.setMustChangePwd(1);
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        userMapper.updateById(user);
    }

    private void resetParam(String key, String value) {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>().eq(SysParam::getParamKey, key).last("LIMIT 1"));
        if (param == null) {
            return;
        }
        param.setParamValue(value);
        paramMapper.updateById(param);
    }

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE biz_type = 'cert'");
        jdbcTemplate.update("DELETE FROM certificate WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P9%') OR student_no LIKE 'P9%'");
        jdbcTemplate.update("DELETE FROM cert_sequence WHERE scope_key LIKE '10588:2026%' OR scope_key LIKE '10588:2027%' OR scope_key LIKE '10588:2028%' OR scope_key LIKE '10588:2029%' OR scope_key LIKE '10588:2030%' OR scope_key LIKE '10588:2031%' OR scope_key LIKE '10588:2032%'");
        jdbcTemplate.update("DELETE FROM video_review WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P9%')");
        jdbcTemplate.update("DELETE FROM ability_test_result WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P9%')");
        jdbcTemplate.update("DELETE FROM process_material WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P9%')");
        jdbcTemplate.update("DELETE FROM training_profile WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P9%')");
        userMapper.delete(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "STUDENT")
                .likeRight(SysUser::getUsername, "P9"));
        studentMapper.delete(new LambdaQueryWrapper<Student>()
                .likeRight(Student::getStudentNo, "P9"));
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
