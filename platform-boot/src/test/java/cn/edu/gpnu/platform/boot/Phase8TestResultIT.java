package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.boot.support.StaleWriteSqlBarrier;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionRequest;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionRequestMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.testresult.entity.AbilityTestResult;
import cn.edu.gpnu.platform.business.testresult.mapper.AbilityTestResultMapper;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronization;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
@Import(StaleWriteSqlBarrier.class)
@Execution(ExecutionMode.SAME_THREAD)
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
    private IdCardProtectionService idCardProtectionService;

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

    @Autowired
    private StaleWriteSqlBarrier staleWriteSqlBarrier;

    @BeforeEach
    @AfterEach
    void resetSeedData() {
        staleWriteSqlBarrier.reset();
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

        // 手动录入/修改端点从未实现（/api/test 仅有 GET），故 POST/PUT 命中「方法不被支持」。
        // P1-10 后该客户端错误正确返回 405（此前无 405 处理器、落到兜底：改造前 200、改造中一度 500）。
        ResponseEntity<String> created = exchange("/api/test", HttpMethod.POST, auditor.accessToken(),
                saveBody(9001L, year, "88", "qualified"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(json(created).at("/code").asInt()).isNotEqualTo(0);

        ResponseEntity<String> updated = exchange("/api/test", HttpMethod.PUT, auditor.accessToken(),
                saveBody(9001L, year, "89", "unqualified"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(json(updated).at("/code").asInt()).isNotEqualTo(0);

        assertThat(resultMapper.selectCount(new LambdaQueryWrapper<AbilityTestResult>()
                .eq(AbilityTestResult::getStudentId, 9001L)
                .eq(AbilityTestResult::getAssessmentYear, year))).isZero();
    }

    @Test
    void unknownApiPathReturnsNotFoundNotServerError() throws Exception {
        // P1-10 收尾（Phase 51）：未映射路径在 Boot 3.2+ 抛 NoResourceFoundException，须归 404（客户端错误）、
        // 而非落 500 兜底（否则任意错拼/探测路径都误报为服务端故障、污染告警）。
        LoginResult auditor = readyLogin("test_college_auditor");
        ResponseEntity<String> resp = exchange("/api/no-such-endpoint-xyz", HttpMethod.GET, auditor.accessToken(), null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
    @Timeout(90)
    void confirmCommitBeforeStaleImportRejectsContentAndKeepsConfirmedState() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String year = "P8-RACE-CFM";
        long id = importOk(academic.accessToken(), 9001L, year, "85", "qualified").asLong();
        staleWriteSqlBarrier.arm(
                StaleWriteSqlBarrier.Mutation.ABILITY_CONFIRM,
                StaleWriteSqlBarrier.Mutation.ABILITY_CONTENT);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<ResponseEntity<String>> staleImport = null;
        Future<ResponseEntity<String>> firstConfirm = null;
        try {
            staleImport = pool.submit(() -> importRows(
                    academic.accessToken(), 9001L, year, "91", "unqualified"));
            awaitLateMutation(staleImport, "能力结果迟到导入");

            firstConfirm = pool.submit(() -> exchange(
                    "/api/test/" + id + "/confirm", HttpMethod.POST, academic.accessToken(), Map.of()));
            awaitFirstCommit("能力结果确认");
            ResponseEntity<String> confirmed = firstConfirm.get(15, TimeUnit.SECONDS);
            assertThat(json(confirmed).at("/code").asInt()).isEqualTo(0);

            AbilityTestResult committed = resultMapper.selectById(id);
            assertThat(committed.getConfirmStatus()).isEqualTo("CONFIRMED");
            assertThat(committed.getLocked()).isEqualTo(1);
            assertThat(committed.getScore()).isEqualTo("85");
            assertThat(committed.getConclusion()).isEqualTo("qualified");

            staleWriteSqlBarrier.releaseLate();
            ResponseEntity<String> rejected = staleImport.get(15, TimeUnit.SECONDS);
            JsonNode rejectedBody = json(rejected);
            assertThat(rejectedBody.at("/code").asInt()).isEqualTo(1000);
            assertThat(rejectedBody.at("/msg").asText()).contains("操作冲突");

            AbilityTestResult after = resultMapper.selectById(id);
            assertThat(after.getConfirmStatus()).isEqualTo("CONFIRMED");
            assertThat(after.getLocked()).isEqualTo(1);
            assertThat(after.getScore()).isEqualTo("85");
            assertThat(after.getConclusion()).isEqualTo("qualified");
        } finally {
            releaseAndClose(pool, staleImport, firstConfirm);
        }
    }

    @Test
    @Timeout(90)
    void importCommitBeforeStaleConfirmPreservesContentWhileConfirming() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String year = "P8-RACE-IMP";
        long id = importOk(academic.accessToken(), 9001L, year, "85", "qualified").asLong();
        staleWriteSqlBarrier.arm(
                StaleWriteSqlBarrier.Mutation.ABILITY_CONTENT,
                StaleWriteSqlBarrier.Mutation.ABILITY_CONFIRM);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<ResponseEntity<String>> staleConfirm = null;
        Future<ResponseEntity<String>> firstImport = null;
        try {
            staleConfirm = pool.submit(() -> exchange(
                    "/api/test/" + id + "/confirm", HttpMethod.POST, academic.accessToken(), Map.of()));
            awaitLateMutation(staleConfirm, "能力结果迟到确认");

            firstImport = pool.submit(() -> importRows(
                    academic.accessToken(), 9001L, year, "91", "unqualified"));
            awaitFirstCommit("能力结果重新导入");
            ResponseEntity<String> imported = firstImport.get(15, TimeUnit.SECONDS);
            assertThat(json(imported).at("/code").asInt()).isEqualTo(0);

            AbilityTestResult committed = resultMapper.selectById(id);
            assertThat(committed.getConfirmStatus()).isEqualTo("PENDING");
            assertThat(committed.getLocked()).isZero();
            assertThat(committed.getScore()).isEqualTo("91");
            assertThat(committed.getConclusion()).isEqualTo("unqualified");

            staleWriteSqlBarrier.releaseLate();
            ResponseEntity<String> confirmed = staleConfirm.get(15, TimeUnit.SECONDS);
            assertThat(json(confirmed).at("/code").asInt()).isEqualTo(0);

            AbilityTestResult after = resultMapper.selectById(id);
            assertThat(after.getConfirmStatus()).isEqualTo("CONFIRMED");
            assertThat(after.getLocked()).isEqualTo(1);
            assertThat(after.getScore()).isEqualTo("91");
            assertThat(after.getConclusion()).isEqualTo("unqualified");
        } finally {
            releaseAndClose(pool, staleConfirm, firstImport);
        }
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

    /**
     * Phase 44e-contract（P1-1 真分页样例 · 数据范围 × 分页组合的正确性证明）：
     * 学院文员（学院A）对含跨学院同年度测试结果的列表做真分页——
     * ① total 为「已按学院范围过滤」的总数（3，学院B 那条不计入，证明分页 count SQL 也走了数据权限拦截器）；
     * ② 每页条数=请求 size；③ 各页均无学院B 数据；④ 页间记录不重叠（真 LIMIT/OFFSET，非全表包壳）。
     */
    @Test
    void paginatedTestResultListIsScopedAndPagedForCollegeUser() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String year = "P8-PAGE";
        String studentNoPrefix = "P8PAGE" + System.nanoTime();
        long studentA2 = createStudent(studentNoPrefix + "A2", COLLEGE_A);
        long studentA3 = createStudent(studentNoPrefix + "A3", COLLEGE_A);

        importOk(academic.accessToken(), 9001L, year, "81", "qualified");
        importOk(academic.accessToken(), studentA2, year, "82", "qualified");
        importOk(academic.accessToken(), studentA3, year, "83", "qualified");
        // 学院B 同年度 1 条：年度筛选能命中，但学院文员的数据范围应把它排除在 total 与 records 之外。
        importOk(academic.accessToken(), 9002L, year, "84", "qualified");

        LoginResult clerk = readyLogin("test_college_clerk");

        JsonNode page1 = json(exchange("/api/test?assessmentYear=" + year + "&page=1&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page1.at("/total").asLong()).isEqualTo(3);
        assertThat(page1.at("/records").size()).isEqualTo(2);
        assertThat(page1.at("/records").toString()).contains(String.valueOf(COLLEGE_A));
        assertThat(page1.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));

        JsonNode page2 = json(exchange("/api/test?assessmentYear=" + year + "&page=2&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page2.at("/total").asLong()).isEqualTo(3);
        assertThat(page2.at("/records").size()).isEqualTo(1);
        assertThat(page2.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));

        assertThat(page1.at("/records/0/id").asLong())
                .isNotEqualTo(page2.at("/records/0/id").asLong());
    }

    private JsonNode importOk(String token, long studentId, String year, String score, String conclusion) throws Exception {
        ResponseEntity<String> response = importRows(token, studentId, year, score, conclusion);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data/0");
    }

    private void awaitLateMutation(Future<?> lateFuture, String label) throws InterruptedException {
        assertThat(staleWriteSqlBarrier.awaitLateAtUpdate(15, TimeUnit.SECONDS))
                .as("%s 必须到达真实 MyBatis UPDATE 屏障；observed=%s", label, staleWriteSqlBarrier.observedSql())
                .isTrue();
        assertThat(lateFuture.isDone()).as("%s 在放行前不得完成", label).isFalse();
    }

    private void awaitFirstCommit(String label) throws InterruptedException {
        assertThat(staleWriteSqlBarrier.awaitFirstAtUpdate(15, TimeUnit.SECONDS))
                .as("%s 必须到达真实 MyBatis UPDATE；observed=%s", label, staleWriteSqlBarrier.observedSql())
                .isTrue();
        assertThat(staleWriteSqlBarrier.awaitFirstCompletion(15, TimeUnit.SECONDS))
                .as("%s 必须在放行迟到写之前完成真实事务", label)
                .isTrue();
        assertThat(staleWriteSqlBarrier.firstCompletionStatus())
                .as("%s 必须真实 COMMIT", label)
                .isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(staleWriteSqlBarrier.firstAutoCommit()).isFalse();
        assertThat(staleWriteSqlBarrier.lateAutoCommit()).isFalse();
        assertThat(staleWriteSqlBarrier.firstConnectionId()).isNotNull();
        assertThat(staleWriteSqlBarrier.lateConnectionId()).isNotNull();
        assertThat(staleWriteSqlBarrier.firstConnectionId())
                .as("两个竞争事务必须使用不同 MySQL CONNECTION_ID()")
                .isNotEqualTo(staleWriteSqlBarrier.lateConnectionId());
    }

    @SafeVarargs
    private final void releaseAndClose(ExecutorService pool, Future<ResponseEntity<String>>... futures)
            throws InterruptedException {
        staleWriteSqlBarrier.releaseLate();
        for (Future<?> future : futures) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
        pool.shutdownNow();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS))
                .as("F-03 能力结果交错测试不得遗留工作线程")
                .isTrue();
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

    // Phase 44e 真分页 IT（P1-1）：分页×数据范围样例需要同年度、跨学院的多条学生数据；直接经 studentMapper
    // 插入（而非走 /api/student 建档流程），绕开与本测试无关的证件格式/姓名等校验，只保证分页所需的
    // college_id 区分与 student_no 唯一（uk_student_no 不受 deleted 限定，故仍用 nanoTime 后缀防串号）。
    private long createStudent(String studentNo, long collegeId) {
        String idCardNo = "P8IDCARD" + System.nanoTime();
        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName("分页测试学生");
        student.setGender("female");
        student.setIdCardType("resident_id_card");
        student.setIdCardNo(idCardProtectionService.encrypt(idCardNo));
        student.setIdCardHmac(idCardProtectionService.hmac(idCardNo));
        student.setBirthDate("2000/1/1");
        student.setIdentityType("normal_student");
        student.setCollegeId(collegeId);
        student.setStatus("DRAFT");
        student.setLocked(0);
        studentMapper.insert(student);
        return student.getId();
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
        // Phase 44e 真分页 IT（P1-1）：paginatedTestResultListIsScopedAndPagedForCollegeUser 经 createStudent()
        // 直接插入的分页测试专用学生（学号前缀 P8PAGE，非 REST 建档流程产生），随其余 P8 测试数据一并硬删除。
        jdbcTemplate.update("DELETE FROM student WHERE student_no LIKE 'P8PAGE%'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
