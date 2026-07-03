package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.exchange.entity.ImportExportBatch;
import cn.edu.gpnu.platform.exchange.mapper.ImportExportBatchMapper;
import cn.edu.gpnu.platform.exchange.model.ExchangeColumn;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    private TrainingProfileMapper trainingProfileMapper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private ImportExportBatchMapper batchMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void resetData() {
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

        ResponseEntity<byte[]> exported = download("/api/exchange/export/STANDARD", HttpMethod.POST,
                academic.accessToken(), Map.of("assessmentYear", "2026", "keyword", "00123"));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.getBody()))) {
            assertThat(new DataFormatter().formatCellValue(workbook.getSheetAt(0).getRow(1).getCell(3))).isEqualTo("00123");
        }

        JsonNode rolledBack = rollback(academic.accessToken(), pre.at("/batchId").asLong()).at("/data");
        assertThat(rolledBack.at("/conflictCount").asInt()).isZero();
        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, "00123"))).isZero();

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
            for (int i = 1; i <= workbook.getSheetAt(0).getLastRowNum(); i++) {
                values.add(formatter.formatCellValue(workbook.getSheetAt(0).getRow(i).getCell(3)));
            }
            assertThat(values).contains("P10SCPA").doesNotContain("P10SCPB");
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
                academic.accessToken(), Map.of("assessmentYear", "2026", "keyword", "P10SCPA"));
        assertThat(sensitive.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sensitive.getBody()))) {
            assertThat(new DataFormatter().formatCellValue(workbook.getSheetAt(0).getRow(1).getCell(2))).isEqualTo("A12345678");
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
        assertThat(after.getIdCardNo()).isEqualTo("B98765432");
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
        JsonNode imported = confirm(academic.accessToken(), pre.at("/batchId").asLong(), "INSERT_ONLY");
        assertThat(imported.at("/code").asInt()).isEqualTo(0);
        assertThat(imported.at("/data/successCount").asInt()).isEqualTo(1);
        assertThat(imported.at("/data/failCount").asInt()).isEqualTo(1);
        assertThat(imported.at("/data/status").asText()).isEqualTo("FAILED");

        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, "P10ISOOK"))).isEqualTo(1);
        assertThat(certificateMapper.selectCount(new LambdaQueryWrapper<Certificate>().eq(Certificate::getStudentNo, "P10ISOOK"))).isEqualTo(1);
        assertThat(studentMapper.selectCount(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, tooLongStudentNo))).isZero();
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

    private void seedCertificateSnapshot(String prefix, long collegeId, String year, String studentNo,
                                         String idCardNo, String certNo, String validUntil) {
        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName(prefix + "学生");
        student.setGender("female");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo(idCardNo);
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
        certificate.setIdCardNo(idCardNo);
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
        jdbcTemplate.update("DELETE FROM import_record_ref WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'");
        jdbcTemplate.update("DELETE FROM import_error_detail WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'");
        jdbcTemplate.update("DELETE FROM import_export_batch WHERE batch_no LIKE 'IMP-%' OR batch_no LIKE 'EXP-%'");
        jdbcTemplate.update("DELETE FROM certificate WHERE student_no LIKE 'P10%' OR student_no = '00123'");
        jdbcTemplate.update("DELETE FROM training_profile WHERE student_id IN (SELECT id FROM student WHERE student_no LIKE 'P10%' OR student_no = '00123')");
        jdbcTemplate.update("DELETE FROM student WHERE student_no LIKE 'P10%' OR student_no = '00123'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
