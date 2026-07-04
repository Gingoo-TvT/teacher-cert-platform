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
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.system.entity.SysUser;
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

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase11StatsIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final String YEAR = "2036";
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
    private SysUserMapper userMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private ExemptionRequestMapper exemptionMapper;

    @Autowired
    private VideoReviewMapper videoReviewMapper;

    @Autowired
    private TrainingProfileMapper trainingMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void resetData() {
        cleanupGeneratedData();
        resetUser("test_college_clerk", true);
        resetUser("test_academic_admin", true);
    }

    @Test
    void materialCompletionStatsReconcileWithDetailsAndGroupedRows() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String className = "Phase11材料对账班";
        long complete = seedStudent("P11MAT-A", COLLEGE_A, className);
        long incomplete = seedStudent("P11MAT-B", COLLEGE_A, className);
        insertMaterials(complete, COLLEGE_A, true);
        insertMaterials(incomplete, COLLEGE_A, false);

        JsonNode report = json(exchange("/api/stats/materials?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, academic.accessToken(), null)).at("/data");

        assertThat(metric(report, "应交人数")).isEqualTo("2");
        assertThat(rowCount(report, "overall", "PASSED")).isEqualTo(1);
        assertThat(rowCount(report, "overall", "NOT_QUALIFIED")).isEqualTo(1);
        assertThat(report.at("/details").toString()).contains("P11MAT-B").contains("材料未复审通过或缺失");

        Map<String, Long> actualPassedByCategory = materialMapper.selectList(new LambdaQueryWrapper<ProcessMaterial>()
                        .eq(ProcessMaterial::getAssessmentYear, YEAR)
                        .in(ProcessMaterial::getStudentId, List.of(complete, incomplete)))
                .stream()
                .filter(item -> "PASSED".equals(item.getStatus()))
                .collect(LinkedHashMap::new,
                        (map, item) -> map.merge(item.getCategory(), 1L, Long::sum),
                        Map::putAll);
        for (String category : MATERIAL_CATEGORIES) {
            assertThat(categoryRowCount(report, category, "PASSED"))
                    .isEqualTo(actualPassedByCategory.getOrDefault(category, 0L));
        }
    }

    @Test
    void collegeStatsExcludeOtherCollegeData() throws Exception {
        LoginResult clerk = readyLogin("test_college_clerk");
        String className = "Phase11范围班";
        seedStudent("P11SCPA", COLLEGE_A, className);
        seedStudent("P11SCPB", COLLEGE_B, className);

        JsonNode report = json(exchange("/api/stats/submission?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");

        assertThat(metric(report, "学生数")).isEqualTo("1");
        assertThat(report.at("/details").toString()).contains("P11SCPA").doesNotContain("P11SCPB");
    }

    @Test
    void certificateStatsReconcileWithCertificateListGrouping() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String className = "Phase11证书对账班";
        long generatedStudent = seedStudent("P11CERT-G", COLLEGE_A, className);
        long issuedStudent = seedStudent("P11CERT-I", COLLEGE_A, className);
        long voidedStudent = seedStudent("P11CERT-V", COLLEGE_A, className);
        insertCertificate(generatedStudent, "P11CERT-G", COLLEGE_A, "203610588344400001", "GENERATED");
        insertCertificate(issuedStudent, "P11CERT-I", COLLEGE_A, "203610588344400002", "ISSUED");
        insertCertificate(voidedStudent, "P11CERT-V", COLLEGE_A, "203610588344400003", "VOIDED");

        JsonNode report = json(exchange("/api/stats/certificates?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, academic.accessToken(), null)).at("/data");

        assertThat(rowCount(report, "certificate", "GENERATED")).isEqualTo(1);
        assertThat(rowCount(report, "certificate", "ISSUED")).isEqualTo(1);
        assertThat(rowCount(report, "certificate", "VOIDED")).isEqualTo(1);
        assertThat(rowCount(report, "certificate", "WAIT_GENERATE")).isEqualTo(1);

        Map<String, Long> actualByStatus = certificateMapper.selectList(new LambdaQueryWrapper<Certificate>()
                        .eq(Certificate::getAssessmentYear, YEAR)
                        .in(Certificate::getStudentId, List.of(generatedStudent, issuedStudent, voidedStudent)))
                .stream()
                .collect(LinkedHashMap::new,
                        (map, item) -> map.merge(item.getStatus(), 1L, Long::sum),
                        Map::putAll);
        assertThat(actualByStatus).containsEntry("GENERATED", 1L)
                .containsEntry("ISSUED", 1L)
                .containsEntry("VOIDED", 1L);
        assertThat(report.at("/details").toString()).contains("203610588344400001")
                .contains("203610588344400002")
                .contains("203610588344400003");
    }

    // Phase 44d（P1-3）：证书聚合下推 SQL 后，学院用户仅见本学院数据（证明数据范围随下推保留）。
    @Test
    void collegeCertificateStatsExcludeOtherCollegeData() throws Exception {
        String className = "Phase11证书范围班";
        long collegeAStudent = seedStudent("P11CSC-A", COLLEGE_A, className);
        long collegeBStudent = seedStudent("P11CSC-B", COLLEGE_B, className);
        insertCertificate(collegeAStudent, "P11CSC-A", COLLEGE_A, "203610588344401001", "GENERATED");
        insertCertificate(collegeBStudent, "P11CSC-B", COLLEGE_B, "203610588344401002", "ISSUED");

        LoginResult clerk = readyLogin("test_college_clerk");
        JsonNode scoped = json(exchange("/api/stats/certificates?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");

        assertThat(rowCount(scoped, "certificate", "GENERATED")).isEqualTo(1);
        assertThat(rowCount(scoped, "certificate", "ISSUED")).isEqualTo(0);
        assertThat(metric(scoped, "应生成学生数")).isEqualTo("1");
        assertThat(metric(scoped, "证书记录数")).isEqualTo("1");
        assertThat(scoped.at("/details").toString())
                .contains("203610588344401001")
                .doesNotContain("203610588344401002");
    }

    // Phase 44d（P1-3）：免考聚合下推 SQL：SCHOOL 口径分组对账；COLLEGE 口径仅本学院。
    @Test
    void exemptionStatsReconcileWithSqlGroupingAndCollegeScope() throws Exception {
        String className = "Phase11免考班";
        long collegeAStudent = seedStudent("P11EXM-A", COLLEGE_A, className);
        long collegeBStudent = seedStudent("P11EXM-B", COLLEGE_B, className);
        insertExemption(collegeAStudent, COLLEGE_A, "chinese", "语文", "PASSED");
        insertExemption(collegeAStudent, COLLEGE_A, "math", "数学", "SECOND_REVIEW");
        insertExemption(collegeBStudent, COLLEGE_B, "chinese", "语文", "PASSED");

        LoginResult academic = readyLogin("test_academic_admin");
        JsonNode all = json(exchange("/api/stats/exemptions?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(metric(all, "免考申请科目数")).isEqualTo("3");
        assertThat(metric(all, "复审通过科目数")).isEqualTo("2");
        assertThat(subjectRowCount(all, "语文", "PASSED")).isEqualTo(2);
        assertThat(subjectRowCount(all, "数学", "SECOND_REVIEW")).isEqualTo(1);

        LoginResult clerk = readyLogin("test_college_clerk");
        JsonNode scoped = json(exchange("/api/stats/exemptions?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(metric(scoped, "免考申请科目数")).isEqualTo("2");
        assertThat(metric(scoped, "复审通过科目数")).isEqualTo("1");
        assertThat(subjectRowCount(scoped, "语文", "PASSED")).isEqualTo(1);
        assertThat(scoped.at("/details").toString()).contains("P11EXM-A").doesNotContain("P11EXM-B");
    }

    // Phase 44d（P1-3）：视频聚合下推 SQL：SCHOOL 口径状态分组/已上传对账；COLLEGE 口径仅本学院。
    @Test
    void videoStatsReconcileWithSqlGroupingAndCollegeScope() throws Exception {
        String className = "Phase11视频班";
        long collegeAStudent = seedStudent("P11VID-A", COLLEGE_A, className);
        long collegeBStudent = seedStudent("P11VID-B", COLLEGE_B, className);
        insertVideoReview(collegeAStudent, COLLEGE_A, "REVIEW_COMPLETED", 111L);
        insertVideoReview(collegeBStudent, COLLEGE_B, "NEED_REVIEW", 222L);

        LoginResult academic = readyLogin("test_academic_admin");
        JsonNode all = json(exchange("/api/stats/videos?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(metric(all, "应传人数")).isEqualTo("2");
        assertThat(metric(all, "已上传人数")).isEqualTo("2");
        assertThat(metric(all, "需复评")).isEqualTo("1");
        assertThat(rowCount(all, "video", "REVIEW_COMPLETED")).isEqualTo(1);
        assertThat(rowCount(all, "video", "NEED_REVIEW")).isEqualTo(1);

        LoginResult clerk = readyLogin("test_college_clerk");
        JsonNode scoped = json(exchange("/api/stats/videos?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(metric(scoped, "应传人数")).isEqualTo("1");
        assertThat(metric(scoped, "已上传人数")).isEqualTo("1");
        assertThat(metric(scoped, "需复评")).isEqualTo("0");
        assertThat(rowCount(scoped, "video", "REVIEW_COMPLETED")).isEqualTo(1);
        assertThat(rowCount(scoped, "video", "NEED_REVIEW")).isEqualTo(0);
    }

    // Phase 44d（P1-3）：交叉聚合下推 SQL（JOIN student 取身份类型）：SCHOOL 分组对账；COLLEGE 仅本学院（证明 JOIN 未泄漏范围）。
    @Test
    void crossStatsReconcileWithSqlGroupingAndCollegeScope() throws Exception {
        String className = "Phase11交叉班";
        long collegeAStudent = seedStudent("P11CRS-A", COLLEGE_A, className);
        long collegeBStudent = seedStudent("P11CRS-B", COLLEGE_B, className);
        insertTraining(collegeAStudent, COLLEGE_A, "senior_middle_school", "语文", "bachelor");
        insertTraining(collegeBStudent, COLLEGE_B, "senior_middle_school", "语文", "bachelor");

        LoginResult academic = readyLogin("test_academic_admin");
        JsonNode all = json(exchange("/api/stats/cross?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(metric(all, "培养信息记录数")).isEqualTo("2");
        assertThat(segmentSubjectRowCount(all, "senior_middle_school/语文", "normal_student")).isEqualTo(2);

        LoginResult clerk = readyLogin("test_college_clerk");
        JsonNode scoped = json(exchange("/api/stats/cross?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(metric(scoped, "培养信息记录数")).isEqualTo("1");
        assertThat(segmentSubjectRowCount(scoped, "senior_middle_school/语文", "normal_student")).isEqualTo(1);
    }

    @Test
    void anomalyStatsLocateStudentAndField() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String className = "Phase11异常班";
        long studentId = seedStudent("P11BAD", COLLEGE_A, className);
        Student student = studentMapper.selectById(studentId);
        student.setIdCardType("resident_id_card");
        student.setIdCardNo("bad-id");
        student.setBirthDate("2000/12/31");
        studentMapper.updateById(student);

        JsonNode report = json(exchange("/api/stats/anomalies?assessmentYear=" + YEAR + "&className=" + className,
                HttpMethod.GET, academic.accessToken(), null)).at("/data");

        assertThat(report.at("/details").toString()).contains("P11BAD").contains("证件号码");
        assertThat(metric(report, "异常数")).isNotEqualTo("0");
    }

    @Test
    void statsExportGeneratesTextFormattedWorkbook() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String className = "Phase11导出班";
        long studentId = seedStudent("P11EXP", COLLEGE_A, className);
        insertMaterials(studentId, COLLEGE_A, true);

        ResponseEntity<byte[]> response = download("/api/stats/materials/export", HttpMethod.POST, academic.accessToken(),
                Map.of("assessmentYear", YEAR, "className", className));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getBody()))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            DataFormatter formatter = new DataFormatter();
            assertThat(formatter.formatCellValue(header.getCell(0))).isEqualTo("统计类型");
            CellStyle firstColumnStyle = workbook.getSheetAt(0).getColumnStyle(0);
            assertThat(firstColumnStyle.getDataFormatString()).isEqualTo("@");
            assertThat(formatter.formatCellValue(workbook.getSheetAt(0).getRow(1).getCell(5))).isNotBlank();
        }
    }

    private long seedStudent(String studentNo, long collegeId, String className) {
        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName("统计学生" + studentNo);
        student.setGender("female");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo(uniqueTravelPermit());
        student.setBirthDate("2000/12/31");
        student.setIdentityType("normal_student");
        student.setSourceProvince("440000");
        student.setSourceCity("440100");
        student.setSourceCounty("440106");
        student.setSourceFull("广东省/广州市/天河区");
        student.setCollegeId(collegeId);
        student.setGrade("2022");
        student.setClassName(className);
        student.setStatus("PASSED");
        student.setLocked(1);
        studentMapper.insert(student);
        return student.getId();
    }

    private void insertMaterials(long studentId, long collegeId, boolean complete) {
        List<String> categories = complete ? MATERIAL_CATEGORIES : MATERIAL_CATEGORIES.subList(0, MATERIAL_CATEGORIES.size() - 1);
        for (String category : categories) {
            ProcessMaterial material = new ProcessMaterial();
            material.setStudentId(studentId);
            material.setCollegeId(collegeId);
            material.setAssessmentYear(YEAR);
            material.setCategory(category);
            material.setFileId(0L);
            material.setFileName(category + ".pdf");
            material.setFilePath("phase11/" + category + ".pdf");
            material.setFileSize(12L);
            material.setContentType("application/pdf");
            material.setUploaderId(0L);
            material.setUploadTime(LocalDateTime.now());
            material.setStatus("PASSED");
            material.setLocked(1);
            materialMapper.insert(material);
        }
    }

    private void insertCertificate(long studentId, String studentNo, long collegeId, String certNo, String status) {
        Certificate certificate = new Certificate();
        certificate.setStudentId(studentId);
        certificate.setCollegeId(collegeId);
        certificate.setAssessmentYear(YEAR);
        certificate.setCertNo(certNo);
        certificate.setStudentNo(studentNo);
        certificate.setStudentName("统计学生" + studentNo);
        certificate.setIdCardType("hm_travel_permit");
        certificate.setIdCardNo(uniqueTravelPermit());
        certificate.setEducationLevel("bachelor");
        certificate.setTrainingGoal("senior_middle_school_teacher");
        certificate.setTeachingSegment("senior_middle_school");
        certificate.setTeachingSubjectCode("sms_chinese");
        certificate.setTeachingSubjectName("语文");
        certificate.setIssuer("校长");
        certificate.setIssueDate("2036/6/1");
        certificate.setValidUntil("2039/6/30");
        certificate.setStatus(status);
        certificate.setLocked("WAIT_GENERATE".equals(status) ? 0 : 1);
        certificateMapper.insert(certificate);
    }

    private void insertExemption(long studentId, long collegeId, String subject, String subjectLabel, String finalStatus) {
        ExemptionRequest exemption = new ExemptionRequest();
        exemption.setStudentId(studentId);
        exemption.setCollegeId(collegeId);
        exemption.setAssessmentYear(YEAR);
        exemption.setTeachingSegment("senior_middle_school");
        exemption.setSubject(subject);
        exemption.setSubjectLabel(subjectLabel);
        exemption.setBasis("national_college_english_test");
        exemption.setBasisLabel("全国大学英语等级考试");
        exemption.setFinalStatus(finalStatus);
        exemptionMapper.insert(exemption);
    }

    private void insertVideoReview(long studentId, long collegeId, String status, Long videoFileId) {
        VideoReview review = new VideoReview();
        review.setStudentId(studentId);
        review.setCollegeId(collegeId);
        review.setAssessmentYear(YEAR);
        review.setStatus(status);
        review.setVideoFileId(videoFileId);
        videoReviewMapper.insert(review);
    }

    private void insertTraining(long studentId, long collegeId, String segment, String subjectName, String educationLevel) {
        TrainingProfile training = new TrainingProfile();
        training.setStudentId(studentId);
        training.setCollegeId(collegeId);
        training.setAssessmentYear(YEAR);
        training.setSecondDisciplineCode("0401");
        training.setSecondDisciplineName("教育学");
        training.setEducationLevel(educationLevel);
        training.setTrainingGoal("senior_middle_school_teacher");
        training.setInternshipOrgMode("centralized");
        training.setInternshipLocation("school");
        training.setTeachingSegment(segment);
        training.setTeachingSubjectId(1L);
        training.setTeachingSubjectCode("sms_chinese");
        training.setTeachingSubjectName(subjectName);
        training.setInterviewOrgMode("centralized");
        trainingMapper.insert(training);
    }

    private long rowCount(JsonNode report, String dimension, String status) {
        for (JsonNode row : report.at("/rows")) {
            if (dimension.equals(row.at("/dimension").asText()) && status.equals(row.at("/status").asText())) {
                return row.at("/count").asLong();
            }
        }
        return 0L;
    }

    private long categoryRowCount(JsonNode report, String category, String status) {
        for (JsonNode row : report.at("/rows")) {
            if ("category".equals(row.at("/dimension").asText())
                    && status.equals(row.at("/status").asText())
                    && category.equals(row.at("/values/材料类别").asText())) {
                return row.at("/count").asLong();
            }
        }
        return 0L;
    }

    private long subjectRowCount(JsonNode report, String label, String status) {
        for (JsonNode row : report.at("/rows")) {
            if ("subject".equals(row.at("/dimension").asText())
                    && label.equals(row.at("/dimensionLabel").asText())
                    && status.equals(row.at("/status").asText())) {
                return row.at("/count").asLong();
            }
        }
        return 0L;
    }

    private long segmentSubjectRowCount(JsonNode report, String label, String identity) {
        for (JsonNode row : report.at("/rows")) {
            if ("segmentSubject".equals(row.at("/dimension").asText())
                    && label.equals(row.at("/dimensionLabel").asText())
                    && identity.equals(row.at("/status").asText())) {
                return row.at("/count").asLong();
            }
        }
        return 0L;
    }

    private String metric(JsonNode report, String label) {
        for (JsonNode metric : report.at("/metrics")) {
            if (label.equals(metric.at("/label").asText())) {
                return metric.at("/value").asText();
            }
        }
        return "";
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
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
        JsonNode initialRoot = objectMapper.readTree(initial.getBody());
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
        JsonNode root = objectMapper.readTree(response.getBody());
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        JsonNode data = root.at("/data");
        return new LoginResult(data.at("/accessToken").asText(), data.at("/refreshToken").asText(),
                data.at("/mustChangePwd").asBoolean());
    }

    private ResponseEntity<String> loginRaw(String username, String password) throws Exception {
        JsonNode captcha = objectMapper.readTree(rest.getForEntity(url("/api/auth/captcha"), String.class).getBody()).at("/data");
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

    private String uniqueTravelPermit() {
        return "H" + ("%08d").formatted(Math.floorMod(System.nanoTime(), 100000000));
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

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE biz_type = 'stats'");
        jdbcTemplate.update("DELETE FROM certificate WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P11%') OR student_no LIKE 'P11%'");
        jdbcTemplate.update("DELETE FROM process_material WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P11%')");
        jdbcTemplate.update("DELETE FROM exemption_request WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P11%')");
        jdbcTemplate.update("DELETE FROM video_review WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P11%')");
        jdbcTemplate.update("DELETE FROM training_profile WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P11%')");
        jdbcTemplate.update("DELETE FROM student WHERE student_no LIKE 'P11%'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
