package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.exchange.entity.ImportErrorDetail;
import cn.edu.gpnu.platform.exchange.entity.ImportExportBatch;
import cn.edu.gpnu.platform.exchange.mapper.ImportErrorDetailMapper;
import cn.edu.gpnu.platform.exchange.mapper.ImportExportBatchMapper;
import cn.edu.gpnu.platform.exchange.model.ExchangeColumn;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.exchange.support.ExchangeImportHook;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(Phase10ExchangeIT.ExchangeHookTestConfiguration.class)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase10ExchangeIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;
    private static final String STATUS_ONLY_BATCH_LOCK_SQL =
            "select status from import_export_batch where id = ? and deleted = 0 for update";

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
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private IdCardProtectionService idCardProtectionService;

    @Autowired
    private TrainingProfileMapper trainingProfileMapper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private ProcessMaterialMapper processMaterialMapper;

    @Autowired
    private VideoReviewMapper videoReviewMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private ImportExportBatchMapper batchMapper;

    @Autowired
    private ImportErrorDetailMapper errorDetailMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ControlledExchangeImportHook exchangeImportHook;

    @Autowired
    private BatchLockSqlProbe batchLockSqlProbe;

    @BeforeEach
    @AfterEach
    void resetData() {
        exchangeImportHook.reset();
        batchLockSqlProbe.reset();
        cleanupGeneratedData();
        ensureSecondCollegeStudent();
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
    }

    @Test
    void templateAndStandardExportKeepTwentySixTextColumns() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        ResponseEntity<byte[]> template = download("/api/exchange/template", HttpMethod.GET, academic.accessToken(), null);
        assertThat(template.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertWorkbookHeaderAndTextFormat(template.getBody());

        seedCertificateSnapshot("P10EXP", COLLEGE_A, "2026", "00123", "44010620001231001X",
                "202610588344300001", "2029/6/30");
        ResponseEntity<byte[]> exported = download("/api/exchange/export/STANDARD", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", "2026"));
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertWorkbookHeaderAndTextFormat(exported.getBody());
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            DataFormatter formatter = new DataFormatter();
            assertThat(formatter.formatCellValue(row.getCell(3))).isEqualTo("00123");
            assertThat(formatter.formatCellValue(row.getCell(7))).contains("440106");
            assertThat(formatter.formatCellValue(row.getCell(22))).isEqualTo("202610588344300001");
            assertThat(formatter.formatCellValue(row.getCell(23))).isEqualTo("2029/6/30");
        }
    }

    @Test
    void fullReviewIncludesAnnualMaterialStudentWithoutTrainingOrCertificate() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        seedCertificateSnapshot("P10FULL", COLLEGE_A, "2026", "P10FULL", "F12345678",
                "202610588344300181", "2029/6/30");
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, "P10FULL").last("LIMIT 1"));
        jdbcTemplate.update("DELETE FROM certificate WHERE student_id = ?", student.getId());
        jdbcTemplate.update("DELETE FROM training_profile WHERE student_id = ?", student.getId());

        ProcessMaterial material = new ProcessMaterial();
        material.setStudentId(student.getId());
        material.setCollegeId(COLLEGE_A);
        material.setAssessmentYear("2026");
        material.setCategory("morality_teacher_ethics");
        material.setFileId(990000000000000181L);
        material.setFileName("full-review.pdf");
        material.setFilePath("phase10/full-review.pdf");
        material.setFileSize(4L);
        material.setContentType("application/pdf");
        material.setUploaderId(0L);
        material.setUploadTime(LocalDateTime.now());
        material.setStatus("PASSED");
        material.setLocked(1);
        processMaterialMapper.insert(material);

        ResponseEntity<byte[]> exported = download("/api/exchange/export/FULL_REVIEW", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", "2026", "keyword", "P10FULL"));

        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            DataFormatter formatter = new DataFormatter();
            Row header = workbook.getSheetAt(0).getRow(0);
            Row data = workbook.getSheetAt(0).getRow(1);
            assertThat(formatter.formatCellValue(data.getCell(3))).isEqualTo("P10FULL");
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                headers.add(formatter.formatCellValue(header.getCell(i)));
            }
            assertThat(headers).contains("基本信息初审人", "培养信息复审时间",
                    "思想品德及师德素养状态", "免考科目", "视频教师1分", "测试确认人", "证书状态");
        }
    }

    @Test
    void attachmentZipStreamsMaterialAndVideoForAuthorizedStudentWithoutCertificate() throws Exception {
        LoginResult clerk = readyLogin("test_college_clerk");
        assertThat(userMapper.selectByUsername("test_college_clerk").getCollegeId()).isEqualTo(COLLEGE_A);
        byte[] allowedMaterial = "phase10-authorized-material".getBytes(StandardCharsets.UTF_8);
        byte[] allowedVideo = "phase10-authorized-video".getBytes(StandardCharsets.UTF_8);
        byte[] foreignMaterial = "phase10-foreign-material".getBytes(StandardCharsets.UTF_8);
        byte[] foreignVideo = "phase10-foreign-video".getBytes(StandardCharsets.UTF_8);
        AttachmentSeed allowed = seedAttachmentOnlyStudent(
                "P10ZIPA", COLLEGE_A, "ZA1234567", "202610588344300191",
                allowedMaterial, allowedVideo);
        AttachmentSeed foreign = seedAttachmentOnlyStudent(
                "P10ZIPB", COLLEGE_B, "ZB1234567", "202610588344300192",
                foreignMaterial, foreignVideo);

        try {
            ResponseEntity<byte[]> response = download("/api/exchange/export/attachments", HttpMethod.POST,
                    clerk.accessToken(), Map.of("assessmentYear", "2026", "keyword", "P10ZIP"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, byte[]> entries = unzip(response.getBody());
            String studentDirectory = "学生材料/P10ZIPA_" + allowed.studentId();
            String materialEntry = studentDirectory + "/材料/" + allowed.materialId() + "-P10ZIPA-material.txt";
            String videoEntry = studentDirectory + "/视频/" + allowed.videoReviewId() + "-P10ZIPA-video.mp4";
            assertThat(entries.keySet()).containsExactlyInAnyOrder(
                    "附件清单.xlsx", materialEntry, videoEntry);
            assertThat(entries.get(materialEntry)).isEqualTo(allowedMaterial);
            assertThat(entries.get(videoEntry)).isEqualTo(allowedVideo);

            try (Workbook workbook = new XSSFWorkbook(
                    new ByteArrayInputStream(entries.get("附件清单.xlsx")))) {
                DataFormatter formatter = new DataFormatter();
                List<String> studentNos = new ArrayList<>();
                List<String> fileNames = new ArrayList<>();
                for (int i = 1; i <= workbook.getSheetAt(0).getLastRowNum(); i++) {
                    Row row = workbook.getSheetAt(0).getRow(i);
                    studentNos.add(formatter.formatCellValue(row.getCell(0)));
                    fileNames.add(formatter.formatCellValue(row.getCell(3)));
                }
                assertThat(studentNos).containsExactly("P10ZIPA", "P10ZIPA");
                assertThat(fileNames).containsExactlyInAnyOrder(
                        "P10ZIPA-material.txt", "P10ZIPA-video.mp4");
                assertThat(studentNos).doesNotContain("P10ZIPB");
            }
        } finally {
            fileService.delete(allowed.materialFileId());
            fileService.delete(allowed.videoFileId());
            fileService.delete(foreign.materialFileId());
            fileService.delete(foreign.videoFileId());
        }
    }

    @Test
    void standardExportFiltersInvalidStatesTransitionsIssuedAndLinksBatchAudit() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        Map<String, String> states = new LinkedHashMap<>();
        states.put("P10STI", "ISSUED");
        states.put("P10STE", "EXPORTED");
        states.put("P10STA", "ARCHIVED");
        states.put("P10STG", "GENERATED");
        states.put("P10STV", "VOIDED");
        states.put("P10STR", "REISSUED");
        int sequence = 182;
        for (Map.Entry<String, String> entry : states.entrySet()) {
            String certNo = "202610588344300" + sequence++;
            seedCertificateSnapshot(entry.getKey(), COLLEGE_A, "2026", entry.getKey(),
                    entry.getKey().substring(5, 6) + "12345678", certNo, "2029/6/30");
            Certificate certificate = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                    .eq(Certificate::getStudentNo, entry.getKey()).last("LIMIT 1"));
            certificate.setStatus(entry.getValue());
            certificateMapper.updateById(certificate);
        }

        ResponseEntity<byte[]> exported = download("/api/exchange/export/STANDARD", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", "2026", "keyword", "P10ST"));

        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> studentNos = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= workbook.getSheetAt(0).getLastRowNum(); i++) {
                studentNos.add(formatter.formatCellValue(workbook.getSheetAt(0).getRow(i).getCell(3)));
            }
        }
        assertThat(studentNos).containsExactlyInAnyOrder("P10STI", "P10STE", "P10STA");
        Certificate transitioned = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getStudentNo, "P10STI").last("LIMIT 1"));
        assertThat(transitioned.getStatus()).isEqualTo("EXPORTED");
        ImportExportBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<ImportExportBatch>()
                .eq(ImportExportBatch::getStrategy, "STANDARD")
                .orderByDesc(ImportExportBatch::getId).last("LIMIT 1"));
        assertThat(batch.getTotal()).isEqualTo(3);
        Integer auditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                WHERE operation = 'export' AND target LIKE ? AND deleted = 0
                """, Integer.class, "%:export-batch:" + batch.getId());
        assertThat(auditCount).isEqualTo(3);
    }

    @Test
    void updateEmptyPreservesTeachingSubjectGroupAndOverwriteReplacesIt() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String certNo = "202610588344300188";
        seedCertificateSnapshot("P10SUBJ", COLLEGE_A, "2026", "P10SUBJ", "U12345678",
                certNo, "2029/6/30");
        ExchangeStandardRow incoming = row("P10SUBJ", "2026", certNo);
        incoming.setIdCardType("hm_travel_permit");
        incoming.setIdCardNo("U12345678");
        incoming.setBirthDate("2000/12/31");
        incoming.setTeachingSubject("jms_math");

        JsonNode updateEmptyPre = prevalidate(academic.accessToken(), List.of(incoming)).at("/data");
        JsonNode updateEmpty = confirm(
                academic.accessToken(), updateEmptyPre.at("/batchId").asLong(), "UPDATE_EMPTY").at("/data");
        assertThat(updateEmpty.at("/successCount").asInt()).isEqualTo(1);
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, "P10SUBJ").last("LIMIT 1"));
        TrainingProfile preserved = trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, student.getId())
                .eq(TrainingProfile::getAssessmentYear, "2026").last("LIMIT 1"));
        assertThat(preserved.getTeachingSubjectCode()).isEqualTo("jms_chinese");
        assertThat(preserved.getTeachingSubjectName()).isEqualTo("语文");

        JsonNode overwritePre = prevalidate(academic.accessToken(), List.of(incoming)).at("/data");
        JsonNode overwrite = confirm(
                academic.accessToken(), overwritePre.at("/batchId").asLong(), "OVERWRITE").at("/data");
        assertThat(overwrite.at("/successCount").asInt()).isEqualTo(1);
        TrainingProfile replaced = trainingProfileMapper.selectById(preserved.getId());
        assertThat(replaced.getTeachingSubjectCode()).isEqualTo("jms_math");
        assertThat(replaced.getTeachingSubjectName()).isEqualTo("数学");
        Certificate certificate = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, certNo).last("LIMIT 1"));
        assertThat(certificate.getTeachingSubjectCode()).isEqualTo("jms_math");
        assertThat(certificate.getTeachingSubjectName()).isEqualTo("数学");
    }

    @Test
    void prevalidateReportsAllV01ToV13AndDoesNotImportInvalidRows() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        List<ExchangeStandardRow> invalid = new ArrayList<>();
        invalid.add(row("P10V01", "2026", "202610588344300101"));
        invalid.get(0).setName("");
        invalid.add(row("P10V02", "2026", "202610588344300102"));
        invalid.get(1).setStudentNo("1.23E+5");
        invalid.add(row("P10V03", "2026", "202610588344300103"));
        invalid.get(2).setSchoolCode("99999");
        invalid.add(row("P10V04", "2026", "202610588344300104"));
        invalid.get(3).setName("张三3");
        invalid.add(row("P10V05", "2026", "202610588344300105"));
        invalid.get(4).setIdCardNo("bad-id");
        invalid.add(row("P10V06", "2026", "202610588344300106"));
        invalid.get(5).setBirthDate("1999/1/1");
        invalid.add(row("P10V07", "2026", "202610588344300107"));
        invalid.get(6).setIdentityType("bad_identity");
        invalid.add(row("P10V08", "2026", "202610588344300108"));
        invalid.get(7).setIdentityType("education_master");
        invalid.get(7).setSecondDisciplineCode("030101");
        invalid.get(7).setInternalMajorCode("");
        invalid.get(7).setInternalMajorName("");
        invalid.add(row("P10V09", "2026", "202610588344300109"));
        invalid.get(8).setInternshipLocation("enterprise_vocational_education");
        invalid.add(row("P10V10", "2026", "202610588344300110"));
        invalid.get(9).setTeachingSubject("sms_chinese");
        invalid.add(row("P10V11", "2026", "bad-cert"));
        invalid.add(row("P10V12", "2026", "202610588344300112"));
        invalid.get(11).setValidUntil("2031/6/30");
        invalid.add(row("P10V13A", "2026", "202610588344300113"));
        invalid.add(row("P10V13B", "2026", "202610588344300113"));

        JsonNode result = prevalidate(academic.accessToken(), invalid).at("/data");
        String errors = result.at("/errors").toString();
        for (int i = 1; i <= 13; i++) {
            assertThat(errors).contains("V-%02d".formatted(i));
        }
        assertThat(result.at("/successCount").asInt()).isZero();
        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>().likeRight(Student::getStudentNo, "P10V"))).isZero();

        long batchId = result.at("/batchId").asLong();
        ResponseEntity<byte[]> report = download("/api/exchange/prevalidate/" + batchId + "/error-report",
                HttpMethod.GET, academic.accessToken(), null);
        assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report.getBody()))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            DataFormatter formatter = new DataFormatter();
            assertThat(formatter.formatCellValue(header.getCell(0))).isEqualTo("批次号");
            assertThat(formatter.formatCellValue(header.getCell(1))).isEqualTo("行号");
            assertThat(formatter.formatCellValue(header.getCell(4))).isEqualTo("字段");
            assertThat(formatter.formatCellValue(header.getCell(7))).isEqualTo("建议处理方式");
        }
    }

    @Test
    void prevalidateCountsCanonicalIdCardDuplicates() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        ExchangeStandardRow residentLower = row("P10CAN01", "2026", "202610588344300171");
        residentLower.setIdCardNo("44010620001231001x");
        ExchangeStandardRow residentUpper = row("P10CAN02", "2026", "202610588344300172");
        residentUpper.setIdCardNo("44010620001231001X");

        ExchangeStandardRow travelUpper = row("P10CAN03", "2026", "202610588344300173");
        travelUpper.setIdCardType("hm_travel_permit");
        travelUpper.setIdCardNo("A12345678");
        travelUpper.setBirthDate("2000/12/31");
        ExchangeStandardRow travelLower = row("P10CAN04", "2026", "202610588344300174");
        travelLower.setIdCardType("hm_travel_permit");
        travelLower.setIdCardNo("a12345678");
        travelLower.setBirthDate("2000/12/31");

        JsonNode result = prevalidate(academic.accessToken(),
                List.of(residentLower, residentUpper, travelUpper, travelLower)).at("/data");

        assertThat(result.at("/total").asInt()).isEqualTo(4);
        assertThat(result.at("/successCount").asInt()).isZero();
        assertThat(result.at("/failCount").asInt()).isEqualTo(4);
        assertThat(result.at("/previewRows").size()).isZero();
        List<Integer> duplicateRows = new ArrayList<>();
        for (JsonNode error : result.at("/errors")) {
            if ("身份证件号码".equals(error.at("/fieldName").asText())
                    && error.at("/errorReason").asText().contains("V-13")) {
                duplicateRows.add(error.at("/rowNo").asInt());
            }
        }
        assertThat(duplicateRows).containsExactlyInAnyOrder(2, 3, 4, 5);
    }

    @Test
    void prevalidateCountsFailedRowsSeparatelyFromErrorDetails() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        ExchangeStandardRow invalid = row("P10STAT", "2026", "202610588344300175");
        invalid.setStudentNo("1.23E+5");
        invalid.setName("");

        JsonNode result = prevalidate(academic.accessToken(), List.of(invalid)).at("/data");

        assertThat(result.at("/total").asInt()).isEqualTo(1);
        assertThat(result.at("/successCount").asInt()).isZero();
        assertThat(result.at("/failCount").asInt()).isEqualTo(1);
        assertThat(result.at("/previewRows").size()).isZero();
        assertThat(result.at("/errors").size()).isGreaterThanOrEqualTo(2);
        long batchId = result.at("/batchId").asLong();
        assertThat(batchMapper.selectById(batchId).getFailCount()).isEqualTo(1);

        JsonNode records = json(exchange("/api/exchange/batches?type=import&page=1&size=100",
                HttpMethod.GET, academic.accessToken(), null)).at("/data/records");
        JsonNode listedBatch = null;
        for (JsonNode record : records) {
            if (record.at("/id").asLong() == batchId) {
                listedBatch = record;
                break;
            }
        }
        assertThat(listedBatch).isNotNull();
        assertThat(listedBatch.at("/failCount").asInt()).isEqualTo(1);
    }

    @Test
    void importStrategiesRollbackConflictAndLeadingZerosWork() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        seedCertificateSnapshot("P10DUP", COLLEGE_A, "2026", "P10DUPNO", "44010620001231020X",
                "202610588344400120", "2029/6/30");

        ExchangeStandardRow duplicate = row("P10DUPNO", "2026", "202610588344300121");
        duplicate.setIdCardNo("H12345678");
        duplicate.setIdCardType("hm_travel_permit");
        duplicate.setBirthDate("2000/12/31");
        JsonNode dupPre = prevalidate(academic.accessToken(), List.of(duplicate)).at("/data");
        assertThat(dupPre.at("/successCount").asInt()).isEqualTo(1);
        JsonNode skipped = confirm(academic.accessToken(), dupPre.at("/batchId").asLong(), "SKIP_DUPLICATE").at("/data");
        assertThat(skipped.at("/failCount").asInt()).isEqualTo(1);

        ExchangeStandardRow row = row("00123", "2026", "202610588344300122");
        row.setIdCardNo("H87654321");
        row.setIdCardType("hm_travel_permit");
        row.setBirthDate("2000/12/31");
        JsonNode pre = prevalidate(academic.accessToken(), List.of(row)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(1);
        JsonNode imported = confirm(academic.accessToken(), pre.at("/batchId").asLong(), "INSERT_ONLY").at("/data");
        assertThat(imported.at("/successCount").asInt()).isEqualTo(1);
        Student inserted = studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, "00123").last("LIMIT 1"));
        assertThat(inserted).isNotNull();
        assertThat(inserted.getStudentNo()).isEqualTo("00123");

        JsonNode rolledBack = rollback(academic.accessToken(), pre.at("/batchId").asLong()).at("/data");
        assertThat(rolledBack.at("/conflictCount").asInt()).isZero();
        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, "00123"))).isZero();

        seedCertificateSnapshot("P10LEAD", COLLEGE_A, "2026", "00123", "H87654321",
                "202610588344300122", "2029/6/30");
        ResponseEntity<byte[]> exported = download("/api/exchange/export/STANDARD", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", "2026", "keyword", "00123"));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            assertThat(new DataFormatter().formatCellValue(workbook.getSheetAt(0).getRow(1).getCell(3))).isEqualTo("00123");
        }

        ExchangeStandardRow conflictRow = row("P10CONFLICT", "2026", "202610588344300123");
        conflictRow.setIdCardType("hm_travel_permit");
        conflictRow.setIdCardNo("C12345678");
        conflictRow.setBirthDate("2000/12/31");
        JsonNode conflictPre = prevalidate(academic.accessToken(), List.of(conflictRow)).at("/data");
        confirm(academic.accessToken(), conflictPre.at("/batchId").asLong(), "INSERT_ONLY");
        Student changed = studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, "P10CONFLICT").last("LIMIT 1"));
        changed.setName("后续修改");
        studentMapper.updateById(changed);
        JsonNode conflictRollback = rollback(academic.accessToken(), conflictPre.at("/batchId").asLong()).at("/data");
        assertThat(conflictRollback.at("/conflictCount").asInt()).isGreaterThan(0);
        assertThat(studentMapper.selectById(changed.getId()).getName()).isEqualTo("后续修改");
    }

    @Test
    void rollbackRestoresNullFieldsAndCompleteSnapshotsForUpdatedAggregates() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String studentNo = "P10NULL";
        String certNo = "202610588344300176";
        seedCertificateSnapshot("P10NULL", COLLEGE_A, "2026", studentNo, "N12345678",
                certNo, "2029/6/30");

        Student seededStudent = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, studentNo)
                .last("LIMIT 1"));
        TrainingProfile seededTraining = trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, seededStudent.getId())
                .eq(TrainingProfile::getAssessmentYear, "2026")
                .last("LIMIT 1"));
        Certificate seededCertificate = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, certNo)
                .last("LIMIT 1"));
        assertThat(studentMapper.update(null, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, seededStudent.getId())
                .set(Student::getSourceFull, null))).isEqualTo(1);
        assertThat(trainingProfileMapper.update(null, new LambdaUpdateWrapper<TrainingProfile>()
                .eq(TrainingProfile::getId, seededTraining.getId())
                .set(TrainingProfile::getInternalMajorName, null))).isEqualTo(1);
        assertThat(certificateMapper.update(null, new LambdaUpdateWrapper<Certificate>()
                .eq(Certificate::getId, seededCertificate.getId())
                .set(Certificate::getIssuer, null))).isEqualTo(1);

        Student beforeStudent = studentMapper.selectById(seededStudent.getId());
        TrainingProfile beforeTraining = trainingProfileMapper.selectById(seededTraining.getId());
        Certificate beforeCertificate = certificateMapper.selectById(seededCertificate.getId());
        assertThat(beforeStudent.getSourceFull()).isNull();
        assertThat(beforeTraining.getInternalMajorName()).isNull();
        assertThat(beforeCertificate.getIssuer()).isNull();

        ExchangeStandardRow overwrite = row(studentNo, "2026", certNo);
        overwrite.setIdCardType("hm_travel_permit");
        overwrite.setIdCardNo("N12345678");
        overwrite.setBirthDate("2000/12/31");
        JsonNode prevalidated = prevalidate(academic.accessToken(), List.of(overwrite)).at("/data");
        assertThat(prevalidated.at("/successCount").asInt()).isEqualTo(1);
        JsonNode imported = confirm(academic.accessToken(), prevalidated.at("/batchId").asLong(), "OVERWRITE")
                .at("/data");
        assertThat(imported.at("/successCount").asInt()).isEqualTo(1);
        assertThat(studentMapper.selectById(beforeStudent.getId()).getSourceFull()).isNotNull();
        assertThat(trainingProfileMapper.selectById(beforeTraining.getId()).getInternalMajorName()).isNotNull();
        assertThat(certificateMapper.selectById(beforeCertificate.getId()).getIssuer()).isNotNull();

        JsonNode rolledBack = rollback(academic.accessToken(), prevalidated.at("/batchId").asLong()).at("/data");
        assertThat(rolledBack.at("/rolledBackCount").asInt()).isEqualTo(3);
        assertThat(rolledBack.at("/conflictCount").asInt()).isZero();
        assertThat(rolledBack.at("/status").asText()).isEqualTo("ROLLED_BACK");

        Student restoredStudent = studentMapper.selectById(beforeStudent.getId());
        TrainingProfile restoredTraining = trainingProfileMapper.selectById(beforeTraining.getId());
        Certificate restoredCertificate = certificateMapper.selectById(beforeCertificate.getId());
        assertThat(restoredStudent.getSourceFull()).isNull();
        assertThat(restoredTraining.getInternalMajorName()).isNull();
        assertThat(restoredCertificate.getIssuer()).isNull();
        assertThat(restoredStudent).usingRecursiveComparison().isEqualTo(beforeStudent);
        assertThat(restoredTraining).usingRecursiveComparison().isEqualTo(beforeTraining);
        assertThat(restoredCertificate).usingRecursiveComparison().isEqualTo(beforeCertificate);
    }

    @Test
    void overwriteCannotRebindExistingCertificateNumberToAnotherStudent() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String certNo = "202610588344300166";
        seedCertificateSnapshot("P10BINDA", COLLEGE_A, "2026", "P10BINDA", "A87654321",
                certNo, "2029/6/30");
        Student originalStudent = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, "P10BINDA")
                .last("LIMIT 1"));
        Certificate originalCertificate = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, certNo)
                .last("LIMIT 1"));

        ExchangeStandardRow attemptedRebind = row("P10BINDB", "2026", certNo);
        attemptedRebind.setIdCardType("hm_travel_permit");
        attemptedRebind.setIdCardNo("B87654321");
        attemptedRebind.setBirthDate("2000/12/31");
        JsonNode prevalidated = prevalidate(
                academic.accessToken(), List.of(attemptedRebind)).at("/data");
        long batchId = prevalidated.at("/batchId").asLong();

        JsonNode imported = confirm(academic.accessToken(), batchId, "OVERWRITE").at("/data");

        assertThat(imported.at("/successCount").asInt()).isZero();
        assertThat(imported.at("/failCount").asInt()).isEqualTo(1);
        Certificate after = certificateMapper.selectById(originalCertificate.getId());
        assertThat(after.getStudentId()).isEqualTo(originalStudent.getId());
        assertThat(after.getAssessmentYear()).isEqualTo("2026");
        assertThat(after.getStudentNo()).isEqualTo("P10BINDA");
        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, "P10BINDB"))).isZero();
        assertThat(recordRefCount(batchId, 2)).isZero();
        assertThat(errorDetailCount(batchId, 2)).isEqualTo(1);
    }

    @Test
    void exportAndImportRespectDataScopeAndSensitivePermission() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        seedCertificateSnapshot("P10SCPA", COLLEGE_A, "2026", "P10SCPA", "A12345678",
                "202610588344400130", "2029/6/30");
        seedCertificateSnapshot("P10SCPB", COLLEGE_B, "2026", "P10SCPB", "B12345678",
                "202610588344400131", "2029/6/30");

        ResponseEntity<byte[]> clerkExport = download("/api/exchange/export/STANDARD", HttpMethod.POST,
                clerk.accessToken(), Map.of("assessmentYear", "2026"));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(clerkExport.getBody()))) {
            String sheet = workbook.getSheetAt(0).toString();
            DataFormatter formatter = new DataFormatter();
            List<String> values = new ArrayList<>();
            String maskedIdCard = null;
            String maskedBirthDate = null;
            for (int i = 1; i <= workbook.getSheetAt(0).getLastRowNum(); i++) {
                Row dataRow = workbook.getSheetAt(0).getRow(i);
                String studentNo = formatter.formatCellValue(dataRow.getCell(3));
                values.add(studentNo);
                if ("P10SCPA".equals(studentNo)) {
                    maskedIdCard = formatter.formatCellValue(dataRow.getCell(7));
                    maskedBirthDate = formatter.formatCellValue(dataRow.getCell(8));
                }
            }
            assertThat(values).contains("P10SCPA").doesNotContain("P10SCPB");
            assertThat(maskedIdCard).isNotEqualTo("A12345678").contains("*");
            assertThat(maskedBirthDate).isEqualTo("****/**/**");
            assertThat(sheet).isNotNull();
        }

        ResponseEntity<byte[]> maskedSummary = download("/api/exchange/export/CERT_SUMMARY", HttpMethod.POST,
                clerk.accessToken(), Map.of("assessmentYear", "2026"));
        assertThat(maskedSummary.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(maskedSummary.getBody()))) {
            DataFormatter formatter = new DataFormatter();
            String maskedIdCard = null;
            for (int i = 1; i <= workbook.getSheetAt(0).getLastRowNum(); i++) {
                Row dataRow = workbook.getSheetAt(0).getRow(i);
                if ("P10SCPA".equals(formatter.formatCellValue(dataRow.getCell(0)))) {
                    maskedIdCard = formatter.formatCellValue(dataRow.getCell(2));
                    break;
                }
            }
            assertThat(maskedIdCard).isNotNull();
            assertThat(maskedIdCard).isNotEqualTo("A12345678").contains("*");
        }

        ExchangeStandardRow cross = row("P10CROSS", "2026", "202610588344300132");
        cross.setInternalMajorCode("P4_NORMAL_B");
        cross.setInternalMajorName("Phase4普通师范试点专业B");
        cross.setSecondDisciplineCode("070101");
        cross.setSecondDisciplineName("数学与应用数学");
        cross.setTeachingSubject("jms_math");
        cross.setIdCardType("hm_travel_permit");
        cross.setIdCardNo("Z12345678");
        cross.setBirthDate("2000/12/31");
        cross.setRemark(String.valueOf(COLLEGE_B));
        JsonNode pre = prevalidate(auditor.accessToken(), List.of(cross)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(1);
        JsonNode imported = confirm(auditor.accessToken(), pre.at("/batchId").asLong(), "INSERT_ONLY");
        assertThat(imported.at("/code").asInt()).isEqualTo(0);
        assertThat(imported.at("/data/failCount").asInt()).isEqualTo(1);
        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, "P10CROSS"))).isZero();

        ResponseEntity<byte[]> sensitive = download("/api/exchange/export/CERT_SUMMARY", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", "2026", "keyword", "A12345678"));
        assertThat(sensitive.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sensitive.getBody()))) {
            assertThat(new DataFormatter().formatCellValue(workbook.getSheetAt(0).getRow(1).getCell(2))).isEqualTo("a12345678");
        }
        ImportExportBatch protectedScope = batchMapper.selectOne(new LambdaQueryWrapper<ImportExportBatch>()
                .eq(ImportExportBatch::getType, "export")
                .eq(ImportExportBatch::getOperatorId, userMapper.selectByUsername("test_academic_admin").getId())
                .orderByDesc(ImportExportBatch::getId)
                .last("LIMIT 1"));
        String storedKeyword = objectMapper.readTree(protectedScope.getScopeJson()).path("keyword").asText();
        assertThat(protectedScope.getScopeJson()).doesNotContain("A12345678");
        assertThat(idCardProtectionService.isEncrypted(storedKeyword)).isTrue();
        assertThat(idCardProtectionService.decrypt(storedKeyword)).isEqualTo("A12345678");
        ResponseEntity<byte[]> sensitiveStandard = download("/api/exchange/export/STANDARD", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", "2026", "keyword", "P10SCPA"));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sensitiveStandard.getBody()))) {
            DataFormatter formatter = new DataFormatter();
            assertThat(formatter.formatCellValue(workbook.getSheetAt(0).getRow(1).getCell(7)))
                    .isEqualTo("a12345678");
            assertThat(formatter.formatCellValue(workbook.getSheetAt(0).getRow(1).getCell(8)))
                    .isEqualTo("2000/12/31");
        }
    }

    @Test
    void errorExportIsExactBatchOwnerOnlyAcrossCollegesAndMasksSensitiveValues() throws Exception {
        SysUser collegeBUser = userMapper.selectByUsername("test_college_auditor");
        assertThat(collegeBUser).isNotNull();
        Long originalCollegeId = collegeBUser.getCollegeId();
        int movedToCollegeB = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, collegeBUser.getId())
                .set(SysUser::getCollegeId, COLLEGE_B));
        assertThat(movedToCollegeB).isEqualTo(1);
        assertThat(userMapper.selectById(collegeBUser.getId()).getCollegeId()).isEqualTo(COLLEGE_B);

        try {
            LoginResult collegeA = readyLogin("test_college_clerk");
            LoginResult collegeB = readyLogin("test_college_auditor");
            LoginResult school = readyLogin("test_academic_admin");
            long operatorA = userMapper.selectByUsername("test_college_clerk").getId();
            long operatorB = collegeBUser.getId();
            String batchNoA = "P10F01-A-" + System.nanoTime();
            String batchNoB = "P10F01-B-" + System.nanoTime();
            String rawIdCard = "11010119900628002X";
            String rawBirthDate = "1991/7/1";
            long batchA = seedErrorBatch(batchNoA, operatorA, COLLEGE_A,
                    "身份证件号码", rawIdCard);
            long batchB = seedErrorBatch(batchNoB, operatorB, COLLEGE_B,
                    "出生日期", rawBirthDate);

            ErrorExportRow ownA = onlyErrorRow(download("/api/exchange/export/ERROR", HttpMethod.POST,
                    collegeA.accessToken(), Map.of("batchId", batchA)).getBody());
            assertThat(ownA.batchNo()).isEqualTo(batchNoA);
            assertThat(ownA.fieldName()).isEqualTo("身份证件号码");
            assertThat(ownA.errorValue()).isNotEqualTo(rawIdCard).contains("*");

            ErrorExportRow ownB = onlyErrorRow(download("/api/exchange/export/ERROR", HttpMethod.POST,
                    collegeB.accessToken(), Map.of("batchId", batchB)).getBody());
            assertThat(ownB.batchNo()).isEqualTo(batchNoB);
            assertThat(ownB.fieldName()).isEqualTo("出生日期");
            assertThat(ownB.errorValue()).isNotEqualTo(rawBirthDate).contains("*");

            assertForbiddenErrorExport(collegeA.accessToken(), batchB);
            assertForbiddenErrorExport(collegeB.accessToken(), batchA);

            ErrorExportRow schoolA = onlyErrorRow(download("/api/exchange/export/ERROR", HttpMethod.POST,
                    school.accessToken(), Map.of("batchId", batchA)).getBody());
            ErrorExportRow schoolB = onlyErrorRow(download("/api/exchange/export/ERROR", HttpMethod.POST,
                    school.accessToken(), Map.of("batchId", batchB)).getBody());
            assertThat(schoolA.batchNo()).isEqualTo(batchNoA);
            assertThat(schoolA.errorValue()).isEqualTo(rawIdCard);
            assertThat(schoolB.batchNo()).isEqualTo(batchNoB);
            assertThat(schoolB.errorValue()).isEqualTo(rawBirthDate);

            ResponseEntity<String> missingBatch = exchange("/api/exchange/export/ERROR", HttpMethod.POST,
                    school.accessToken(), Map.of());
            assertThat(missingBatch.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode missingBatchRoot = json(missingBatch);
            assertThat(missingBatchRoot.at("/code").asInt()).isNotZero();
            assertThat(missingBatchRoot.at("/msg").asText()).contains("必须指定导入批次");
        } finally {
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, collegeBUser.getId())
                    .set(SysUser::getCollegeId, originalCollegeId));
        }
    }

    @Test
    void collegeAuditorCannotOverwriteExistingStudentOutsideWriteScope() throws Exception {
        LoginResult auditor = readyLogin("test_college_auditor");
        seedCertificateSnapshot("P10OWNB", COLLEGE_B, "2026", "P10OWNB", "B98765432",
                "202610588344300140", "2029/6/30");
        Student before = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, "P10OWNB").last("LIMIT 1"));
        assertThat(before.getCollegeId()).isEqualTo(COLLEGE_B);

        ExchangeStandardRow overwrite = row("P10OWNB", "2026", "202610588344300140");
        overwrite.setName("越权覆盖");
        overwrite.setIdCardType("hm_travel_permit");
        overwrite.setIdCardNo("O12345678");
        overwrite.setBirthDate("2000/12/31");
        overwrite.setRemark(String.valueOf(COLLEGE_A));
        JsonNode pre = prevalidate(auditor.accessToken(), List.of(overwrite)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(1);

        JsonNode imported = confirm(auditor.accessToken(), pre.at("/batchId").asLong(), "OVERWRITE");
        assertThat(imported.at("/code").asInt()).isEqualTo(0);
        assertThat(imported.at("/data/successCount").asInt()).isZero();
        assertThat(imported.at("/data/failCount").asInt()).isEqualTo(1);

        Student after = studentMapper.selectById(before.getId());
        assertThat(after.getCollegeId()).isEqualTo(COLLEGE_B);
        assertThat(after.getName()).isEqualTo(before.getName());
        assertThat(after.getIdCardNo()).isEqualTo(before.getIdCardNo());
        assertThat(after.getIdCardHmac()).isEqualTo(before.getIdCardHmac());
        Certificate certificate = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getStudentNo, "P10OWNB").last("LIMIT 1"));
        assertThat(certificate.getCollegeId()).isEqualTo(COLLEGE_B);
    }

    @Test
    void importRowsAreCommittedIndependentlyWhenLaterRowHitsDatabaseException() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        ExchangeStandardRow ok = row("P10ISOOK", "2026", "202610588344300150");
        ok.setIdCardType("hm_travel_permit");
        ok.setIdCardNo("I12345678");
        ok.setBirthDate("2000/12/31");
        String tooLongStudentNo = "P10" + "X".repeat(80);
        ExchangeStandardRow bad = row(tooLongStudentNo, "2026", "202610588344300151");
        bad.setIdCardType("hm_travel_permit");
        bad.setIdCardNo("I87654321");
        bad.setBirthDate("2000/12/31");

        JsonNode pre = prevalidate(academic.accessToken(), List.of(ok, bad)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(2);
        batchLockSqlProbe.reset();
        JsonNode imported = confirm(academic.accessToken(), pre.at("/batchId").asLong(), "INSERT_ONLY");
        assertThat(imported.at("/code").asInt()).isEqualTo(0);
        assertThat(imported.at("/data/successCount").asInt()).isEqualTo(1);
        assertThat(imported.at("/data/failCount").asInt()).isEqualTo(1);
        assertThat(imported.at("/data/status").asText()).isEqualTo("FAILED");
        assertThat(batchLockSqlProbe.snapshot())
                .as("两条逐行事务与一条错误明细事务必须实际走定长 status 行锁投影")
                .hasSize(3)
                .allMatch(STATUS_ONLY_BATCH_LOCK_SQL::equals);

        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, "P10ISOOK"))).isEqualTo(1);
        assertThat(certificateMapper.selectCount(new LambdaQueryWrapper<Certificate>().eq(Certificate::getStudentNo, "P10ISOOK"))).isEqualTo(1);
        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, tooLongStudentNo))).isZero();
    }

    @Test
    void rowBatchLockQueryUsesFixedStatusOnlyProjection() {
        String statementId = ImportExportBatchMapper.class.getName() + ".selectStatusByIdForUpdate";
        MappedStatement statement = sqlSessionFactory.getConfiguration().getMappedStatement(statementId);
        BoundSql boundSql = statement.getBoundSql(Map.of("id", 1L));
        String normalizedSql = boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);

        assertThat(normalizedSql)
                .isEqualTo(STATUS_ONLY_BATCH_LOCK_SQL)
                .doesNotContain("*", "preview_json");
        assertThat(statement.getResultMaps())
                .singleElement()
                .satisfies(resultMap -> assertThat(resultMap.getType()).isEqualTo(String.class));

        String rollbackStatementId = ImportExportBatchMapper.class.getName() + ".selectByIdForUpdate";
        String rollbackSql = sqlSessionFactory.getConfiguration().getMappedStatement(rollbackStatementId)
                .getBoundSql(Map.of("id", 1L))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        assertThat(rollbackSql)
                .contains("from import_export_batch")
                .endsWith("for update");
    }

    /**
     * Phase 42.2：两个并发 confirmImport 打同一个 PREVALIDATED 批次 → confirmImport 开头对
     * PREVALIDATED→IMPORTING 的原子认领只放行首个调用者，另一个被拒，全量导入只发生一次、无重复行。
     * 本 IT 跑在真实内嵌 Tomcat + 真实 MySQL 上，两线程经 HTTP 真并发命中，认领由 InnoDB 行锁串行化，
     * 因此这是对该竞态的真实复现（非 mock）。
     */
    @Test
    void concurrentConfirmImportClaimsBatchAtomicallyAndImportsExactlyOnce() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        ExchangeStandardRow row = row("P10RACE", "2026", "202610588344300160");
        row.setIdCardType("hm_travel_permit");
        row.setIdCardNo("R12345678");
        row.setBirthDate("2000/12/31");
        JsonNode pre = prevalidate(academic.accessToken(), List.of(row)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(1);
        long batchId = pre.at("/batchId").asLong();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<JsonNode>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                ResponseEntity<String> resp = exchange("/api/exchange/import/" + batchId + "/confirm",
                        HttpMethod.POST, academic.accessToken(), Map.of("strategy", "INSERT_ONLY"));
                return objectMapper.readTree(resp.getBody());
            }));
        }
        startGate.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int winners = 0;
        int losers = 0;
        for (Future<JsonNode> future : futures) {
            JsonNode body = future.get();
            if (body.at("/code").asInt() == 0) {
                winners++;
                assertThat(body.at("/data/status").asText()).isEqualTo("IMPORTED");
                assertThat(body.at("/data/successCount").asInt()).isEqualTo(1);
            } else {
                losers++;
                assertThat(body.at("/code").asInt()).isEqualTo(1000);
                assertThat(body.at("/msg").asText()).contains("不可确认导入");
            }
        }
        // 恰好一个认领成功、一个被原子认领拒绝（旧代码会两个都过 PREVALIDATED 检查、两个都进导入循环）
        assertThat(winners).isEqualTo(1);
        assertThat(losers).isEqualTo(1);

        // 无重复导入：学生/证书各恰好一行
        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, "P10RACE"))).isEqualTo(1);
        assertThat(certificateMapper.selectCount(new LambdaQueryWrapper<Certificate>().eq(Certificate::getStudentNo, "P10RACE"))).isEqualTo(1);

        // 批次落定为 IMPORTED，未卡在过渡态 IMPORTING
        ImportExportBatch persisted = batchMapper.selectById(batchId);
        assertThat(persisted.getStatus()).isEqualTo("IMPORTED");
    }

    /**
     * Phase 42 PG-H3：第一行独立事务已提交后暂停确认导入，让回滚先完成，再释放确认线程。
     * 回滚持有批次锁直至补偿与终态一并提交；后续行醒来只能观察到 ROLLED_BACK，不能产生晚提交。
    */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void confirmAndRollbackInterleavingCannotLeaveLateCommittedRows() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        ExchangeStandardRow first = row("P10RBAR1", "2026", "202610588344300161");
        first.setIdCardType("hm_travel_permit");
        first.setIdCardNo("B12345678");
        first.setBirthDate("2000/12/31");
        ExchangeStandardRow second = row("P10RBAR2", "2026", "202610588344300162");
        second.setIdCardType("hm_travel_permit");
        second.setIdCardNo("B87654321");
        second.setBirthDate("2000/12/31");

        JsonNode pre = prevalidate(academic.accessToken(), List.of(first, second)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(2);
        long batchId = pre.at("/batchId").asLong();
        exchangeImportHook.armAfterCommit(batchId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<JsonNode> confirmFuture = null;
        Future<JsonNode> rollbackFuture = null;
        try {
            confirmFuture = pool.submit(
                    () -> confirm(academic.accessToken(), batchId, "INSERT_ONLY"));
            assertThat(exchangeImportHook.awaitFirstCommit(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exchangeImportHook.firstCommittedRowNo()).isEqualTo(2);
            assertThat(batchMapper.selectById(batchId).getStatus()).isEqualTo("IMPORTING");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM import_record_ref
                    WHERE batch_id = ? AND row_no = ? AND deleted = 0 AND action <> 'SKIP'
                    """, Integer.class, batchId, 2)).isEqualTo(3);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM student
                    WHERE student_no = 'P10RBAR1' AND deleted = 0
                    """, Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM student
                    WHERE student_no = 'P10RBAR2' AND deleted = 0
                    """, Integer.class)).isZero();

            rollbackFuture = pool.submit(
                    () -> rollback(academic.accessToken(), batchId));
            JsonNode rolledBack = rollbackFuture.get(10, TimeUnit.SECONDS);
            assertThat(rolledBack.at("/code").asInt()).isEqualTo(0);
            assertThat(rolledBack.at("/data/status").asText()).isEqualTo("ROLLED_BACK");
            assertThat(batchMapper.selectById(batchId).getStatus()).isEqualTo("ROLLED_BACK");

            exchangeImportHook.allowNextRow();
            JsonNode confirmResult = confirmFuture.get(10, TimeUnit.SECONDS);
            assertThat(confirmResult.at("/code").asInt()).isEqualTo(1000);
            assertThat(confirmResult.at("/msg").asText())
                    .contains("导入已停止")
                    .contains("ROLLED_BACK");

            assertThat(batchMapper.selectById(batchId).getStatus()).isEqualTo("ROLLED_BACK");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM student
                    WHERE student_no IN ('P10RBAR1', 'P10RBAR2') AND deleted = 0
                    """, Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM training_profile
                    WHERE student_id IN (
                        SELECT id FROM student WHERE student_no IN ('P10RBAR1', 'P10RBAR2')
                    ) AND deleted = 0
                    """, Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM certificate
                    WHERE student_no IN ('P10RBAR1', 'P10RBAR2') AND deleted = 0
                    """, Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM import_record_ref
                    WHERE batch_id = ? AND row_no = 3 AND deleted = 0
                    """, Integer.class, batchId)).isZero();
        } finally {
            exchangeImportHook.releaseAll();
            awaitFutureQuietly(confirmFuture);
            awaitFutureQuietly(rollbackFuture);
            shutdownExecutor(pool);
        }
    }

    /**
     * Phase 42 PG-H3 反向交错：导入行已经取得 batch 锁但尚未写业务数据时发起 rollback。
     * rollback 必须等待该行事务提交，再把其完整 refs 纳入同一事务补偿，不能基于旧快照提前结束。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void rollbackWaitsForInFlightRowAndCompensatesItsCommittedRefs() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        ExchangeStandardRow row = row("P10RLOCK", "2026", "202610588344300163");
        row.setIdCardType("hm_travel_permit");
        row.setIdCardNo("L12345678");
        row.setBirthDate("2000/12/31");

        JsonNode pre = prevalidate(academic.accessToken(), List.of(row)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(1);
        long batchId = pre.at("/batchId").asLong();
        exchangeImportHook.armInFlight(batchId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<JsonNode> confirmFuture = null;
        Future<JsonNode> rollbackFuture = null;
        try {
            confirmFuture = pool.submit(
                    () -> confirm(academic.accessToken(), batchId, "INSERT_ONLY"));
            assertThat(exchangeImportHook.awaitBatchLocked(10, TimeUnit.SECONDS)).isTrue();
            assertThat(batchMapper.selectById(batchId).getStatus()).isEqualTo("IMPORTING");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM student
                    WHERE student_no = 'P10RLOCK' AND deleted = 0
                    """, Integer.class)).isZero();

            batchLockSqlProbe.armRollbackLockQuery();
            Future<JsonNode> blockedRollback = pool.submit(
                    () -> rollback(academic.accessToken(), batchId));
            rollbackFuture = blockedRollback;
            assertThat(exchangeImportHook.awaitRollbackEntered(10, TimeUnit.SECONDS)).isTrue();
            exchangeImportHook.allowRollbackLockAttempt();
            assertThat(batchLockSqlProbe.awaitRollbackLockQueryEntered(10, TimeUnit.SECONDS))
                    .as("rollback 线程必须已触达 MyBatis StatementHandler.query 执行边界")
                    .isTrue();
            assertThatThrownBy(() -> blockedRollback.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            exchangeImportHook.allowRowCommit();
            assertThat(exchangeImportHook.awaitFirstCommit(10, TimeUnit.SECONDS)).isTrue();
            JsonNode rolledBack = blockedRollback.get(10, TimeUnit.SECONDS);
            assertThat(rolledBack.at("/code").asInt()).isEqualTo(0);
            assertThat(rolledBack.at("/data/status").asText()).isEqualTo("ROLLED_BACK");
            assertThat(rolledBack.at("/data/rolledBackCount").asInt()).isEqualTo(3);

            exchangeImportHook.allowNextRow();
            JsonNode confirmResult = confirmFuture.get(10, TimeUnit.SECONDS);
            assertThat(confirmResult.at("/code").asInt()).isEqualTo(1000);
            assertThat(confirmResult.at("/msg").asText())
                    .contains("导入已停止")
                    .contains("ROLLED_BACK");

            assertThat(batchMapper.selectById(batchId).getStatus()).isEqualTo("ROLLED_BACK");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM student
                    WHERE student_no = 'P10RLOCK' AND deleted = 0
                    """, Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM training_profile
                    WHERE student_id IN (
                        SELECT id FROM student WHERE student_no = 'P10RLOCK'
                    ) AND deleted = 0
                    """, Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM certificate
                    WHERE student_no = 'P10RLOCK' AND deleted = 0
                    """, Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM import_record_ref
                    WHERE batch_id = ? AND row_no = 2 AND deleted = 0
                    """, Integer.class, batchId)).isEqualTo(3);
        } finally {
            exchangeImportHook.releaseAll();
            awaitFutureQuietly(confirmFuture);
            awaitFutureQuietly(rollbackFuture);
            shutdownExecutor(pool);
        }
    }

    /**
     * Phase 42 T-IMP-5C：失败行事务回滚后，在错误明细竞争 batch 锁之前暂停。
     * rollback 先提交终态后，错误明细事务必须因持久状态已非 IMPORTING 而停止，不能迟到落库。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void rollbackBeforeErrorDetailLockPreventsLateErrorDetail() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String tooLongStudentNo = "P10" + "E".repeat(80);
        ExchangeStandardRow bad = row(tooLongStudentNo, "2026", "202610588344300164");
        bad.setIdCardType("hm_travel_permit");
        bad.setIdCardNo("E12345678");
        bad.setBirthDate("2000/12/31");

        JsonNode pre = prevalidate(academic.accessToken(), List.of(bad)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(1);
        long batchId = pre.at("/batchId").asLong();
        exchangeImportHook.armBeforeErrorDetailLock(batchId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<JsonNode> confirmFuture = null;
        Future<JsonNode> rollbackFuture = null;
        try {
            confirmFuture = pool.submit(
                    () -> confirm(academic.accessToken(), batchId, "INSERT_ONLY"));
            assertThat(exchangeImportHook.awaitErrorDetailBeforeLock(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exchangeImportHook.errorDetailRowNo()).isEqualTo(2);
            assertThat(batchMapper.selectById(batchId).getStatus()).isEqualTo("IMPORTING");
            assertThat(errorDetailCount(batchId, 2)).isZero();
            assertThat(recordRefCount(batchId, 2)).isZero();
            assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                    .eq(Student::getStudentNo, tooLongStudentNo))).isZero();

            rollbackFuture = pool.submit(
                    () -> rollback(academic.accessToken(), batchId));
            JsonNode rolledBack = rollbackFuture.get(10, TimeUnit.SECONDS);
            assertThat(rolledBack.at("/code").asInt()).isEqualTo(0);
            assertThat(rolledBack.at("/data/status").asText()).isEqualTo("ROLLED_BACK");
            assertThat(rolledBack.at("/data/rolledBackCount").asInt()).isZero();
            assertThat(errorDetailCount(batchId, 2)).isZero();

            exchangeImportHook.allowErrorDetailLockAttempt();
            JsonNode confirmResult = confirmFuture.get(10, TimeUnit.SECONDS);
            assertThat(confirmResult.at("/code").asInt()).isEqualTo(1000);
            assertThat(confirmResult.at("/msg").asText())
                    .contains("导入已停止")
                    .contains("ROLLED_BACK");

            assertThat(batchMapper.selectById(batchId).getStatus()).isEqualTo("ROLLED_BACK");
            assertThat(errorDetailCount(batchId, 2)).isZero();
            assertThat(recordRefCount(batchId, 2)).isZero();
        } finally {
            exchangeImportHook.releaseAll();
            awaitFutureQuietly(confirmFuture);
            awaitFutureQuietly(rollbackFuture);
            shutdownExecutor(pool);
        }
    }

    /**
     * Phase 42 T-IMP-5C 反向交错：错误明细事务已持有 batch 锁时发起 rollback。
     * rollback 必须等待错误明细提交，之后再落回滚终态；已线性化的错误明细保留且 confirm 不伪报成功。
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void rollbackWaitsForLockedErrorDetailAndPreservesCommittedDetail() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        String tooLongStudentNo = "P10" + "F".repeat(80);
        ExchangeStandardRow bad = row(tooLongStudentNo, "2026", "202610588344300165");
        bad.setIdCardType("hm_travel_permit");
        bad.setIdCardNo("F12345678");
        bad.setBirthDate("2000/12/31");

        JsonNode pre = prevalidate(academic.accessToken(), List.of(bad)).at("/data");
        assertThat(pre.at("/successCount").asInt()).isEqualTo(1);
        long batchId = pre.at("/batchId").asLong();
        exchangeImportHook.armAfterErrorDetailLock(batchId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<JsonNode> confirmFuture = null;
        Future<JsonNode> rollbackFuture = null;
        try {
            confirmFuture = pool.submit(
                    () -> confirm(academic.accessToken(), batchId, "INSERT_ONLY"));
            assertThat(exchangeImportHook.awaitErrorDetailLocked(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exchangeImportHook.errorDetailRowNo()).isEqualTo(2);
            assertThat(batchMapper.selectById(batchId).getStatus()).isEqualTo("IMPORTING");
            assertThat(errorDetailCount(batchId, 2)).isZero();

            batchLockSqlProbe.armRollbackLockQuery();
            Future<JsonNode> blockedRollback = pool.submit(
                    () -> rollback(academic.accessToken(), batchId));
            rollbackFuture = blockedRollback;
            assertThat(exchangeImportHook.awaitRollbackEntered(10, TimeUnit.SECONDS)).isTrue();
            exchangeImportHook.allowRollbackLockAttempt();
            assertThat(batchLockSqlProbe.awaitRollbackLockQueryEntered(10, TimeUnit.SECONDS))
                    .as("rollback 线程必须已触达 MyBatis StatementHandler.query 执行边界")
                    .isTrue();
            assertThatThrownBy(() -> blockedRollback.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            exchangeImportHook.allowErrorDetailWrite();
            assertThat(exchangeImportHook.awaitErrorDetailWritten(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exchangeImportHook.awaitErrorDetailCommitted(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exchangeImportHook.awaitRollbackBatchLocked(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exchangeImportHook.errorDetailWrittenOrder())
                    .as("错误明细必须在事务仍持有 batch 锁时写完，rollback 才能取得同一行锁")
                    .isPositive()
                    .isLessThan(exchangeImportHook.rollbackBatchLockedOrder());
            JsonNode rolledBack = blockedRollback.get(10, TimeUnit.SECONDS);
            assertThat(rolledBack.at("/code").asInt()).isEqualTo(0);
            assertThat(rolledBack.at("/data/status").asText()).isEqualTo("ROLLED_BACK");
            assertThat(rolledBack.at("/data/rolledBackCount").asInt()).isZero();
            assertThat(errorDetailCount(batchId, 2)).isEqualTo(1);

            exchangeImportHook.allowConfirmAfterErrorDetail();
            JsonNode confirmResult = confirmFuture.get(10, TimeUnit.SECONDS);
            assertThat(confirmResult.at("/code").asInt()).isEqualTo(1000);
            assertThat(confirmResult.at("/msg").asText())
                    .contains("导入已停止")
                    .contains("ROLLED_BACK");

            assertThat(batchMapper.selectById(batchId).getStatus()).isEqualTo("ROLLED_BACK");
            assertThat(errorDetailCount(batchId, 2)).isEqualTo(1);
            assertThat(recordRefCount(batchId, 2)).isZero();
        } finally {
            exchangeImportHook.releaseAll();
            awaitFutureQuietly(confirmFuture);
            awaitFutureQuietly(rollbackFuture);
            shutdownExecutor(pool);
        }
    }

    /**
     * Phase 44e-rollout（P1-1 真分页 · variant C：Java 侧手工 operatorId 过滤下推进 wrapper）：
     * GET /api/exchange/batches 对非全校范围用户（COLLEGE_CLERK/COLLEGE_AUDITOR 的 exchange:import 均为
     * COLLEGE 域、非 allSchool——见 V8__rbac_seed.sql:150、V20__rbac_regrant.sql:71）只能看到「本人创建」
     * 的批次（按 operatorId，而非按学院）。真分页改造前是 selectList 全量 + Java
     * uid.equals(b.getOperatorId()) 后置过滤；若下推 wrapper 时有遗漏，分页拦截器生成的 COUNT/LIMIT 会先于
     * 范围过滤执行，导致 total 把「同学院其他人」甚至「全校」批次一并计入、造成跨用户数据泄露。
     * 本测试用两个真实登录的不同 operatorId 用户互相核验：任一方的 total 与分页记录都必须与另一方严格互斥。
     */
    @Test
    void batchListIsScopedToOperatorAndSupportsRealPaginationForNonAllSchoolUser() throws Exception {
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        long clerkId = userMapper.selectByUsername("test_college_clerk").getId();
        long auditorId = userMapper.selectByUsername("test_college_auditor").getId();

        long c1 = seedBatch("P10PAGE-C1-" + System.nanoTime(), clerkId, "import");
        long c2 = seedBatch("P10PAGE-C2-" + System.nanoTime(), clerkId, "import");
        long c3 = seedBatch("P10PAGE-C3-" + System.nanoTime(), clerkId, "import");
        long a1 = seedBatch("P10PAGE-A1-" + System.nanoTime(), auditorId, "import");
        long a2 = seedBatch("P10PAGE-A2-" + System.nanoTime(), auditorId, "import");

        // 学院文员：total 只计本人 3 条；审计员的 2 条既不计入 total，也不出现在任何一页。
        JsonNode clerkPage1 = json(exchange("/api/exchange/batches?type=import&page=1&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        JsonNode clerkPage2 = json(exchange("/api/exchange/batches?type=import&page=2&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(clerkPage1.at("/total").asLong()).isEqualTo(3);
        assertThat(clerkPage2.at("/total").asLong()).isEqualTo(3);
        assertThat(clerkPage1.at("/records").size()).isEqualTo(2);
        assertThat(clerkPage2.at("/records").size()).isEqualTo(1);

        List<Long> clerkIds = new ArrayList<>();
        for (JsonNode node : clerkPage1.at("/records")) {
            clerkIds.add(node.at("/id").asLong());
        }
        for (JsonNode node : clerkPage2.at("/records")) {
            clerkIds.add(node.at("/id").asLong());
        }
        assertThat(clerkIds).containsExactlyInAnyOrder(c1, c2, c3);
        assertThat(clerkIds).doesNotContain(a1, a2);

        // 反向核验：审计员只看到自己的 2 条，一条 clerk 的批次也看不到——证明过滤键是 operatorId 本人，
        // 而非学院或全校范围（两人均为 COLLEGE 域，若误按学院过滤会彼此互相看到对方批次）。
        JsonNode auditorPage = json(exchange("/api/exchange/batches?type=import&page=1&size=50",
                HttpMethod.GET, auditor.accessToken(), null)).at("/data");
        assertThat(auditorPage.at("/total").asLong()).isEqualTo(2);
        List<Long> auditorIds = new ArrayList<>();
        for (JsonNode node : auditorPage.at("/records")) {
            auditorIds.add(node.at("/id").asLong());
        }
        assertThat(auditorIds).containsExactlyInAnyOrder(a1, a2);
        assertThat(auditorIds).doesNotContain(c1, c2, c3);
    }

    private long seedBatch(String batchNo, long operatorId, String type) {
        ImportExportBatch batch = new ImportExportBatch();
        batch.setBatchNo(batchNo);
        batch.setType(type);
        batch.setFileName(batchNo + ".xlsx");
        batch.setOperatorId(operatorId);
        batch.setOperateTime(LocalDateTime.now());
        batch.setTotal(1);
        batch.setSuccessCount(1);
        batch.setFailCount(0);
        batch.setStrategy("INSERT_ONLY");
        batch.setStatus("IMPORTED");
        batch.setRemark("P10PAGE");
        batchMapper.insert(batch);
        return batch.getId();
    }

    private long seedErrorBatch(String batchNo, long operatorId, long collegeId,
                                String fieldName, String errorValue) {
        ImportExportBatch batch = new ImportExportBatch();
        batch.setBatchNo(batchNo);
        batch.setType("import");
        batch.setFileName(batchNo + ".xlsx");
        batch.setOperatorId(operatorId);
        batch.setOperateTime(LocalDateTime.now());
        batch.setTotal(1);
        batch.setSuccessCount(0);
        batch.setFailCount(1);
        batch.setScopeJson("{\"collegeId\":" + collegeId + "}");
        batch.setStatus("PREVALIDATED");
        batch.setRemark("FINAL-F01-DYN");
        batchMapper.insert(batch);

        ImportErrorDetail detail = new ImportErrorDetail();
        detail.setBatchId(batch.getId());
        detail.setBatchNo(batchNo);
        detail.setRowNo(2);
        detail.setStudentNo(batchNo + "-STUDENT");
        detail.setStudentName("F-01测试学生");
        detail.setFieldName(fieldName);
        detail.setErrorValue("身份证件号码".equals(fieldName)
                ? idCardProtectionService.encrypt(errorValue) : errorValue);
        detail.setErrorReason("F-01敏感错误值");
        detail.setSuggestion("请更正");
        errorDetailMapper.insert(detail);
        return batch.getId();
    }

    private void assertForbiddenErrorExport(String token, long batchId) throws Exception {
        ResponseEntity<String> response = exchange("/api/exchange/export/ERROR", HttpMethod.POST,
                token, Map.of("batchId", batchId));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(403);
        assertThat(root.at("/msg").asText()).contains("无权访问该批次");
    }

    private ErrorExportRow onlyErrorRow(byte[] content) throws Exception {
        assertThat(content).isNotNull();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            Row row = workbook.getSheetAt(0).getRow(1);
            DataFormatter formatter = new DataFormatter();
            return new ErrorExportRow(
                    formatter.formatCellValue(row.getCell(0)),
                    formatter.formatCellValue(row.getCell(4)),
                    formatter.formatCellValue(row.getCell(5)));
        }
    }

    private void assertWorkbookHeaderAndTextFormat(byte[] content) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            DataFormatter formatter = new DataFormatter();
            assertThat(header.getLastCellNum()).isEqualTo((short) 26);
            for (int i = 0; i < ExchangeColumn.ALL.size(); i++) {
                assertThat(formatter.formatCellValue(header.getCell(i))).isEqualTo(ExchangeColumn.ALL.get(i).header());
                CellStyle style = workbook.getSheetAt(0).getColumnStyle(i);
                assertThat(style.getDataFormatString()).isEqualTo("@");
            }
            assertThat(formatter.formatCellValue(header.getCell(7))).isEqualTo("身份证件号码");
        }
    }

    private JsonNode prevalidate(String token, List<ExchangeStandardRow> rows) throws Exception {
        byte[] workbook = excelHelper.writeStandardWorkbook(rows, null);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource("phase10.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook));
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

    private JsonNode rollback(String token, long batchId) throws Exception {
        ResponseEntity<String> response = exchange("/api/exchange/import/" + batchId + "/rollback", HttpMethod.POST, token, Map.of());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(response);
    }

    private ExchangeStandardRow row(String studentNo, String year, String certNo) {
        ExchangeStandardRow row = new ExchangeStandardRow();
        row.setSequenceNo("1");
        row.setSchoolCode("10588");
        row.setSchoolName("广东技术师范大学");
        row.setStudentNo(studentNo);
        row.setName("测试学生");
        row.setGender("female");
        row.setIdCardType("resident_id_card");
        row.setIdCardNo("44010620001231001X");
        row.setBirthDate("2000/12/31");
        row.setIdentityType("normal_student");
        row.setSourcePlace("广东省/广州市/天河区");
        row.setSecondDisciplineCode("050101");
        row.setSecondDisciplineName("汉语言文学");
        row.setInternalMajorCode("P4_NORMAL_A");
        row.setInternalMajorName("Phase4普通师范试点专业A");
        row.setEducationLevel("bachelor");
        row.setTrainingGoal("junior_middle_school_teacher");
        row.setInternshipOrgMode("school_organized");
        row.setInternshipLocation("primary_secondary_school");
        row.setTeachingSegment("junior_middle_school");
        row.setTeachingSubject("jms_chinese");
        row.setInterviewOrgMode("separate_interview");
        row.setCertNo(certNo);
        row.setValidUntil((Integer.parseInt(year) + 3) + "/6/30");
        row.setIssuer("校长");
        row.setRemark(String.valueOf(COLLEGE_A));
        return row;
    }

    private AttachmentSeed seedAttachmentOnlyStudent(String studentNo, long collegeId, String idCardNo,
                                                      String certNo, byte[] materialBytes, byte[] videoBytes) {
        seedCertificateSnapshot(studentNo, collegeId, "2026", studentNo, idCardNo, certNo, "2029/6/30");
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, studentNo).last("LIMIT 1"));
        jdbcTemplate.update("DELETE FROM certificate WHERE student_id = ?", student.getId());
        jdbcTemplate.update("DELETE FROM training_profile WHERE student_id = ?", student.getId());

        FileObject materialFile = fileService.upload(new ByteArrayInputStream(materialBytes),
                studentNo + "-material.txt", "text/plain", materialBytes.length, "process-material");
        ProcessMaterial material = new ProcessMaterial();
        material.setStudentId(student.getId());
        material.setCollegeId(collegeId);
        material.setAssessmentYear("2026");
        material.setCategory("morality_teacher_ethics");
        material.setFileId(materialFile.getId());
        material.setFileName(materialFile.getOriginalName());
        material.setFilePath(materialFile.getObjectKey());
        material.setFileSize(materialFile.getSize());
        material.setContentType(materialFile.getContentType());
        material.setUploaderId(0L);
        material.setUploadTime(LocalDateTime.now());
        material.setStatus("PASSED");
        material.setLocked(1);
        processMaterialMapper.insert(material);

        FileObject videoFile = fileService.upload(new ByteArrayInputStream(videoBytes),
                studentNo + "-video.mp4", "video/mp4", videoBytes.length, "teaching-video");
        VideoReview video = new VideoReview();
        video.setStudentId(student.getId());
        video.setCollegeId(collegeId);
        video.setAssessmentYear("2026");
        video.setVideoFileId(videoFile.getId());
        video.setVideoFileName(videoFile.getOriginalName());
        video.setDurationSeconds(900);
        video.setFormatCheck("PASS");
        video.setStatus("CONFIRMED");
        video.setFinalScore(90);
        video.setFinalConclusion("PASS");
        video.setConfirmedBy(0L);
        video.setConfirmedAt(LocalDateTime.now());
        video.setLocked(1);
        videoReviewMapper.insert(video);
        return new AttachmentSeed(student.getId(), material.getId(), video.getId(),
                materialFile.getId(), videoFile.getId());
    }

    private Map<String, byte[]> unzip(byte[] content) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        return entries;
    }

    private void seedCertificateSnapshot(String prefix, long collegeId, String year, String studentNo,
                                         String idCardNo, String certNo, String validUntil) {
        String normalizedIdCardNo = idCardNo.toLowerCase(Locale.ROOT);
        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName(prefix + "学生");
        student.setGender("female");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo(idCardProtectionService.encrypt(normalizedIdCardNo));
        student.setIdCardHmac(idCardProtectionService.hmac(normalizedIdCardNo));
        student.setBirthDate("2000/12/31");
        student.setIdentityType("normal_student");
        student.setSourceFull("广东省/广州市/天河区");
        student.setCollegeId(collegeId);
        student.setGrade("2022");
        student.setClassName("Phase10测试班");
        student.setStatus("PASSED");
        student.setLocked(1);
        studentMapper.insert(student);

        TrainingProfile training = new TrainingProfile();
        training.setStudentId(student.getId());
        training.setCollegeId(collegeId);
        training.setAssessmentYear(year);
        training.setSecondDisciplineCode(collegeId == COLLEGE_A ? "050101" : "070101");
        training.setSecondDisciplineName(collegeId == COLLEGE_A ? "汉语言文学" : "数学与应用数学");
        training.setInternalMajorCode(collegeId == COLLEGE_A ? "P4_NORMAL_A" : "P4_NORMAL_B");
        training.setInternalMajorName(collegeId == COLLEGE_A ? "Phase4普通师范试点专业A" : "Phase4普通师范试点专业B");
        training.setEducationLevel("bachelor");
        training.setTrainingGoal("junior_middle_school_teacher");
        training.setInternshipOrgMode("school_organized");
        training.setInternshipLocation("primary_secondary_school");
        training.setTeachingSegment("junior_middle_school");
        training.setTeachingSubjectId(500000000000000201L);
        training.setTeachingSubjectCode(collegeId == COLLEGE_A ? "jms_chinese" : "jms_math");
        training.setTeachingSubjectName(collegeId == COLLEGE_A ? "语文" : "数学");
        training.setInterviewOrgMode("separate_interview");
        training.setAbilityTestConclusion("qualified");
        training.setStatus("PASSED");
        training.setLocked(1);
        trainingProfileMapper.insert(training);

        Certificate certificate = new Certificate();
        certificate.setStudentId(student.getId());
        certificate.setCollegeId(collegeId);
        certificate.setAssessmentYear(year);
        certificate.setCertNo(certNo);
        certificate.setStudentNo(studentNo);
        certificate.setStudentName(student.getName());
        certificate.setIdCardType(student.getIdCardType());
        certificate.setIdCardNo(idCardProtectionService.encrypt(normalizedIdCardNo));
        certificate.setIdCardHmac(idCardProtectionService.hmac(normalizedIdCardNo));
        certificate.setEducationLevel("bachelor");
        certificate.setTrainingGoal("junior_middle_school_teacher");
        certificate.setTeachingSegment("junior_middle_school");
        certificate.setTeachingSubjectCode(training.getTeachingSubjectCode());
        certificate.setTeachingSubjectName(training.getTeachingSubjectName());
        certificate.setIssuer("校长");
        certificate.setIssueDate(year + "/6/1");
        certificate.setValidUntil(validUntil);
        certificate.setStatus("ISSUED");
        certificate.setLocked(1);
        certificateMapper.insert(certificate);
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

    private void cleanupGeneratedData() {
        jdbcTemplate.update("""
                DELETE FROM import_record_ref
                WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%' OR batch_no LIKE 'P10F01-%'
                """);
        jdbcTemplate.update("""
                DELETE FROM import_error_detail
                WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%' OR batch_no LIKE 'P10F01-%'
                """);
        jdbcTemplate.update("""
                DELETE FROM import_export_batch
                WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'
                   OR batch_no LIKE 'P10PAGE-%' OR batch_no LIKE 'P10F01-%'
                """);
        jdbcTemplate.update("DELETE FROM process_material WHERE student_id IN "
                + "(SELECT id FROM student WHERE student_no LIKE 'P10%' OR student_no = '00123')");
        jdbcTemplate.update("DELETE FROM video_review_task WHERE student_id IN "
                + "(SELECT id FROM student WHERE student_no LIKE 'P10%' OR student_no = '00123')");
        jdbcTemplate.update("DELETE FROM video_review WHERE student_id IN "
                + "(SELECT id FROM student WHERE student_no LIKE 'P10%' OR student_no = '00123')");
        jdbcTemplate.update("DELETE FROM certificate WHERE student_no LIKE 'P10%' OR student_no = '00123'");
        jdbcTemplate.update("DELETE FROM training_profile WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P10%' OR student_no = '00123')");
        jdbcTemplate.update("DELETE FROM student WHERE student_no LIKE 'P10%' OR student_no = '00123'");
    }

    private int errorDetailCount(long batchId, int rowNo) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM import_error_detail
                WHERE batch_id = ? AND row_no = ? AND deleted = 0
                """, Integer.class, batchId, rowNo);
    }

    private int recordRefCount(long batchId, int rowNo) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM import_record_ref
                WHERE batch_id = ? AND row_no = ? AND deleted = 0
                """, Integer.class, batchId, rowNo);
    }

    private void awaitFutureQuietly(Future<?> future) {
        if (future == null || future.isDone()) {
            return;
        }
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            future.cancel(true);
        } catch (ExecutionException | CancellationException ignored) {
            // 保留测试正文中的原始断言/异常。
        }
    }

    private void shutdownExecutor(ExecutorService pool) {
        pool.shutdown();
        boolean terminated;
        try {
            terminated = pool.awaitTermination(10, TimeUnit.SECONDS);
            if (!terminated) {
                pool.shutdownNow();
                terminated = pool.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            throw new AssertionError("Phase 42 交错测试线程池清理被中断", e);
        }
        assertThat(terminated).as("Phase 42 交错测试不得遗留工作线程").isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExchangeHookTestConfiguration {

        @Bean
        @Primary
        ControlledExchangeImportHook controlledExchangeImportHook() {
            return new ControlledExchangeImportHook();
        }

        @Bean
        BatchLockSqlProbe batchLockSqlProbe() {
            return new BatchLockSqlProbe();
        }
    }

    static final class ControlledExchangeImportHook extends ExchangeImportHook {
        private volatile BarrierPlan plan = BarrierPlan.disarmed();

        synchronized void reset() {
            replacePlan(BarrierPlan.disarmed());
        }

        synchronized void armAfterCommit(long batchId) {
            replacePlan(new BarrierPlan(batchId, BarrierMode.AFTER_COMMIT));
        }

        synchronized void armInFlight(long batchId) {
            replacePlan(new BarrierPlan(batchId, BarrierMode.IN_FLIGHT));
        }

        synchronized void armBeforeErrorDetailLock(long batchId) {
            replacePlan(new BarrierPlan(batchId, BarrierMode.ERROR_BEFORE_LOCK));
        }

        synchronized void armAfterErrorDetailLock(long batchId) {
            replacePlan(new BarrierPlan(batchId, BarrierMode.ERROR_AFTER_LOCK));
        }

        @Override
        public void afterBatchLocked(Long batchId, Integer rowNo) {
            BarrierPlan current = plan;
            if (!current.matches(batchId, BarrierMode.IN_FLIGHT)
                    || !current.batchLockedOnce.compareAndSet(false, true)) {
                return;
            }
            current.firstCommittedRowNo = rowNo;
            current.batchLocked.countDown();
            await(current.allowRowCommit, "等待 Phase 42 测试允许行事务提交超时");
        }

        @Override
        public void beforeRollbackLock(Long batchId) {
            BarrierPlan current = plan;
            if (!current.matches(batchId, BarrierMode.IN_FLIGHT)
                    && !current.matches(batchId, BarrierMode.ERROR_AFTER_LOCK)) {
                return;
            }
            current.rollbackEntered.countDown();
            await(current.allowRollbackLockAttempt, "等待 Phase 42 测试允许回滚竞争批次锁超时");
        }

        @Override
        public void afterRollbackBatchLocked(Long batchId) {
            BarrierPlan current = plan;
            if (!current.matches(batchId, BarrierMode.ERROR_AFTER_LOCK)
                    || !current.rollbackLockedOnce.compareAndSet(false, true)) {
                return;
            }
            current.rollbackBatchLockedOrder = current.eventSequence.incrementAndGet();
            current.rollbackBatchLocked.countDown();
        }

        @Override
        public void beforeErrorDetailLock(Long batchId, Integer rowNo) {
            BarrierPlan current = plan;
            if (!current.matches(batchId, BarrierMode.ERROR_BEFORE_LOCK)
                    || !current.errorBeforeLockOnce.compareAndSet(false, true)) {
                return;
            }
            current.errorDetailRowNo = rowNo;
            current.errorDetailBeforeLock.countDown();
            await(current.allowErrorDetailLockAttempt, "等待 Phase 42 测试允许错误明细竞争批次锁超时");
        }

        @Override
        public void afterErrorDetailBatchLocked(Long batchId, Integer rowNo) {
            BarrierPlan current = plan;
            if (!current.matches(batchId, BarrierMode.ERROR_AFTER_LOCK)
                    || !current.errorLockedOnce.compareAndSet(false, true)) {
                return;
            }
            current.errorDetailRowNo = rowNo;
            current.errorDetailLocked.countDown();
            await(current.allowErrorDetailWrite, "等待 Phase 42 测试允许错误明细写入超时");
        }

        @Override
        public void afterErrorDetailWrite(Long batchId, Integer rowNo) {
            BarrierPlan current = plan;
            if (!current.matches(batchId, BarrierMode.ERROR_AFTER_LOCK)
                    || !current.errorWrittenOnce.compareAndSet(false, true)) {
                return;
            }
            current.errorDetailWrittenOrder = current.eventSequence.incrementAndGet();
            current.errorDetailWritten.countDown();
        }

        @Override
        public void afterErrorDetailCommitted(Long batchId, Integer rowNo) {
            BarrierPlan current = plan;
            if (!current.matches(batchId, BarrierMode.ERROR_AFTER_LOCK)
                    || !current.errorCommittedOnce.compareAndSet(false, true)) {
                return;
            }
            current.errorDetailRowNo = rowNo;
            current.errorDetailCommitted.countDown();
            await(current.allowConfirmAfterErrorDetail, "等待 Phase 42 测试允许确认导入收尾超时");
        }

        @Override
        public void afterRowCommitted(Long batchId, Integer rowNo) {
            BarrierPlan current = plan;
            if (!current.matches(batchId)
                    || !current.rowCommittedOnce.compareAndSet(false, true)) {
                return;
            }
            current.firstCommittedRowNo = rowNo;
            current.firstCommit.countDown();
            await(current.allowNextRow, "等待 Phase 42 测试释放导入线程超时");
        }

        private void await(CountDownLatch latch, String timeoutMessage) {
            try {
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(timeoutMessage);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Phase 42 交错测试导入线程被中断", e);
            }
        }

        boolean awaitFirstCommit(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.firstCommit.await(timeout, unit);
        }

        boolean awaitBatchLocked(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.batchLocked.await(timeout, unit);
        }

        boolean awaitRollbackEntered(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.rollbackEntered.await(timeout, unit);
        }

        boolean awaitErrorDetailBeforeLock(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.errorDetailBeforeLock.await(timeout, unit);
        }

        boolean awaitErrorDetailLocked(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.errorDetailLocked.await(timeout, unit);
        }

        boolean awaitErrorDetailCommitted(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.errorDetailCommitted.await(timeout, unit);
        }

        boolean awaitErrorDetailWritten(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.errorDetailWritten.await(timeout, unit);
        }

        boolean awaitRollbackBatchLocked(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.rollbackBatchLocked.await(timeout, unit);
        }

        Integer firstCommittedRowNo() {
            return plan.firstCommittedRowNo;
        }

        Integer errorDetailRowNo() {
            return plan.errorDetailRowNo;
        }

        int errorDetailWrittenOrder() {
            return plan.errorDetailWrittenOrder;
        }

        int rollbackBatchLockedOrder() {
            return plan.rollbackBatchLockedOrder;
        }

        void allowRowCommit() {
            plan.allowRowCommit.countDown();
        }

        void allowRollbackLockAttempt() {
            plan.allowRollbackLockAttempt.countDown();
        }

        void allowErrorDetailLockAttempt() {
            plan.allowErrorDetailLockAttempt.countDown();
        }

        void allowErrorDetailWrite() {
            plan.allowErrorDetailWrite.countDown();
        }

        void allowConfirmAfterErrorDetail() {
            plan.allowConfirmAfterErrorDetail.countDown();
        }

        void allowNextRow() {
            plan.allowNextRow.countDown();
        }

        void releaseAll() {
            plan.releaseAll();
        }

        private void replacePlan(BarrierPlan next) {
            BarrierPlan previous = plan;
            plan = next;
            previous.releaseAll();
        }
    }

    enum BarrierMode {
        DISARMED,
        AFTER_COMMIT,
        IN_FLIGHT,
        ERROR_BEFORE_LOCK,
        ERROR_AFTER_LOCK
    }

    static final class BarrierPlan {
        private final long batchId;
        private final BarrierMode mode;
        private final AtomicBoolean batchLockedOnce = new AtomicBoolean();
        private final AtomicBoolean rowCommittedOnce = new AtomicBoolean();
        private final AtomicBoolean errorBeforeLockOnce = new AtomicBoolean();
        private final AtomicBoolean errorLockedOnce = new AtomicBoolean();
        private final AtomicBoolean errorWrittenOnce = new AtomicBoolean();
        private final AtomicBoolean errorCommittedOnce = new AtomicBoolean();
        private final AtomicBoolean rollbackLockedOnce = new AtomicBoolean();
        private final AtomicInteger eventSequence = new AtomicInteger();
        private final CountDownLatch batchLocked = new CountDownLatch(1);
        private final CountDownLatch allowRowCommit = new CountDownLatch(1);
        private final CountDownLatch rollbackEntered = new CountDownLatch(1);
        private final CountDownLatch allowRollbackLockAttempt = new CountDownLatch(1);
        private final CountDownLatch firstCommit = new CountDownLatch(1);
        private final CountDownLatch allowNextRow = new CountDownLatch(1);
        private final CountDownLatch errorDetailBeforeLock = new CountDownLatch(1);
        private final CountDownLatch allowErrorDetailLockAttempt = new CountDownLatch(1);
        private final CountDownLatch errorDetailLocked = new CountDownLatch(1);
        private final CountDownLatch allowErrorDetailWrite = new CountDownLatch(1);
        private final CountDownLatch errorDetailWritten = new CountDownLatch(1);
        private final CountDownLatch errorDetailCommitted = new CountDownLatch(1);
        private final CountDownLatch allowConfirmAfterErrorDetail = new CountDownLatch(1);
        private final CountDownLatch rollbackBatchLocked = new CountDownLatch(1);
        private volatile Integer firstCommittedRowNo;
        private volatile Integer errorDetailRowNo;
        private volatile int errorDetailWrittenOrder;
        private volatile int rollbackBatchLockedOrder;

        private BarrierPlan(long batchId, BarrierMode mode) {
            this.batchId = batchId;
            this.mode = mode;
        }

        static BarrierPlan disarmed() {
            BarrierPlan plan = new BarrierPlan(-1L, BarrierMode.DISARMED);
            plan.releaseAll();
            return plan;
        }

        boolean matches(Long currentBatchId) {
            return currentBatchId != null
                    && currentBatchId.longValue() == batchId
                    && mode != BarrierMode.DISARMED;
        }

        boolean matches(Long currentBatchId, BarrierMode expectedMode) {
            return mode == expectedMode && matches(currentBatchId);
        }

        void releaseAll() {
            allowRowCommit.countDown();
            allowRollbackLockAttempt.countDown();
            allowNextRow.countDown();
            allowErrorDetailLockAttempt.countDown();
            allowErrorDetailWrite.countDown();
            allowConfirmAfterErrorDetail.countDown();
        }
    }

    @Intercepts({
            @Signature(
                    type = StatementHandler.class,
                    method = "prepare",
                    args = {Connection.class, Integer.class}
            ),
            @Signature(
                    type = StatementHandler.class,
                    method = "query",
                    args = {Statement.class, ResultHandler.class}
            )
    })
    static final class BatchLockSqlProbe implements Interceptor {
        private final ConcurrentLinkedQueue<String> batchLockSql = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean observeRollbackLockQuery = new AtomicBoolean();
        private volatile CountDownLatch rollbackLockQueryEntered = new CountDownLatch(1);

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            String normalizedSql = statementHandler.getBoundSql()
                    .getSql()
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if ("prepare".equals(invocation.getMethod().getName())
                    && normalizedSql.contains("from import_export_batch")
                    && normalizedSql.endsWith("for update")) {
                batchLockSql.add(normalizedSql);
            }
            if ("query".equals(invocation.getMethod().getName())
                    && !STATUS_ONLY_BATCH_LOCK_SQL.equals(normalizedSql)
                    && normalizedSql.contains("from import_export_batch")
                    && normalizedSql.endsWith("for update")
                    && observeRollbackLockQuery.compareAndSet(true, false)) {
                rollbackLockQueryEntered.countDown();
            }
            return invocation.proceed();
        }

        void reset() {
            batchLockSql.clear();
            observeRollbackLockQuery.set(false);
            rollbackLockQueryEntered = new CountDownLatch(1);
        }

        void armRollbackLockQuery() {
            rollbackLockQueryEntered = new CountDownLatch(1);
            observeRollbackLockQuery.set(true);
        }

        boolean awaitRollbackLockQueryEntered(long timeout, TimeUnit unit) throws InterruptedException {
            return rollbackLockQueryEntered.await(timeout, unit);
        }

        List<String> snapshot() {
            return List.copyOf(batchLockSql);
        }
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }

    private record ErrorExportRow(String batchNo, String fieldName, String errorValue) {
    }

    private record AttachmentSeed(long studentId, long materialId, long videoReviewId,
                                  long materialFileId, long videoFileId) {
    }
}
