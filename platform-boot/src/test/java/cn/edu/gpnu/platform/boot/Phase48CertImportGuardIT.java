package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.entity.SysUser;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 48 §7.4 证书完整性收尾（待复核合并）。
 * <p>
 * Item 1（P1 · 复现→阻断）：证书导入曾经在解析既有证书时（certificateByNo / certificateByStudentYear）
 * 不校验状态，会静默改写「已作废/已归档」等终态证书 —— 与 correct() 的终态守卫矛盾的数据完整性漏洞。
 * 修复后：导入命中 VOIDED/REISSUED/ARCHIVED 证书直接拒绝（逐行错误 + REQUIRES_NEW 回滚），被命中证书保持不变；
 * 非终态证书仍可正常导入更新（无误伤）。
 * <p>
 * Item 2（P2）：correct() 更正 任教学段/证书编号 时校验 18 位标准证书号内嵌的 学历码/学段码 与字段一致，
 * 拒绝把「编号」与「学段/层次」改成互相矛盾。
 * <p>
 * 跑在真实内嵌 Tomcat + 真实 MySQL 上：导入走 HTTP prevalidate→confirm，作废/更正走 HTTP /api/cert/*。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase48CertImportGuardIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final String YEAR = "2048";
    private static final String VALID_UNTIL = "2051/6/30";
    // 18 位标准号：年(2048) + 校码(10588) + 学历码(3=本科) + 省码(44) + 学段码(3=初中) + 序号
    private static final String JUNIOR_CERT_NO_1 = "204810588344300801";
    private static final String JUNIOR_CERT_NO_2 = "204810588344300802";
    private static final String JUNIOR_CERT_NO_3 = "204810588344300803";
    // 学段码=4（高中）的一致新号，用于 Item 2 正例（学段+编号同时更正到自洽）
    private static final String SENIOR_CERT_NO = "204810588344400901";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExchangeExcelHelper excelHelper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private SysUserMapper userMapper;

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
        resetUser("test_academic_admin");
    }

    /**
     * Item 1 复现→阻断：
     * ① 导入建立一张证书（ISSUED）；
     * ② OVERWRITE 再导入同一行（仅改签发人）→ 成功、证书被更新（证明非终态证书正常导入、无误伤）；
     * ③ 作废该证书（VOIDED）；
     * ④ OVERWRITE 再导入（换一个证书号命中 certificateByStudentYear，且改签发人）→ 该行被拒、
     *    successCount=0/failCount=1、逐行错误含守卫措辞；作废证书的 状态/编号/签发人/有效期 均保持不变。
     */
    @Test
    void importDoesNotSilentlyOverwriteVoidedCertificate() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        // ① 导入建立 ISSUED 证书
        importOk(academic.accessToken(),
                juniorRow("P48V", "H48000801", JUNIOR_CERT_NO_1, VALID_UNTIL, "初始校长"), "INSERT_ONLY");
        Certificate created = requireCertByNo(JUNIOR_CERT_NO_1);
        long certId = created.getId();
        assertThat(created.getStatus()).isEqualTo("ISSUED");
        assertThat(created.getIssuer()).isEqualTo("初始校长");

        // ② 非终态证书 OVERWRITE 导入正常更新（无误伤）
        JsonNode positive = importResult(academic.accessToken(),
                juniorRow("P48V", "H48000801", JUNIOR_CERT_NO_1, VALID_UNTIL, "更新校长"), "OVERWRITE");
        assertThat(positive.at("/successCount").asInt()).describedAs(positive.toString()).isEqualTo(1);
        assertThat(positive.at("/failCount").asInt()).isZero();
        Certificate afterUpdate = certificateMapper.selectById(certId);
        assertThat(afterUpdate.getStatus()).isEqualTo("ISSUED");
        assertThat(afterUpdate.getIssuer()).isEqualTo("更新校长");

        // ③ 作废
        JsonNode voided = voidCert(academic.accessToken(), certId, "编号错误作废");
        assertThat(voided.at("/status").asText()).isEqualTo("VOIDED");
        Certificate voidedCert = certificateMapper.selectById(certId);
        assertThat(voidedCert.getStatus()).isEqualTo("VOIDED");

        // ④ 复现攻击面：换号命中 certificateByStudentYear（同年度、异编号），并企图改签发人 → 必须被阻断
        JsonNode blocked = importResult(academic.accessToken(),
                juniorRow("P48V", "H48000801", JUNIOR_CERT_NO_2, VALID_UNTIL, "越权覆盖校长"), "OVERWRITE");
        assertThat(blocked.at("/successCount").asInt()).describedAs(blocked.toString()).isZero();
        assertThat(blocked.at("/failCount").asInt()).isEqualTo(1);
        assertThat(blocked.at("/messages").toString()).contains("不可通过导入修改");

        // 作废证书未被静默改写：状态/编号/签发人/有效期 全部保持 ③ 之后的样子
        Certificate stillVoided = certificateMapper.selectById(certId);
        assertThat(stillVoided.getStatus()).isEqualTo("VOIDED");
        assertThat(stillVoided.getCertNo()).isEqualTo(JUNIOR_CERT_NO_1);
        assertThat(stillVoided.getIssuer()).isEqualTo("更新校长");
        assertThat(stillVoided.getValidUntil()).isEqualTo(voidedCert.getValidUntil());
        assertThat(stillVoided.getVoidReason()).isEqualTo("编号错误作废");
        // 未产生一张以攻击号命名的新证书（既没新增、也没改到既有证书）
        assertThat(certificateMapper.selectCount(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, JUNIOR_CERT_NO_2))).isZero();
    }

    /**
     * Item 2：correct() 校验证书号内嵌段码与更正后 学段 一致。
     * ① 只把 任教学段 从「初中(码3)」改到「高中(码4)」而不动编号（编号第13位仍是3）→ 拒绝、证书不变；
     * ② 学段与编号同时改成自洽（高中 + 第13位=4 的新号）→ 通过、落库。
     */
    @Test
    void correctRejectsSegmentInconsistentWithEmbeddedCertNoCode() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        importOk(academic.accessToken(),
                juniorRow("P48C", "H48000901", JUNIOR_CERT_NO_3, VALID_UNTIL, "初始校长"), "INSERT_ONLY");
        Certificate created = requireCertByNo(JUNIOR_CERT_NO_3);
        long certId = created.getId();
        assertThat(created.getTeachingSegment()).isEqualTo("junior_middle_school");

        // ① 只改学段、不动编号 → 编号内嵌学段码(3) 与新学段(senior=4) 矛盾 → 拒绝
        JsonNode rejected = correctCert(academic.accessToken(), certId, Map.of(
                "teachingSegment", "senior_middle_school",
                "reason", "只改学段不改编号（应被拒）"));
        assertThat(rejected.at("/code").asInt()).describedAs(rejected.toString()).isEqualTo(1000);
        assertThat(rejected.at("/msg").asText()).contains("学段码与任教学段不一致");
        Certificate unchanged = certificateMapper.selectById(certId);
        assertThat(unchanged.getTeachingSegment()).isEqualTo("junior_middle_school");
        assertThat(unchanged.getCertNo()).isEqualTo(JUNIOR_CERT_NO_3);

        // ② 学段与编号同时改成自洽（高中 + 第13位=4）→ 通过
        JsonNode ok = correctCert(academic.accessToken(), certId, Map.of(
                "teachingSegment", "senior_middle_school",
                "certNo", SENIOR_CERT_NO,
                "reason", "学段与编号同时更正到自洽"));
        assertThat(ok.at("/code").asInt()).describedAs(ok.toString()).isEqualTo(0);
        Certificate corrected = certificateMapper.selectById(certId);
        assertThat(corrected.getTeachingSegment()).isEqualTo("senior_middle_school");
        assertThat(corrected.getCertNo()).isEqualTo(SENIOR_CERT_NO);
    }

    // ---------- 导入行构造（与 Phase43CertRoundTripIT 的合法初中行同构） ----------

    private ExchangeStandardRow juniorRow(String studentNo, String idCardNo, String certNo,
                                          String validUntil, String issuer) {
        ExchangeStandardRow row = new ExchangeStandardRow();
        row.setSequenceNo("1");
        row.setSchoolCode("10588");
        row.setSchoolName("广东技术师范大学");
        row.setStudentNo(studentNo);
        row.setName("证书导入守卫");
        row.setGender("female");
        row.setIdCardType("hm_travel_permit");
        row.setIdCardNo(idCardNo);
        row.setBirthDate("2000/12/31");
        row.setIdentityType("normal_student");
        row.setSourcePlace("广东省/广州市/天河区");
        row.setEducationLevel("bachelor");
        row.setInternshipOrgMode("school_organized");
        row.setInternshipLocation("primary_secondary_school");
        row.setInterviewOrgMode("separate_interview");
        row.setSecondDisciplineCode("050101");
        row.setSecondDisciplineName("汉语言文学");
        row.setInternalMajorCode("P4_NORMAL_A");
        row.setInternalMajorName("Phase4普通师范试点专业A");
        row.setTrainingGoal("junior_middle_school_teacher");
        row.setTeachingSegment("junior_middle_school");
        row.setTeachingSubject("jms_chinese");
        row.setCertNo(certNo);
        row.setValidUntil(validUntil);
        row.setIssuer(issuer);
        row.setRemark(String.valueOf(COLLEGE_A));
        return row;
    }

    // ---------- HTTP helpers ----------

    private void importOk(String token, ExchangeStandardRow row, String strategy) throws Exception {
        JsonNode data = importResult(token, row, strategy);
        assertThat(data.at("/successCount").asInt()).describedAs(data.toString()).isEqualTo(1);
    }

    private JsonNode importResult(String token, ExchangeStandardRow row, String strategy) throws Exception {
        JsonNode pre = prevalidate(token, List.of(row)).at("/data");
        assertThat(pre.at("/successCount").asInt()).describedAs(pre.toString()).isEqualTo(1);
        return confirm(token, pre.at("/batchId").asLong(), strategy).at("/data");
    }

    private JsonNode prevalidate(String token, List<ExchangeStandardRow> rows) throws Exception {
        byte[] workbook = excelHelper.writeStandardWorkbook(rows, null);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource("phase48.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook));
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.exchange(url("/api/exchange/prevalidate"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(response);
    }

    private JsonNode confirm(String token, long batchId, String strategy) throws Exception {
        ResponseEntity<String> response = exchange("/api/exchange/import/" + batchId + "/confirm", HttpMethod.POST,
                token, Map.of("strategy", strategy));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(response);
    }

    private JsonNode voidCert(String token, long certId, String reason) throws Exception {
        ResponseEntity<String> response = exchange("/api/cert/" + certId + "/void", HttpMethod.POST, token,
                Map.of("reason", reason));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).describedAs(root.toString()).isEqualTo(0);
        return root.at("/data");
    }

    private JsonNode correctCert(String token, long certId, Map<String, Object> body) throws Exception {
        ResponseEntity<String> response = exchange("/api/cert/" + certId + "/correct", HttpMethod.PUT, token, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(response);
    }

    private Certificate requireCertByNo(String certNo) {
        Certificate certificate = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, certNo).last("LIMIT 1"));
        assertThat(certificate).describedAs("cert " + certNo + " not found").isNotNull();
        return certificate;
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        return rest.exchange(url(path), method, new HttpEntity<>(body, authHeaders(accessToken)), String.class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
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

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private void resetUser(String username) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            return;
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setStatus("ENABLED");
        user.setMustChangePwd(1);
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

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM import_record_ref WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'");
        jdbcTemplate.update("DELETE FROM import_error_detail WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'");
        jdbcTemplate.update("DELETE FROM import_export_batch WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE biz_type = 'cert'");
        jdbcTemplate.update("DELETE FROM certificate WHERE student_no LIKE 'P48%'");
        jdbcTemplate.update("DELETE FROM training_profile WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P48%')");
        jdbcTemplate.update("DELETE FROM student WHERE student_no LIKE 'P48%'");
        jdbcTemplate.update("DELETE FROM cert_sequence WHERE scope_key LIKE '10588:2048:%'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
