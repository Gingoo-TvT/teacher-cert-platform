package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.student.dto.StudentSaveRequest;
import cn.edu.gpnu.platform.business.student.service.StudentService;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
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
import org.apache.ibatis.annotations.Select;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    private String originalAutoCreateAccount;
    private Long staffRoleId;

    @BeforeEach
    void setUp() {
        lockHook.reset();
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

            Future<OperationOutcome<Void>> deleteFuture = tasks.submit(() -> asSystemAdmin(() -> {
                organizationService.deleteCollege(fixture.collegeId());
                return null;
            }));
            assertThat(lockHook.awaitContenderBeforeLock(10, TimeUnit.SECONDS))
                    .as("学院删除应已到达真实锁查询边界")
                    .isTrue();
            assertWinnerPrecedesContender();
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

            Future<OperationOutcome<Long>> createFuture =
                    tasks.submit(() -> asSystemAdmin(() -> createChild(childKind, fixture)));
            assertThat(lockHook.awaitContenderBeforeLock(10, TimeUnit.SECONDS))
                    .as("%s 新增应已到达真实锁查询边界", childKind.label())
                    .isTrue();
            assertWinnerPrecedesContender();
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

    private void assertWinnerPrecedesContender() {
        assertThat(lockHook.winnerLockedOrder()).isPositive();
        assertThat(lockHook.contenderBeforeLockOrder())
                .as("竞争方必须在胜方取得真实父行锁后才进入锁查询")
                .isGreaterThan(lockHook.winnerLockedOrder());
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
                DELETE FROM sys_user_role
                 WHERE user_id IN (
                       SELECT id
                         FROM sys_user
                        WHERE username LIKE ?
                 )
                """, FIXTURE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", FIXTURE_PREFIX + "%");
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
        ControlledCollegeParentLockHook controlledCollegeParentLockHook() {
            return new ControlledCollegeParentLockHook();
        }
    }

    static final class ControlledCollegeParentLockHook extends CollegeParentLockHook {

        private volatile LockPlan plan = LockPlan.disarmed();

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

        boolean awaitContenderBeforeLock(long timeout, TimeUnit unit) throws InterruptedException {
            return plan.contenderBeforeLock.await(timeout, unit);
        }

        long winnerLockedOrder() {
            return plan.winnerLockedOrder;
        }

        long contenderBeforeLockOrder() {
            return plan.contenderBeforeLockOrder;
        }

        @Override
        public void beforeLock(CollegeParentGuard.Operation operation, Long collegeId) {
            LockPlan current = plan;
            if (!current.matches(collegeId) || operation == current.winnerOperation
                    || !current.contenderObserved.compareAndSet(false, true)) {
                return;
            }
            current.contenderBeforeLockOrder = current.eventSequence.incrementAndGet();
            current.contenderBeforeLock.countDown();
        }

        @Override
        public void afterLock(CollegeParentGuard.Operation operation, Long collegeId, Integer status) {
            LockPlan current = plan;
            if (!current.matches(collegeId) || operation != current.winnerOperation
                    || !current.winnerObserved.compareAndSet(false, true)) {
                return;
            }
            if (status == null) {
                throw new IllegalStateException("Phase 39 胜方取得父行锁时学院不应已删除");
            }
            current.winnerLockedOrder = current.eventSequence.incrementAndGet();
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

    private static final class LockPlan {

        private final Long collegeId;
        private final CollegeParentGuard.Operation winnerOperation;
        private final CountDownLatch winnerLocked;
        private final CountDownLatch contenderBeforeLock;
        private final CountDownLatch releaseWinner;
        private final AtomicBoolean winnerObserved = new AtomicBoolean();
        private final AtomicBoolean contenderObserved = new AtomicBoolean();
        private final AtomicLong eventSequence = new AtomicLong();
        private volatile long winnerLockedOrder;
        private volatile long contenderBeforeLockOrder;

        private LockPlan(Long collegeId,
                         CollegeParentGuard.Operation winnerOperation,
                         int latchCount) {
            this.collegeId = collegeId;
            this.winnerOperation = winnerOperation;
            this.winnerLocked = new CountDownLatch(latchCount);
            this.contenderBeforeLock = new CountDownLatch(latchCount);
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
