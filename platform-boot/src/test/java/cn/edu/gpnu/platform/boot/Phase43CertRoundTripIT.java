package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
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
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.entity.TeachingSubject;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.TeachingSubjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 43.2 §7.4 证书往返/序列/签发日期正确性收尾（待复核合并）。
 * 三条缺陷的端到端复现→阻断：
 * 1) 往返列语义：标准导出把「学院ID」写入备注列（旧实现误写证书状态），与导入端 resolveCollegeId 读备注解析学院ID 对齐，
 *    导出→导入 学院标识无损往返（旧实现重导出文件的备注="ISSUED"，被 parseLong 当学院ID → null → 学院识别损坏）。
 * 2) 序列占用：导入的18位标准证书号推进 cert_sequence，后续自动生成跳过已占号
 *    （旧实现导入不占序列 → generate 撞号回滚又不推进序列 → “证书编号已存在，请重试”永远失败）。
 * 3) 签发日期：导入的已签发证书补一个与 validUntil 规则自洽的签发日期（旧实现签发日期永远为空、无补设路径）。
 *
 * 跑在真实内嵌 Tomcat + 真实 MySQL 上：导入走 HTTP prevalidate→confirm，生成走 HTTP /api/cert/generate。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase43CertRoundTripIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final String YEAR = "2035";
    private static final String JUNIOR_SEGMENT = "junior_middle_school";
    private static final String JUNIOR_SUBJECT_CODE = "jms_chinese";
    private static final String JUNIOR_SCOPE_KEY = "10588:2035:3";
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
    private StudentMapper studentMapper;

    @Autowired
    private TrainingProfileMapper trainingProfileMapper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private AbilityTestResultMapper testResultMapper;

    @Autowired
    private VideoReviewMapper videoReviewMapper;

    @Autowired
    private TeachingSubjectMapper teachingSubjectMapper;

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
    void resetData() {
        cleanupGeneratedData();
        resetParam("cert.seq.scope", "SCHOOL_YEAR_SEGMENT");
        resetParam("video.required", "true");
        resetUser("test_academic_admin");
    }

    /**
     * 缺陷1（往返列语义）+ 缺陷3（签发日期）：
     * 导入一张 COLLEGE_B 证书 → 标准导出的备注列(Z=索引25)承载学院ID（非证书状态） →
     * 把导出文件读回、改学号/证件号/证书号后重新导入 → 新记录仍落在 COLLEGE_B（学院无损往返），
     * 且新证书带自洽签发日期。
     */
    @Test
    void standardExportCarriesCollegeIdInRemarkAndRoundTripsCollegeAndIssueDate() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        ExchangeStandardRow seed = collegeBRow("P43RTB", "H43000701", "203510588344300741");
        JsonNode seedPre = prevalidate(academic.accessToken(), List.of(seed)).at("/data");
        assertThat(seedPre.at("/successCount").asInt()).isEqualTo(1);
        JsonNode seedImport = confirm(academic.accessToken(), seedPre.at("/batchId").asLong(), "INSERT_ONLY").at("/data");
        assertThat(seedImport.at("/successCount").asInt()).isEqualTo(1);

        // 缺陷3：首次导入的已签发证书已带自洽签发日期（旧实现为空）。
        Certificate seededCert = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, "203510588344300741").last("LIMIT 1"));
        assertThat(seededCert).isNotNull();
        assertThat(seededCert.getStatus()).isEqualTo("ISSUED");
        assertThat(seededCert.getIssueDate()).isEqualTo("2035/6/30");

        // 标准导出：备注列(索引25)= 学院ID（旧实现会写证书状态 "ISSUED"）。
        ResponseEntity<byte[]> exported = download("/api/exchange/export/STANDARD", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", YEAR, "keyword", "P43RTB"));
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            DataFormatter fmt = new DataFormatter();
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(fmt.formatCellValue(row.getCell(3))).isEqualTo("P43RTB");
            assertThat(fmt.formatCellValue(row.getCell(25))).isEqualTo(String.valueOf(COLLEGE_B));
            assertThat(fmt.formatCellValue(row.getCell(25))).isNotEqualTo("ISSUED");
        }

        // 读回导出文件 → 备注就是学院ID；改三处唯一键后重新导入（新记录）。
        List<ExchangeExcelHelper.ReadRow> readBack = excelHelper.readStandardRows(exported.getBody());
        assertThat(readBack).hasSize(1);
        ExchangeStandardRow roundTrip = readBack.get(0).row();
        assertThat(roundTrip.getRemark()).isEqualTo(String.valueOf(COLLEGE_B));
        roundTrip.setStudentNo("P43RTB2");
        roundTrip.setIdCardNo("H43000742");
        roundTrip.setCertNo("203510588344300742");

        JsonNode pre = prevalidate(academic.accessToken(), List.of(roundTrip)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(1);
        JsonNode imported = confirm(academic.accessToken(), pre.at("/batchId").asLong(), "INSERT_ONLY").at("/data");
        assertThat(imported.at("/successCount").asInt()).isEqualTo(1);

        // 学院标识无损往返：新学生/新证书仍落在 COLLEGE_B。
        Student newStudent = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, "P43RTB2").last("LIMIT 1"));
        assertThat(newStudent).isNotNull();
        assertThat(newStudent.getCollegeId()).isEqualTo(COLLEGE_B);
        Certificate newCert = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, "203510588344300742").last("LIMIT 1"));
        assertThat(newCert).isNotNull();
        assertThat(newCert.getCollegeId()).isEqualTo(COLLEGE_B);
        // 缺陷3：往返导入的已签发证书亦带自洽签发日期。
        assertThat(newCert.getStatus()).isEqualTo("ISSUED");
        assertThat(newCert.getIssueDate()).isEqualTo("2035/6/30");
    }

    /**
     * 缺陷2（序列占用）：导入一张证书号占用 junior/2035 作用域序号 00001（即后续自动生成本应产出的下一个号）→
     * cert_sequence 被推进到 ≥1 → 对同一作用域自动生成新证书应产出 00002（跳过已占号），而非撞号失败。
     * 旧实现：导入不占序列 → generate 命中 00001 撞 uk_certificate_cert_no → 回滚、序列不推进 →
     * “证书编号已存在，请重试”永久失败（本用例在旧码上会在 generate 处失败）。
     */
    @Test
    void importedCertNoOccupiesSequenceSoLaterGenerateDoesNotDeadCollide() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        ExchangeStandardRow occupying = collegeAJuniorRow("P43SEQ", "S43000001", "203510588344300001");
        JsonNode pre = prevalidate(academic.accessToken(), List.of(occupying)).at("/data");
        assertThat(pre.at("/successCount").asInt()).describedAs(pre.toString()).isEqualTo(1);
        JsonNode imported = confirm(academic.accessToken(), pre.at("/batchId").asLong(), "INSERT_ONLY").at("/data");
        assertThat(imported.at("/successCount").asInt()).isEqualTo(1);

        // 代码边界证据：导入号已占用/推进序列（scope 10588:2035:3 的 current_seq ≥ 1）。
        Integer currentSeq = jdbcTemplate.queryForObject(
                "SELECT current_seq FROM cert_sequence WHERE scope_key = ? AND deleted = 0",
                Integer.class, JUNIOR_SCOPE_KEY);
        assertThat(currentSeq).isNotNull();
        assertThat(currentSeq).isGreaterThanOrEqualTo(1);

        // 端到端：对同一作用域自动生成 → 得 00002（跳过导入占用的 00001），不撞号、不死锁。
        long studentId = seedEligibleStudent("P43GEN", COLLEGE_A, YEAR, JUNIOR_SEGMENT, JUNIOR_SUBJECT_CODE, "语文");
        JsonNode generated = generateOk(academic.accessToken(), studentId, YEAR);
        assertThat(generated.at("/certNo").asText()).isEqualTo("203510588344300002");
    }

    // ---------- 导入行构造 ----------

    private ExchangeStandardRow collegeBRow(String studentNo, String idCardNo, String certNo) {
        ExchangeStandardRow row = baseRow(studentNo, idCardNo, certNo);
        row.setSecondDisciplineCode("070101");
        row.setSecondDisciplineName("数学与应用数学");
        row.setInternalMajorCode("P4_NORMAL_B");
        row.setInternalMajorName("Phase4普通师范试点专业B");
        row.setTrainingGoal("junior_middle_school_teacher");
        row.setTeachingSegment("junior_middle_school");
        row.setTeachingSubject("jms_math");
        row.setRemark(String.valueOf(COLLEGE_B));
        return row;
    }

    private ExchangeStandardRow collegeAJuniorRow(String studentNo, String idCardNo, String certNo) {
        ExchangeStandardRow row = baseRow(studentNo, idCardNo, certNo);
        row.setSecondDisciplineCode("050101");
        row.setSecondDisciplineName("汉语言文学");
        row.setInternalMajorCode("P4_NORMAL_A");
        row.setInternalMajorName("Phase4普通师范试点专业A");
        row.setTrainingGoal("junior_middle_school_teacher");
        row.setTeachingSegment("junior_middle_school");
        row.setTeachingSubject("jms_chinese");
        row.setRemark(String.valueOf(COLLEGE_A));
        return row;
    }

    private ExchangeStandardRow baseRow(String studentNo, String idCardNo, String certNo) {
        ExchangeStandardRow row = new ExchangeStandardRow();
        row.setSequenceNo("1");
        row.setSchoolCode("10588");
        row.setSchoolName("广东技术师范大学");
        row.setStudentNo(studentNo);
        row.setName("往返测试学生");
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
        row.setCertNo(certNo);
        row.setValidUntil("2038/6/30");
        row.setIssuer("校长");
        return row;
    }

    // ---------- 生成资格学生（供 /api/cert/generate 端到端）----------

    private long seedEligibleStudent(String prefix, long collegeId, String year, String segment,
                                     String subjectCode, String subjectName) {
        long studentId = insertStudent(prefix, collegeId);
        insertTraining(studentId, collegeId, year, segment, subjectCode, subjectName);
        insertMaterials(studentId, collegeId, year);
        insertTestResult(studentId, collegeId, year);
        insertVideo(studentId, collegeId, year);
        return studentId;
    }

    private long insertStudent(String prefix, long collegeId) {
        Student student = new Student();
        student.setStudentNo(prefix + "-" + Math.floorMod(System.nanoTime(), 1_000_000_000L));
        student.setName("证书学生" + prefix);
        student.setGender("female");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo("G" + ("%08d").formatted(Math.floorMod(System.nanoTime(), 100000000)));
        student.setBirthDate("2001/1/2");
        student.setIdentityType("normal_student");
        student.setSourceFull("广东省/广州市/天河区");
        student.setCollegeId(collegeId);
        student.setGrade("2022");
        student.setClassName("Phase43测试班");
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
        training.setTrainingGoal("junior_middle_school_teacher");
        training.setInternshipOrgMode("school_organized");
        training.setInternshipLocation("primary_secondary_school");
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

    // ---------- HTTP helpers ----------

    private JsonNode prevalidate(String token, List<ExchangeStandardRow> rows) throws Exception {
        byte[] workbook = excelHelper.writeStandardWorkbook(rows, null);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource("phase43.xlsx",
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

    private JsonNode generateOk(String token, long studentId, String year) throws Exception {
        ResponseEntity<String> response = exchange("/api/cert/generate", HttpMethod.POST, token,
                Map.of("studentId", studentId, "assessmentYear", year));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).describedAs(root.toString()).isEqualTo(0);
        return root.at("/data");
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

    private void resetParam(String key, String value) {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, key).last("LIMIT 1"));
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
        jdbcTemplate.update("DELETE FROM import_record_ref WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'");
        jdbcTemplate.update("DELETE FROM import_error_detail WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'");
        jdbcTemplate.update("DELETE FROM import_export_batch WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE biz_type = 'cert'");
        jdbcTemplate.update("DELETE FROM certificate WHERE student_no LIKE 'P43%'");
        jdbcTemplate.update("DELETE FROM video_review WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P43%')");
        jdbcTemplate.update("DELETE FROM ability_test_result WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P43%')");
        jdbcTemplate.update("DELETE FROM process_material WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P43%')");
        jdbcTemplate.update("DELETE FROM training_profile WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P43%')");
        jdbcTemplate.update("DELETE FROM student WHERE student_no LIKE 'P43%'");
        jdbcTemplate.update("DELETE FROM cert_sequence WHERE scope_key LIKE '10588:2035:%'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
