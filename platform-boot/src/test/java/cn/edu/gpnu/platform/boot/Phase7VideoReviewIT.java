package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadChunk;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadSession;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadChunkMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadSessionMapper;
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

import java.security.MessageDigest;
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
class Phase7VideoReviewIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long REVIEW_TEACHER_ROLE_ID = 800000000000000004L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;
    private static final long REVIEWER_B_USER_ID = 800000000000003010L;
    private static final long REVIEWER_C_USER_ID = 800000000000003011L;
    private static final long REVIEWER_B_ROLE_ID = 800000000000004010L;
    private static final long REVIEWER_C_ROLE_ID = 800000000000004011L;
    private static final String YEAR = "P7-2026";

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
    private VideoReviewMapper reviewMapper;

    @Autowired
    private VideoReviewTaskMapper taskMapper;

    @Autowired
    private VideoUploadSessionMapper sessionMapper;

    @Autowired
    private VideoUploadChunkMapper chunkMapper;

    @Autowired
    private SysParamMapper paramMapper;

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
        ensureReviewer("test_review_teacher_b", REVIEWER_B_USER_ID, REVIEWER_B_ROLE_ID, "评审教师测试账号B");
        ensureReviewer("test_review_teacher_c", REVIEWER_C_USER_ID, REVIEWER_C_ROLE_ID, "评审教师测试账号C");
        resetParam("file.maxSize.video", "2147483648");
        resetParam("video.durationTarget", "900");
        resetParam("video.durationTolerance", "60");
        resetParam("video.passLine", "60");
        resetParam("video.diffThreshold", "12");
        resetParam("video.reviewerCount", "2");
        resetParam("video.arbitrate.mode", "thirdExpert");
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
        resetUser("test_review_teacher", true);
        resetUser("test_review_teacher_b", true);
        resetUser("test_review_teacher_c", true);
    }

    @Test
    void chunkUploadMergeResumeAndInstantHitWorkWithValidation() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] content = mp4("phase7-ok");
        String md5 = md5(content);

        JsonNode init = initUpload(student.accessToken(), 9001L, YEAR, "lesson.mp4", "video/mp4", content.length, 4, md5, 900);
        String uploadId = init.at("/uploadId").asText();
        uploadChunk(student.accessToken(), uploadId, 0, slice(content, 0, 4));
        JsonNode resume = initUpload(student.accessToken(), 9001L, YEAR, "lesson.mp4", "video/mp4", content.length, 4, md5, 900);
        assertThat(resume.at("/uploadedChunks").toString()).contains("0");
        for (int offset = 4, index = 1; offset < content.length; offset += 4, index++) {
            uploadChunk(student.accessToken(), uploadId, index, slice(content, offset, Math.min(4, content.length - offset)));
        }

        JsonNode merged = merge(student.accessToken(), uploadId, 900);
        assertThat(merged.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        assertThat(merged.at("/formatCheck").asText()).isEqualTo("PASS");

        JsonNode instant = initUpload(student.accessToken(), 9001L, "P7-INSTANT", "lesson.mp4", "video/mp4", content.length, 4, md5, 900);
        assertThat(instant.at("/instantHit").asBoolean()).isTrue();
        assertThat(instant.at("/status").asText()).isEqualTo("WAIT_REVIEW");
    }

    @Test
    void nonMp4OrInvalidDurationAreRejectedByServerValidation() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] avi = "RIFF-AVI".getBytes();
        ResponseEntity<String> badType = exchange("/api/video/upload/init", HttpMethod.POST, student.accessToken(),
                initBody(9001L, YEAR, "bad.avi", "video/x-msvideo", avi.length, 4, md5(avi), 900));
        assertThat(json(badType).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(badType).at("/msg").asText()).contains("视频格式必须为MP4");

        byte[] content = mp4("duration");
        JsonNode init = initUpload(student.accessToken(), 9001L, "P7-DURATION", "duration.mp4", "video/mp4",
                content.length, 4, md5(content), 1200);
        String uploadId = init.at("/uploadId").asText();
        uploadAll(student.accessToken(), uploadId, content, 4);
        JsonNode merged = merge(student.accessToken(), uploadId, 1200);
        assertThat(merged.at("/status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(merged.at("/validationMessage").asText()).contains("视频时长超出容差");
    }

    @Test
    void independentReviewHidesOtherScoresBeforeSettlementAndSettlesNormalCase() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, YEAR);
        assign(clerk.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        long taskA = taskId(reviewerA.accessToken());
        long taskB = taskId(reviewerB.accessToken());

        score(reviewerA.accessToken(), taskA, 85, "PASS");
        JsonNode bVisibleReview = json(exchange("/api/video/reviews/" + reviewId, HttpMethod.GET,
                reviewerB.accessToken(), null)).at("/data/tasks");
        assertThat(bVisibleReview.size()).isEqualTo(1);
        assertThat(bVisibleReview.at("/0/id").asLong()).isEqualTo(taskB);
        assertThat(bVisibleReview.toString()).doesNotContain("评分意见-85");
        assertThat(bVisibleReview.toString()).doesNotContain("\"score\":85");

        score(reviewerB.accessToken(), taskB, 80, "PASS");
        VideoReview review = reviewMapper.selectById(reviewId);
        assertThat(review.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(review.getFinalScore()).isEqualTo(83);
        assertThat(review.getFinalConclusion()).isEqualTo("PASS");
    }

    @Test
    void diffOverThresholdOrConclusionConflictRequiresReviewAndThirdExpertPairMinSettles() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-DIFF");
        assign(clerk.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskId(reviewerA.accessToken()), 85, "PASS");
        score(reviewerB.accessToken(), taskId(reviewerB.accessToken()), 60, "PASS");
        assertThat(reviewMapper.selectById(reviewId).getStatus()).isEqualTo("NEED_REVIEW");
        thirdReview(auditor.accessToken(), reviewId, REVIEWER_C_USER_ID, 81, "PASS");
        VideoReview settled = reviewMapper.selectById(reviewId);
        assertThat(settled.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(settled.getFinalScore()).isEqualTo(83);

        long conflictId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-CONFLICT");
        assign(clerk.accessToken(), conflictId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), conflictId), 85, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), conflictId), 58, "FAIL");
        assertThat(reviewMapper.selectById(conflictId).getStatus()).isEqualTo("NEED_REVIEW");
    }

    @Test
    void sysParamChangesAffectThresholdAndReviewerCount() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");
        updateParam("video.diffThreshold", "30");
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-PARAM-DIFF");
        assign(clerk.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), reviewId), 85, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), reviewId), 60, "PASS");
        assertThat(reviewMapper.selectById(reviewId).getStatus()).isEqualTo("REVIEW_COMPLETED");

        updateParam("video.reviewerCount", "3");
        long threeReviewerId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-PARAM-COUNT");
        ResponseEntity<String> twoReviewers = exchange("/api/video/reviews/" + threeReviewerId + "/assign",
                HttpMethod.POST, clerk.accessToken(), Map.of("reviewerIds", List.of(800000000000003005L, REVIEWER_B_USER_ID)));
        assertThat(json(twoReviewers).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(twoReviewers).at("/msg").asText()).contains("video.reviewerCount");
    }

    @Test
    void readWriteScopeReviewerScopeAndPlaybackAuthAreEnforced() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult reviewerA = readyLogin("test_review_teacher");

        ResponseEntity<String> crossStudent = exchange("/api/video/upload/init", HttpMethod.POST, studentA.accessToken(),
                initBody(9002L, YEAR, "cross.mp4", "video/mp4", 8, 4, md5(mp4("cross")), 900));
        assertThat(json(crossStudent).at("/code").asInt()).isEqualTo(403);

        long own = uploadValidatedVideo(studentA.accessToken(), 9001L, "P7-SCOPE-A");
        long other = uploadValidatedVideo(studentB.accessToken(), 9002L, "P7-SCOPE-B");
        JsonNode clerkList = json(exchange("/api/video/reviews?assessmentYear=P7-SCOPE-A",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data/records");
        assertThat(clerkList.toString()).contains(String.valueOf(own));
        assertThat(clerkList.toString()).doesNotContain(String.valueOf(other));

        JsonNode studentList = json(exchange("/api/video/reviews?assessmentYear=P7-SCOPE-A",
                HttpMethod.GET, studentA.accessToken(), null)).at("/data/records");
        assertThat(studentList.size()).isEqualTo(1);
        assertThat(studentList.at("/0/studentId").asLong()).isEqualTo(9001L);

        assign(clerk.accessToken(), own, 800000000000003005L, REVIEWER_B_USER_ID);
        JsonNode reviewerTasks = json(exchange("/api/video/tasks/my", HttpMethod.GET, reviewerA.accessToken(), null)).at("/data/records");
        assertThat(reviewerTasks.toString()).contains(String.valueOf(own));

        ResponseEntity<String> noToken = rest.getForEntity(url("/api/video/reviews/" + own + "/play"), String.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode play = json(exchange("/api/video/reviews/" + own + "/play", HttpMethod.GET, reviewerA.accessToken(), null)).at("/data");
        assertThat(play.at("/url").asText()).contains("X-Amz-");
        assertThat(play.at("/watermarkText").asText()).contains("test_review_teacher");
    }

    private long uploadValidatedVideo(String token, long studentId, String year) throws Exception {
        byte[] content = mp4(year);
        String hash = md5(content);
        JsonNode init = initUpload(token, studentId, year, "lesson-" + year + ".mp4", "video/mp4", content.length, 4, hash, 900);
        String uploadId = init.at("/uploadId").asText();
        uploadAll(token, uploadId, content, 4);
        JsonNode merged = merge(token, uploadId, 900);
        assertThat(merged.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        return merged.at("/id").asLong();
    }

    private JsonNode initUpload(String token, long studentId, String year, String fileName, String contentType,
                                long size, long chunkSize, String md5, int duration) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/upload/init", HttpMethod.POST, token,
                initBody(studentId, year, fileName, contentType, size, chunkSize, md5, duration));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data");
    }

    private Map<String, Object> initBody(long studentId, String year, String fileName, String contentType,
                                         long size, long chunkSize, String md5, int duration) {
        return Map.of(
                "studentId", studentId,
                "assessmentYear", year,
                "fileMd5", md5,
                "fileName", fileName,
                "contentType", contentType,
                "size", size,
                "chunkSize", chunkSize,
                "durationSeconds", duration
        );
    }

    private void uploadAll(String token, String uploadId, byte[] content, int chunkSize) throws Exception {
        int index = 0;
        for (int offset = 0; offset < content.length; offset += chunkSize) {
            int length = Math.min(chunkSize, content.length - offset);
            uploadChunk(token, uploadId, index++, slice(content, offset, length));
        }
    }

    private void uploadChunk(String token, String uploadId, int index, byte[] content) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("uploadId", uploadId);
        body.add("index", String.valueOf(index));
        body.add("md5", md5(content));
        body.add("file", resource("chunk-" + index, "application/octet-stream", content));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.exchange(url("/api/video/upload/chunk"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private JsonNode merge(String token, String uploadId, int duration) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/upload/merge", HttpMethod.POST, token,
                Map.of("uploadId", uploadId, "durationSeconds", duration));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data");
    }

    private void assign(String token, long reviewId, Long... reviewerIds) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/reviews/" + reviewId + "/assign", HttpMethod.POST,
                token, Map.of("reviewerIds", List.of(reviewerIds)));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void score(String token, long taskId, int score, String conclusion) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/tasks/" + taskId + "/score", HttpMethod.POST,
                token, scoreBody(score, conclusion));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void thirdReview(String token, long reviewId, long reviewerId, int score, String conclusion) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(scoreBody(score, conclusion));
        body.put("reviewerId", reviewerId);
        ResponseEntity<String> response = exchange("/api/video/reviews/" + reviewId + "/third-review", HttpMethod.POST,
                token, body);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
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
                "comment", "评分意见-" + score,
                "conclusion", conclusion
        );
    }

    private long taskId(String token) throws Exception {
        JsonNode tasks = json(exchange("/api/video/tasks/my", HttpMethod.GET, token, null)).at("/data/records");
        assertThat(tasks.size()).isGreaterThan(0);
        return tasks.at("/0/id").asLong();
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

    private void ensureReviewer(String username, long userId, long userRoleId, String realName) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            user = new SysUser();
            user.setId(userId);
            user.setUsername(username);
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setRealName(realName);
        user.setWorkNo(username.toUpperCase());
        user.setStatus("ENABLED");
        user.setUserType("STAFF");
        user.setCollegeId(COLLEGE_A);
        user.setStudentId(null);
        user.setMustChangePwd(1);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(null);
        if (userMapper.selectByUsername(username) == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        userRoleMapper.upsert(userRoleId, user.getId(), REVIEW_TEACHER_ROLE_ID, 0L);
    }

    private void resetParam(String key, String value) {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>().eq(SysParam::getParamKey, key).last("LIMIT 1"));
        if (param == null) {
            return;
        }
        param.setParamValue(value);
        paramMapper.updateById(param);
    }

    private void updateParam(String key, String value) {
        resetParam(key, value);
    }

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM video_review_task WHERE video_review_id IN (SELECT id FROM video_review WHERE assessment_year LIKE 'P7%')");
        jdbcTemplate.update("DELETE FROM video_review WHERE assessment_year LIKE 'P7%'");
        jdbcTemplate.update("DELETE FROM video_upload_chunk WHERE upload_id IN (SELECT upload_id FROM video_upload_session WHERE assessment_year LIKE 'P7%')");
        jdbcTemplate.update("DELETE FROM video_upload_session WHERE assessment_year LIKE 'P7%'");
        jdbcTemplate.update("DELETE FROM file_object WHERE biz_type = 'teaching-video'");
    }

    private byte[] mp4(String text) {
        return ("....ftypmp42" + text + "-mdat").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] slice(byte[] input, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(input, offset, out, 0, length);
        return out;
    }

    private String md5(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
