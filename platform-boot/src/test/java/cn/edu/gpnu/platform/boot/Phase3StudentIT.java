package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
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
class Phase3StudentIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;

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
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void resetSeedUsers() {
        cleanupGeneratedStudents();
        ensureSecondCollegeStudent();
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
    }

    @Test
    void idCardBirthNameTextAndLockRules() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        assertCreateFails(academic.accessToken(), student("P3BAD17", "张三", "resident_id_card",
                "11010119900628002", "1990/6/28", COLLEGE_A), "证件类型与号码不匹配");
        assertCreateFails(academic.accessToken(), student("P3BADBIRTH", "张三", "resident_id_card",
                "11010119900628003X", "1990/7/1", COLLEGE_A), "出生日期与证件号码不一致");
        assertCreateFails(academic.accessToken(), student("P3BADHM", "张三", "hm_travel_permit",
                "123456789", "1990/7/1", COLLEGE_A), "证件类型与号码不匹配");
        assertCreateFails(academic.accessToken(), student("P3BADTW", "张三", "tw_travel_permit_5y",
                "123456789", "1990/7/1", COLLEGE_A), "证件类型与号码不匹配");
        assertCreateFails(academic.accessToken(), student("P3BADNAME", "张三3", "resident_id_card",
                "11010119900628004X", "1990/6/28", COLLEGE_A), "姓名格式异常");

        String residentNo = uniqueResidentId("19900628");
        long residentId = create(academic.accessToken(), student(uniqueNo("P3R"), "阿依古丽·买买提",
                "resident_id_card", residentNo, "1990/6/28", COLLEGE_A));
        long hmtResidenceId = create(academic.accessToken(), student(uniqueNo("P3HMT"), "赵六",
                "hmt_residence_permit", uniqueHmtResidenceId("19900628"), "1990/6/28", COLLEGE_A));
        long hmTravelId = create(academic.accessToken(), student(uniqueNo("P3HM"), "李四",
                "hm_travel_permit", uniqueTravelPermit("H"), "1990/7/1", COLLEGE_A));
        long twTravelId = create(academic.accessToken(), student(uniqueNo("P3TW"), "王五",
                "tw_travel_permit_5y", uniqueDigits(8), "1990/7/1", COLLEGE_A));
        long textId = create(academic.accessToken(), student("00" + uniqueNo("P3TXT"), "钱七",
                "hm_travel_permit", uniqueTravelPermit("M"), "2001/01/02", COLLEGE_A));

        assertThat(residentId).isPositive();
        assertThat(hmtResidenceId).isPositive();
        assertThat(hmTravelId).isPositive();
        assertThat(twTravelId).isPositive();

        JsonNode residentDetail = json(exchange("/api/student/" + residentId, HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(residentDetail.at("/idCardNo").asText())
                .isEqualTo(residentNo.substring(0, 6) + "********" + residentNo.substring(14));

        JsonNode textDetail = json(exchange("/api/student/" + textId, HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(textDetail.at("/studentNo").asText()).startsWith("00P3TXT");
        assertThat(textDetail.at("/idCardNo").asText()).isNotEqualTo("M12345678");
        assertThat(textDetail.at("/birthDate").asText()).isEqualTo("2001/01/02");

        ResponseEntity<String> noSensitive = exchange("/api/student/" + residentId + "/id-card?plain=1",
                HttpMethod.GET, readyLogin("test_college_clerk").accessToken(), null);
        assertThat(noSensitive.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        submitAndApprove(academic.accessToken(), residentId);
        Map<String, Object> changed = student(uniqueNo("P3LOCK"), "改名", "resident_id_card",
                "11010119900628006X", "1990/6/28", COLLEGE_A);
        ResponseEntity<String> locked = exchange("/api/student/" + residentId, HttpMethod.PUT, academic.accessToken(), changed);
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(locked).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(locked).at("/msg").asText()).contains("关键字段已锁定");
    }

    @Test
    void dataScopeUsesRealStudentTable() throws Exception {
        LoginResult clerk = readyLogin("test_college_clerk");
        ResponseEntity<String> clerkList = exchange("/api/student", HttpMethod.GET, clerk.accessToken(), null);
        assertThat(clerkList.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode clerkData = json(clerkList).at("/data/records");
        assertThat(clerkData.size()).isGreaterThanOrEqualTo(1);
        assertThat(clerkData.toString()).contains(String.valueOf(COLLEGE_A));
        assertThat(clerkData.toString()).doesNotContain(String.valueOf(COLLEGE_B));

        LoginResult student = readyLogin("test_student");
        ResponseEntity<String> studentList = exchange("/api/student", HttpMethod.GET, student.accessToken(), null);
        assertThat(studentList.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode studentData = json(studentList).at("/data/records");
        assertThat(studentData.size()).isEqualTo(1);
        assertThat(studentData.at("/0/id").asLong()).isEqualTo(9001L);
        assertThat(studentData.toString()).doesNotContain("11010119900628002X");

        ResponseEntity<String> otherDetail = exchange("/api/student/9002", HttpMethod.GET, student.accessToken(), null);
        assertThat(otherDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(otherDetail).at("/code").asInt()).isEqualTo(404);
    }

    @Test
    void collegeClerkCannotWriteStudentOutsideAuthorizedCollege() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult clerk = readyLogin("test_college_clerk");

        String crossCreateNo = uniqueNo("P3CROSSCREATE");
        ResponseEntity<String> crossCreate = exchange("/api/student", HttpMethod.POST, clerk.accessToken(),
                student(crossCreateNo, "跨院新增", "hm_travel_permit", uniqueTravelPermit("C"), "2001/1/2", COLLEGE_B));
        assertThat(crossCreate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(crossCreate).at("/code").asInt()).isEqualTo(403);
        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, crossCreateNo)
                .eq(Student::getCollegeId, COLLEGE_B))).isZero();
        assertThat(userMapper.selectByUsername(crossCreateNo)).isNull();

        long ownStudentId = create(academic.accessToken(), student(uniqueNo("P3CROSSUPDATE"), "跨院修改",
                "hm_travel_permit", uniqueTravelPermit("U"), "2001/1/2", COLLEGE_A));
        String movedNo = uniqueNo("P3MOVED");
        ResponseEntity<String> crossUpdate = exchange("/api/student/" + ownStudentId, HttpMethod.PUT, clerk.accessToken(),
                student(movedNo, "跨院修改", "hm_travel_permit", uniqueTravelPermit("V"), "2001/1/2", COLLEGE_B));
        assertThat(crossUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(crossUpdate).at("/code").asInt()).isEqualTo(403);
        Student after = studentMapper.selectById(ownStudentId);
        assertThat(after.getCollegeId()).isEqualTo(COLLEGE_A);
        assertThat(after.getStudentNo()).isNotEqualTo(movedNo);
        assertThat(userMapper.selectByUsername(movedNo)).isNull();
    }

    @Test
    void createdStudentAccountCanLoginWithInitialPassword() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String studentNo = uniqueNo("P3LOGIN");
        String permitNo = uniqueTravelPermit("L");
        create(academic.accessToken(), student(studentNo, "登录测试", "hm_travel_permit",
                permitNo, "2000/1/2", COLLEGE_A));

        LoginResult created = login(studentNo, permitNo.substring(permitNo.length() - 6));
        assertThat(created.mustChangePwd()).isTrue();
        assertThat(created.permissions()).contains("student:view").contains("student:confirm");
    }

    @Test
    void batchCreateAndDeleteRespectTextAndLockRules() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String firstNo = uniqueNo("P3BATCH");
        String secondNo = "00" + uniqueNo("P3BATCH");
        ResponseEntity<String> batch = exchange("/api/student/batch", HttpMethod.POST, academic.accessToken(), java.util.List.of(
                student(firstNo, "批量一", "hm_travel_permit", uniqueTravelPermit("B"), "2001/1/2", COLLEGE_A),
                student(secondNo, "批量二", "tw_travel_permit_5y", uniqueDigits(8), "2002/01/03", COLLEGE_A)
        ));
        assertThat(batch.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(batch);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        assertThat(root.at("/data").size()).isEqualTo(2);

        long secondId = root.at("/data/1").asLong();
        JsonNode detail = json(exchange("/api/student/" + secondId, HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(detail.at("/studentNo").asText()).isEqualTo(secondNo);
        assertThat(detail.at("/birthDate").asText()).isEqualTo("2002/01/03");

        ResponseEntity<String> deleted = exchange("/api/student/" + secondId, HttpMethod.DELETE, academic.accessToken(), null);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(deleted).at("/code").asInt()).isEqualTo(0);
        ResponseEntity<String> missing = exchange("/api/student/" + secondId, HttpMethod.GET, academic.accessToken(), null);
        assertThat(json(missing).at("/code").asInt()).isEqualTo(404);

        long lockedId = create(academic.accessToken(), student(uniqueNo("P3DELLOCK"), "锁定删除", "resident_id_card",
                uniqueResidentId("19900628"), "1990/6/28", COLLEGE_A));
        submitAndApprove(academic.accessToken(), lockedId);
        ResponseEntity<String> lockedDelete = exchange("/api/student/" + lockedId, HttpMethod.DELETE, academic.accessToken(), null);
        assertThat(lockedDelete.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(lockedDelete).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(lockedDelete).at("/msg").asText()).contains("关键字段已锁定");
    }

    private void submitAndApprove(String academicToken, long studentId) throws Exception {
        exchange("/api/student/" + studentId + "/submit", HttpMethod.POST, academicToken, Map.of());
        LoginResult clerk = readyLogin("test_college_clerk");
        ResponseEntity<String> first = exchange("/api/student/" + studentId + "/first-review", HttpMethod.POST,
                clerk.accessToken(), Map.of("action", "PASS", "comment", "初审通过"));
        assertThat(json(first).at("/code").asInt()).isEqualTo(0);
        LoginResult auditor = readyLogin("test_college_auditor");
        ResponseEntity<String> second = exchange("/api/student/" + studentId + "/second-review", HttpMethod.POST,
                auditor.accessToken(), Map.of("action", "PASS", "comment", "复审通过"));
        assertThat(json(second).at("/code").asInt()).isEqualTo(0);
    }

    private void assertCreateFails(String token, Map<String, Object> body, String message) throws Exception {
        ResponseEntity<String> response = exchange("/api/student", HttpMethod.POST, token, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(1000);
        assertThat(root.at("/msg").asText()).contains(message);
    }

    private long create(String token, Map<String, Object> body) throws Exception {
        ResponseEntity<String> response = exchange("/api/student", HttpMethod.POST, token, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data").asLong();
    }

    private Map<String, Object> student(String studentNo, String name, String idCardType, String idCardNo,
                                        String birthDate, long collegeId) {
        return Map.ofEntries(
                Map.entry("studentNo", studentNo),
                Map.entry("name", name),
                Map.entry("gender", "female"),
                Map.entry("idCardType", idCardType),
                Map.entry("idCardNo", idCardNo),
                Map.entry("birthDate", birthDate),
                Map.entry("identityType", "normal_student"),
                Map.entry("sourceProvince", "440000"),
                Map.entry("sourceCity", "440100"),
                Map.entry("sourceCounty", "440106"),
                Map.entry("sourceFull", "广东省/广州市/天河区"),
                Map.entry("collegeId", collegeId),
                Map.entry("grade", "2022"),
                Map.entry("className", "师范测试班")
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

    private String uniqueResidentId(String yyyymmdd) {
        return "110101" + yyyymmdd + "%03d".formatted(Math.floorMod(System.nanoTime(), 1000)) + "X";
    }

    private String uniqueHmtResidenceId(String yyyymmdd) {
        return "810000" + yyyymmdd + "%04d".formatted(Math.floorMod(System.nanoTime(), 10000));
    }

    private String uniqueTravelPermit(String prefix) {
        return prefix + uniqueDigits(8);
    }

    private String uniqueDigits(int length) {
        long mod = (long) Math.pow(10, length);
        return ("%0" + length + "d").formatted(Math.floorMod(System.nanoTime(), mod));
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

    private void ensureSecondCollegeStudent() {
        Student studentB = studentMapper.selectById(9002L);
        if (studentB != null) {
            studentB.setCollegeId(COLLEGE_B);
            studentB.setIdCardNo("110101199107010019");
            studentMapper.updateById(studentB);
        }
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

    private void cleanupGeneratedStudents() {
        userMapper.delete(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "STUDENT")
                .and(w -> w.likeRight(SysUser::getUsername, "P3")
                        .or()
                        .likeRight(SysUser::getUsername, "00P3")));
        studentMapper.delete(new LambdaQueryWrapper<Student>()
                .likeRight(Student::getStudentNo, "P3")
                .or()
                .likeRight(Student::getStudentNo, "00P3"));
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd, String permissions) {
    }
}
