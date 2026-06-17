package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.system.entity.Notification;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase12NotificationIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_A_ID = 9001L;
    private static final long STUDENT_B_ID = 9002L;
    private static final long CLERK_ID = 800000000000003003L;
    private static final long REVIEWER_ID = 800000000000003005L;
    private static final String YEAR = "P12-2026";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private VideoReviewMapper videoReviewMapper;

    @Autowired
    private VideoReviewTaskMapper taskMapper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private SysParamMapper paramMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void resetData() {
        cleanupGeneratedData();
        resetSeedStudents();
        resetParam("video.reviewerCount", "2");
        resetUser("test_student", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
        resetUser("test_review_teacher", true);
    }

    @Test
    void materialSubmitNotifiesCollegeClerkAndRejectNotifiesStudent() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        long materialId = seedDraftMaterial(STUDENT_A_ID, COLLEGE_A);

        ResponseEntity<String> submit = exchange("/api/material/" + materialId + "/submit",
                HttpMethod.POST, student.accessToken(), Map.of());
        assertOk(submit);

        JsonNode clerkNotices = notices(clerk.accessToken(), false);
        assertThat(clerkNotices.toString()).contains("待初审").contains("过程性材料");

        ResponseEntity<String> reject = exchange("/api/material/" + materialId + "/first-review",
                HttpMethod.POST, clerk.accessToken(), Map.of("action", "REJECT", "comment", "补充材料"));
        assertOk(reject);

        JsonNode studentNotices = notices(student.accessToken(), false);
        assertThat(studentNotices.toString()).contains("退回").contains("过程性材料");
    }

    @Test
    void videoAssignNotifiesReviewer() throws Exception {
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult reviewer = readyLogin("test_review_teacher");
        long reviewId = seedVideoReview(STUDENT_A_ID, COLLEGE_A);

        ResponseEntity<String> assign = exchange("/api/video/reviews/" + reviewId + "/assign",
                HttpMethod.POST, clerk.accessToken(), Map.of("reviewerIds", List.of(REVIEWER_ID, CLERK_ID)));
        assertOk(assign);

        JsonNode reviewerNotices = notices(reviewer.accessToken(), false);
        assertThat(reviewerNotices.toString()).contains("评审提醒").contains("教学能力视频");
        assertThat(taskMapper.selectCount(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, reviewId))).isEqualTo(2L);
    }

    @Test
    void exportCompletedNotifiesOperatorAndUnreadCountChanges() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        seedCertificateSnapshot();
        assertThat(unreadCount(academic.accessToken())).isZero();

        ResponseEntity<byte[]> exported = download("/api/exchange/export/STANDARD", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", YEAR));
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exported.getBody()).isNotEmpty();

        assertThat(unreadCount(academic.accessToken())).isEqualTo(1);
        JsonNode notices = notices(academic.accessToken(), false);
        assertThat(notices.toString()).contains("导出完成");

        long noticeId = notices.at("/0/id").asLong();
        assertOk(exchange("/api/notice/" + noticeId + "/read", HttpMethod.POST, academic.accessToken(), Map.of()));
        assertThat(unreadCount(academic.accessToken())).isZero();
    }

    @Test
    void noticeCanOnlyBeListedAndReadByOwner() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        long ownedByClerk = seedNotice(clerkUserId(), "待初审测试");
        seedNotice(studentUserId(), "学生本人通知");

        JsonNode clerkNotices = notices(clerk.accessToken(), null);
        assertThat(clerkNotices.toString()).contains("待初审测试");
        assertThat(clerkNotices.toString()).doesNotContain("学生本人通知");

        JsonNode studentNotices = notices(student.accessToken(), null);
        assertThat(studentNotices.toString()).contains("学生本人通知");
        assertThat(studentNotices.toString()).doesNotContain("待初审测试");

        JsonNode forbidden = json(exchange("/api/notice/" + ownedByClerk + "/read",
                HttpMethod.POST, student.accessToken(), Map.of()));
        assertThat(forbidden.at("/code").asInt()).isEqualTo(403);
        assertThat(notificationMapper.selectById(ownedByClerk).getReadFlag()).isZero();

        assertOk(exchange("/api/notice/read-all", HttpMethod.POST, clerk.accessToken(), Map.of()));
        assertThat(notificationMapper.selectById(ownedByClerk).getReadFlag()).isEqualTo(1);
    }

    private long seedDraftMaterial(long studentId, long collegeId) {
        ProcessMaterial material = new ProcessMaterial();
        material.setStudentId(studentId);
        material.setCollegeId(collegeId);
        material.setAssessmentYear(YEAR);
        material.setCategory("morality_teacher_ethics");
        material.setFileId(0L);
        material.setFileName("p12.pdf");
        material.setFilePath("p12.pdf");
        material.setFileSize(8L);
        material.setContentType("application/pdf");
        material.setUploaderId(studentUserId());
        material.setUploadTime(LocalDateTime.now());
        material.setStatus("DRAFT");
        material.setLocked(0);
        materialMapper.insert(material);
        return material.getId();
    }

    private long seedVideoReview(long studentId, long collegeId) {
        VideoReview review = new VideoReview();
        review.setStudentId(studentId);
        review.setCollegeId(collegeId);
        review.setAssessmentYear(YEAR);
        review.setVideoFileId(0L);
        review.setVideoFileName("p12.mp4");
        review.setFileMd5("p12-md5-" + System.nanoTime());
        review.setDurationSeconds(900);
        review.setFormatCheck("PASS");
        review.setValidationMessage("OK");
        review.setStatus("WAIT_REVIEW");
        review.setLocked(0);
        videoReviewMapper.insert(review);
        return review.getId();
    }

    private void seedCertificateSnapshot() {
        Student student = new Student();
        student.setStudentNo("P12CERT");
        student.setName("P12证书学生");
        student.setGender("female");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo("P12345678");
        student.setBirthDate("2000/12/31");
        student.setIdentityType("normal_student");
        student.setCollegeId(COLLEGE_A);
        student.setStatus("PASSED");
        student.setLocked(1);
        studentMapper.insert(student);

        Certificate certificate = new Certificate();
        certificate.setStudentId(student.getId());
        certificate.setCollegeId(COLLEGE_A);
        certificate.setAssessmentYear(YEAR);
        certificate.setCertNo("202610588344300912");
        certificate.setStudentNo(student.getStudentNo());
        certificate.setStudentName(student.getName());
        certificate.setIdCardType(student.getIdCardType());
        certificate.setIdCardNo(student.getIdCardNo());
        certificate.setEducationLevel("bachelor");
        certificate.setTrainingGoal("junior_middle_school_teacher");
        certificate.setTeachingSegment("junior_middle_school");
        certificate.setTeachingSubjectCode("jms_chinese");
        certificate.setTeachingSubjectName("语文");
        certificate.setIssuer("校长");
        certificate.setIssueDate("2026/6/1");
        certificate.setValidUntil("2029/6/30");
        certificate.setStatus("ISSUED");
        certificate.setLocked(1);
        certificateMapper.insert(certificate);
    }

    private long seedNotice(long userId, String title) {
        Notification notice = new Notification();
        notice.setUserId(userId);
        notice.setType("TEST");
        notice.setTitle(title);
        notice.setContent(title);
        notice.setBizType("phase12");
        notice.setBizId(title);
        notice.setReadFlag(0);
        notificationMapper.insert(notice);
        return notice.getId();
    }

    private JsonNode notices(String token, Boolean read) throws Exception {
        String path = read == null ? "/api/notice" : "/api/notice?read=" + read;
        JsonNode root = json(exchange(path, HttpMethod.GET, token, null));
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data/records");
    }

    private long unreadCount(String token) throws Exception {
        JsonNode root = json(exchange("/api/notice/unread-count", HttpMethod.GET, token, null));
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data").asLong();
    }

    private Long studentUserId() {
        return userMapper.selectByUsername("test_student").getId();
    }

    private Long clerkUserId() {
        return userMapper.selectByUsername("test_college_clerk").getId();
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        return rest.exchange(url(path), method, new HttpEntity<>(body, authHeaders(accessToken)), String.class);
    }

    private ResponseEntity<byte[]> download(String path, HttpMethod method, String accessToken, Object body) {
        return rest.exchange(url(path), method, new HttpEntity<>(body, authHeaders(accessToken)), byte[].class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
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

    private void resetSeedStudents() {
        Student studentA = studentMapper.selectById(STUDENT_A_ID);
        if (studentA != null) {
            studentA.setCollegeId(COLLEGE_A);
            studentA.setStatus("DRAFT");
            studentA.setLocked(0);
            studentMapper.updateById(studentA);
        }
        Student studentB = studentMapper.selectById(STUDENT_B_ID);
        if (studentB != null) {
            studentB.setCollegeId(COLLEGE_B);
            studentB.setStatus("DRAFT");
            studentB.setLocked(0);
            studentMapper.updateById(studentB);
        }
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
        jdbcTemplate.update("DELETE FROM notification WHERE biz_type IN ('process_material','video_review','import_export_batch','phase12')");
        jdbcTemplate.update("DELETE FROM video_review_task WHERE video_review_id IN (SELECT id FROM video_review WHERE assessment_year = ?)", YEAR);
        jdbcTemplate.update("DELETE FROM video_review WHERE assessment_year = ?", YEAR);
        jdbcTemplate.update("DELETE FROM process_material WHERE assessment_year = ?", YEAR);
        jdbcTemplate.update("DELETE FROM import_export_batch WHERE scope_json LIKE '%P12-2026%'");
        jdbcTemplate.update("DELETE FROM certificate WHERE student_no LIKE 'P12%'");
        jdbcTemplate.update("DELETE FROM student WHERE student_no LIKE 'P12%'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
