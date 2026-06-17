package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase13SystemAuditIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long REVIEW_TEACHER_ROLE_ID = 800000000000000004L;
    private static final long REVIEWER_B_USER_ID = 800000000000003010L;
    private static final long REVIEWER_B_ROLE_ID = 800000000000004010L;
    private static final long AUDITOR_ID = 800000000000003004L;
    private static final String YEAR = "P13-2026";

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
    private SysParamMapper paramMapper;

    @Autowired
    private SysAuditLogMapper auditLogMapper;

    @Autowired
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private VideoReviewMapper reviewMapper;

    @Autowired
    private VideoReviewTaskMapper taskMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void resetData() {
        cleanupGeneratedData();
        resetParam("video.diffThreshold", "12");
        resetParam("video.reviewerCount", "2");
        resetParam("video.passLine", "60");
        resetUser("test_student", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
        resetUser("test_review_teacher", true);
        resetUser("test_review_teacher_b", true);
        ensureReviewerB();
    }

    @Test
    void secondReviewRejectWritesRichAuditLogWithOldNewCommentOperatorAndIp() throws Exception {
        LoginResult auditor = readyLogin("test_college_auditor");
        assertThat(auditLogMapper.selectOne(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "auth")
                .eq(SysAuditLog::getBizId, AUDITOR_ID)
                .eq(SysAuditLog::getOperatorId, AUDITOR_ID)
                .eq(SysAuditLog::getOperation, "login")
                .eq(SysAuditLog::getNewStatus, "SUCCESS")
                .last("LIMIT 1"))).isNotNull();
        long materialId = seedMaterial(COLLEGE_A, "SECOND_REVIEW", "p13-rich-audit.pdf");

        ResponseEntity<String> rejected = exchange("/api/material/" + materialId + "/second-review",
                HttpMethod.POST, auditor.accessToken(), Map.of("action", "REJECT", "comment", "复审退回原因"));
        assertOk(rejected);

        SysAuditLog log = auditLogMapper.selectOne(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "material")
                .eq(SysAuditLog::getBizId, materialId)
                .eq(SysAuditLog::getOperation, "secondReview")
                .eq(SysAuditLog::getOldStatus, "SECOND_REVIEW")
                .eq(SysAuditLog::getNewStatus, "SECOND_REJECTED")
                .last("LIMIT 1"));
        assertThat(log).isNotNull();
        assertThat(log.getComment()).contains("复审退回原因");
        assertThat(log.getOperatorId()).isEqualTo(AUDITOR_ID);
        assertThat(log.getIp()).isNotBlank();
        assertThat(log.getTarget()).contains(String.valueOf(materialId)).contains(YEAR);

        JsonNode auditList = json(exchange("/api/audit/log?bizType=material&studentId=9001&keyword=复审退回原因",
                HttpMethod.GET, auditor.accessToken(), null)).at("/data/records");
        assertThat(auditList.toString()).contains("SECOND_REVIEW").contains("SECOND_REJECTED").contains("复审退回原因");
    }

    @Test
    void normalAdminCannotDeleteAuditLog() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        SysAuditLog log = seedAudit("material", 9001L, "P13删除反例", COLLEGE_A);

        ResponseEntity<String> deleted = exchange("/api/audit/log/" + log.getId(), HttpMethod.DELETE,
                academic.accessToken(), null);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(deleted).at("/code").asInt()).isEqualTo(403);
        assertThat(auditLogMapper.selectById(log.getId())).isNotNull();
        assertThat(auditLogMapper.selectOne(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "audit")
                .eq(SysAuditLog::getBizId, log.getId())
                .eq(SysAuditLog::getOperation, "deleteRejected")
                .eq(SysAuditLog::getNewStatus, "REJECTED")
                .last("LIMIT 1"))).isNotNull();
    }

    @Test
    void systemParamUpdateTakesEffectImmediatelyForVideoSettlement() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        SysParam threshold = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, "video.diffThreshold")
                .last("LIMIT 1"));
        assertThat(threshold).isNotNull();
        assertOk(exchange("/api/system/param/" + threshold.getId(), HttpMethod.PUT,
                academic.accessToken(), Map.of("paramValue", "8", "description", threshold.getDescription())));

        long reviewId = seedReviewingVideo();
        List<VideoReviewTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, reviewId)
                .orderByAsc(VideoReviewTask::getReviewerId));
        score(reviewerA.accessToken(), taskIdFor(tasks, 800000000000003005L), 70, "PASS");
        score(reviewerB.accessToken(), taskIdFor(tasks, REVIEWER_B_USER_ID), 80, "PASS");

        VideoReview settled = reviewMapper.selectById(reviewId);
        assertThat(settled.getStatus()).isEqualTo("NEED_REVIEW");
        assertThat(settled.getFinalScore()).isNull();
    }

    @Test
    void plaintextIdCardRequiresSensitiveExportPermission() throws Exception {
        LoginResult clerk = readyLogin("test_college_clerk");

        ResponseEntity<String> response = exchange("/api/student/9001/id-card?plain=1",
                HttpMethod.GET, clerk.accessToken(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void auditQueryRespectsCollegeScopeAndSupportsBusinessRecordCollege() throws Exception {
        LoginResult clerk = readyLogin("test_college_clerk");
        long ownMaterial = seedMaterial(COLLEGE_A, "DRAFT", "p13-own.pdf");
        long otherMaterial = seedMaterial(COLLEGE_B, "DRAFT", "p13-other.pdf");
        seedAudit("material", ownMaterial, "P13学院A审计", null);
        seedAudit("material", otherMaterial, "P13学院B审计", COLLEGE_B);

        JsonNode records = json(exchange("/api/audit/log?bizType=material&keyword=P13学院",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data/records");

        assertThat(records.toString()).contains("P13学院A审计");
        assertThat(records.toString()).doesNotContain("P13学院B审计");
    }

    private long seedMaterial(long collegeId, String status, String fileName) {
        ProcessMaterial material = new ProcessMaterial();
        material.setStudentId(collegeId == COLLEGE_A ? 9001L : 9002L);
        material.setCollegeId(collegeId);
        material.setAssessmentYear(YEAR);
        material.setCategory("morality_teacher_ethics");
        material.setFileId(0L);
        material.setFileName(fileName);
        material.setFilePath(fileName);
        material.setFileSize(8L);
        material.setContentType("application/pdf");
        material.setUploaderId(userMapper.selectByUsername("test_student").getId());
        material.setUploadTime(LocalDateTime.now());
        material.setStatus(status);
        material.setLocked(0);
        materialMapper.insert(material);
        return material.getId();
    }

    private long seedReviewingVideo() {
        VideoReview review = new VideoReview();
        review.setStudentId(9001L);
        review.setCollegeId(COLLEGE_A);
        review.setAssessmentYear(YEAR);
        review.setVideoFileId(0L);
        review.setVideoFileName("p13.mp4");
        review.setFileMd5("p13-md5-" + System.nanoTime());
        review.setDurationSeconds(900);
        review.setFormatCheck("PASS");
        review.setValidationMessage("OK");
        review.setStatus("REVIEWING");
        review.setLocked(0);
        reviewMapper.insert(review);
        insertTask(review, 800000000000003005L);
        insertTask(review, REVIEWER_B_USER_ID);
        return review.getId();
    }

    private void insertTask(VideoReview review, long reviewerId) {
        VideoReviewTask task = new VideoReviewTask();
        task.setVideoReviewId(review.getId());
        task.setStudentId(review.getStudentId());
        task.setCollegeId(review.getCollegeId());
        task.setReviewerId(reviewerId);
        task.setReviewerRole("REVIEWER");
        task.setSubmitted(0);
        taskMapper.insert(task);
    }

    private long taskIdFor(List<VideoReviewTask> tasks, long reviewerId) {
        return tasks.stream()
                .filter(task -> task.getReviewerId().equals(reviewerId))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private void score(String token, long taskId, int score, String conclusion) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/tasks/" + taskId + "/score", HttpMethod.POST,
                token, scoreBody(score, conclusion));
        assertOk(response);
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
        return Map.of(
                "score", score,
                "dimensionScores", dimensions,
                "comment", "P13评分-" + score,
                "conclusion", conclusion
        );
    }

    private SysAuditLog seedAudit(String bizType, long bizId, String comment, Long operatorCollegeId) {
        SysAuditLog log = new SysAuditLog();
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setTarget(comment);
        log.setOperatorId(operatorCollegeId == null || operatorCollegeId == COLLEGE_A
                ? 800000000000003003L
                : 0L);
        log.setOperateTime(LocalDateTime.now());
        log.setComment(comment);
        log.setOperation("P13_TEST");
        log.setIp("127.0.0.1");
        auditLogMapper.insert(log);
        return log;
    }

    private void ensureReviewerB() {
        SysUser user = userMapper.selectByUsername("test_review_teacher_b");
        if (user == null) {
            user = new SysUser();
            user.setId(REVIEWER_B_USER_ID);
            user.setUsername("test_review_teacher_b");
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
        userRoleMapper.upsert(REVIEWER_B_ROLE_ID, user.getId(), REVIEW_TEACHER_ROLE_ID, 0L);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.add("X-Forwarded-For", "127.0.0.1");
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertOk(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
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

    private void resetParam(String key, String value) {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, key)
                .last("LIMIT 1"));
        if (param != null) {
            param.setParamValue(value);
            paramMapper.updateById(param);
        }
    }

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE target LIKE 'P13%' OR comment LIKE 'P13%' OR comment = '复审退回原因'");
        jdbcTemplate.update("DELETE FROM video_review_task WHERE video_review_id IN (SELECT id FROM video_review WHERE assessment_year = ?)", YEAR);
        jdbcTemplate.update("DELETE FROM video_review WHERE assessment_year = ?", YEAR);
        jdbcTemplate.update("DELETE FROM process_material WHERE assessment_year = ?", YEAR);
        jdbcTemplate.update("DELETE FROM backup_record WHERE remark LIKE 'P13%' OR scope LIKE 'P13%'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
