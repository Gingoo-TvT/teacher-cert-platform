package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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

    @Autowired
    private SysParamMapper paramMapper;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    @AfterEach
    void resetSeedUsers() {
        setStudentAccountParams(false, "random");
        cleanupGeneratedStudents();
        ensureSecondCollegeStudent();
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
        resetUser("test_sys_admin", true);
    }

    @Test
    void idCardBirthNameTextAndLockRules() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        assertCreateFails(academic.accessToken(), student("P3BAD17", "张三", "resident_id_card",
                "11010119900628002", "1990/6/28", COLLEGE_A), "证件类型与号码不匹配");
        assertCreateFails(academic.accessToken(), student("P3BADBIRTH", "张三", "resident_id_card",
                "11010119900628003X", "1990/7/1", COLLEGE_A), "出生日期与证件号码不一致");
        // Phase43.4: BirthDateValidator 严格校验——2023-02-30 这类日历上不存在的日期必须被拒绝（此前只检查月1-12/日1-31会误放行）。
        assertCreateFails(academic.accessToken(), student("P3BADCALENDAR", "张三", "resident_id_card",
                "11010119900628005X", "2023/02/30", COLLEGE_A), "出生日期格式异常");
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
        // Phase 44e-contract（真分页契约）：SELF 范围 total 亦经数据权限过滤 → 恰 1（本人），非全表条数。
        assertThat(json(studentList).at("/data/total").asLong()).isEqualTo(1);

        ResponseEntity<String> otherDetail = exchange("/api/student/9002", HttpMethod.GET, student.accessToken(), null);
        assertThat(otherDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(otherDetail).at("/code").asInt()).isEqualTo(404);
    }

    /**
     * Phase 44e-contract（P1-1 真分页样例 · 数据范围 × 分页组合的正确性证明）：
     * 学院文员（学院A）对含跨学院同前缀数据的学生列表做真分页——
     * ① total 为「已按学院范围过滤」的总数（3，学院B 那条不计入，证明分页 count SQL 也走了数据权限拦截器）；
     * ② 每页条数=请求 size；③ 各页均无学院B 数据；④ 页间记录不重叠（真 LIMIT/OFFSET，非全表包壳）。
     */
    @Test
    void paginatedStudentListIsScopedAndPagedForCollegeUser() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String prefix = "P3PAGE" + System.nanoTime();
        create(academic.accessToken(), student(prefix + "A1", "分页甲", "hm_travel_permit",
                uniqueTravelPermit("H"), "2001/1/2", COLLEGE_A));
        create(academic.accessToken(), student(prefix + "A2", "分页乙", "hm_travel_permit",
                uniqueTravelPermit("J"), "2001/1/2", COLLEGE_A));
        create(academic.accessToken(), student(prefix + "A3", "分页丙", "hm_travel_permit",
                uniqueTravelPermit("K"), "2001/1/2", COLLEGE_A));
        // 学院B 同前缀 1 条：关键词能命中，但学院文员的数据范围应把它排除在 total 与 records 之外。
        create(academic.accessToken(), student(prefix + "B1", "分页乙院", "hm_travel_permit",
                uniqueTravelPermit("L"), "2001/1/2", COLLEGE_B));

        LoginResult clerk = readyLogin("test_college_clerk");

        JsonNode page1 = json(exchange("/api/student?keyword=" + prefix + "&page=1&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page1.at("/total").asLong()).isEqualTo(3);
        assertThat(page1.at("/records").size()).isEqualTo(2);
        assertThat(page1.at("/records").toString()).contains(String.valueOf(COLLEGE_A));
        assertThat(page1.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));

        JsonNode page2 = json(exchange("/api/student?keyword=" + prefix + "&page=2&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page2.at("/total").asLong()).isEqualTo(3);
        assertThat(page2.at("/records").size()).isEqualTo(1);
        assertThat(page2.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));

        assertThat(page1.at("/records/0/id").asLong())
                .isNotEqualTo(page2.at("/records/0/id").asLong());
    }

    @Test
    void studentConfirmCannotMoveOwnCollege() throws Exception {
        LoginResult student = readyLogin("test_student");
        Student before = studentMapper.selectById(9001L);
        assertThat(before.getCollegeId()).isEqualTo(COLLEGE_A);

        Map<String, Object> changedCollege = student(before.getStudentNo(), before.getName(), before.getIdCardType(),
                before.getIdCardNo(), before.getBirthDate(), COLLEGE_B);
        ResponseEntity<String> response = exchange("/api/student/confirm", HttpMethod.POST, student.accessToken(), changedCollege);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
        assertThat(json(response).at("/data/collegeId").asLong()).isEqualTo(COLLEGE_A);

        Student after = studentMapper.selectById(9001L);
        assertThat(after.getCollegeId()).isEqualTo(COLLEGE_A);
    }

    @Test
    void studentInReviewCannotBeEditedOrConfirmed() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        long studentId = create(academic.accessToken(), student(uniqueNo("P3REVIEW"), "在审学生",
                "hm_travel_permit", uniqueTravelPermit("R"), "2001/1/2", COLLEGE_A));
        ResponseEntity<String> submit = exchange("/api/student/" + studentId + "/submit", HttpMethod.POST,
                academic.accessToken(), Map.of());
        assertThat(json(submit).at("/code").asInt()).isEqualTo(0);

        ResponseEntity<String> update = exchange("/api/student/" + studentId, HttpMethod.PUT, academic.accessToken(),
                student(uniqueNo("P3REVIEWNEW"), "在审改名", "hm_travel_permit", uniqueTravelPermit("E"), "2001/1/2", COLLEGE_A));
        assertThat(json(update).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(update).at("/msg").asText()).contains("当前状态不可编辑");
    }

    @Test
    void collegeAuditorCannotWriteStudentOutsideAuthorizedCollege() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult auditor = readyLogin("test_college_auditor");

        String crossCreateNo = uniqueNo("P3CROSSCREATE");
        ResponseEntity<String> crossCreate = exchange("/api/student", HttpMethod.POST, auditor.accessToken(),
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
        ResponseEntity<String> crossUpdate = exchange("/api/student/" + ownStudentId, HttpMethod.PUT, auditor.accessToken(),
                student(movedNo, "跨院修改", "hm_travel_permit", uniqueTravelPermit("V"), "2001/1/2", COLLEGE_B));
        assertThat(crossUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(crossUpdate).at("/code").asInt()).isEqualTo(403);
        Student after = studentMapper.selectById(ownStudentId);
        assertThat(after.getCollegeId()).isEqualTo(COLLEGE_A);
        assertThat(after.getStudentNo()).isNotEqualTo(movedNo);
        assertThat(userMapper.selectByUsername(movedNo)).isNull();
    }

    @Test
    void importedStudentHasNoIdCardDerivedLogin() throws Exception {
        // WS-2（审计#4）：学生自动开户默认关闭（V27 把 student.autoCreateAccount 出厂默认翻为 false）+ 初始口令
        // 不再由证件号派生（student.defaultPwd 默认 random）→ 导入学生拿不到「证件号后六位」这类可猜口令。
        // 本用例把原「创建即可用证件号后六位登录」改写为其安全反面（复现→阻断），不弱化被测语义。
        LoginResult academic = readyLogin("test_academic_admin");
        String studentNo = uniqueNo("P3LOGIN");
        String permitNo = uniqueTravelPermit("L");
        create(academic.accessToken(), student(studentNo, "登录测试", "hm_travel_permit",
                permitNo, "2000/1/2", COLLEGE_A));

        // 默认不再自动开通登录账号
        assertThat(userMapper.selectByUsername(studentNo)).isNull();
        // 证件号后六位登录被拒（账号不存在 → 凭据无效，业务码非 0）
        ResponseEntity<String> attempt = loginRaw(studentNo, permitNo.substring(permitNo.length() - 6));
        assertThat(json(attempt).at("/code").asInt()).isNotEqualTo(0);

        // studentNo 与既有 STAFF 用户名冲突时必须整体回滚，不能把 STAFF 劫持为学生账号。
        assertCreateFails(academic.accessToken(), student("test_college_clerk", "账号冲突", "hm_travel_permit",
                uniqueTravelPermit("S"), "2000/1/2", COLLEGE_A), "学号已被其他账号使用");
        assertThat(userMapper.selectByUsername("test_college_clerk").getUserType()).isEqualTo("STAFF");

        // 历史遗留的未绑定 STUDENT 也不能只凭同名学号被新学生接管，尤其不能跨学院改写归属。
        String orphanStudentNo = uniqueNo("P3ORPHAN");
        SysUser orphan = new SysUser();
        orphan.setUsername(orphanStudentNo);
        orphan.setPasswordHash(passwordEncoder.encode("Orphan-Student-2026!"));
        orphan.setRealName("历史孤儿学生账号");
        orphan.setStatus("DISABLED");
        orphan.setUserType("STUDENT");
        orphan.setCollegeId(COLLEGE_B);
        orphan.setStudentId(null);
        orphan.setMustChangePwd(1);
        orphan.setFailedLoginCount(0);
        userMapper.insert(orphan);
        String orphanHash = orphan.getPasswordHash();

        assertCreateFails(academic.accessToken(), student(orphanStudentNo, "跨院接管反例", "hm_travel_permit",
                uniqueTravelPermit("O"), "2000/1/2", COLLEGE_A), "学号已被其他账号使用");
        SysUser unchangedOrphan = userMapper.selectById(orphan.getId());
        assertThat(unchangedOrphan.getStudentId()).isNull();
        assertThat(unchangedOrphan.getCollegeId()).isEqualTo(COLLEGE_B);
        assertThat(unchangedOrphan.getPasswordHash()).isEqualTo(orphanHash);
        assertThat(studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, orphanStudentNo))).isNull();

        // 显式开启但使用 random 时只创建停用账号；随机明文不外发，须管理员受控重置后才会启用。
        LoginResult sysAdmin = readyLogin("test_sys_admin");
        setStudentAccountParams(true, "random");
        String deniedStudentNo = uniqueNo("P3CEILING");
        ResponseEntity<String> deniedAutoAccount = exchange("/api/student", HttpMethod.POST,
                academic.accessToken(), student(deniedStudentNo, "委派拦截", "hm_travel_permit",
                        uniqueTravelPermit("G"), "2000/1/2", COLLEGE_A));
        assertThat(json(deniedAutoAccount).at("/code").asInt()).isEqualTo(403);
        assertThat(userMapper.selectByUsername(deniedStudentNo)).isNull();
        assertThat(studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, deniedStudentNo))).isNull();

        String randomStudentNo = uniqueNo("P3RANDOMPWD");
        String randomPermitNo = uniqueTravelPermit("R");
        create(sysAdmin.accessToken(), student(randomStudentNo, "随机口令", "hm_travel_permit",
                randomPermitNo, "2000/1/2", COLLEGE_A));
        SysUser randomAccount = userMapper.selectByUsername(randomStudentNo);
        assertThat(randomAccount).isNotNull();
        assertThat(randomAccount.getStatus()).isEqualTo("DISABLED");
        assertThat(randomAccount.getMustChangePwd()).isEqualTo(1);
        assertThat(passwordEncoder.matches(
                randomPermitNo.substring(randomPermitNo.length() - 6), randomAccount.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches(INITIAL_PASSWORD, randomAccount.getPasswordHash())).isFalse();

        // 只有显式配置满足强度要求的受控口令，自动开户账号才会启用并可完成首次登录。
        String controlledPassword = "Student-Init-2026!";
        setStudentAccountParams(true, controlledPassword);
        String controlledStudentNo = uniqueNo("P3CONTROLLEDPWD");
        String controlledPermitNo = uniqueTravelPermit("C");
        long controlledStudentId = create(sysAdmin.accessToken(), student(controlledStudentNo, "受控口令", "hm_travel_permit",
                controlledPermitNo, "2000/1/2", COLLEGE_A));
        SysUser controlledAccount = userMapper.selectByUsername(controlledStudentNo);
        assertThat(controlledAccount).isNotNull();
        assertThat(controlledAccount.getStatus()).isEqualTo("ENABLED");
        assertThat(passwordEncoder.matches(controlledPassword, controlledAccount.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(
                controlledPermitNo.substring(controlledPermitNo.length() - 6), controlledAccount.getPasswordHash())).isFalse();
        LoginResult controlledLogin = login(controlledStudentNo, controlledPassword);
        assertThat(controlledLogin.mustChangePwd()).isTrue();
        assertThat(controlledLogin.permissions()).contains("student:view").contains("student:confirm");

        // 关闭自动开户后修改学号，既有账号仍须按 student_id 跟随改名，不得留下旧用户名。
        setStudentAccountParams(false, "random");
        String renamedStudentNo = uniqueNo("P3RENAMEDPWD");
        ResponseEntity<String> renamed = exchange("/api/student/" + controlledStudentId, HttpMethod.PUT,
                academic.accessToken(), student(renamedStudentNo, "受控口令", "hm_travel_permit",
                        controlledPermitNo, "2000/1/2", COLLEGE_A));
        assertThat(json(renamed).at("/code").asInt()).isEqualTo(0);
        assertThat(userMapper.selectByUsername(controlledStudentNo)).isNull();
        SysUser renamedAccount = userMapper.selectByUsername(renamedStudentNo);
        assertThat(renamedAccount).isNotNull();
        assertThat(renamedAccount.getId()).isEqualTo(controlledAccount.getId());
        assertThat(renamedAccount.getStudentId()).isEqualTo(controlledStudentId);
        assertThat(passwordEncoder.matches(controlledPassword, renamedAccount.getPasswordHash())).isTrue();
        assertThat(login(renamedStudentNo, controlledPassword).mustChangePwd()).isTrue();

        // 显式受控口令仍必须达到强度要求，弱值不得创建可登录账号。
        setStudentAccountParams(true, "weak");
        assertCreateFails(sysAdmin.accessToken(), student(uniqueNo("P3WEAKPWD"), "弱口令", "hm_travel_permit",
                uniqueTravelPermit("W"), "2000/1/2", COLLEGE_A),
                "student.defaultPwd 必须为 12-64 位并包含大小写字母、数字和特殊字符");
        setStudentAccountParams(true, INITIAL_PASSWORD);
        assertCreateFails(sysAdmin.accessToken(), student(uniqueNo("P3PUBLICPWD"), "公开口令", "hm_travel_permit",
                uniqueTravelPermit("D"), "2000/1/2", COLLEGE_A),
                "student.defaultPwd 不得使用公开 dev/示例口令");
    }

    @Test
    void deletingABoundStudentAccountRespectsTheAuthorizationCeiling() throws Exception {
        LoginResult sysAdmin = readyLogin("test_sys_admin");
        LoginResult academic = readyLogin("test_academic_admin");
        setStudentAccountParams(true, "Student-Delete-2026!");

        String studentNo = uniqueNo("P3DELETECEILING");
        long studentId = create(sysAdmin.accessToken(), student(
                studentNo, "删除天花板", "hm_travel_permit", uniqueTravelPermit("Z"),
                "2000/1/2", COLLEGE_A));
        SysUser account = userMapper.selectByUsername(studentNo);
        assertThat(account).isNotNull();
        assertThat(account.getStatus()).isEqualTo("ENABLED");

        ResponseEntity<String> denied = exchange(
                "/api/student/" + studentId, HttpMethod.DELETE, academic.accessToken(), null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(denied).at("/code").asInt()).isEqualTo(403);
        assertThat(studentMapper.selectById(studentId)).isNotNull();
        assertThat(userMapper.selectById(account.getId()).getStatus()).isEqualTo("ENABLED");

        ResponseEntity<String> deleted = exchange(
                "/api/student/" + studentId, HttpMethod.DELETE, sysAdmin.accessToken(), null);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(deleted).at("/code").asInt()).isEqualTo(0);
        assertThat(studentMapper.selectById(studentId)).isNull();
        assertThat(userMapper.selectById(account.getId()).getStatus()).isEqualTo("DISABLED");
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
        userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, "STUDENT")
                        .and(w -> w.likeRight(SysUser::getUsername, "P3")
                                .or()
                                .likeRight(SysUser::getUsername, "00P3")))
                .forEach(user -> userRoleMapper.deleteByUserId(user.getId()));
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

    private void setStudentAccountParams(boolean autoCreate, String passwordRule) {
        paramMapper.update(null, new LambdaUpdateWrapper<SysParam>()
                .eq(SysParam::getParamKey, "student.autoCreateAccount")
                .set(SysParam::getParamValue, Boolean.toString(autoCreate)));
        paramMapper.update(null, new LambdaUpdateWrapper<SysParam>()
                .eq(SysParam::getParamKey, "student.defaultPwd")
                .set(SysParam::getParamValue, passwordRule));
        Cache cache = cacheManager.getCache("sysParam");
        if (cache != null) {
            cache.clear();
        }
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd, String permissions) {
    }
}
