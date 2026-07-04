package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase5MaterialIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;
    private static final String YEAR = "P5-2026";
    private static final String MORALITY = "morality_teacher_ethics";
    private static final String COURSE = "teacher_education_course";
    private static final String PRACTICE = "education_internship_practice";
    private static final String SKILL = "professional_ability_skill_training";

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
        resetParam("file.maxSize.material", "52428800");
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
    }

    @Test
    void uploadRejectsUnsupportedTypeAndOversize() throws Exception {
        LoginResult student = readyLogin("test_student");
        ResponseEntity<String> docx = upload(student.accessToken(), 9001L, YEAR, MORALITY,
                "bad.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "bad".getBytes());
        assertThat(docx.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(docx).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(docx).at("/msg").asText()).contains("附件类型不支持");

        updateParam("file.maxSize.material", "2");
        ResponseEntity<String> oversize = upload(student.accessToken(), 9001L, YEAR, MORALITY,
                "big.pdf", "application/pdf", "%PDF-1.4".getBytes());
        assertThat(oversize.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(oversize).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(oversize).at("/msg").asText()).contains("附件大小超过限制");
    }

    @Test
    void processQualifiedRequiresAllFourCategoriesPassed() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        long first = uploadOk(student.accessToken(), 9001L, YEAR, MORALITY, "morality.pdf");
        long second = uploadOk(student.accessToken(), 9001L, YEAR, COURSE, "course.pdf");
        long third = uploadOk(student.accessToken(), 9001L, YEAR, PRACTICE, "practice.pdf");
        approve(clerk.accessToken(), auditor.accessToken(), first);
        approve(clerk.accessToken(), auditor.accessToken(), second);
        approve(clerk.accessToken(), auditor.accessToken(), third);
        JsonNode missingOne = processStatus(student.accessToken(), 9001L, YEAR);
        assertThat(missingOne.at("/qualified").asBoolean()).isFalse();

        long failed = uploadOk(student.accessToken(), 9001L, YEAR, SKILL, "skill.pdf");
        submit(student.accessToken(), failed);
        firstReview(clerk.accessToken(), failed, "PASS", "初审通过");
        secondReview(auditor.accessToken(), failed, "FAIL", "不通过");
        JsonNode failedOne = processStatus(student.accessToken(), 9001L, YEAR);
        assertThat(failedOne.at("/qualified").asBoolean()).isFalse();

        String nextYear = "P5-2027";
        long a = uploadOk(student.accessToken(), 9001L, nextYear, MORALITY, "a.pdf");
        long b = uploadOk(student.accessToken(), 9001L, nextYear, COURSE, "b.pdf");
        long c = uploadOk(student.accessToken(), 9001L, nextYear, PRACTICE, "c.pdf");
        long d = uploadOk(student.accessToken(), 9001L, nextYear, SKILL, "d.pdf");
        approve(clerk.accessToken(), auditor.accessToken(), a);
        approve(clerk.accessToken(), auditor.accessToken(), b);
        approve(clerk.accessToken(), auditor.accessToken(), c);
        approve(clerk.accessToken(), auditor.accessToken(), d);
        JsonNode allPassed = processStatus(student.accessToken(), 9001L, nextYear);
        assertThat(allPassed.at("/qualified").asBoolean()).isTrue();
        assertThat(allPassed.at("/categories").size()).isEqualTo(4);
    }

    @Test
    void passedMaterialCannotBeReplacedUntilReturnedAndBatchDownloadHasManifest() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        long materialId = uploadOk(student.accessToken(), 9001L, YEAR, MORALITY, "locked.pdf");
        approve(clerk.accessToken(), auditor.accessToken(), materialId);

        ResponseEntity<String> replacePassed = replace(student.accessToken(), materialId, "new.pdf", "application/pdf", "%PDF-new".getBytes());
        assertThat(replacePassed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(replacePassed).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(replacePassed).at("/msg").asText()).contains("当前状态不可编辑");

        ResponseEntity<byte[]> download = exchangeBytes("/api/material/batch-download", HttpMethod.POST,
                clerk.accessToken(), Map.of("assessmentYear", YEAR));
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).isNotEmpty();
        assertZipContainsManifest(download.getBody());
    }

    @Test
    void materialReadAndWriteScopeAreEnforced() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        LoginResult clerk = readyLogin("test_college_clerk");
        long own = uploadOk(studentA.accessToken(), 9001L, YEAR, MORALITY, "own.pdf");

        ResponseEntity<String> crossStudentWrite = upload(studentA.accessToken(), 9002L, YEAR, MORALITY,
                "cross.pdf", "application/pdf", "%PDF-cross".getBytes());
        assertThat(json(crossStudentWrite).at("/code").asInt()).isEqualTo(403);
        assertThat(materialMapper.selectCount(new LambdaQueryWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getStudentId, 9002L)
                .eq(ProcessMaterial::getAssessmentYear, YEAR))).isZero();

        long b = uploadOk(studentB.accessToken(), 9002L, YEAR, MORALITY, "b.pdf");
        ResponseEntity<String> list = exchange("/api/material?assessmentYear=" + YEAR, HttpMethod.GET, clerk.accessToken(), null);
        JsonNode records = json(list).at("/data/records");
        assertThat(records.toString()).contains(String.valueOf(own));
        assertThat(records.toString()).doesNotContain(String.valueOf(b));

        ResponseEntity<String> studentList = exchange("/api/material?assessmentYear=" + YEAR, HttpMethod.GET, studentA.accessToken(), null);
        JsonNode studentRecords = json(studentList).at("/data/records");
        assertThat(studentRecords.size()).isEqualTo(1);
        assertThat(studentRecords.at("/0/studentId").asLong()).isEqualTo(9001L);
    }

    @Test
    // Phase 43.3 / §7.5 Rule 11：某类别先被判 FAILED（终态、locked），学生为同一类别重传一份新材料并复审通过 →
    // 合格判定取"最新/有效"一份材料（更晚的 PASSED 取代更早的 FAILED）→ 该类别恢复合格。
    // 旧实现按全历史行 passedCount>0 && failedCount==0 计数 → 一条 FAILED 永久钉住不合格、无恢复路径（本用例即证阻断）。
    void failedCategoryRecoversWhenNewerMaterialPasses() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        long failed = uploadOk(student.accessToken(), 9001L, YEAR, MORALITY, "morality-v1.pdf");
        submit(student.accessToken(), failed);
        firstReview(clerk.accessToken(), failed, "PASS", "初审通过");
        secondReview(auditor.accessToken(), failed, "FAIL", "复审不通过");
        JsonNode afterFail = processStatus(student.accessToken(), 9001L, YEAR);
        assertThat(categoryPassed(afterFail, MORALITY)).isFalse();

        long recovered = uploadOk(student.accessToken(), 9001L, YEAR, MORALITY, "morality-v2.pdf");
        submit(student.accessToken(), recovered);
        firstReview(clerk.accessToken(), recovered, "PASS", "初审通过");
        secondReview(auditor.accessToken(), recovered, "PASS", "复审通过");
        JsonNode afterRecover = processStatus(student.accessToken(), 9001L, YEAR);
        JsonNode morality = category(afterRecover, MORALITY);
        assertThat(morality.at("/passed").asBoolean()).isTrue();
        // 历史计数口径不变（信息展示）：仍保留 1 条 FAILED、1 条 PASSED，但合格只看最新一份。
        assertThat(morality.at("/totalCount").asLong()).isEqualTo(2);
        assertThat(morality.at("/passedCount").asLong()).isEqualTo(1);
        assertThat(morality.at("/failedCount").asLong()).isEqualTo(1);
    }

    @Test
    // Phase 43.3 / §7.5 Rule 11 修复正确性边界（负向）：真失败仍不合格。
    // 情形一：类别唯一一份材料 FAILED、无更晚材料 → 不合格。
    // 情形二：先复审通过(PASSED) 再上传一份更晚材料被判 FAILED → "最新一份为 FAILED"取代先前 PASSED → 仍不合格
    //        （证明修复不是"只要有过 PASSED 就放行"，而是严格按最新/有效材料判定）。
    void genuineFailureStaysUnqualifiedIncludingLatestFailOverridingEarlierPass() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        long onlyFailed = uploadOk(student.accessToken(), 9001L, YEAR, COURSE, "course.pdf");
        submit(student.accessToken(), onlyFailed);
        firstReview(clerk.accessToken(), onlyFailed, "PASS", "初审通过");
        secondReview(auditor.accessToken(), onlyFailed, "FAIL", "复审不通过");

        long earlierPass = uploadOk(student.accessToken(), 9001L, YEAR, PRACTICE, "practice-pass.pdf");
        submit(student.accessToken(), earlierPass);
        firstReview(clerk.accessToken(), earlierPass, "PASS", "初审通过");
        secondReview(auditor.accessToken(), earlierPass, "PASS", "复审通过");
        long laterFail = uploadOk(student.accessToken(), 9001L, YEAR, PRACTICE, "practice-fail.pdf");
        submit(student.accessToken(), laterFail);
        firstReview(clerk.accessToken(), laterFail, "PASS", "初审通过");
        secondReview(auditor.accessToken(), laterFail, "FAIL", "复审不通过");

        JsonNode status = processStatus(student.accessToken(), 9001L, YEAR);
        assertThat(categoryPassed(status, COURSE)).isFalse();
        JsonNode practice = category(status, PRACTICE);
        assertThat(practice.at("/passed").asBoolean()).isFalse();
        assertThat(practice.at("/passedCount").asLong()).isEqualTo(1);
        assertThat(practice.at("/failedCount").asLong()).isEqualTo(1);
        assertThat(status.at("/qualified").asBoolean()).isFalse();
    }

    private JsonNode category(JsonNode processStatus, String categoryCode) {
        for (JsonNode node : processStatus.at("/categories")) {
            if (categoryCode.equals(node.at("/category").asText())) {
                return node;
            }
        }
        throw new AssertionError("类别不存在于聚合状态: " + categoryCode);
    }

    private boolean categoryPassed(JsonNode processStatus, String categoryCode) {
        return category(processStatus, categoryCode).at("/passed").asBoolean();
    }

    private void approve(String clerkToken, String auditorToken, long id) throws Exception {
        submit(readyLogin("test_student").accessToken(), id);
        firstReview(clerkToken, id, "PASS", "初审通过");
        secondReview(auditorToken, id, "PASS", "复审通过");
    }

    private void submit(String token, long id) throws Exception {
        ResponseEntity<String> response = exchange("/api/material/" + id + "/submit", HttpMethod.POST, token, Map.of());
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void firstReview(String token, long id, String action, String comment) throws Exception {
        ResponseEntity<String> response = exchange("/api/material/" + id + "/first-review", HttpMethod.POST,
                token, Map.of("action", action, "comment", comment));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void secondReview(String token, long id, String action, String comment) throws Exception {
        ResponseEntity<String> response = exchange("/api/material/" + id + "/second-review", HttpMethod.POST,
                token, Map.of("action", action, "comment", comment));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private JsonNode processStatus(String token, long studentId, String year) throws Exception {
        ResponseEntity<String> response = exchange("/api/material/process-status/" + studentId + "?year=" + year,
                HttpMethod.GET, token, null);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
        return json(response).at("/data");
    }

    private long uploadOk(String token, long studentId, String year, String category, String filename) throws Exception {
        ResponseEntity<String> response = upload(token, studentId, year, category, filename, "application/pdf", "%PDF-1.4".getBytes());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data").asLong();
    }

    private ResponseEntity<String> upload(String token, long studentId, String year, String category,
                                          String filename, String contentType, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("studentId", String.valueOf(studentId));
        body.add("assessmentYear", year);
        body.add("category", category);
        body.add("file", resource(filename, contentType, content));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url("/api/material/upload"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> replace(String token, long materialId, String filename, String contentType, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource(filename, contentType, content));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url("/api/material/" + materialId + "/replace"), HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
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

    private void assertZipContainsManifest(byte[] zipBytes) throws Exception {
        boolean manifest = false;
        boolean materialFile = false;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes), java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("manifest.csv".equals(entry.getName())) {
                    manifest = true;
                }
                if (entry.getName().endsWith(".pdf")) {
                    materialFile = true;
                }
            }
        }
        assertThat(manifest).isTrue();
        assertThat(materialFile).isTrue();
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<byte[]> exchangeBytes(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), byte[].class);
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

    private void updateParam(String key, String value) {
        resetParam(key, value);
    }

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM process_material WHERE assessment_year LIKE 'P5%'");
        jdbcTemplate.update("DELETE FROM file_object WHERE biz_type = 'process-material'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
