package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.boot.support.StaleWriteSqlBarrier;
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
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
@Import(StaleWriteSqlBarrier.class)
@Execution(ExecutionMode.SAME_THREAD)
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
    private IdCardProtectionService idCardProtectionService;

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
    private org.springframework.cache.CacheManager cacheManager;

    @Autowired
    private SysAuditLogMapper auditLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StaleWriteSqlBarrier staleWriteSqlBarrier;

    @BeforeEach
    @AfterEach
    void resetSeedUsers() {
        staleWriteSqlBarrier.reset();
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
    @Timeout(90)
    void issueCommitBeforeStaleCorrectionRejectsContentAndKeepsIssuedState() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        long studentId = seedEligibleStudent(
                "P9RACEIF", COLLEGE_A, "2030", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        long certId = generateOk(academic.accessToken(), studentId, "2030").at("/id").asLong();
        staleWriteSqlBarrier.arm(
                StaleWriteSqlBarrier.Mutation.CERTIFICATE_ISSUE,
                StaleWriteSqlBarrier.Mutation.CERTIFICATE_CORRECT);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<ResponseEntity<String>> staleCorrection = null;
        Future<ResponseEntity<String>> firstIssue = null;
        try {
            staleCorrection = pool.submit(() -> exchange(
                    "/api/cert/" + certId + "/correct", HttpMethod.PUT, academic.accessToken(), Map.of(
                            "teachingSubjectCode", "sms_math",
                            "teachingSubjectName", "数学",
                            "reason", "F-03 迟到更正"
                    )));
            awaitLateMutation(staleCorrection, "证书迟到更正");

            firstIssue = pool.submit(() -> exchange(
                    "/api/cert/" + certId + "/issue", HttpMethod.POST, academic.accessToken(), Map.of(
                            "issuer", "F-03签发人",
                            "issueDate", "2030/3/15"
                    )));
            awaitFirstCommit("证书签发");
            ResponseEntity<String> issued = firstIssue.get(15, TimeUnit.SECONDS);
            assertThat(json(issued).at("/code").asInt()).isEqualTo(0);

            Certificate committed = certificateMapper.selectById(certId);
            assertThat(committed.getStatus()).isEqualTo("ISSUED");
            assertThat(committed.getTeachingSubjectCode()).isEqualTo(SENIOR_SUBJECT_CODE);
            assertThat(committed.getTeachingSubjectName()).isEqualTo("语文");

            staleWriteSqlBarrier.releaseLate();
            ResponseEntity<String> rejected = staleCorrection.get(15, TimeUnit.SECONDS);
            JsonNode rejectedBody = json(rejected);
            assertThat(rejectedBody.at("/code").asInt()).isEqualTo(1000);
            assertThat(rejectedBody.at("/msg").asText()).contains("操作冲突");

            Certificate after = certificateMapper.selectById(certId);
            assertThat(after.getStatus()).isEqualTo("ISSUED");
            assertThat(after.getTeachingSubjectCode()).isEqualTo(SENIOR_SUBJECT_CODE);
            assertThat(after.getTeachingSubjectName()).isEqualTo("语文");
            assertThat(after.getIssuer()).isEqualTo("F-03签发人");
        } finally {
            releaseAndClose(pool, staleCorrection, firstIssue);
        }
    }

    @Test
    @Timeout(90)
    void correctionCommitBeforeStaleIssuePreservesContentWhileIssuing() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        long studentId = seedEligibleStudent(
                "P9RACECF", COLLEGE_A, "2031", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        long certId = generateOk(academic.accessToken(), studentId, "2031").at("/id").asLong();
        staleWriteSqlBarrier.arm(
                StaleWriteSqlBarrier.Mutation.CERTIFICATE_CORRECT,
                StaleWriteSqlBarrier.Mutation.CERTIFICATE_ISSUE);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<ResponseEntity<String>> staleIssue = null;
        Future<ResponseEntity<String>> firstCorrection = null;
        try {
            staleIssue = pool.submit(() -> exchange(
                    "/api/cert/" + certId + "/issue", HttpMethod.POST, academic.accessToken(), Map.of(
                            "issuer", "F-03签发人",
                            "issueDate", "2031/9/1"
                    )));
            awaitLateMutation(staleIssue, "证书迟到签发");

            firstCorrection = pool.submit(() -> exchange(
                    "/api/cert/" + certId + "/correct", HttpMethod.PUT, academic.accessToken(), Map.of(
                            "teachingSubjectCode", "sms_math",
                            "teachingSubjectName", "数学",
                            "reason", "F-03 先提交更正"
                    )));
            awaitFirstCommit("证书更正");
            ResponseEntity<String> corrected = firstCorrection.get(15, TimeUnit.SECONDS);
            assertThat(json(corrected).at("/code").asInt()).isEqualTo(0);

            Certificate committed = certificateMapper.selectById(certId);
            assertThat(committed.getStatus()).isEqualTo("GENERATED");
            assertThat(committed.getTeachingSubjectCode()).isEqualTo("sms_math");
            assertThat(committed.getTeachingSubjectName()).isEqualTo("数学");
            assertThat(committed.getCorrectionReason()).isEqualTo("F-03 先提交更正");

            staleWriteSqlBarrier.releaseLate();
            ResponseEntity<String> issued = staleIssue.get(15, TimeUnit.SECONDS);
            assertThat(json(issued).at("/code").asInt()).isEqualTo(0);

            Certificate after = certificateMapper.selectById(certId);
            assertThat(after.getStatus()).isEqualTo("ISSUED");
            assertThat(after.getTeachingSubjectCode()).isEqualTo("sms_math");
            assertThat(after.getTeachingSubjectName()).isEqualTo("数学");
            assertThat(after.getCorrectionReason()).isEqualTo("F-03 先提交更正");
            assertThat(after.getIssuer()).isEqualTo("F-03签发人");
        } finally {
            releaseAndClose(pool, staleIssue, firstCorrection);
        }
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
        // 原证经原子条件更新由 VOIDED → REISSUED 落库（§7.4 死枚举闭环，与审计 VOIDED→REISSUED 记录对齐）
        assertThat(certificateMapper.selectById(certId).getStatus()).isEqualTo("REISSUED");
        // 原证已非 VOIDED，不可再次重开（阻断"作废证书被反复重开"无界链）
        ResponseEntity<String> secondReissue = exchange("/api/cert/" + certId + "/reissue", HttpMethod.POST,
                academic.accessToken(), Map.of());
        assertThat(json(secondReissue).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(secondReissue).at("/msg").asText()).contains("仅已作废证书可重开");
    }

    @Test
    void certificateReadScopeAndSchoolOnlyWritesAreEnforced() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult clerk = readyLogin("test_college_clerk");
        long a = seedEligibleStudent("P9SCPA", COLLEGE_A, "2032", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        long b = seedEligibleStudent("P9SCPB", COLLEGE_B, "2032", SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        String rawIdCardA = idCardProtectionService.decrypt(studentMapper.selectById(a).getIdCardNo());
        String rawIdCardB = idCardProtectionService.decrypt(studentMapper.selectById(b).getIdCardNo());
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
        JsonNode clerkRecord = null;
        for (JsonNode record : clerkRecords) {
            if (certA.at("/certNo").asText().equals(record.at("/certNo").asText())) {
                clerkRecord = record;
                break;
            }
        }
        assertThat(clerkRecord).isNotNull();
        assertThat(clerkRecord.at("/idCardNo").asText())
                .isNotEqualTo(rawIdCardA)
                .contains("*");
        assertThat(clerkRecords.toString()).doesNotContain(rawIdCardA);

        JsonNode clerkDetail = json(exchange("/api/cert/" + certA.at("/id").asLong(),
                HttpMethod.GET, clerk.accessToken(), null));
        assertThat(clerkDetail.at("/code").asInt()).isZero();
        assertThat(clerkDetail.at("/data/idCardNo").asText())
                .isNotEqualTo(rawIdCardA)
                .contains("*");
        assertThat(clerkDetail.toString()).doesNotContain(rawIdCardA);

        JsonNode crossCollegeDetail = json(exchange("/api/cert/" + certB.at("/id").asLong(),
                HttpMethod.GET, clerk.accessToken(), null));
        assertThat(crossCollegeDetail.at("/code").asInt()).isEqualTo(403);
        assertThat(crossCollegeDetail.toString()).doesNotContain(rawIdCardB);

        JsonNode studentARecords = json(exchange("/api/cert?assessmentYear=2032",
                HttpMethod.GET, studentA.accessToken(), null)).at("/data/records");
        assertThat(studentARecords.size()).isEqualTo(1);
        assertThat(studentARecords.at("/0/studentId").asLong()).isEqualTo(a);

        JsonNode studentBRecords = json(exchange("/api/cert?assessmentYear=2032",
                HttpMethod.GET, studentB.accessToken(), null)).at("/data/records");
        assertThat(studentBRecords.size()).isEqualTo(1);
        assertThat(studentBRecords.at("/0/studentId").asLong()).isEqualTo(b);
    }

    /**
     * Phase 44e（P1-1 真分页铺开 · 数据范围 × 分页组合的正确性证明，逐字仿 Phase3StudentIT
     * .paginatedStudentListIsScopedAndPagedForCollegeUser）：
     * 学院文员（学院A）对含跨学院同前缀证书列表做真分页——
     * ① total 为「已按学院范围过滤」的总数（3，学院B 那条不计入，证明分页 count SQL 也走了数据权限拦截器）；
     * ② 每页条数=请求 size；③ 各页均无学院B 数据；④ 页间记录不重叠（真 LIMIT/OFFSET，非全表包壳）。
     */
    @Test
    void paginatedCertificateListIsScopedAndPagedForCollegeUser() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String prefix = "P9PAGE" + System.nanoTime();
        String year = "2033";
        long a1 = seedEligibleStudent(prefix + "A1", COLLEGE_A, year, SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        long a2 = seedEligibleStudent(prefix + "A2", COLLEGE_A, year, SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        long a3 = seedEligibleStudent(prefix + "A3", COLLEGE_A, year, SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        // 学院B 同前缀 1 条：关键词能命中，但学院文员的数据范围应把它排除在 total 与 records 之外。
        long b1 = seedEligibleStudent(prefix + "B1", COLLEGE_B, year, SENIOR_SEGMENT, SENIOR_SUBJECT_CODE, "语文");
        generateOk(academic.accessToken(), a1, year);
        generateOk(academic.accessToken(), a2, year);
        generateOk(academic.accessToken(), a3, year);
        generateOk(academic.accessToken(), b1, year);

        LoginResult clerk = readyLogin("test_college_clerk");

        JsonNode page1 = json(exchange("/api/cert?keyword=" + prefix + "&page=1&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page1.at("/total").asLong()).isEqualTo(3);
        assertThat(page1.at("/records").size()).isEqualTo(2);
        assertThat(page1.at("/records").toString()).contains(String.valueOf(COLLEGE_A));
        assertThat(page1.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));

        JsonNode page2 = json(exchange("/api/cert?keyword=" + prefix + "&page=2&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page2.at("/total").asLong()).isEqualTo(3);
        assertThat(page2.at("/records").size()).isEqualTo(1);
        assertThat(page2.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));

        assertThat(page1.at("/records/0/id").asLong())
                .isNotEqualTo(page2.at("/records/0/id").asLong());
    }

    private JsonNode generateOk(String token, long studentId, String year) throws Exception {
        ResponseEntity<String> response = exchange("/api/cert/generate", HttpMethod.POST, token,
                Map.of("studentId", studentId, "assessmentYear", year));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data");
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
                .as("F-03 证书交错测试不得遗留工作线程")
                .isTrue();
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
        String idCardNo = uniqueTravelPermit(prefix.substring(0, 1));
        Student student = new Student();
        student.setStudentNo(prefix + "-" + Math.floorMod(System.nanoTime(), 1_000_000_000L));
        student.setName("证书学生" + prefix);
        student.setGender("female");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo(idCardProtectionService.encrypt(idCardNo));
        student.setIdCardHmac(idCardProtectionService.hmac(idCardNo));
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
        training.setInternshipLocation(VOCATIONAL_SEGMENT.equals(segment) ? "other" : "primary_secondary_school");
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
        // Phase 44c（§7.3）：测试越过 service 直接改库，须与生产 updateParam 一样逐出参数缓存，否则经 ParamService 读到旧值。
        org.springframework.cache.Cache paramCache = cacheManager.getCache("sysParam");
        if (paramCache != null) {
            paramCache.clear();
        }
    }

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE biz_type = 'cert'");
        jdbcTemplate.update("DELETE FROM certificate WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P9%') OR student_no LIKE 'P9%'");
        jdbcTemplate.update("DELETE FROM cert_sequence WHERE scope_key LIKE '10588:2026%' OR scope_key LIKE '10588:2027%' OR scope_key LIKE '10588:2028%' OR scope_key LIKE '10588:2029%' OR scope_key LIKE '10588:2030%' OR scope_key LIKE '10588:2031%' OR scope_key LIKE '10588:2032%' OR scope_key LIKE '10588:2033%'");
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
