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
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.BackupRecordMapper;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30",
        "platform.audit.trusted-proxies="
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
    private static final long ACADEMIC_ID = 800000000000003006L;
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
    private org.springframework.cache.CacheManager cacheManager;

    @Autowired
    private SysAuditLogMapper auditLogMapper;

    @Autowired
    private BackupRecordMapper backupRecordMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private IdCardProtectionService idCardProtectionService;

    @Autowired
    private TrainingProfileMapper trainingProfileMapper;

    @Autowired
    private ExemptionRequestMapper exemptionRequestMapper;

    @Autowired
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private VideoReviewMapper reviewMapper;

    @Autowired
    private VideoReviewTaskMapper taskMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditLogService auditLogService;

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
        resetUser("test_sys_admin", true);
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
    void auditFailureRollsBackSystemParamUpdateInSameMysqlTransaction() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        SysParam parameter = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, "video.diffThreshold")
                .last("LIMIT 1"));
        assertThat(parameter).isNotNull();
        Map<String, Object> before = jdbcTemplate.queryForMap(
                "SELECT * FROM sys_param WHERE id = ?", parameter.getId());
        long lastAuditId = latestAuditId();

        doThrow(new IllegalStateException("forced generic audit failure"))
                .when(auditLogService)
                .record(argThat((SysAuditLog entry) -> "systemParam".equals(entry.getBizType())
                        && "update".equals(entry.getOperation())));

        ResponseEntity<String> failed = exchange("/api/system/param/" + parameter.getId(), HttpMethod.PUT,
                academic.accessToken(), Map.of("paramValue", "8"));

        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(json(failed).at("/code").asInt()).isNotEqualTo(0);
        verify(auditLogService, times(1)).record(argThat((SysAuditLog entry) ->
                "systemParam".equals(entry.getBizType()) && "update".equals(entry.getOperation())));
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM sys_param WHERE id = ?", parameter.getId()))
                .as("默认 @AuditLog 写入失败时，业务参数更新必须由真实 MySQL 同事务回滚")
                .isEqualTo(before);
        assertThat(auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .gt(SysAuditLog::getId, lastAuditId)
                .eq(SysAuditLog::getBizType, "systemParam")
                .eq(SysAuditLog::getOperation, "update")))
                .as("审计失败不得留下成功样式日志")
                .isZero();
    }

    @Test
    void untrustedAndOverlongForwardedHeadersCannotSpoofPersistedAuditIp() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");
        SysParam parameter = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, "video.diffThreshold")
                .last("LIMIT 1"));
        assertThat(parameter).isNotNull();
        long lastAuditId = latestAuditId();

        ResponseEntity<String> updated = exchangeWithForwardedHeaders(
                "/api/system/param/" + parameter.getId(), HttpMethod.PUT, academic.accessToken(),
                Map.of("paramValue", "8"), "198.51.100.19", "198.51.100.20");
        assertOk(updated);

        SysAuditLog log = auditLogMapper.selectOne(new LambdaQueryWrapper<SysAuditLog>()
                .gt(SysAuditLog::getId, lastAuditId)
                .eq(SysAuditLog::getBizType, "systemParam")
                .eq(SysAuditLog::getOperation, "update")
                .orderByDesc(SysAuditLog::getId)
                .last("LIMIT 1"));
        assertThat(log).isNotNull();
        assertThat(log.getIp())
                .as("未列入 trusted-proxies 的直连请求必须忽略客户端伪造的转发头")
                .isEqualTo("127.0.0.1")
                .isNotEqualTo("198.51.100.19")
                .isNotEqualTo("198.51.100.20");

        long forgedAuditId = log.getId();
        ResponseEntity<String> overlong = exchangeWithForwardedHeaders(
                "/api/system/param/" + parameter.getId(), HttpMethod.PUT, academic.accessToken(),
                Map.of("paramValue", "9"), "198.51.100.19," + "9".repeat(600), "198.51.100.20");
        assertOk(overlong);
        SysAuditLog overlongLog = auditLogMapper.selectOne(new LambdaQueryWrapper<SysAuditLog>()
                .gt(SysAuditLog::getId, forgedAuditId)
                .eq(SysAuditLog::getBizType, "systemParam")
                .eq(SysAuditLog::getOperation, "update")
                .orderByDesc(SysAuditLog::getId)
                .last("LIMIT 1"));
        assertThat(overlongLog).isNotNull();
        assertThat(overlongLog.getIp())
                .as("超长转发头不得进入审计 IP 字段")
                .isEqualTo("127.0.0.1")
                .hasSizeLessThanOrEqualTo(45);
    }

    @Test
    void majorReviewFlowsWriteRichAuditAndCanBeQueriedByStudent() throws Exception {
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult academic = readyLogin("test_academic_admin");
        long studentId = seedStudent("P13AUD-" + System.nanoTime(), "SECOND_REVIEW");
        long trainingId = seedTraining(studentId, "SECOND_REVIEW");
        long exemptionId = seedExemption(studentId, "SECOND_REVIEW");
        long certificateId = seedCertificate(studentId, "ISSUED");

        assertOk(exchange("/api/student/" + studentId + "/second-review", HttpMethod.POST,
                auditor.accessToken(), Map.of("action", "REJECT", "comment", "P13学生复审退回")));
        assertOk(exchange("/api/training/" + trainingId + "/second-review", HttpMethod.POST,
                auditor.accessToken(), Map.of("action", "REJECT", "comment", "P13培养复审退回")));
        assertOk(exchange("/api/exemption/" + exemptionId + "/second-review", HttpMethod.POST,
                auditor.accessToken(), Map.of("action", "REJECT", "comment", "P13免考复审退回")));
        assertOk(exchange("/api/cert/" + certificateId + "/void", HttpMethod.POST,
                academic.accessToken(), Map.of("reason", "P13证书作废原因")));

        assertRichAudit("student", studentId, "secondReview", "SECOND_REVIEW", "SECOND_REJECTED",
                "P13学生复审退回", AUDITOR_ID);
        assertRichAudit("training", trainingId, "secondReview", "SECOND_REVIEW", "SECOND_REJECTED",
                "P13培养复审退回", AUDITOR_ID);
        assertRichAudit("exemption", exemptionId, "secondReview", "SECOND_REVIEW", "SECOND_REJECTED",
                "P13免考复审退回", AUDITOR_ID);
        assertRichAudit("cert", certificateId, "void", "ISSUED", "VOIDED",
                "P13证书作废原因", ACADEMIC_ID);

        JsonNode records = json(exchange("/api/audit/log?studentId=" + studentId + "&keyword=P13",
                HttpMethod.GET, academic.accessToken(), null)).at("/data/records");
        String text = records.toString();
        assertThat(text).contains("P13学生复审退回")
                .contains("P13培养复审退回")
                .contains("P13免考复审退回")
                .contains("P13证书作废原因")
                .contains("SECOND_REVIEW")
                .contains("SECOND_REJECTED");
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

    /**
     * Phase 44e-contract（P1-1 真分页样例 · 非数据范围系统列表）：系统参数为种子参考数据、条数远超页大小，
     * 用于证明 selectPage 真分页——① 页大小生效（records==size，非全表）；② total 为全量且跨页稳定；
     * ③ 页间记录不重叠；④ 客户端传超大 size 被钳制（MAX_SIZE=200），不退化为全表。
     */
    @Test
    void paramListSupportsRealServerSidePagination() throws Exception {
        LoginResult academic = readyLogin("test_academic_admin");

        JsonNode page1 = json(exchange("/api/system/param?page=1&size=2",
                HttpMethod.GET, academic.accessToken(), null)).at("/data");
        long total = page1.at("/total").asLong();
        assertThat(total).isGreaterThan(2);                       // 旧全表口径 records==total；真分页 records<total
        assertThat(page1.at("/records").size()).isEqualTo(2);     // 页大小生效

        JsonNode page2 = json(exchange("/api/system/param?page=2&size=2",
                HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(page2.at("/total").asLong()).isEqualTo(total); // total 跨页稳定
        assertThat(page2.at("/records").size()).isBetween(1, 2);
        assertThat(page1.at("/records/0/id").asLong())
                .isNotEqualTo(page2.at("/records/0/id").asLong()); // 页间不重叠

        JsonNode clamped = json(exchange("/api/system/param?page=1&size=100000",
                HttpMethod.GET, academic.accessToken(), null)).at("/data");
        assertThat(clamped.at("/records").size()).isLessThanOrEqualTo(200); // size 上限钳制
    }

    /**
     * Phase 44e-rollout（P1-1 真分页 · variant B：无 @DataScope 的运维列表）：GET /api/system/backup
     * 由全表 selectList 改为 selectPage。备份记录无学院/学生归属，故无需像 exchange.batches(variant C)/
     * auditLogs(variant D) 那样验证数据范围隔离；本测试聚焦①页大小生效；②total 跨页稳定且不等于
     * "当页命中数"（证明不是旧全表口径）；③页间记录不重叠；④status 过滤在真分页下依旧精确生效
     * （不会因为分页拦截器介入而被漏掉）。system:backup 仅 SYS_ADMIN 角色在 SYSTEM 域拥有
     * （见 V8__rbac_seed.sql:223/281），故用 test_sys_admin 登录，而非本文件其他测试常用的
     * test_academic_admin（该账号没有此权限，会 403）。
     */
    @Test
    void backupListSupportsRealServerSidePaginationAndStatusFilter() throws Exception {
        LoginResult sysAdmin = readyLogin("test_sys_admin");
        String tag = "P13BACKUP-" + System.nanoTime();
        for (int i = 0; i < 5; i++) {
            seedBackup("COMPLETED", tag);
        }
        seedBackup("FAILED", tag);

        JsonNode page1 = json(exchange("/api/system/backup?status=COMPLETED&page=1&size=2",
                HttpMethod.GET, sysAdmin.accessToken(), null)).at("/data");
        long total = page1.at("/total").asLong();
        assertThat(total).isGreaterThanOrEqualTo(5);               // 至少本测试种下的 5 条 COMPLETED
        assertThat(page1.at("/records").size()).isEqualTo(2);      // 页大小生效，非全表
        for (JsonNode record : page1.at("/records")) {
            assertThat(record.at("/status").asText()).isEqualTo("COMPLETED");
        }

        JsonNode page2 = json(exchange("/api/system/backup?status=COMPLETED&page=2&size=2",
                HttpMethod.GET, sysAdmin.accessToken(), null)).at("/data");
        assertThat(page2.at("/total").asLong()).isEqualTo(total);  // total 跨页稳定
        assertThat(page1.at("/records/0/id").asLong())
                .isNotEqualTo(page2.at("/records/0/id").asLong()); // 页间不重叠

        JsonNode failedOnly = json(exchange("/api/system/backup?status=FAILED&page=1&size=50",
                HttpMethod.GET, sysAdmin.accessToken(), null)).at("/data");
        assertThat(failedOnly.at("/total").asLong()).isGreaterThanOrEqualTo(1);
        for (JsonNode record : failedOnly.at("/records")) {
            assertThat(record.at("/status").asText()).isEqualTo("FAILED"); // status 过滤在真分页下仍精确
        }

        JsonNode noMatch = json(exchange("/api/system/backup?status=NO_SUCH_STATUS&page=1&size=10",
                HttpMethod.GET, sysAdmin.accessToken(), null)).at("/data");
        assertThat(noMatch.at("/total").asLong()).isEqualTo(0);
        assertThat(noMatch.at("/records").size()).isEqualTo(0);
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

    /**
     * Phase 44e-rollout（P1-1 真分页 · variant D：手写 @Select mapper + IPage 首参数）：
     * GET /api/audit/log 原以 LIMIT 500 兜底防止无上限拉全表；rollout 后交给 PaginationInnerInterceptor
     * 生成 COUNT + LIMIT/OFFSET。selectLogs 是本次 rollout 12 个端点里唯一的手写 SQL（非 MyBatis-Plus
     * wrapper 自动生成），最需要单独验证"学院范围过滤"与"分页 COUNT/LIMIT"是否同步生效——
     * 如果范围条件只在数据查询里生效、COUNT 未同步套用同一 WHERE，会出现 total 把其他学院记录一并计入
     * （即"范围过滤晚于分页"，造成跨学院数据/总数泄露）；如果范围条件两处都生效但分页逻辑本身出错，
     * 会出现跨页重复或遗漏。本测试用 COLLEGE_A 种 5 条、COLLEGE_B 种 1 条同关键字审计日志，
     * 学院文员（COLLEGE 域，见 auditQueryRespectsCollegeScopeAndSupportsBusinessRecordCollege 的既有假设）
     * 分页查询：total 必须精确等于 5（而非 6），且三页记录合计精确覆盖这 5 条、互不重叠。
     */
    @Test
    void auditLogListSupportsRealServerSidePaginationWithinCollegeScope() throws Exception {
        LoginResult clerk = readyLogin("test_college_clerk");
        String tag = "P13分页审计" + System.nanoTime();
        for (int i = 0; i < 5; i++) {
            long materialId = seedMaterial(COLLEGE_A, "DRAFT", tag + "-A" + i + ".pdf");
            seedAudit("material", materialId, tag + "-A" + i, null);
        }
        long otherMaterialId = seedMaterial(COLLEGE_B, "DRAFT", tag + "-B.pdf");
        seedAudit("material", otherMaterialId, tag + "-B", COLLEGE_B);

        JsonNode page1 = json(exchange("/api/audit/log?bizType=material&keyword=" + tag + "&page=1&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page1.at("/total").asLong()).isEqualTo(5);        // 精确 5 条，COLLEGE_B 的 1 条未计入 COUNT
        assertThat(page1.at("/records").size()).isEqualTo(2);        // 页大小生效
        assertThat(page1.toString()).doesNotContain(tag + "-B");

        JsonNode page2 = json(exchange("/api/audit/log?bizType=material&keyword=" + tag + "&page=2&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page2.at("/total").asLong()).isEqualTo(5);        // total 跨页稳定
        assertThat(page2.at("/records").size()).isEqualTo(2);
        assertThat(page2.toString()).doesNotContain(tag + "-B");
        assertThat(page1.at("/records/0/id").asLong()).isNotEqualTo(page2.at("/records/0/id").asLong());
        assertThat(page1.at("/records/1/id").asLong()).isNotEqualTo(page2.at("/records/0/id").asLong());
        assertThat(page1.at("/records/0/id").asLong()).isNotEqualTo(page2.at("/records/1/id").asLong());
        assertThat(page1.at("/records/1/id").asLong()).isNotEqualTo(page2.at("/records/1/id").asLong());

        JsonNode page3 = json(exchange("/api/audit/log?bizType=material&keyword=" + tag + "&page=3&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page3.at("/total").asLong()).isEqualTo(5);
        assertThat(page3.at("/records").size()).isEqualTo(1);        // 5 条/每页 2 条 → 第 3 页剩 1 条
        assertThat(page3.toString()).doesNotContain(tag + "-B");
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

    private long seedBackup(String status, String remark) {
        BackupRecord backup = new BackupRecord();
        backup.setBackupType("mysql");
        backup.setStatus(status);
        backup.setScope("P13");
        backup.setStartedAt(LocalDateTime.now());
        backup.setFinishedAt(LocalDateTime.now());
        backup.setRemark(remark);
        backupRecordMapper.insert(backup);
        return backup.getId();
    }

    private void assertRichAudit(String bizType, long bizId, String operation, String oldStatus, String newStatus,
                                 String comment, long operatorId) {
        SysAuditLog log = auditLogMapper.selectOne(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, bizType)
                .eq(SysAuditLog::getBizId, bizId)
                .eq(SysAuditLog::getOperation, operation)
                .eq(SysAuditLog::getOldStatus, oldStatus)
                .eq(SysAuditLog::getNewStatus, newStatus)
                .eq(SysAuditLog::getComment, comment)
                .last("LIMIT 1"));
        assertThat(log).isNotNull();
        assertThat(log.getOperatorId()).isEqualTo(operatorId);
        assertThat(log.getIp()).isNotBlank();
        assertThat(log.getTarget()).contains(String.valueOf(bizId));
    }

    private long seedStudent(String studentNo, String status) {
        String idCardNo = "H" + Math.floorMod(System.nanoTime(), 100000000);
        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName("P13审计学生");
        student.setGender("M");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo(idCardProtectionService.encrypt(idCardNo));
        student.setIdCardHmac(idCardProtectionService.hmac(idCardNo));
        student.setBirthDate("2001/1/2");
        student.setIdentityType("normal_student");
        student.setCollegeId(COLLEGE_A);
        student.setGrade("2026");
        student.setClassName("P13审计班");
        student.setStatus(status);
        student.setLocked(0);
        studentMapper.insert(student);
        return student.getId();
    }

    private long seedTraining(long studentId, String status) {
        TrainingProfile training = new TrainingProfile();
        training.setStudentId(studentId);
        training.setCollegeId(COLLEGE_A);
        training.setAssessmentYear(YEAR);
        training.setSecondDisciplineCode("050101");
        training.setSecondDisciplineName("汉语言文学");
        training.setInternalMajorCode("P13AUD");
        training.setInternalMajorName("P13审计专业");
        training.setEducationLevel("undergraduate");
        training.setTrainingGoal("normal_education");
        training.setInternshipOrgMode("school_unified");
        training.setInternshipLocation("primary_secondary_school");
        training.setTeachingSegment("senior_middle_school");
        training.setTeachingSubjectId(0L);
        training.setTeachingSubjectCode("jms_chinese");
        training.setTeachingSubjectName("语文");
        training.setInterviewOrgMode("school_unified");
        training.setAbilityTestConclusion("qualified");
        training.setStatus(status);
        training.setLocked(0);
        trainingProfileMapper.insert(training);
        return training.getId();
    }

    private long seedExemption(long studentId, String status) {
        ExemptionRequest exemption = new ExemptionRequest();
        exemption.setStudentId(studentId);
        exemption.setCollegeId(COLLEGE_A);
        exemption.setAssessmentYear(YEAR);
        exemption.setTeachingSegment("senior_middle_school");
        exemption.setSubject("subject_a");
        exemption.setSubjectLabel("P13免考科目");
        exemption.setBasis("basis_a");
        exemption.setBasisLabel("P13免考依据");
        exemption.setSecondReviewStatus("PENDING");
        exemption.setFinalStatus(status);
        exemption.setIncludedInExam(1);
        exemption.setLocked(0);
        exemptionRequestMapper.insert(exemption);
        return exemption.getId();
    }

    private long seedCertificate(long studentId, String status) {
        String idCardNo = "H" + Math.floorMod(System.nanoTime(), 100000000);
        Certificate certificate = new Certificate();
        certificate.setStudentId(studentId);
        certificate.setCollegeId(COLLEGE_A);
        certificate.setAssessmentYear(YEAR);
        certificate.setCertNo("2099105883444" + String.format("%05d", Math.floorMod(System.nanoTime(), 100000)));
        certificate.setStudentNo("P13CERT-" + studentId);
        certificate.setStudentName("P13审计学生");
        certificate.setIdCardType("hm_travel_permit");
        certificate.setIdCardNo(idCardProtectionService.encrypt(idCardNo));
        certificate.setIdCardHmac(idCardProtectionService.hmac(idCardNo));
        certificate.setEducationLevel("undergraduate");
        certificate.setTrainingGoal("normal_education");
        certificate.setTeachingSegment("senior_middle_school");
        certificate.setTeachingSubjectCode("jms_chinese");
        certificate.setTeachingSubjectName("语文");
        certificate.setIssuer("广东技术师范大学");
        certificate.setIssueDate("2099/06/01");
        certificate.setValidUntil("2102/06/30");
        certificate.setStatus(status);
        certificate.setLocked(1);
        certificateMapper.insert(certificate);
        return certificate.getId();
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

    private ResponseEntity<String> exchangeWithForwardedHeaders(String path, HttpMethod method,
                                                                 String accessToken, Object body,
                                                                 String forwardedFor, String realIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.add("X-Forwarded-For", forwardedFor);
        headers.add("X-Real-IP", realIp);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private long latestAuditId() {
        Long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM audit_log", Long.class);
        return id == null ? 0L : id;
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
            // Phase 44c（§7.3）：测试越过 service 直接改库，须与生产 updateParam 一样逐出参数缓存，否则经 ParamService 读到旧值。
            org.springframework.cache.Cache paramCache = cacheManager.getCache("sysParam");
            if (paramCache != null) {
                paramCache.clear();
            }
        }
    }

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE target LIKE 'P13%' OR comment LIKE 'P13%' OR comment = '复审退回原因'");
        jdbcTemplate.update("DELETE FROM certificate WHERE assessment_year = ?", YEAR);
        jdbcTemplate.update("DELETE FROM exemption_request WHERE assessment_year = ?", YEAR);
        jdbcTemplate.update("DELETE FROM training_profile WHERE assessment_year = ?", YEAR);
        jdbcTemplate.update("DELETE FROM video_review_task WHERE video_review_id IN (SELECT id FROM video_review WHERE assessment_year = ?)", YEAR);
        jdbcTemplate.update("DELETE FROM video_review WHERE assessment_year = ?", YEAR);
        jdbcTemplate.update("DELETE FROM process_material WHERE assessment_year = ?", YEAR);
        jdbcTemplate.update("DELETE FROM student WHERE student_no LIKE 'P13AUD-%'");
        jdbcTemplate.update("DELETE FROM backup_record WHERE remark LIKE 'P13%' OR scope LIKE 'P13%'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
