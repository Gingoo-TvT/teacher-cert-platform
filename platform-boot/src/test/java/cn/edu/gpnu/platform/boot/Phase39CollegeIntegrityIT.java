package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.student.dto.StudentSaveRequest;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.student.service.StudentService;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.entity.ImportExportBatch;
import cn.edu.gpnu.platform.exchange.entity.ImportRecordRef;
import cn.edu.gpnu.platform.exchange.mapper.ImportExportBatchMapper;
import cn.edu.gpnu.platform.exchange.mapper.ImportRecordRefMapper;
import cn.edu.gpnu.platform.exchange.service.ExchangeService;
import cn.edu.gpnu.platform.exchange.vo.RollbackResultVO;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.security.service.SecurityAdminService;
import cn.edu.gpnu.platform.system.config.CacheConfig;
import cn.edu.gpnu.platform.system.dto.MajorSaveRequest;
import cn.edu.gpnu.platform.system.dto.UserSaveRequest;
import cn.edu.gpnu.platform.system.entity.SysCollege;
import cn.edu.gpnu.platform.system.entity.SysRole;
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.service.CollegeParentGuard;
import cn.edu.gpnu.platform.system.service.CollegeParentLockHook;
import cn.edu.gpnu.platform.system.service.OrganizationService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 39 / PG-H1：在项目刻意不设数据库外键的前提下，验证学院删除与三类子记录新增
 * 共用同一父行锁，不会在并发交错后留下指向已删学院的静默孤儿。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.initial-password=ChangeMe123!"
})
@Import(Phase39CollegeIntegrityIT.CollegeLockHookTestConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class Phase39CollegeIntegrityIT {

    private static final String FIXTURE_PREFIX = "P39CI";
    private static final String STATUS_ONLY_COLLEGE_LOCK_SQL =
            "select status from sys_college where id = ? and deleted = 0 for update";
    private static final long SYS_ADMIN_USER_ID = 800000000000003008L;
    private static final AtomicLong FIXTURE_SEQUENCE =
            new AtomicLong(Math.floorMod(System.nanoTime(), 10_000_000L));

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private SecurityAdminService securityAdminService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private IdCardProtectionService idCardProtectionService;

    @Autowired
    private TrainingProfileMapper trainingProfileMapper;

    @Autowired
    private CertificateMapper certificateMapper;

    @Autowired
    private ImportExportBatchMapper batchMapper;

    @Autowired
    private ImportRecordRefMapper recordRefMapper;

    @Autowired
    private ExchangeService exchangeService;

    @Autowired
    private SysCollegeMapper collegeMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ParamService paramService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ControlledCollegeParentLockHook lockHook;

    @Autowired
    private CollegeLockSqlProbe collegeLockSqlProbe;

    @Autowired
    private LockOrderTrace lockOrderTrace;

    @Autowired
    private ObjectMapper objectMapper;

    private String originalAutoCreateAccount;
    private Long staffRoleId;

    @BeforeEach
    void setUp() {
        lockHook.reset();
        collegeLockSqlProbe.reset();
        lockOrderTrace.reset();
        cleanupFixtures();
        originalAutoCreateAccount = jdbcTemplate.queryForObject(
                "SELECT param_value FROM sys_param WHERE param_key = 'student.autoCreateAccount' AND deleted = 0",
                String.class);
        jdbcTemplate.update("""
                UPDATE sys_param
                   SET param_value = 'false',
                       updated_at = CURRENT_TIMESTAMP
                 WHERE param_key = 'student.autoCreateAccount'
                   AND deleted = 0
                """);
        clearParamCache();
        assertThat(paramService.getBoolean("student.autoCreateAccount", true))
                .as("Phase 39 学生反例必须显式关闭自动开户")
                .isFalse();

        SysRole staffRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, "COLLEGE_CLERK")
                .eq(SysRole::getStatus, 1)
                .last("LIMIT 1"));
        assertThat(staffRole).as("V8 应提供可由 SYS_ADMIN 授予的 STAFF 角色").isNotNull();
        staffRoleId = staffRole.getId();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ? AND status = 'ENABLED' AND deleted = 0",
                Long.class, SYS_ADMIN_USER_ID)).isEqualTo(1L);
    }

    @AfterEach
    void tearDown() {
        lockHook.releaseAll();
        collegeLockSqlProbe.reset();
        lockOrderTrace.reset();
        cleanupFixtures();
        if (originalAutoCreateAccount != null) {
            jdbcTemplate.update("""
                    UPDATE sys_param
                       SET param_value = ?,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE param_key = 'student.autoCreateAccount'
                       AND deleted = 0
                    """, originalAutoCreateAccount);
        }
        clearParamCache();
        DataScopeContext.clear();
        UserContext.clear();
        lockHook.reset();
        collegeLockSqlProbe.reset();
        lockOrderTrace.reset();
    }

    @Test
    @Timeout(120)
    void majorCreateWinsThenCollegeDeleteIsRejected() throws Exception {
        assertCollegeLockSqlContract();
        assertCreateWins(ChildKind.MAJOR);
    }

    @Test
    @Timeout(120)
    void collegeDeleteWinsThenMajorCreateIsRejected() throws Exception {
        assertDeleteWins(ChildKind.MAJOR);
    }

    @Test
    @Timeout(120)
    void staffUserCreateWinsThenCollegeDeleteIsRejected() throws Exception {
        assertCreateWins(ChildKind.USER);
    }

    @Test
    @Timeout(120)
    void collegeDeleteWinsThenStaffUserCreateIsRejected() throws Exception {
        assertDeleteWins(ChildKind.USER);
    }

    @Test
    @Timeout(120)
    void studentCreateWinsThenCollegeDeleteIsRejectedWithoutAutoAccount() throws Exception {
        assertThat(paramService.getBoolean("student.autoCreateAccount", true)).isFalse();
        assertCreateWins(ChildKind.STUDENT);
        assertNoFixtureStudentAccounts();
    }

    @Test
    @Timeout(120)
    void collegeDeleteWinsThenStudentCreateIsRejectedWithoutAutoAccount() throws Exception {
        assertThat(paramService.getBoolean("student.autoCreateAccount", true)).isFalse();
        assertDeleteWins(ChildKind.STUDENT);
        assertNoFixtureStudentAccounts();
    }

    @Test
    @Timeout(120)
    void rollbackToDeletedHistoricalCollegeFailsClosedForAllThreeUpdateRefs() throws Exception {
        HistoricalRollbackFixture fixture = seedHistoricalRollbackFixture("HD");

        OperationOutcome<Void> deleted = asSystemAdmin(() -> {
            organizationService.deleteCollege(fixture.sourceCollege().collegeId());
            return null;
        });
        assertSucceeded(deleted, "历史学院删除");

        OperationOutcome<RollbackResultVO> rolledBack =
                asSystemAdmin(() -> exchangeService.rollback(fixture.batchId()));
        assertSucceeded(rolledBack, "历史批次回滚");
        assertAllRestoreTargetsRejected(rolledBack.value(), fixture);

        assertThat(activeCollegeCount(fixture.sourceCollege())).isZero();
        assertEntityColleges(fixture,
                fixture.targetCollege().collegeId(),
                fixture.targetCollege().collegeId(),
                fixture.targetCollege().collegeId());
        assertNoHistoricalOrphans(fixture);
    }

    /**
     * rollback 先锁住历史学院 A：即使学生因 after 快照已被后续修改而不能恢复，
     * 培养信息和证书仍恢复到 A；等待中的删除必须重新检查这两类直接子记录并拒绝。
     */
    @Test
    @Timeout(120)
    void rollbackLocksHistoricalCollegeFirstThenDeleteRejectsTrainingAndCertificateChildren() throws Exception {
        HistoricalRollbackFixture fixture = seedHistoricalRollbackFixture("RW");
        String laterStudentName = "Phase39回滚后续修改";
        Student changedStudent = studentMapper.selectById(fixture.studentId());
        changedStudent.setName(laterStudentName);
        studentMapper.updateById(changedStudent);

        lockHook.arm(fixture.sourceCollege().collegeId(), CollegeParentGuard.Operation.ROLLBACK_RESTORE);
        try (InterleavingTasks tasks = new InterleavingTasks()) {
            Future<OperationOutcome<RollbackResultVO>> rollbackFuture =
                    tasks.submit(() -> asSystemAdmin(() -> exchangeService.rollback(fixture.batchId())));
            assertThat(lockHook.awaitWinnerLocked(10, TimeUnit.SECONDS))
                    .as("rollback 应先取得历史学院 A 的真实 FOR UPDATE 锁")
                    .isTrue();

            collegeLockSqlProbe.arm(fixture.sourceCollege().collegeId());
            Future<OperationOutcome<Void>> deleteFuture = tasks.submit(() -> asSystemAdmin(() -> {
                organizationService.deleteCollege(fixture.sourceCollege().collegeId());
                return null;
            }));
            assertThat(collegeLockSqlProbe.awaitQueryEntered(10, TimeUnit.SECONDS))
                    .as("学院删除应已触达 MyBatis StatementHandler.query 执行边界")
                    .isTrue();
            assertThat(deleteFuture.isDone())
                    .as("rollback 持有历史学院锁时，学院删除不得完成")
                    .isFalse();

            lockHook.releaseWinner();
            OperationOutcome<RollbackResultVO> rolledBack = awaitOutcome(rollbackFuture, "历史批次回滚");
            OperationOutcome<Void> deleted = awaitOutcome(deleteFuture, "历史学院删除");

            assertSucceeded(rolledBack, "历史批次回滚");
            assertThat(rolledBack.value().getStatus()).isEqualTo("PARTIAL_ROLLBACK");
            assertThat(rolledBack.value().getRolledBackCount()).isEqualTo(2);
            assertThat(rolledBack.value().getConflictCount()).isEqualTo(1);
            assertThat(rolledBack.value().getConflicts())
                    .singleElement()
                    .asString()
                    .contains("学生", "已被后续修改");
            assertFailedWith(deleted, "学院下存在");

            assertThat(activeCollegeCount(fixture.sourceCollege())).isEqualTo(1L);
            assertThat(studentMapper.selectById(fixture.studentId()).getName()).isEqualTo(laterStudentName);
            assertEntityColleges(fixture,
                    fixture.targetCollege().collegeId(),
                    fixture.sourceCollege().collegeId(),
                    fixture.sourceCollege().collegeId());
            assertNoHistoricalOrphans(fixture);
        }
    }

    /**
     * 进一步隔离证书直接计数：学生、培养信息都因 after 快照不匹配留在 B，
     * 仅证书恢复到 A，等待中的删除必须明确由证书记录拒绝。
     */
    @Test
    @Timeout(120)
    void rollbackLocksHistoricalCollegeFirstThenDeleteRejectsCertificateOnlyChild() throws Exception {
        HistoricalRollbackFixture fixture = seedHistoricalRollbackFixture("RC");
        Student changedStudent = studentMapper.selectById(fixture.studentId());
        changedStudent.setName("Phase39证书守卫学生后续修改");
        studentMapper.updateById(changedStudent);
        TrainingProfile changedTraining = trainingProfileMapper.selectById(fixture.trainingId());
        changedTraining.setAbilityTestConclusion("Phase39证书守卫培养后续修改");
        trainingProfileMapper.updateById(changedTraining);

        lockHook.arm(fixture.sourceCollege().collegeId(), CollegeParentGuard.Operation.ROLLBACK_RESTORE);
        try (InterleavingTasks tasks = new InterleavingTasks()) {
            Future<OperationOutcome<RollbackResultVO>> rollbackFuture =
                    tasks.submit(() -> asSystemAdmin(() -> exchangeService.rollback(fixture.batchId())));
            assertThat(lockHook.awaitWinnerLocked(10, TimeUnit.SECONDS))
                    .as("rollback 应先取得历史学院 A 的真实 FOR UPDATE 锁")
                    .isTrue();

            collegeLockSqlProbe.arm(fixture.sourceCollege().collegeId());
            Future<OperationOutcome<Void>> deleteFuture = tasks.submit(() -> asSystemAdmin(() -> {
                organizationService.deleteCollege(fixture.sourceCollege().collegeId());
                return null;
            }));
            assertThat(collegeLockSqlProbe.awaitQueryEntered(10, TimeUnit.SECONDS))
                    .as("学院删除应已触达 MyBatis StatementHandler.query 执行边界")
                    .isTrue();
            assertThat(deleteFuture.isDone())
                    .as("rollback 持有历史学院锁时，学院删除不得完成")
                    .isFalse();

            lockHook.releaseWinner();
            OperationOutcome<RollbackResultVO> rolledBack = awaitOutcome(rollbackFuture, "历史批次回滚");
            OperationOutcome<Void> deleted = awaitOutcome(deleteFuture, "历史学院删除");

            assertSucceeded(rolledBack, "历史批次回滚");
            assertThat(rolledBack.value().getStatus()).isEqualTo("PARTIAL_ROLLBACK");
            assertThat(rolledBack.value().getRolledBackCount()).isEqualTo(1);
            assertThat(rolledBack.value().getConflictCount()).isEqualTo(2);
            assertThat(String.join(" | ", rolledBack.value().getConflicts()))
                    .contains("学生", "培养信息", "已被后续修改");
            assertFailedWith(deleted, "学院下存在证书");

            assertThat(activeCollegeCount(fixture.sourceCollege())).isEqualTo(1L);
            assertEntityColleges(fixture,
                    fixture.targetCollege().collegeId(),
                    fixture.targetCollege().collegeId(),
                    fixture.sourceCollege().collegeId());
            assertNoHistoricalOrphans(fixture);
        }
    }

    @Test
    @Timeout(120)
    void rollbackLocksDistinctHistoricalParentsInAscendingOrderBeforeAnyChildQuery() throws Exception {
        HistoricalRollbackFixture fixture = seedHistoricalRollbackFixture("MO");
        CollegeFixture secondSourceCollege = insertCollege("MOB");
        moveStudentAndCertificateRestoreTargets(fixture, secondSourceCollege.collegeId());
        assertThat(rollbackTargetCollegeLayout(fixture.batchId()))
                .as("ref 倒序遍历应呈现 A2、A1、A2 的重复目标布局")
                .containsExactly(
                        secondSourceCollege.collegeId(),
                        fixture.sourceCollege().collegeId(),
                        secondSourceCollege.collegeId());
        List<Long> expectedParentOrder = List.of(
                        fixture.sourceCollege().collegeId(),
                        secondSourceCollege.collegeId())
                .stream()
                .sorted()
                .toList();

        lockOrderTrace.reset();
        collegeLockSqlProbe.armFirstChildQuery();
        OperationOutcome<RollbackResultVO> rolledBack =
                asSystemAdmin(() -> exchangeService.rollback(fixture.batchId()));

        assertSucceeded(rolledBack, "多父学院历史批次回滚");
        assertThat(rolledBack.value().getStatus()).isEqualTo("ROLLED_BACK");
        assertThat(rolledBack.value().getRolledBackCount()).isEqualTo(3);
        assertThat(rolledBack.value().getConflictCount()).isZero();
        assertThat(collegeLockSqlProbe.awaitFirstChildQueryEntered(10, TimeUnit.SECONDS))
                .as("rollback 应实际进入 student/training_profile/certificate 子行 FOR UPDATE 查询")
                .isTrue();
        assertThat(lockOrderTrace.parentCollegeIds())
                .as("重复且倒序出现的恢复目标必须去重后按学院 ID 数值升序加锁")
                .containsExactlyElementsOf(expectedParentOrder);
        assertThat(lockOrderTrace.firstChildTable())
                .as("应记录首个业务子行 FOR UPDATE 查询")
                .isIn("student", "training_profile", "certificate");
        assertThat(lockOrderTrace.lastParentLockOrder())
                .as("全部学院父锁应早于首个业务子行锁查询完成")
                .isPositive()
                .isLessThan(lockOrderTrace.firstChildQueryOrder());

        assertEntityColleges(fixture,
                secondSourceCollege.collegeId(),
                fixture.sourceCollege().collegeId(),
                secondSourceCollege.collegeId());
        assertNoHistoricalOrphans(fixture);
    }

    @Test
    @Timeout(120)
    void collegeDeleteLocksHistoricalCollegeFirstThenRollbackConflictsWithoutOrphans() throws Exception {
        HistoricalRollbackFixture fixture = seedHistoricalRollbackFixture("DW");

        lockHook.arm(fixture.sourceCollege().collegeId(), CollegeParentGuard.Operation.DELETE);
        try (InterleavingTasks tasks = new InterleavingTasks()) {
            Future<OperationOutcome<Void>> deleteFuture = tasks.submit(() -> asSystemAdmin(() -> {
                organizationService.deleteCollege(fixture.sourceCollege().collegeId());
                return null;
            }));
            assertThat(lockHook.awaitWinnerLocked(10, TimeUnit.SECONDS))
                    .as("学院删除应先取得历史学院 A 的真实 FOR UPDATE 锁")
                    .isTrue();

            collegeLockSqlProbe.arm(fixture.sourceCollege().collegeId());
            Future<OperationOutcome<RollbackResultVO>> rollbackFuture =
                    tasks.submit(() -> asSystemAdmin(() -> exchangeService.rollback(fixture.batchId())));
            assertThat(collegeLockSqlProbe.awaitQueryEntered(10, TimeUnit.SECONDS))
                    .as("rollback 应已触达历史学院 A 的 MyBatis StatementHandler.query 执行边界")
                    .isTrue();
            assertThat(rollbackFuture.isDone())
                    .as("删除事务持有历史学院锁时，rollback 不得完成")
                    .isFalse();

            lockHook.releaseWinner();
            OperationOutcome<Void> deleted = awaitOutcome(deleteFuture, "历史学院删除");
            OperationOutcome<RollbackResultVO> rolledBack = awaitOutcome(rollbackFuture, "历史批次回滚");

            assertSucceeded(deleted, "历史学院删除");
            assertSucceeded(rolledBack, "历史批次回滚");
            assertAllRestoreTargetsRejected(rolledBack.value(), fixture);

            assertThat(activeCollegeCount(fixture.sourceCollege())).isZero();
            assertEntityColleges(fixture,
                    fixture.targetCollege().collegeId(),
                    fixture.targetCollege().collegeId(),
                    fixture.targetCollege().collegeId());
            assertNoHistoricalOrphans(fixture);
        }
    }

    /**
     * 子记录先锁父行：删除必须等子事务提交，随后看到已提交子记录并拒绝删除。
     */
    private void assertCreateWins(ChildKind childKind) throws Exception {
        CollegeFixture fixture = insertCollege("CW");
        lockHook.arm(fixture.collegeId(), childKind.operation());
        try (InterleavingTasks tasks = new InterleavingTasks()) {
            Future<OperationOutcome<Long>> createFuture =
                    tasks.submit(() -> asSystemAdmin(() -> createChild(childKind, fixture)));
            assertThat(lockHook.awaitWinnerLocked(10, TimeUnit.SECONDS))
                    .as("%s 新增应已取得真实学院 FOR UPDATE 锁", childKind.label())
                    .isTrue();

            collegeLockSqlProbe.arm(fixture.collegeId());
            Future<OperationOutcome<Void>> deleteFuture = tasks.submit(() -> asSystemAdmin(() -> {
                organizationService.deleteCollege(fixture.collegeId());
                return null;
            }));
            assertThat(collegeLockSqlProbe.awaitQueryEntered(10, TimeUnit.SECONDS))
                    .as("学院删除应已触达 MyBatis StatementHandler.query 执行边界")
                    .isTrue();
            assertThat(deleteFuture.isDone())
                    .as("持锁子事务释放前，删除不得完成（仅作锁等待辅助证据）")
                    .isFalse();

            lockHook.releaseWinner();
            OperationOutcome<Long> created = awaitOutcome(createFuture, childKind.label() + "新增");
            OperationOutcome<Void> deleted = awaitOutcome(deleteFuture, "学院删除");

            assertSucceeded(created, childKind.label() + "新增");
            assertFailedWith(deleted, childKind.deleteBlockedMessage());
            assertThat(activeCollegeCount(fixture)).isEqualTo(1L);
            assertThat(activeChildCount(childKind, fixture)).isEqualTo(1L);
            assertThat(orphanCount(childKind, fixture)).isZero();
            assertStudentHasNoGeneratedAccount(childKind, fixture);
        }
    }

    /**
     * 删除先锁父行：子事务必须等待删除提交，随后锁定读看不到 deleted=0 的父行并整体失败。
     */
    private void assertDeleteWins(ChildKind childKind) throws Exception {
        CollegeFixture fixture = insertCollege("DW");
        lockHook.arm(fixture.collegeId(), CollegeParentGuard.Operation.DELETE);
        try (InterleavingTasks tasks = new InterleavingTasks()) {
            Future<OperationOutcome<Void>> deleteFuture = tasks.submit(() -> asSystemAdmin(() -> {
                organizationService.deleteCollege(fixture.collegeId());
                return null;
            }));
            assertThat(lockHook.awaitWinnerLocked(10, TimeUnit.SECONDS))
                    .as("学院删除应已取得真实学院 FOR UPDATE 锁")
                    .isTrue();

            collegeLockSqlProbe.arm(fixture.collegeId());
            Future<OperationOutcome<Long>> createFuture =
                    tasks.submit(() -> asSystemAdmin(() -> createChild(childKind, fixture)));
            assertThat(collegeLockSqlProbe.awaitQueryEntered(10, TimeUnit.SECONDS))
                    .as("%s 新增应已触达 MyBatis StatementHandler.query 执行边界", childKind.label())
                    .isTrue();
            assertThat(createFuture.isDone())
                    .as("删除事务释放前，%s 新增不得完成（仅作锁等待辅助证据）", childKind.label())
                    .isFalse();

            lockHook.releaseWinner();
            OperationOutcome<Void> deleted = awaitOutcome(deleteFuture, "学院删除");
            OperationOutcome<Long> created = awaitOutcome(createFuture, childKind.label() + "新增");

            assertSucceeded(deleted, "学院删除");
            assertFailedWith(created, "学院不存在");
            assertThat(activeCollegeCount(fixture)).isZero();
            assertThat(activeChildCount(childKind, fixture)).isZero();
            assertThat(orphanCount(childKind, fixture)).isZero();
            assertStudentHasNoGeneratedAccount(childKind, fixture);
        }
    }

    private Long createChild(ChildKind childKind, CollegeFixture fixture) {
        return switch (childKind) {
            case MAJOR -> organizationService.createMajor(majorRequest(fixture));
            case USER -> securityAdminService.createUser(userRequest(fixture));
            case STUDENT -> studentService.create(studentRequest(fixture));
        };
    }

    private MajorSaveRequest majorRequest(CollegeFixture fixture) {
        MajorSaveRequest request = new MajorSaveRequest();
        request.setCollegeId(fixture.collegeId());
        request.setInternalMajorCode(fixture.key());
        request.setInternalMajorName("Phase39并发专业");
        request.setYearVersion("P39");
        request.setPilotScopeFlag(0);
        request.setSort(1);
        request.setStatus(1);
        return request;
    }

    private UserSaveRequest userRequest(CollegeFixture fixture) {
        UserSaveRequest request = new UserSaveRequest();
        request.setUsername(fixture.key());
        request.setRealName("Phase三九并发职员");
        request.setWorkNo("W" + fixture.digits());
        request.setStatus("ENABLED");
        request.setUserType("STAFF");
        request.setCollegeId(fixture.collegeId());
        request.setRoleIds(List.of(staffRoleId));
        return request;
    }

    private StudentSaveRequest studentRequest(CollegeFixture fixture) {
        StudentSaveRequest request = new StudentSaveRequest();
        request.setStudentNo(fixture.key());
        request.setName("并发学生");
        request.setGender("MALE");
        request.setIdCardType("hm_travel_permit");
        request.setIdCardNo("H" + fixture.digits());
        request.setBirthDate("2000/1/2");
        request.setIdentityType("normal");
        request.setCollegeId(fixture.collegeId());
        request.setGrade("2039");
        request.setClassName("Phase39并发班");
        return request;
    }

    private HistoricalRollbackFixture seedHistoricalRollbackFixture(String direction) throws Exception {
        CollegeFixture sourceCollege = insertCollege(direction + "A");
        CollegeFixture targetCollege = insertCollege(direction + "B");
        String idCardNo = "R" + sourceCollege.digits();

        Student student = new Student();
        student.setStudentNo(sourceCollege.key() + "S");
        student.setName("Phase39历史回滚学生");
        student.setGender("MALE");
        student.setIdCardType("hm_travel_permit");
        student.setIdCardNo(idCardProtectionService.encrypt(idCardNo));
        student.setIdCardHmac(idCardProtectionService.hmac(idCardNo));
        student.setBirthDate("2000/1/2");
        student.setIdentityType("normal");
        student.setCollegeId(sourceCollege.collegeId());
        student.setGrade("2039");
        student.setClassName("Phase39历史回滚班");
        student.setStatus("DRAFT");
        student.setLocked(0);
        studentMapper.insert(student);
        Student beforeStudent = studentMapper.selectById(student.getId());
        String beforeStudentJson = snapshot(beforeStudent);
        beforeStudent.setCollegeId(targetCollege.collegeId());
        studentMapper.updateById(beforeStudent);
        Student afterStudent = studentMapper.selectById(student.getId());
        String afterStudentJson = snapshot(afterStudent);

        TrainingProfile training = new TrainingProfile();
        training.setStudentId(student.getId());
        training.setCollegeId(sourceCollege.collegeId());
        training.setAssessmentYear("2039");
        training.setSecondDisciplineCode("050101");
        training.setSecondDisciplineName("Phase39历史专业");
        training.setInternalMajorCode("P39-HISTORY");
        training.setInternalMajorName("Phase39历史专业");
        training.setEducationLevel("undergraduate");
        training.setTrainingGoal("primary_school_teacher");
        training.setInternshipOrgMode("school_centralized");
        training.setInternshipLocation("primary_secondary_school");
        training.setTeachingSegment("primary_school");
        training.setTeachingSubjectId(1L);
        training.setTeachingSubjectCode("P39-SUBJECT");
        training.setTeachingSubjectName("Phase39学科");
        training.setInterviewOrgMode("school_centralized");
        training.setStatus("DRAFT");
        training.setLocked(0);
        trainingProfileMapper.insert(training);
        TrainingProfile beforeTraining = trainingProfileMapper.selectById(training.getId());
        String beforeTrainingJson = snapshot(beforeTraining);
        beforeTraining.setCollegeId(targetCollege.collegeId());
        trainingProfileMapper.updateById(beforeTraining);
        TrainingProfile afterTraining = trainingProfileMapper.selectById(training.getId());
        String afterTrainingJson = snapshot(afterTraining);

        Certificate certificate = new Certificate();
        certificate.setStudentId(student.getId());
        certificate.setCollegeId(sourceCollege.collegeId());
        certificate.setAssessmentYear("2039");
        certificate.setStudentNo(student.getStudentNo());
        certificate.setStudentName(student.getName());
        certificate.setIdCardType(student.getIdCardType());
        certificate.setIdCardNo(idCardProtectionService.encrypt(idCardNo));
        certificate.setIdCardHmac(idCardProtectionService.hmac(idCardNo));
        certificate.setEducationLevel("undergraduate");
        certificate.setTrainingGoal("primary_school_teacher");
        certificate.setTeachingSegment("primary_school");
        certificate.setTeachingSubjectCode("P39-SUBJECT");
        certificate.setTeachingSubjectName("Phase39学科");
        certificate.setIssuer("Phase39历史签发人");
        certificate.setIssueDate("2039/6/30");
        certificate.setValidUntil("2042/6/30");
        certificate.setStatus("ISSUED");
        certificate.setLocked(1);
        certificateMapper.insert(certificate);
        Certificate beforeCertificate = certificateMapper.selectById(certificate.getId());
        String beforeCertificateJson = snapshot(beforeCertificate);
        beforeCertificate.setCollegeId(targetCollege.collegeId());
        certificateMapper.updateById(beforeCertificate);
        Certificate afterCertificate = certificateMapper.selectById(certificate.getId());
        String afterCertificateJson = snapshot(afterCertificate);

        ImportExportBatch batch = new ImportExportBatch();
        batch.setBatchNo(sourceCollege.key() + "BATCH");
        batch.setType("import");
        batch.setFileName("phase39-historical-rollback.xlsx");
        batch.setOperatorId(SYS_ADMIN_USER_ID);
        batch.setOperateTime(LocalDateTime.now());
        batch.setTotal(1);
        batch.setSuccessCount(1);
        batch.setFailCount(0);
        batch.setScopeJson("{}");
        batch.setStrategy("OVERWRITE");
        batch.setStatus("IMPORTED");
        batch.setRemark("Phase39历史跨学院UPDATE回滚反例");
        batchMapper.insert(batch);

        insertUpdateRef(batch, "student", student.getId(),
                beforeStudentJson, afterStudentJson, 1);
        insertUpdateRef(batch, "training_profile", training.getId(),
                beforeTrainingJson, afterTrainingJson, 1);
        insertUpdateRef(batch, "certificate", certificate.getId(),
                beforeCertificateJson, afterCertificateJson, 1);

        return new HistoricalRollbackFixture(
                sourceCollege,
                targetCollege,
                batch.getId(),
                student.getId(),
                training.getId(),
                certificate.getId());
    }

    private void insertUpdateRef(ImportExportBatch batch,
                                 String tableName,
                                 Long recordId,
                                 String beforeJson,
                                 String afterJson,
                                 int rowNo) {
        ImportRecordRef ref = new ImportRecordRef();
        ref.setBatchId(batch.getId());
        ref.setBatchNo(batch.getBatchNo());
        ref.setTableName(tableName);
        ref.setRecordId(recordId);
        ref.setAction("UPDATE");
        ref.setBeforeJson(beforeJson);
        ref.setAfterJson(afterJson);
        ref.setRowNo(rowNo);
        ref.setRemark("Phase39历史跨学院UPDATE回滚反例");
        recordRefMapper.insert(ref);
    }

    private void moveStudentAndCertificateRestoreTargets(
            HistoricalRollbackFixture fixture, Long secondSourceCollegeId) throws Exception {
        List<ImportRecordRef> refs = recordRefMapper.selectList(
                new LambdaQueryWrapper<ImportRecordRef>()
                        .eq(ImportRecordRef::getBatchId, fixture.batchId()));
        for (ImportRecordRef ref : refs) {
            if ("student".equals(ref.getTableName())) {
                Student before = objectMapper.readValue(ref.getBeforeJson(), Student.class);
                before.setCollegeId(secondSourceCollegeId);
                ref.setBeforeJson(snapshot(before));
                recordRefMapper.updateById(ref);
            } else if ("certificate".equals(ref.getTableName())) {
                Certificate before = objectMapper.readValue(ref.getBeforeJson(), Certificate.class);
                before.setCollegeId(secondSourceCollegeId);
                ref.setBeforeJson(snapshot(before));
                recordRefMapper.updateById(ref);
            }
        }
    }

    private List<Long> rollbackTargetCollegeLayout(Long batchId) throws Exception {
        List<ImportRecordRef> refs = recordRefMapper.selectList(
                new LambdaQueryWrapper<ImportRecordRef>()
                        .eq(ImportRecordRef::getBatchId, batchId));
        refs.sort((left, right) -> right.getId().compareTo(left.getId()));
        List<Long> layout = new ArrayList<>(refs.size());
        for (ImportRecordRef ref : refs) {
            layout.add(objectMapper.readTree(ref.getBeforeJson()).path("collegeId").asLong());
        }
        return layout;
    }

    private String snapshot(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private void assertAllRestoreTargetsRejected(RollbackResultVO result,
                                                 HistoricalRollbackFixture fixture) {
        assertThat(result.getStatus()).isEqualTo("PARTIAL_ROLLBACK");
        assertThat(result.getRolledBackCount()).isZero();
        assertThat(result.getConflictCount()).isEqualTo(3);
        assertThat(result.getConflicts()).hasSize(3);
        String conflicts = String.join(" | ", result.getConflicts());
        assertThat(conflicts)
                .as("三类历史 UPDATE ref 都应明确记录目标学院已删除冲突")
                .contains("学生#" + fixture.studentId())
                .contains("培养信息#" + fixture.trainingId())
                .contains("证书#" + fixture.certificateId())
                .contains("学院")
                .contains(fixture.sourceCollege().collegeId().toString());
    }

    private void assertEntityColleges(HistoricalRollbackFixture fixture,
                                      Long expectedStudentCollegeId,
                                      Long expectedTrainingCollegeId,
                                      Long expectedCertificateCollegeId) {
        assertThat(studentMapper.selectById(fixture.studentId()).getCollegeId())
                .as("学生最终学院")
                .isEqualTo(expectedStudentCollegeId);
        assertThat(trainingProfileMapper.selectById(fixture.trainingId()).getCollegeId())
                .as("培养信息最终学院")
                .isEqualTo(expectedTrainingCollegeId);
        assertThat(certificateMapper.selectById(fixture.certificateId()).getCollegeId())
                .as("证书最终学院")
                .isEqualTo(expectedCertificateCollegeId);
    }

    private void assertNoHistoricalOrphans(HistoricalRollbackFixture fixture) {
        assertThat(orphanCountById("student", fixture.studentId())).as("学生孤儿数").isZero();
        assertThat(orphanCountById("training_profile", fixture.trainingId())).as("培养信息孤儿数").isZero();
        assertThat(orphanCountById("certificate", fixture.certificateId())).as("证书孤儿数").isZero();
    }

    private long orphanCountById(String tableName, Long recordId) {
        String sql = "SELECT COUNT(*) FROM " + tableName
                + " child LEFT JOIN sys_college parent"
                + " ON parent.id = child.college_id AND parent.deleted = 0"
                + " WHERE child.id = ? AND child.deleted = 0 AND parent.id IS NULL";
        return jdbcTemplate.queryForObject(sql, Long.class, recordId);
    }

    private CollegeFixture insertCollege(String direction) {
        long sequence = FIXTURE_SEQUENCE.incrementAndGet();
        String digits = "%08d".formatted(Math.floorMod(sequence, 100_000_000L));
        String key = FIXTURE_PREFIX + direction + digits;
        SysCollege college = new SysCollege();
        college.setCode(key);
        college.setName("Phase39并发学院" + digits);
        college.setSort(3900);
        college.setStatus(1);
        collegeMapper.insert(college);
        assertThat(college.getId()).isNotNull();
        return new CollegeFixture(college.getId(), key, digits);
    }

    private long activeCollegeCount(CollegeFixture fixture) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_college
                 WHERE id = ?
                   AND deleted = 0
                """, Long.class, fixture.collegeId());
    }

    private long activeChildCount(ChildKind childKind, CollegeFixture fixture) {
        String sql = "SELECT COUNT(*) FROM " + childKind.tableName()
                + " WHERE college_id = ? AND " + childKind.keyColumn() + " = ? AND deleted = 0";
        return jdbcTemplate.queryForObject(sql, Long.class, fixture.collegeId(), fixture.key());
    }

    private long orphanCount(ChildKind childKind, CollegeFixture fixture) {
        String sql = "SELECT COUNT(*) FROM " + childKind.tableName()
                + " child LEFT JOIN sys_college parent"
                + " ON parent.id = child.college_id AND parent.deleted = 0"
                + " WHERE child.college_id = ? AND child." + childKind.keyColumn()
                + " = ? AND child.deleted = 0 AND parent.id IS NULL";
        return jdbcTemplate.queryForObject(sql, Long.class, fixture.collegeId(), fixture.key());
    }

    private void assertStudentHasNoGeneratedAccount(ChildKind childKind, CollegeFixture fixture) {
        if (childKind != ChildKind.STUDENT) {
            return;
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_user
                 WHERE username = ?
                   AND deleted = 0
                """, Long.class, fixture.key()))
                .as("学生父子锁反例不得依赖自动生成的 sys_user")
                .isZero();
    }

    private void assertNoFixtureStudentAccounts() {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_user
                 WHERE username LIKE ?
                   AND deleted = 0
                """, Long.class, FIXTURE_PREFIX + "%"))
                .as("student.autoCreateAccount=false 时学生新增不得旁生 sys_user")
                .isZero();
    }

    private <T> OperationOutcome<T> asSystemAdmin(Callable<T> action) {
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(SYS_ADMIN_USER_ID);
        user.setUsername("test_sys_admin");
        user.setRealName("系统管理员测试账号");
        user.setUserType("STAFF");
        user.setRoles(Set.of("SYS_ADMIN"));
        UserContext.set(user);
        try {
            return OperationOutcome.succeeded(action.call());
        } catch (Exception exception) {
            return OperationOutcome.failed(exception);
        } finally {
            DataScopeContext.clear();
            UserContext.clear();
        }
    }

    private <T> OperationOutcome<T> awaitOutcome(Future<OperationOutcome<T>> future, String operation)
            throws InterruptedException {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw new AssertionError(operation + "工作线程异常终止", exception.getCause());
        } catch (TimeoutException exception) {
            throw new AssertionError(operation + "未在锁释放后按期完成", exception);
        }
    }

    private void assertSucceeded(OperationOutcome<?> outcome, String operation) {
        assertThat(outcome.failure())
                .as(operation + "应成功，实际异常")
                .isNull();
        if (outcome.value() instanceof Long value) {
            assertThat(value).as(operation + "应返回持久化主键").isPositive();
        }
    }

    private void assertFailedWith(OperationOutcome<?> outcome, String messageFragment) {
        assertThat(outcome.failure())
                .isInstanceOf(BizException.class)
                .hasMessageContaining(messageFragment);
        assertThat(outcome.value()).isNull();
    }

    private void assertCollegeLockSqlContract() {
        List<Method> lockMethods = Arrays.stream(SysCollegeMapper.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Select.class))
                .filter(method -> normalizedSql(method).contains("from sys_college"))
                .filter(method -> normalizedSql(method).contains("for update"))
                .toList();
        assertThat(lockMethods).as("sys_college 应且仅应有一个父行锁定读").hasSize(1);

        Method lockMethod = lockMethods.get(0);
        String sql = normalizedSql(lockMethod);
        int selectStart = sql.indexOf("select") + "select".length();
        int fromStart = sql.indexOf("from");
        assertThat(sql.substring(selectStart, fromStart).trim())
                .as("父行锁只投影状态，不读取整行敏感/无关列")
                .isEqualTo("status");
        assertThat(sql).contains("deleted = 0", "for update");
        assertThat(lockMethod.getReturnType()).isEqualTo(Integer.class);
    }

    private String normalizedSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private void releaseAndClose(ExecutorService pool, List<Future<?>> futures) {
        lockHook.releaseAll();
        boolean interrupted = Thread.interrupted();
        futures.stream()
                .filter(future -> !future.isDone())
                .forEach(future -> future.cancel(true));
        pool.shutdown();
        TerminationWait firstWait = awaitTerminationPreservingInterrupt(pool, 10, TimeUnit.SECONDS);
        interrupted |= firstWait.interrupted();
        boolean terminated = firstWait.terminated();
        if (!terminated) {
            pool.shutdownNow();
            TerminationWait secondWait = awaitTerminationPreservingInterrupt(pool, 10, TimeUnit.SECONDS);
            interrupted |= secondWait.interrupted();
            terminated = secondWait.terminated();
        }
        lockHook.reset();
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        assertThat(terminated).as("Phase 39 交错测试不得遗留工作线程").isTrue();
    }

    private TerminationWait awaitTerminationPreservingInterrupt(
            ExecutorService pool, long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        boolean interrupted = false;
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return new TerminationWait(pool.isTerminated(), interrupted);
            }
            try {
                return new TerminationWait(
                        pool.awaitTermination(remaining, TimeUnit.NANOSECONDS), interrupted);
            } catch (InterruptedException exception) {
                interrupted = true;
                // 清理完成前保持中断位清空；releaseAndClose 退出前统一恢复。
            }
        }
    }

    private void clearParamCache() {
        Cache cache = cacheManager.getCache(CacheConfig.SYS_PARAM);
        if (cache != null) {
            cache.clear();
        }
    }

    private void cleanupFixtures() {
        jdbcTemplate.update("""
                DELETE FROM audit_log
                 WHERE biz_type = 'exchange'
                   AND biz_id IN (
                       SELECT id
                         FROM import_export_batch
                        WHERE batch_no LIKE ?
                   )
                """, FIXTURE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM audit_log
                 WHERE biz_type = 'college'
                   AND biz_id IN (
                       SELECT id
                         FROM sys_college
                        WHERE code LIKE ?
                   )
                """, FIXTURE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM import_record_ref
                 WHERE batch_no LIKE ?
                """, FIXTURE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM import_export_batch
                 WHERE batch_no LIKE ?
                """, FIXTURE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM sys_user_role
                 WHERE user_id IN (
                       SELECT id
                         FROM sys_user
                        WHERE username LIKE ?
                 )
                """, FIXTURE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", FIXTURE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM certificate WHERE student_no LIKE ?", FIXTURE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM training_profile
                 WHERE student_id IN (
                       SELECT id
                         FROM student
                        WHERE student_no LIKE ?
                 )
                """, FIXTURE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM student WHERE student_no LIKE ?", FIXTURE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_major WHERE internal_major_code LIKE ?", FIXTURE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_college WHERE code LIKE ?", FIXTURE_PREFIX + "%");
    }

    private enum ChildKind {
        MAJOR(CollegeParentGuard.Operation.CREATE_MAJOR,
                "专业", "sys_major", "internal_major_code", "学院下存在专业"),
        USER(CollegeParentGuard.Operation.CREATE_USER,
                "STAFF 用户", "sys_user", "username", "学院下存在用户"),
        STUDENT(CollegeParentGuard.Operation.CREATE_STUDENT,
                "学生", "student", "student_no", "学院下存在学生");

        private final CollegeParentGuard.Operation operation;
        private final String label;
        private final String tableName;
        private final String keyColumn;
        private final String deleteBlockedMessage;

        ChildKind(CollegeParentGuard.Operation operation,
                  String label,
                  String tableName,
                  String keyColumn,
                  String deleteBlockedMessage) {
            this.operation = operation;
            this.label = label;
            this.tableName = tableName;
            this.keyColumn = keyColumn;
            this.deleteBlockedMessage = deleteBlockedMessage;
        }

        CollegeParentGuard.Operation operation() {
            return operation;
        }

        String label() {
            return label;
        }

        String tableName() {
            return tableName;
        }

        String keyColumn() {
            return keyColumn;
        }

        String deleteBlockedMessage() {
            return deleteBlockedMessage;
        }
    }

    private record CollegeFixture(Long collegeId, String key, String digits) {
    }

    private record HistoricalRollbackFixture(
            CollegeFixture sourceCollege,
            CollegeFixture targetCollege,
            Long batchId,
            Long studentId,
            Long trainingId,
            Long certificateId) {
    }

    private record OperationOutcome<T>(T value, Exception failure) {

        static <T> OperationOutcome<T> succeeded(T value) {
            return new OperationOutcome<>(value, null);
        }

        static <T> OperationOutcome<T> failed(Exception failure) {
            return new OperationOutcome<>(null, failure);
        }
    }

    private record TerminationWait(boolean terminated, boolean interrupted) {
    }

    private final class InterleavingTasks implements AutoCloseable {

        private final ExecutorService pool = Executors.newFixedThreadPool(2);
        private final List<Future<?>> futures = new ArrayList<>(2);

        <T> Future<T> submit(Callable<T> action) {
            Future<T> future = pool.submit(action);
            futures.add(future);
            return future;
        }

        @Override
        public void close() {
            releaseAndClose(pool, futures);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CollegeLockHookTestConfiguration {

        @Bean
        @Primary
        ControlledCollegeParentLockHook controlledCollegeParentLockHook(LockOrderTrace lockOrderTrace) {
            return new ControlledCollegeParentLockHook(lockOrderTrace);
        }

        @Bean
        CollegeLockSqlProbe collegeLockSqlProbe(LockOrderTrace lockOrderTrace) {
            return new CollegeLockSqlProbe(lockOrderTrace);
        }

        @Bean
        LockOrderTrace lockOrderTrace() {
            return new LockOrderTrace();
        }
    }

    static final class ControlledCollegeParentLockHook extends CollegeParentLockHook {

        private final LockOrderTrace lockOrderTrace;
        private volatile LockPlan plan = LockPlan.disarmed();

        private ControlledCollegeParentLockHook(LockOrderTrace lockOrderTrace) {
            this.lockOrderTrace = lockOrderTrace;
        }

        synchronized void arm(Long collegeId, CollegeParentGuard.Operation winnerOperation) {
            plan.releaseAll();
            plan = LockPlan.armed(collegeId, winnerOperation);
        }

        synchronized void reset() {
            plan.releaseAll();
            plan = LockPlan.disarmed();
        }

        void releaseWinner() {
            plan.releaseWinner.countDown();
        }

        void releaseAll() {
            plan.releaseAll();
        }

        boolean awaitWinnerLocked(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.winnerLocked.await(timeout, unit);
        }

        @Override
        public void afterLock(CollegeParentGuard.Operation operation, Long collegeId, Integer status) {
            if (operation == CollegeParentGuard.Operation.ROLLBACK_RESTORE && status != null) {
                lockOrderTrace.recordParentLock(collegeId);
            }
            LockPlan current = plan;
            if (!current.matches(collegeId) || operation != current.winnerOperation
                    || !current.winnerObserved.compareAndSet(false, true)) {
                return;
            }
            if (status == null) {
                throw new IllegalStateException("Phase 39 胜方取得父行锁时学院不应已删除");
            }
            current.winnerLocked.countDown();
            awaitRelease(current.releaseWinner);
        }

        private void awaitRelease(CountDownLatch release) {
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待 Phase 39 测试释放学院父行锁超时");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Phase 39 学院父行锁测试线程被中断", exception);
            }
        }
    }

    @Intercepts(@Signature(
            type = StatementHandler.class,
            method = "query",
            args = {Statement.class, ResultHandler.class}
    ))
    static final class CollegeLockSqlProbe implements Interceptor {

        private final LockOrderTrace lockOrderTrace;
        private final AtomicReference<Long> armedCollegeId = new AtomicReference<>();
        private final AtomicBoolean observeFirstChildQuery = new AtomicBoolean();
        private volatile CountDownLatch queryEntered = new CountDownLatch(1);
        private volatile CountDownLatch firstChildQueryEntered = new CountDownLatch(1);

        private CollegeLockSqlProbe(LockOrderTrace lockOrderTrace) {
            this.lockOrderTrace = lockOrderTrace;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            BoundSql boundSql = statementHandler.getBoundSql();
            String normalizedSql = boundSql.getSql()
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            Long expectedCollegeId = armedCollegeId.get();
            if (expectedCollegeId != null
                    && STATUS_ONLY_COLLEGE_LOCK_SQL.equals(normalizedSql)
                    && expectedCollegeId.equals(collegeId(boundSql.getParameterObject()))
                    && armedCollegeId.compareAndSet(expectedCollegeId, null)) {
                queryEntered.countDown();
            }
            String childTable = childLockTable(normalizedSql);
            if (childTable != null && observeFirstChildQuery.compareAndSet(true, false)) {
                lockOrderTrace.recordFirstChildQuery(childTable);
                firstChildQueryEntered.countDown();
            }
            return invocation.proceed();
        }

        void arm(Long collegeId) {
            queryEntered = new CountDownLatch(1);
            armedCollegeId.set(collegeId);
        }

        void reset() {
            armedCollegeId.set(null);
            observeFirstChildQuery.set(false);
            queryEntered = new CountDownLatch(1);
            firstChildQueryEntered = new CountDownLatch(1);
        }

        boolean awaitQueryEntered(long timeout, TimeUnit unit) throws InterruptedException {
            return queryEntered.await(timeout, unit);
        }

        void armFirstChildQuery() {
            firstChildQueryEntered = new CountDownLatch(1);
            observeFirstChildQuery.set(true);
        }

        boolean awaitFirstChildQueryEntered(long timeout, TimeUnit unit) throws InterruptedException {
            return firstChildQueryEntered.await(timeout, unit);
        }

        private Long collegeId(Object parameterObject) {
            Object value = parameterObject;
            if (parameterObject instanceof Map<?, ?> parameters) {
                value = parameters.get("id");
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            return null;
        }

        private String childLockTable(String normalizedSql) {
            if (!normalizedSql.endsWith("for update")) {
                return null;
            }
            for (String table : List.of("student", "training_profile", "certificate")) {
                if (normalizedSql.contains("from " + table + " ")) {
                    return table;
                }
            }
            return null;
        }
    }

    static final class LockOrderTrace {

        private final List<Long> parentCollegeIds = new ArrayList<>();
        private long eventSequence;
        private long lastParentLockOrder;
        private long firstChildQueryOrder;
        private String firstChildTable;

        synchronized void reset() {
            parentCollegeIds.clear();
            eventSequence = 0;
            lastParentLockOrder = 0;
            firstChildQueryOrder = 0;
            firstChildTable = null;
        }

        synchronized void recordParentLock(Long collegeId) {
            parentCollegeIds.add(collegeId);
            lastParentLockOrder = ++eventSequence;
        }

        synchronized void recordFirstChildQuery(String table) {
            if (firstChildQueryOrder != 0) {
                return;
            }
            firstChildTable = table;
            firstChildQueryOrder = ++eventSequence;
        }

        synchronized List<Long> parentCollegeIds() {
            return List.copyOf(parentCollegeIds);
        }

        synchronized long lastParentLockOrder() {
            return lastParentLockOrder;
        }

        synchronized long firstChildQueryOrder() {
            return firstChildQueryOrder;
        }

        synchronized String firstChildTable() {
            return firstChildTable;
        }
    }

    private static final class LockPlan {

        private final Long collegeId;
        private final CollegeParentGuard.Operation winnerOperation;
        private final CountDownLatch winnerLocked;
        private final CountDownLatch releaseWinner;
        private final AtomicBoolean winnerObserved = new AtomicBoolean();

        private LockPlan(Long collegeId,
                         CollegeParentGuard.Operation winnerOperation,
                         int latchCount) {
            this.collegeId = collegeId;
            this.winnerOperation = winnerOperation;
            this.winnerLocked = new CountDownLatch(latchCount);
            this.releaseWinner = new CountDownLatch(latchCount);
        }

        static LockPlan armed(Long collegeId, CollegeParentGuard.Operation winnerOperation) {
            return new LockPlan(collegeId, winnerOperation, 1);
        }

        static LockPlan disarmed() {
            return new LockPlan(null, null, 0);
        }

        boolean matches(Long actualCollegeId) {
            return collegeId != null && collegeId.equals(actualCollegeId);
        }

        void releaseAll() {
            releaseWinner.countDown();
        }
    }
}
