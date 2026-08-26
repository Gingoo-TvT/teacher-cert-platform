package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionMaterial;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionRequest;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionMaterialMapper;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionRequestMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase6ExemptionIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;
    private static final String YEAR = "P6-2026";
    private static final String SEGMENT = "junior_middle_school";
    private static final String SUBJECT_A = "comprehensive_quality_junior";
    private static final String SUBJECT_B = "education_knowledge_junior";
    private static final String SUBJECT_C = "subject_knowledge_junior";
    private static final String BASIS = "policy_exemption";

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
    private ExemptionMaterialMapper exemptionMaterialMapper;

    @Autowired
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private SysParamMapper paramMapper;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

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
        resetParam("file.maxSize.exemption", "52428800");
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
    }

    @Test
    void multiSubjectReviewResultsAreIndependentAndExamSubjectsExcludeOnlyPassed() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        List<Long> ids = applyOk(student.accessToken(), 9001L, YEAR, SEGMENT, SUBJECT_A, SUBJECT_B, SUBJECT_C);
        uploadOk(student.accessToken(), ids.get(0), "a.pdf");
        uploadOk(student.accessToken(), ids.get(1), "b.pdf");
        uploadOk(student.accessToken(), ids.get(2), "c.pdf");

        submit(student.accessToken(), ids.get(0));
        firstReview(clerk.accessToken(), ids.get(0), "PASS", "初审通过");
        secondReview(auditor.accessToken(), ids.get(0), "PASS", "复审通过");
        submit(student.accessToken(), ids.get(1));
        firstReview(clerk.accessToken(), ids.get(1), "REJECT", "退回补充");
        submit(student.accessToken(), ids.get(2));
        firstReview(clerk.accessToken(), ids.get(2), "PASS", "初审通过");
        secondReview(auditor.accessToken(), ids.get(2), "FAIL", "不通过");

        ExemptionRequest passed = exemptionRequestMapper.selectById(ids.get(0));
        ExemptionRequest rejected = exemptionRequestMapper.selectById(ids.get(1));
        ExemptionRequest failed = exemptionRequestMapper.selectById(ids.get(2));
        assertThat(passed.getFinalStatus()).isEqualTo("PASSED");
        assertThat(passed.getIncludedInExam()).isZero();
        assertThat(rejected.getFinalStatus()).isEqualTo("FIRST_REJECTED");
        assertThat(rejected.getIncludedInExam()).isEqualTo(1);
        assertThat(failed.getFinalStatus()).isEqualTo("FAILED");
        assertThat(failed.getIncludedInExam()).isEqualTo(1);

        JsonNode examSubjects = json(exchange("/api/exemption/exam-subjects/9001?year=" + YEAR + "&segment=" + SEGMENT,
                HttpMethod.GET, student.accessToken(), null)).at("/data");
        assertSubjectIncluded(examSubjects, SUBJECT_A, false);
        assertSubjectIncluded(examSubjects, SUBJECT_B, true);
        assertSubjectIncluded(examSubjects, SUBJECT_C, true);
    }

    @Test
    void missingEvidenceCannotSubmitAndEditableGuardRejectsInReviewOrPassedWrite() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        List<Long> ids = applyOk(student.accessToken(), 9001L, YEAR, SEGMENT, SUBJECT_A, SUBJECT_B);

        ResponseEntity<String> missingEvidence = exchange("/api/exemption/" + ids.get(0) + "/submit",
                HttpMethod.POST, student.accessToken(), Map.of());
        assertThat(json(missingEvidence).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(missingEvidence).at("/msg").asText()).contains("免考佐证不能为空");

        uploadOk(student.accessToken(), ids.get(0), "review.pdf");
        submit(student.accessToken(), ids.get(0));
        ResponseEntity<String> updateInReview = exchange("/api/exemption/" + ids.get(0), HttpMethod.PUT,
                student.accessToken(), Map.of("basis", BASIS, "remark", "在审修改"));
        assertThat(json(updateInReview).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(updateInReview).at("/msg").asText()).contains("当前状态不可编辑");

        firstReview(clerk.accessToken(), ids.get(0), "PASS", "初审通过");
        secondReview(auditor.accessToken(), ids.get(0), "PASS", "复审通过");
        ResponseEntity<String> replacePassed = replace(student.accessToken(),
                firstMaterialId(ids.get(0)), "new.pdf", "application/pdf", "%PDF-new".getBytes());
        assertThat(json(replacePassed).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(replacePassed).at("/msg").asText()).contains("当前状态不可编辑");
    }

    @Test
    void exemptionDoesNotOverwriteProcessMaterialConclusion() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        ProcessMaterial process = new ProcessMaterial();
        process.setId(960000000000000001L);
        process.setStudentId(9001L);
        process.setCollegeId(COLLEGE_A);
        process.setAssessmentYear(YEAR);
        process.setCategory("morality_teacher_ethics");
        process.setFileId(0L);
        process.setFileName("process.pdf");
        process.setFilePath("process.pdf");
        process.setUploaderId(0L);
        process.setUploadTime(LocalDateTime.now());
        process.setStatus("FAILED");
        process.setLocked(1);
        materialMapper.insert(process);

        List<Long> ids = applyOk(student.accessToken(), 9001L, YEAR, SEGMENT, SUBJECT_A);
        uploadOk(student.accessToken(), ids.get(0), "a.pdf");
        approve(clerk.accessToken(), auditor.accessToken(), ids.get(0));

        ProcessMaterial after = materialMapper.selectById(process.getId());
        assertThat(after.getStatus()).isEqualTo("FAILED");
        assertThat(after.getLocked()).isEqualTo(1);
    }

    @Test
    void exemptionReadAndWriteScopeAreEnforced() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        LoginResult clerk = readyLogin("test_college_clerk");
        List<Long> aIds = applyOk(studentA.accessToken(), 9001L, YEAR, SEGMENT, SUBJECT_A);
        uploadOk(studentA.accessToken(), aIds.get(0), "a.pdf");

        ResponseEntity<String> crossStudentWrite = exchange("/api/exemption", HttpMethod.POST, studentA.accessToken(),
                applyBody(9002L, YEAR, SEGMENT, SUBJECT_A));
        assertThat(json(crossStudentWrite).at("/code").asInt()).isEqualTo(403);
        assertThat(exemptionRequestMapper.selectCount(new LambdaQueryWrapper<ExemptionRequest>()
                .eq(ExemptionRequest::getStudentId, 9002L)
                .eq(ExemptionRequest::getAssessmentYear, YEAR))).isZero();

        ResponseEntity<String> clerkCrossCollege = exchange("/api/exemption", HttpMethod.POST, clerk.accessToken(),
                applyBody(9002L, YEAR, SEGMENT, SUBJECT_B));
        assertThat(json(clerkCrossCollege).at("/code").asInt()).isEqualTo(403);
        assertThat(exemptionRequestMapper.selectCount(new LambdaQueryWrapper<ExemptionRequest>()
                .eq(ExemptionRequest::getStudentId, 9002L)
                .eq(ExemptionRequest::getSubject, SUBJECT_B))).isZero();

        List<Long> bIds = applyOk(studentB.accessToken(), 9002L, YEAR, SEGMENT, SUBJECT_C);
        uploadOk(studentB.accessToken(), bIds.get(0), "b.pdf");
        ResponseEntity<String> clerkList = exchange("/api/exemption?assessmentYear=" + YEAR,
                HttpMethod.GET, clerk.accessToken(), null);
        JsonNode clerkRecords = json(clerkList).at("/data/records");
        assertThat(clerkRecords.toString()).contains(String.valueOf(aIds.get(0)));
        assertThat(clerkRecords.toString()).doesNotContain(String.valueOf(bIds.get(0)));

        ResponseEntity<String> studentList = exchange("/api/exemption?assessmentYear=" + YEAR,
                HttpMethod.GET, studentA.accessToken(), null);
        JsonNode studentRecords = json(studentList).at("/data/records");
        assertThat(studentRecords.size()).isEqualTo(1);
        assertThat(studentRecords.at("/0/studentId").asLong()).isEqualTo(9001L);
    }

    @Test
    void exemptionMaterialCookieSupportsRangeAndRejectsLogoutAndAccountSwitch() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        byte[] contentA = "%PDF-1.7\nexemption-A-range-contract\n%%EOF"
                .getBytes(StandardCharsets.UTF_8);
        long requestA = applyOk(studentA.accessToken(), 9001L, YEAR, SEGMENT, SUBJECT_A).get(0);
        ResponseEntity<String> uploadedA = upload(studentA.accessToken(), requestA,
                "evidence-a.pdf", "application/pdf", contentA);
        assertThat(json(uploadedA).at("/code").asInt()).isZero();
        long materialA = firstMaterialId(requestA);
        String contentPathA = "/api/exemption/materials/" + materialA + "/content";

        assertAll("免考佐证媒体 Cookie 安全合同",
                () -> {
                    ResponseEntity<String> previewA = exchange(
                            "/api/exemption/materials/" + materialA + "/preview",
                            HttpMethod.GET, studentA.accessToken(), null);
                    assertThat(json(previewA).at("/data").asText()).isEqualTo(contentPathA);
                    String cookieA = mediaCookie(previewA);
                    assertThat(cookieMaxAge(previewA)).isBetween(1L, 30L);
                    ResponseEntity<byte[]> rangeA = mediaBytes(contentPathA, cookieA, "bytes=5-11");
                    assertThat(rangeA.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
                    assertThat(rangeA.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                            .isEqualTo("bytes 5-11/" + contentA.length);
                    assertThat(rangeA.getBody())
                            .isEqualTo(java.util.Arrays.copyOfRange(contentA, 5, 12));
                },
                () -> {
                    LoginResult studentB = readyLogin("test_student_b");
                    byte[] contentB = "%PDF-1.7\nexemption-B-account-contract\n%%EOF"
                            .getBytes(StandardCharsets.UTF_8);
                    long requestB = applyOk(
                            studentB.accessToken(), 9002L, YEAR, SEGMENT, SUBJECT_B).get(0);
                    ResponseEntity<String> uploadedB = upload(
                            studentB.accessToken(), requestB,
                            "evidence-b.pdf", "application/pdf", contentB);
                    assertThat(json(uploadedB).at("/code").asInt()).isZero();
                    long materialB = firstMaterialId(requestB);
                    ResponseEntity<String> previewB = exchange(
                            "/api/exemption/materials/" + materialB + "/preview",
                            HttpMethod.GET, studentB.accessToken(), null);
                    ResponseEntity<String> crossAccount = mediaText(
                            contentPathA, mediaCookie(previewB), "bytes=0-3");
                    // 超出 @DataScope 的定向读取按“不可见即不存在”返回 404，避免泄露资源存在性。
                    assertThat(json(crossAccount).at("/code").asInt()).isEqualTo(404);
                },
                () -> {
                    readyLogin("test_student_b");
                    ResponseEntity<String> switched = loginRaw("test_student_b", CHANGED_PASSWORD);
                    assertThat(json(switched).at("/code").asInt()).isZero();
                    assertClearsMediaCookie(switched);
                    assertThat(mediaBytes(contentPathA, cookiePair(switched), "bytes=0-3").getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED);
                },
                () -> {
                    LoginResult freshStudentA = login("test_student", CHANGED_PASSWORD);
                    ResponseEntity<String> freshPreviewA = exchange(
                            "/api/exemption/materials/" + materialA + "/preview",
                            HttpMethod.GET, freshStudentA.accessToken(), null);
                    String retainedOldCookie = mediaCookie(freshPreviewA);
                    ResponseEntity<String> logout = exchange(
                            "/api/auth/logout", HttpMethod.POST, freshStudentA.accessToken(), null);
                    assertThat(json(logout).at("/code").asInt()).isZero();
                    assertClearsMediaCookie(logout);
                    assertThat(mediaBytes(contentPathA, retainedOldCookie, "bytes=0-3").getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    /**
     * Phase 44e-rollout（P1-1 真分页 · 数据范围 × 分页组合的正确性证明，逐字参照
     * Phase3StudentIT.paginatedStudentListIsScopedAndPagedForCollegeUser 落地到免考列表）：
     * 学院文员（学院A）对含跨学院同年度免考申请列表做真分页——
     * ① total 为「已按学院范围过滤」的总数（3，学院B 那条不计入，证明分页 count SQL 也走了数据权限拦截器）；
     * ② 每页条数=请求 size；③ 各页均无学院B 数据；④ 页间记录不重叠（真 LIMIT/OFFSET，非全表包壳）。
     */
    @Test
    void paginatedExemptionListIsScopedAndPagedForCollegeUser() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        LoginResult clerk = readyLogin("test_college_clerk");

        // assessment_year 列是 VARCHAR(16)：完整 nanoTime()（最多 19 位）拼接前缀会超长触发截断异常，
        // 故对 nanoTime 取模到 9 位以内，'P6PAGE'(6 位)+最多 9 位数字 <= 15 位，留有余量。
        String year = "P6PAGE" + Math.abs(System.nanoTime() % 1_000_000_000L);
        // 学院A（学生9001）同年度 3 科申请。
        applyOk(studentA.accessToken(), 9001L, year, SEGMENT, SUBJECT_A, SUBJECT_B, SUBJECT_C);
        // 学院B（学生9002）同年度 1 科：年度能命中，但学院文员的数据范围应把它排除在 total 与 records 之外。
        applyOk(studentB.accessToken(), 9002L, year, SEGMENT, SUBJECT_A);

        JsonNode page1 = json(exchange("/api/exemption?assessmentYear=" + year + "&page=1&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page1.at("/total").asLong()).isEqualTo(3);
        assertThat(page1.at("/records").size()).isEqualTo(2);
        assertThat(page1.at("/records").toString()).contains(String.valueOf(COLLEGE_A));
        assertThat(page1.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));

        JsonNode page2 = json(exchange("/api/exemption?assessmentYear=" + year + "&page=2&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page2.at("/total").asLong()).isEqualTo(3);
        assertThat(page2.at("/records").size()).isEqualTo(1);
        assertThat(page2.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));

        assertThat(page1.at("/records/0/id").asLong())
                .isNotEqualTo(page2.at("/records/0/id").asLong());
    }

    private List<Long> applyOk(String token, long studentId, String year, String segment, String... subjects) throws Exception {
        ResponseEntity<String> response = exchange("/api/exemption", HttpMethod.POST, token,
                applyBody(studentId, year, segment, subjects));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return objectMapper.readerForListOf(Long.class).readValue(root.at("/data"));
    }

    private Map<String, Object> applyBody(long studentId, String year, String segment, String... subjects) {
        return Map.of(
                "studentId", studentId,
                "assessmentYear", year,
                "teachingSegment", segment,
                "items", java.util.Arrays.stream(subjects)
                        .map(subject -> Map.of("subject", subject, "basis", BASIS, "remark", "免考说明"))
                        .toList()
        );
    }

    private void uploadOk(String token, long requestId, String filename) throws Exception {
        ResponseEntity<String> response = upload(token, requestId, filename, "application/pdf", "%PDF-1.4".getBytes());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private ResponseEntity<String> upload(String token, long requestId, String filename, String contentType, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource(filename, contentType, content));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url("/api/exemption/" + requestId + "/materials"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> replace(String token, long materialId, String filename, String contentType, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource(filename, contentType, content));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url("/api/exemption/materials/" + materialId), HttpMethod.PUT,
                new HttpEntity<>(body, headers), String.class);
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

    private void approve(String clerkToken, String auditorToken, long id) throws Exception {
        submit(readyLogin("test_student").accessToken(), id);
        firstReview(clerkToken, id, "PASS", "初审通过");
        secondReview(auditorToken, id, "PASS", "复审通过");
    }

    private void submit(String token, long id) throws Exception {
        ResponseEntity<String> response = exchange("/api/exemption/" + id + "/submit", HttpMethod.POST, token, Map.of());
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void firstReview(String token, long id, String action, String comment) throws Exception {
        ResponseEntity<String> response = exchange("/api/exemption/" + id + "/first-review", HttpMethod.POST,
                token, Map.of("action", action, "comment", comment));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void secondReview(String token, long id, String action, String comment) throws Exception {
        ResponseEntity<String> response = exchange("/api/exemption/" + id + "/second-review", HttpMethod.POST,
                token, Map.of("action", action, "comment", comment));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private long firstMaterialId(long requestId) {
        ExemptionMaterial material = exemptionMaterialMapper.selectOne(new LambdaQueryWrapper<ExemptionMaterial>()
                .eq(ExemptionMaterial::getExemptionRequestId, requestId)
                .last("LIMIT 1"));
        return material.getId();
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

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<byte[]> mediaBytes(String path, String cookie, String range) {
        HttpHeaders headers = mediaHeaders(cookie, range);
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private ResponseEntity<String> mediaText(String path, String cookie, String range) {
        HttpHeaders headers = mediaHeaders(cookie, range);
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private HttpHeaders mediaHeaders(String cookie, String range) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set(HttpHeaders.RANGE, range);
        return headers;
    }

    private String mediaCookie(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("TCP_MEDIA_ACCESS=").contains("HttpOnly").contains("SameSite=Strict");
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private String cookiePair(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private long cookieMaxAge(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return Long.parseLong(setCookie.replaceAll(".*Max-Age=([0-9]+).*", "$1"));
    }

    private void assertClearsMediaCookie(ResponseEntity<?> response) {
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .isNotNull()
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("TCP_MEDIA_ACCESS=")
                        .contains("Max-Age=0")
                        .contains("Path=/api"));
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
        jdbcTemplate.update("DELETE FROM exemption_material WHERE student_id IN (9001, 9002) OR exemption_request_id IN (SELECT id FROM exemption_request WHERE assessment_year LIKE 'P6%')");
        jdbcTemplate.update("DELETE FROM exemption_request WHERE assessment_year LIKE 'P6%'");
        jdbcTemplate.update("DELETE FROM process_material WHERE assessment_year LIKE 'P6%'");
        jdbcTemplate.update("DELETE FROM file_object WHERE biz_type = 'exemption-material'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
