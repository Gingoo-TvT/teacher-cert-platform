package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionRequest;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionRequestMapper;
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
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.exchange.model.ExchangeColumn;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase14E2EIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long REVIEWER_A_ID = 800000000000003005L;
    private static final long REVIEWER_B_ID = 800000000000003010L;
    private static final long AUDITOR_ID = 800000000000003004L;
    private static final String YEAR = "2026";
    private static final String STUDENT_NO = "00123P14";
    private static final String IMPORT_CERT_NO = "202610588344300901";
    private static final String SUBJECT_EXEMPTED = "comprehensive_quality_junior";
    private static final int VIDEO_PART_SIZE = 8 * 1024 * 1024;
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
    private ExchangeExcelHelper excelHelper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysParamMapper paramMapper;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private TrainingProfileMapper trainingProfileMapper;

    @Autowired
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private ExemptionRequestMapper exemptionRequestMapper;

    @Autowired
    private AbilityTestResultMapper testResultMapper;

    @Autowired
    private VideoReviewMapper videoReviewMapper;

    @Autowired
    private VideoReviewTaskMapper videoReviewTaskMapper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private SysAuditLogMapper auditLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExchangeStandardRow importedRow;
    private Student student;
    private TrainingProfile training;
    private long exemptionId;
    private long videoId;
    private long testId;
    private long certId;
    private String issuedCertNo;
    private long certAuditBefore;

    @BeforeAll
    void initializeE2E() {
        resetData();
    }

    @AfterAll
    void cleanupE2E() {
        resetData();
    }

    private void resetData() {
        cleanupGeneratedData();
        resetParam("video.diffThreshold", "12");
        resetParam("video.reviewerCount", "2");
        resetParam("video.passLine", "60");
        resetParam("video.required", "true");
        resetParam("cert.seq.scope", "SCHOOL_YEAR_SEGMENT");
        resetUser("test_student", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
        resetUser("test_review_teacher", true);
        ensureReviewerB();
        resetUser("test_review_teacher_b", true);
    }

    @Test
    @Order(1)
    void importInsertOnlyCreatesDraftStudentAndTrainingProfile() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        importedRow = standardRow();
        JsonNode pre = prevalidate(academic.accessToken(), List.of(importedRow)).at("/data");
        assertThat(pre.at("/successCount").asInt()).describedAs(pre.toPrettyString()).isEqualTo(1);
        JsonNode imported = confirmImport(academic.accessToken(), pre.at("/batchId").asLong()).at("/data");
        assertThat(imported.at("/successCount").asInt()).describedAs(imported.toPrettyString()).isEqualTo(1);

        student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, STUDENT_NO)
                .last("LIMIT 1"));
        assertThat(student).isNotNull();
        assertThat(student.getStudentNo()).isEqualTo(STUDENT_NO);
        assertThat(student.getIdCardNo()).isEqualTo(importedRow.getIdCardNo());
        training = trainingByStudentYear(student.getId(), YEAR);
        assertThat(training.getTeachingSubjectCode()).isEqualTo(importedRow.getTeachingSubject());
        certificateMapper.delete(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getStudentId, student.getId())
                .eq(Certificate::getAssessmentYear, YEAR));
        resetImportedAuditState(student, training);

        bindStudentUser(student.getId(), student.getCollegeId());
    }

    @Test
    @Order(2)
    void studentSubmitAndTwoLevelReviewPass() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult studentLogin = readyLogin("test_student");

        JsonNode studentDetail = json(exchange("/api/student/" + student.getId(), HttpMethod.GET,
                studentLogin.accessToken(), null)).at("/data");
        assertThat(studentDetail.at("/studentNo").asText()).isEqualTo(STUDENT_NO);
        reviewStudent(academic.accessToken(), clerk.accessToken(), auditor.accessToken(), student.getId());
        assertThat(studentMapper.selectById(student.getId()).getStatus()).isEqualTo("PASSED");
    }

    @Test
    @Order(3)
    void trainingProfileTwoLevelReviewPass() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        reviewTraining(academic.accessToken(), clerk.accessToken(), auditor.accessToken(), training.getId());
        assertThat(trainingProfileMapper.selectById(training.getId()).getStatus()).isEqualTo("PASSED");
    }

    @Test
    @Order(4)
    void fourProcessMaterialsPassedMeansQualified() throws Exception {
        LoginResult studentLogin = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        for (String category : MATERIAL_CATEGORIES) {
            long materialId = uploadMaterial(studentLogin.accessToken(), student.getId(), category, category + ".pdf");
            submitAndApproveMaterial(studentLogin.accessToken(), clerk.accessToken(), auditor.accessToken(), materialId);
        }
        JsonNode materialStatus = json(exchange("/api/material/process-status/" + student.getId() + "?year=" + YEAR,
                HttpMethod.GET, studentLogin.accessToken(), null)).at("/data");
        assertThat(materialStatus.at("/qualified").asBoolean()).isTrue();
    }

    @Test
    @Order(5)
    void exemptionPassRemovesSubjectFromExamSubjects() throws Exception {
        LoginResult studentLogin = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        exemptionId = applyExemption(studentLogin.accessToken(), student.getId());
        uploadExemptionMaterial(studentLogin.accessToken(), exemptionId);
        submitAndApproveExemption(studentLogin.accessToken(), clerk.accessToken(), auditor.accessToken(), exemptionId);
        ExemptionRequest exemption = exemptionRequestMapper.selectById(exemptionId);
        assertThat(exemption.getFinalStatus()).isEqualTo("PASSED");
        JsonNode examSubjects = json(exchange("/api/exemption/exam-subjects/" + student.getId() + "?year=" + YEAR
                + "&segment=junior_middle_school", HttpMethod.GET, studentLogin.accessToken(), null)).at("/data");
        assertSubjectIncluded(examSubjects, SUBJECT_EXEMPTED, false);
    }

    @Test
    @Order(6)
    void videoReviewNeedsThirdExpertAndConfirmsFinalScore() throws Exception {
        LoginResult studentLogin = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        videoId = uploadValidatedVideo(studentLogin.accessToken(), student.getId());
        assignVideo(auditor.accessToken(), videoId);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), videoId), 85, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), videoId), 60, "PASS");
        assertThat(videoReviewMapper.selectById(videoId).getStatus()).isEqualTo("NEED_REVIEW");
        thirdReview(auditor.accessToken(), videoId, 81, "PASS");
        confirmVideo(auditor.accessToken(), videoId);
        VideoReview video = videoReviewMapper.selectById(videoId);
        assertThat(video.getStatus()).isEqualTo("CONFIRMED");
        assertThat(video.getFinalScore()).isEqualTo(83);
    }

    @Test
    @Order(7)
    void testResultKeepsLeadingZeroScoreAndLocksAfterConfirm() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        testId = importTestResult(academic.accessToken(), student.getId());
        assertOk(exchange("/api/test/" + testId + "/confirm", HttpMethod.POST, academic.accessToken(), Map.of()));
        AbilityTestResult result = testResultMapper.selectById(testId);
        assertThat(result.getScore()).isEqualTo("00000000000085");
        assertThat(result.getLocked()).isEqualTo(1);
    }

    @Test
    @Order(8)
    void certificatePrecheckPassesAfterAllPrerequisites() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        JsonNode precheck = json(exchange("/api/cert/precheck/" + student.getId() + "?year=" + YEAR,
                HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(precheck.at("/passed").asBoolean()).isTrue();
        certAuditBefore = countCertificateLifecycleAudit();
    }

    @Test
    @Order(9)
    void certificateGenerationReturnsEighteenDigitNumber() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        JsonNode cert = generateCertificate(academic.accessToken(), student.getId());
        certId = cert.at("/id").asLong();
        assertThat(cert.at("/certNo").asText()).hasSize(18);
        assertThat(cert.at("/certNo").asText()).startsWith("202610588");
    }

    @Test
    @Order(10)
    void certificateIssueCalculatesFirstHalfYearValidity() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        JsonNode issued = issueCertificate(academic.accessToken(), certId);
        issuedCertNo = issued.at("/certNo").asText();
        assertThat(issued.at("/validUntil").asText()).isEqualTo("2029/6/30");
    }

    @Test
    @Order(11)
    void certificateExportOperationSucceeds() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        assertOk(exchange("/api/cert/" + certId + "/export", HttpMethod.POST, academic.accessToken(), Map.of()));
    }

    @Test
    @Order(12)
    void certificateArchiveMarksArchived() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        JsonNode archived = json(exchange("/api/cert/" + certId + "/archive",
                HttpMethod.POST, academic.accessToken(), Map.of())).at("/data");
        assertThat(archived.at("/status").asText()).isEqualTo("ARCHIVED");
    }

    @Test
    @Order(13)
    void standardExportMatchesImportedTextFields() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        ResponseEntity<byte[]> exported = download("/api/exchange/export/STANDARD", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", YEAR, "keyword", STUDENT_NO));
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertStandardExport(exported.getBody(), importedRow, issuedCertNo, "2029/6/30");
    }

    @Test
    @Order(14)
    void fullFlowWritesExpectedAuditTrail() {
        assertThat(auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "student")
                .eq(SysAuditLog::getBizId, student.getId())
                .eq(SysAuditLog::getOperation, "secondReview"))).isGreaterThan(0);
        assertThat(auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "training")
                .eq(SysAuditLog::getBizId, training.getId())
                .eq(SysAuditLog::getOperation, "secondReview"))).isGreaterThan(0);
        assertThat(auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "exemption")
                .eq(SysAuditLog::getBizId, exemptionId)
                .eq(SysAuditLog::getOperation, "secondReview"))).isGreaterThan(0);
        assertThat(auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "video")
                .eq(SysAuditLog::getBizId, videoId)
                .eq(SysAuditLog::getOperation, "confirm"))).isGreaterThan(0);
        assertThat(countCertificateLifecycleAudit()).isGreaterThanOrEqualTo(certAuditBefore + 4);
    }

    private JsonNode prevalidate(String token, List<ExchangeStandardRow> rows) throws Exception {
        byte[] workbook = excelHelper.writeStandardWorkbook(rows, null);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource("phase14.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook));
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.exchange(url("/api/exchange/prevalidate"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertOk(response);
        return json(response);
    }

    private JsonNode confirmImport(String token, long batchId) throws Exception {
        ResponseEntity<String> response = exchange("/api/exchange/import/" + batchId + "/confirm",
                HttpMethod.POST, token, Map.of("strategy", "INSERT_ONLY"));
        assertOk(response);
        return json(response);
    }

    private void reviewStudent(String academicToken, String clerkToken, String auditorToken, long studentId) throws Exception {
        assertOk(exchange("/api/student/" + studentId + "/submit", HttpMethod.POST, academicToken, Map.of()));
        assertOk(exchange("/api/student/" + studentId + "/first-review", HttpMethod.POST, clerkToken,
                Map.of("action", "PASS", "comment", "Phase14学生初审通过")));
        assertOk(exchange("/api/student/" + studentId + "/second-review", HttpMethod.POST, auditorToken,
                Map.of("action", "PASS", "comment", "Phase14学生复审通过")));
    }

    private void reviewTraining(String academicToken, String clerkToken, String auditorToken, long trainingId) throws Exception {
        assertOk(exchange("/api/training/" + trainingId + "/submit", HttpMethod.POST, academicToken, Map.of()));
        assertOk(exchange("/api/training/" + trainingId + "/first-review", HttpMethod.POST, clerkToken,
                Map.of("action", "PASS", "comment", "Phase14培养初审通过")));
        assertOk(exchange("/api/training/" + trainingId + "/second-review", HttpMethod.POST, auditorToken,
                Map.of("action", "PASS", "comment", "Phase14培养复审通过")));
    }

    private long uploadMaterial(String token, long studentId, String category, String filename) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("studentId", String.valueOf(studentId));
        body.add("assessmentYear", YEAR);
        body.add("category", category);
        body.add("file", resource(filename, "application/pdf", "%PDF-1.4".getBytes()));
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.exchange(url("/api/material/upload"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertOk(response);
        return json(response).at("/data").asLong();
    }

    private void submitAndApproveMaterial(String studentToken, String clerkToken, String auditorToken, long id) throws Exception {
        assertOk(exchange("/api/material/" + id + "/submit", HttpMethod.POST, studentToken, Map.of()));
        assertOk(exchange("/api/material/" + id + "/first-review", HttpMethod.POST, clerkToken,
                Map.of("action", "PASS", "comment", "Phase14材料初审通过")));
        assertOk(exchange("/api/material/" + id + "/second-review", HttpMethod.POST, auditorToken,
                Map.of("action", "PASS", "comment", "Phase14材料复审通过")));
    }

    private long applyExemption(String token, long studentId) throws Exception {
        ResponseEntity<String> response = exchange("/api/exemption", HttpMethod.POST, token, Map.of(
                "studentId", studentId,
                "assessmentYear", YEAR,
                "teachingSegment", "junior_middle_school",
                "items", List.of(Map.of("subject", SUBJECT_EXEMPTED, "basis", "policy_exemption", "remark", "Phase14免考依据"))
        ));
        assertOk(response);
        return json(response).at("/data/0").asLong();
    }

    private void uploadExemptionMaterial(String token, long requestId) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource("exemption.pdf", "application/pdf", "%PDF-1.4".getBytes()));
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.exchange(url("/api/exemption/" + requestId + "/materials"),
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertOk(response);
    }

    private void submitAndApproveExemption(String studentToken, String clerkToken, String auditorToken, long id) throws Exception {
        assertOk(exchange("/api/exemption/" + id + "/submit", HttpMethod.POST, studentToken, Map.of()));
        assertOk(exchange("/api/exemption/" + id + "/first-review", HttpMethod.POST, clerkToken,
                Map.of("action", "PASS", "comment", "Phase14免考初审通过")));
        assertOk(exchange("/api/exemption/" + id + "/second-review", HttpMethod.POST, auditorToken,
                Map.of("action", "PASS", "comment", "Phase14免考复审通过")));
    }

    private long uploadValidatedVideo(String token, long studentId) throws Exception {
        byte[] content = mp4("phase14");
        String hash = md5(content);
        JsonNode init = json(exchange("/api/video/upload/init", HttpMethod.POST, token, Map.of(
                "studentId", studentId,
                "assessmentYear", YEAR,
                "fileMd5", hash,
                "fileName", "phase14.mp4",
                "contentType", "video/mp4",
                "size", content.length,
                "chunkSize", VIDEO_PART_SIZE,
                "durationSeconds", 900
        ))).at("/data");
        assertThat(init.at("/uploadMode").asText()).isEqualTo("PRESIGNED_MULTIPART");
        assertThat(init.at("/partSize").asInt()).isEqualTo(VIDEO_PART_SIZE);
        String uploadId = init.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> parts =
                PresignedMultipartUploadTestClient.putAll(init, content);
        ResponseEntity<String> completed = exchange("/api/video/upload/complete", HttpMethod.POST, token, Map.of(
                "uploadId", uploadId,
                "durationSeconds", 900,
                "parts", PresignedMultipartUploadTestClient.completionParts(parts)
        ));
        assertOk(completed);
        JsonNode data = json(completed).at("/data");
        assertThat(data.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        return data.at("/id").asLong();
    }

    private void assignVideo(String token, long reviewId) throws Exception {
        assertOk(exchange("/api/video/reviews/" + reviewId + "/assign", HttpMethod.POST, token,
                Map.of("reviewerIds", List.of(REVIEWER_A_ID, REVIEWER_B_ID))));
    }

    private void score(String token, long taskId, int score, String conclusion) throws Exception {
        assertOk(exchange("/api/video/tasks/" + taskId + "/score", HttpMethod.POST, token, scoreBody(score, conclusion)));
    }

    private void thirdReview(String token, long reviewId, int score, String conclusion) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(scoreBody(score, conclusion));
        body.put("reviewerId", AUDITOR_ID);
        assertOk(exchange("/api/video/reviews/" + reviewId + "/third-review", HttpMethod.POST, token, body));
    }

    private void confirmVideo(String token, long reviewId) throws Exception {
        assertOk(exchange("/api/video/reviews/" + reviewId + "/confirm", HttpMethod.POST, token, Map.of()));
    }

    private Map<String, Object> scoreBody(int score, String conclusion) {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("lesson_design", 10);
        dimensions.put("teaching_objective", 10);
        dimensions.put("key_difficulty", 10);
        dimensions.put("teaching_implementation", 10);
        dimensions.put("classroom_organization", 10);
        dimensions.put("subject_literacy", 10);
        dimensions.put("language_expression", 10);
        dimensions.put("courseware_blackboard", 10);
        dimensions.put("teaching_reflection", 10);
        return Map.of("score", score, "dimensionScores", dimensions, "comment", "Phase14评分-" + score,
                "conclusion", conclusion);
    }

    private long taskIdByReview(String token, long reviewId) throws Exception {
        JsonNode tasks = json(exchange("/api/video/tasks/my", HttpMethod.GET, token, null)).at("/data/records");
        for (JsonNode task : tasks) {
            if (task.at("/videoReviewId").asLong() == reviewId) {
                return task.at("/id").asLong();
            }
        }
        throw new AssertionError("task not found for review " + reviewId);
    }

    private long importTestResult(String token, long studentId) throws Exception {
        ResponseEntity<String> response = exchange("/api/test/import", HttpMethod.POST, token, Map.of(
                "rows", List.of(Map.of(
                        "studentId", studentId,
                        "assessmentYear", YEAR,
                        "teachingSegment", "junior_middle_school",
                        "examOrgMode", "separate_interview",
                        "score", "00000000000085",
                        "conclusion", "qualified"
                ))
        ));
        assertOk(response);
        return json(response).at("/data/0").asLong();
    }

    private JsonNode generateCertificate(String token, long studentId) throws Exception {
        ResponseEntity<String> response = exchange("/api/cert/generate", HttpMethod.POST, token,
                Map.of("studentId", studentId, "assessmentYear", YEAR));
        assertOk(response);
        return json(response).at("/data");
    }

    private JsonNode issueCertificate(String token, long certId) throws Exception {
        ResponseEntity<String> response = exchange("/api/cert/" + certId + "/issue", HttpMethod.POST, token,
                Map.of("issuer", "校长", "issueDate", "2026/6/17"));
        assertOk(response);
        return json(response).at("/data");
    }

    private void assertStandardExport(byte[] content, ExchangeStandardRow importedRow, String certNo, String validUntil) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            DataFormatter formatter = new DataFormatter();
            assertThat(header.getLastCellNum()).isEqualTo((short) 26);
            for (int i = 0; i < ExchangeColumn.ALL.size(); i++) {
                assertThat(formatter.formatCellValue(header.getCell(i))).isEqualTo(ExchangeColumn.ALL.get(i).header());
                CellStyle style = workbook.getSheetAt(0).getColumnStyle(i);
                assertThat(style.getDataFormatString()).isEqualTo("@");
            }
            assertThat(formatter.formatCellValue(header.getCell(7))).isEqualTo("身份证件号码");
            Row data = workbook.getSheetAt(0).getRow(1);
            assertThat(formatter.formatCellValue(data.getCell(3))).isEqualTo(importedRow.getStudentNo());
            assertThat(formatter.formatCellValue(data.getCell(4))).isEqualTo(importedRow.getName());
            assertThat(formatter.formatCellValue(data.getCell(7))).isEqualTo(importedRow.getIdCardNo());
            assertThat(formatter.formatCellValue(data.getCell(8))).isEqualTo(importedRow.getBirthDate());
            assertThat(formatter.formatCellValue(data.getCell(20))).isEqualTo(importedRow.getTeachingSubject());
            assertThat(formatter.formatCellValue(data.getCell(22))).isEqualTo(certNo);
            assertThat(formatter.formatCellValue(data.getCell(23))).isEqualTo(validUntil);
        }
    }

    private ExchangeStandardRow standardRow() {
        ExchangeStandardRow row = new ExchangeStandardRow();
        row.setSequenceNo(YEAR);
        row.setSchoolCode("10588");
        row.setSchoolName("广东技术师范大学");
        row.setStudentNo(STUDENT_NO);
        row.setName("阶段十四学生");
        row.setGender("female");
        row.setIdCardType("resident_id_card");
        row.setIdCardNo("44010620001231001X");
        row.setBirthDate("2000/12/31");
        row.setIdentityType("normal_student");
        row.setSourcePlace("广东省/广州市/天河区");
        row.setSecondDisciplineCode("050101");
        row.setSecondDisciplineName("汉语言文学");
        row.setInternalMajorCode("P4_NORMAL_A");
        row.setInternalMajorName("Phase4普通师范试点专业A");
        row.setEducationLevel("bachelor");
        row.setTrainingGoal("junior_middle_school_teacher");
        row.setInternshipOrgMode("school_organized");
        row.setInternshipLocation("primary_secondary_school");
        row.setTeachingSegment("junior_middle_school");
        row.setTeachingSubject("jms_chinese");
        row.setInterviewOrgMode("separate_interview");
        row.setCertNo(IMPORT_CERT_NO);
        row.setValidUntil("2029/6/30");
        row.setIssuer("校长");
        row.setRemark(String.valueOf(COLLEGE_A));
        return row;
    }

    private Map<String, Object> studentBody(Student student) {
        return Map.ofEntries(
                Map.entry("studentNo", student.getStudentNo()),
                Map.entry("name", student.getName()),
                Map.entry("gender", student.getGender()),
                Map.entry("idCardType", student.getIdCardType()),
                Map.entry("idCardNo", student.getIdCardNo()),
                Map.entry("birthDate", student.getBirthDate()),
                Map.entry("identityType", student.getIdentityType()),
                Map.entry("sourceProvince", "440000"),
                Map.entry("sourceCity", "440100"),
                Map.entry("sourceCounty", "440106"),
                Map.entry("sourceFull", student.getSourceFull()),
                Map.entry("collegeId", COLLEGE_A),
                Map.entry("grade", "2022"),
                Map.entry("className", "Phase14端到端班")
        );
    }

    private void resetImportedAuditState(Student student, TrainingProfile training) {
        student.setStatus("DRAFT");
        student.setLocked(0);
        studentMapper.updateById(student);
        training.setStatus("DRAFT");
        training.setLocked(0);
        trainingProfileMapper.updateById(training);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = authHeaders(accessToken);
        headers.add("X-Forwarded-For", "127.0.0.1");
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<byte[]> download(String path, HttpMethod method, String accessToken, Object body) {
        return rest.exchange(url(path), method, new HttpEntity<>(body, authHeaders(accessToken)), byte[].class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<ByteArrayResource> resource(String filename, String contentType, byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        return new HttpEntity<>(resource, headers);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertOk(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).describedAs(root.toString()).isEqualTo(0);
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
            return new LoginResult(data.at("/accessToken").asText(), data.at("/refreshToken").asText(),
                    data.at("/mustChangePwd").asBoolean());
        }
        return login(username, CHANGED_PASSWORD);
    }

    private LoginResult login(String username, String password) throws Exception {
        ResponseEntity<String> response = loginRaw(username, password);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        JsonNode data = root.at("/data");
        return new LoginResult(data.at("/accessToken").asText(), data.at("/refreshToken").asText(),
                data.at("/mustChangePwd").asBoolean());
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

    private void assertSubjectIncluded(JsonNode subjects, String subject, boolean included) {
        boolean found = false;
        for (JsonNode item : subjects) {
            if (subject.equals(item.at("/subject").asText())) {
                found = true;
                assertThat(item.at("/includedInExam").asBoolean()).isEqualTo(included);
            }
        }
        assertThat(found).isTrue();
    }

    private long countCertificateLifecycleAudit() {
        return auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "cert")
                .in(SysAuditLog::getOperation, List.of("generate", "issue", "export", "archive")));
    }

    private TrainingProfile trainingByStudentYear(Long studentId, String year) {
        return trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, studentId)
                .eq(TrainingProfile::getAssessmentYear, year)
                .last("LIMIT 1"));
    }

    private byte[] mp4(String text) {
        try (InputStream input = getClass().getResourceAsStream("/db/demo/sample-video.mp4")) {
            if (input == null) {
                throw new IllegalStateException("测试样例视频不存在");
            }
            byte[] source = input.readAllBytes();
            byte[] marker = text.getBytes(StandardCharsets.UTF_8);
            byte[] result = Arrays.copyOf(source, source.length + 8 + marker.length);
            ByteBuffer.wrap(result, source.length, 8 + marker.length)
                    .putInt(8 + marker.length)
                    .put("free".getBytes(StandardCharsets.US_ASCII))
                    .put(marker);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String md5(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void bindStudentUser(long studentId, long collegeId) {
        SysUser user = userMapper.selectByUsername("test_student");
        assertThat(user).isNotNull();
        user.setStudentId(studentId);
        user.setCollegeId(collegeId);
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setMustChangePwd(1);
        userMapper.updateById(user);
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

    private void ensureReviewerB() {
        SysUser user = userMapper.selectByUsername("test_review_teacher_b");
        if (user == null) {
            user = new SysUser();
            user.setId(REVIEWER_B_ID);
            user.setUsername("test_review_teacher_b");
            user.setUserType("STAFF");
            user.setCollegeId(COLLEGE_A);
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setRealName("评审教师测试账号B");
        user.setWorkNo("RBAC_REVIEWER_B");
        user.setStatus("ENABLED");
        user.setUserType("STAFF");
        user.setCollegeId(COLLEGE_A);
        user.setStudentId(null);
        user.setMustChangePwd(1);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(null);
        if (userMapper.selectByUsername("test_review_teacher_b") == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user_role(id, user_id, role_id, deleted)
                VALUES (?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), role_id = VALUES(role_id), deleted = 0
                """, 800000000000004010L, REVIEWER_B_ID, 800000000000000004L);
    }

    private void resetParam(String key, String value) {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, key)
                .last("LIMIT 1"));
        if (param != null) {
            param.setParamValue(value);
            paramMapper.updateById(param);
            // Phase 44c（§7.3）：测试越过 service 直接改库，须与生产 updateParam 一样逐出参数缓存，否则经 ParamService 读到旧值。
            org.springframework.cache.Cache paramCache = cacheManager.getCache("sysParam");
            if (paramCache != null) {
                paramCache.clear();
            }
        }
    }

    private void cleanupGeneratedData() {
        String likeStudent = "%" + STUDENT_NO + "%";
        jdbcTemplate.update("""
                DELETE FROM audit_log
                WHERE target LIKE '%P14%'
                   OR target LIKE '%phase14%'
                   OR comment LIKE '%Phase14%'
                   OR comment LIKE '%阶段十四%'
                """);
        jdbcTemplate.update("""
                DELETE FROM import_error_detail
                WHERE student_no = ?
                   OR batch_id IN (
                       SELECT id FROM import_export_batch
                       WHERE preview_json LIKE ? OR scope_json LIKE ? OR file_name LIKE ?
                   )
                """, STUDENT_NO, likeStudent, likeStudent, likeStudent);
        jdbcTemplate.update("""
                DELETE FROM import_record_ref
                WHERE before_json LIKE ?
                   OR after_json LIKE ?
                   OR batch_id IN (
                       SELECT id FROM import_export_batch
                       WHERE preview_json LIKE ? OR scope_json LIKE ? OR file_name LIKE ?
                   )
                   OR (table_name = 'student' AND record_id IN (SELECT id FROM student WHERE student_no = ?))
                   OR (table_name = 'training_profile' AND record_id IN (
                       SELECT id FROM training_profile
                       WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)
                   ))
                   OR (table_name = 'certificate' AND record_id IN (
                       SELECT id FROM certificate
                       WHERE student_no = ? OR student_id IN (SELECT id FROM student WHERE student_no = ?)
                   ))
                """, likeStudent, likeStudent, likeStudent, likeStudent, likeStudent,
                STUDENT_NO, STUDENT_NO, STUDENT_NO, STUDENT_NO);
        jdbcTemplate.update("""
                DELETE FROM import_export_batch
                WHERE preview_json LIKE ? OR scope_json LIKE ? OR file_name LIKE ?
                """, likeStudent, likeStudent, likeStudent);
        jdbcTemplate.update("""
                DELETE FROM video_review_task
                WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)
                   OR video_review_id IN (
                       SELECT id FROM video_review
                       WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)
                   )
                """, STUDENT_NO, STUDENT_NO);
        jdbcTemplate.update("""
                DELETE FROM video_upload_chunk
                WHERE upload_id IN (
                    SELECT upload_id FROM video_upload_session
                    WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)
                )
                """, STUDENT_NO);
        jdbcTemplate.update("""
                DELETE FROM file_object
                WHERE id IN (
                    SELECT file_id FROM process_material
                    WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)
                )
                   OR id IN (
                    SELECT file_id FROM exemption_material
                    WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)
                )
                   OR id IN (
                    SELECT video_file_id FROM video_review
                    WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)
                      AND video_file_id IS NOT NULL
                )
                   OR id IN (
                    SELECT file_id FROM video_upload_session
                    WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)
                      AND file_id IS NOT NULL
                )
                """, STUDENT_NO, STUDENT_NO, STUDENT_NO, STUDENT_NO);
        jdbcTemplate.update("DELETE FROM video_upload_session WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)", STUDENT_NO);
        jdbcTemplate.update("DELETE FROM certificate WHERE student_no = ? OR student_id IN (SELECT id FROM student WHERE student_no = ?)", STUDENT_NO, STUDENT_NO);
        jdbcTemplate.update("DELETE FROM ability_test_result WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)", STUDENT_NO);
        jdbcTemplate.update("DELETE FROM video_review WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)", STUDENT_NO);
        jdbcTemplate.update("DELETE FROM exemption_material WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)", STUDENT_NO);
        jdbcTemplate.update("DELETE FROM exemption_request WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)", STUDENT_NO);
        jdbcTemplate.update("DELETE FROM process_material WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)", STUDENT_NO);
        jdbcTemplate.update("DELETE FROM training_profile WHERE student_id IN (SELECT id FROM student WHERE student_no = ?)", STUDENT_NO);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username = ?)", STUDENT_NO);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", STUDENT_NO);
        jdbcTemplate.update("DELETE FROM student WHERE student_no = ?", STUDENT_NO);
        jdbcTemplate.update("UPDATE sys_user SET student_id = 9001, college_id = ? WHERE username = 'test_student'", COLLEGE_A);
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
