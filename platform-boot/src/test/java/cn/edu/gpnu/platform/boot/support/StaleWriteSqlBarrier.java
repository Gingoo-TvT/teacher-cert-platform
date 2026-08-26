package cn.edu.gpnu.platform.boot.support;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * F-03 真实 MySQL 陈旧写门禁：让已完成读取的事务停在目标 UPDATE 之前，
 * 再确认另一连接上的竞争事务已经提交，最后才放行迟到 UPDATE。
 */
@Intercepts(@Signature(type = StatementHandler.class, method = "update", args = {Statement.class}))
public final class StaleWriteSqlBarrier implements Interceptor {

    private static final long INTERCEPTOR_TIMEOUT_SECONDS = 60;
    private static final Logger LOG = LoggerFactory.getLogger(StaleWriteSqlBarrier.class);

    private final AtomicReference<BarrierPlan> plan = new AtomicReference<>(BarrierPlan.disarmed());

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        BarrierPlan current = plan.get();
        if (!current.armed()) {
            return invocation.proceed();
        }
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        String sql = normalize(handler.getBoundSql().getSql());
        Mutation mutation = Mutation.classify(sql);
        if (isObservedAggregateSql(sql)) {
            current.observedSql().add(sql);
        }
        if (mutation == current.lateMutation() && current.lateOnce().compareAndSet(false, true)) {
            Connection connection = ((Statement) invocation.getArgs()[0]).getConnection();
            current.lateConnectionId().set(mysqlConnectionId(connection));
            current.lateAutoCommit().set(connection.getAutoCommit());
            current.lateAtUpdate().countDown();
            awaitRelease(current);
            return invocation.proceed();
        }
        if (mutation == current.firstMutation() && current.firstOnce().compareAndSet(false, true)) {
            Connection connection = ((Statement) invocation.getArgs()[0]).getConnection();
            current.firstConnectionId().set(mysqlConnectionId(connection));
            current.firstAutoCommit().set(connection.getAutoCommit());
            current.firstAtUpdate().countDown();
            registerFirstCompletion(current);
            Object result = invocation.proceed();
            if (!(result instanceof Number affectedRows) || affectedRows.intValue() != 1) {
                throw new AssertionError("F-03 先提交 UPDATE 必须精确影响 1 行，实际=" + result);
            }
            return result;
        }
        return invocation.proceed();
    }

    public void arm(Mutation firstMutation, Mutation lateMutation) {
        Objects.requireNonNull(firstMutation, "firstMutation");
        Objects.requireNonNull(lateMutation, "lateMutation");
        if (firstMutation == lateMutation) {
            throw new IllegalArgumentException("F-03 两个竞争写必须是不同 mutation");
        }
        BarrierPlan next = BarrierPlan.armed(firstMutation, lateMutation);
        BarrierPlan previous = plan.getAndSet(next);
        previous.allowLate().countDown();
    }

    public void reset() {
        BarrierPlan previous = plan.getAndSet(BarrierPlan.disarmed());
        previous.allowLate().countDown();
    }

    public void releaseLate() {
        plan.get().allowLate().countDown();
    }

    public boolean awaitLateAtUpdate(long timeout, TimeUnit unit) throws InterruptedException {
        return plan.get().lateAtUpdate().await(timeout, unit);
    }

    public boolean awaitFirstAtUpdate(long timeout, TimeUnit unit) throws InterruptedException {
        return plan.get().firstAtUpdate().await(timeout, unit);
    }

    public boolean awaitFirstCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return plan.get().firstCompleted().await(timeout, unit);
    }

    public int firstCompletionStatus() {
        return plan.get().firstCompletionStatus();
    }

    public Long firstConnectionId() {
        return plan.get().firstConnectionId().get();
    }

    public Long lateConnectionId() {
        return plan.get().lateConnectionId().get();
    }

    public Boolean firstAutoCommit() {
        return plan.get().firstAutoCommit().get();
    }

    public Boolean lateAutoCommit() {
        return plan.get().lateAutoCommit().get();
    }

    public List<String> observedSql() {
        return List.copyOf(plan.get().observedSql());
    }

    private void registerFirstCompletion(BarrierPlan current) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new AssertionError("F-03 目标 UPDATE 未处于 Spring 事务同步中");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                current.firstCompletionStatus(status);
                LOG.info("F-03 真实写交错: first={} connectionId={} autoCommit={}, late={} connectionId={} "
                                + "autoCommit={}, completionStatus={}",
                        current.firstMutation(), current.firstConnectionId().get(), current.firstAutoCommit().get(),
                        current.lateMutation(), current.lateConnectionId().get(), current.lateAutoCommit().get(), status);
                current.firstCompleted().countDown();
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    current.allowLate().countDown();
                }
            }
        });
    }

    private void awaitRelease(BarrierPlan current) {
        try {
            if (!current.allowLate().await(INTERCEPTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("F-03 迟到 UPDATE 等待放行超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("F-03 迟到 UPDATE 等待被中断", exception);
        }
    }

    private long mysqlConnectionId(Connection connection) throws Exception {
        try (Statement identityStatement = connection.createStatement();
             ResultSet resultSet = identityStatement.executeQuery("SELECT CONNECTION_ID()")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("F-03 无法读取 MySQL CONNECTION_ID()");
            }
            return resultSet.getLong(1);
        }
    }

    private static boolean isObservedAggregateSql(String sql) {
        return sql.startsWith("update certificate set")
                || sql.startsWith("update ability_test_result set");
    }

    private static String normalize(String sql) {
        return sql.replace("`", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public enum Mutation {
        CERTIFICATE_ISSUE,
        CERTIFICATE_CORRECT,
        ABILITY_CONTENT,
        ABILITY_CONFIRM;

        private static Mutation classify(String sql) {
            String setClause = setClause(sql);
            if (sql.startsWith("update certificate set")) {
                if (setClause.contains("correction_reason")) {
                    return CERTIFICATE_CORRECT;
                }
                if (setClause.contains("issuer")
                        && setClause.contains("issue_date")
                        && setClause.contains("status")) {
                    return CERTIFICATE_ISSUE;
                }
            }
            if (sql.startsWith("update ability_test_result set")) {
                if (setClause.contains("exam_org_mode")
                        && setClause.contains("exam_subjects")
                        && setClause.contains("conclusion")) {
                    return ABILITY_CONTENT;
                }
                if (setClause.contains("confirm_status")
                        && setClause.contains("locked")
                        && !setClause.contains("exam_org_mode")) {
                    return ABILITY_CONFIRM;
                }
            }
            return null;
        }

        private static String setClause(String sql) {
            int setIndex = sql.indexOf(" set ");
            int whereIndex = sql.indexOf(" where ");
            if (setIndex < 0 || whereIndex <= setIndex) {
                return "";
            }
            return sql.substring(setIndex + 5, whereIndex);
        }
    }

    private static final class BarrierPlan {

        private final boolean armed;
        private final Mutation firstMutation;
        private final Mutation lateMutation;
        private final AtomicBoolean firstOnce = new AtomicBoolean();
        private final AtomicBoolean lateOnce = new AtomicBoolean();
        private final CountDownLatch firstAtUpdate;
        private final CountDownLatch lateAtUpdate;
        private final CountDownLatch firstCompleted;
        private final CountDownLatch allowLate;
        private final AtomicReference<Long> firstConnectionId = new AtomicReference<>();
        private final AtomicReference<Long> lateConnectionId = new AtomicReference<>();
        private final AtomicReference<Boolean> firstAutoCommit = new AtomicReference<>();
        private final AtomicReference<Boolean> lateAutoCommit = new AtomicReference<>();
        private final List<String> observedSql = new CopyOnWriteArrayList<>();
        private volatile int firstCompletionStatus = Integer.MIN_VALUE;

        private BarrierPlan(boolean armed, Mutation firstMutation, Mutation lateMutation) {
            this.armed = armed;
            this.firstMutation = firstMutation;
            this.lateMutation = lateMutation;
            int latchCount = armed ? 1 : 0;
            this.firstAtUpdate = new CountDownLatch(latchCount);
            this.lateAtUpdate = new CountDownLatch(latchCount);
            this.firstCompleted = new CountDownLatch(latchCount);
            this.allowLate = new CountDownLatch(latchCount);
        }

        static BarrierPlan armed(Mutation firstMutation, Mutation lateMutation) {
            return new BarrierPlan(true, firstMutation, lateMutation);
        }

        static BarrierPlan disarmed() {
            return new BarrierPlan(false, null, null);
        }

        boolean armed() {
            return armed;
        }

        Mutation firstMutation() {
            return firstMutation;
        }

        Mutation lateMutation() {
            return lateMutation;
        }

        AtomicBoolean firstOnce() {
            return firstOnce;
        }

        AtomicBoolean lateOnce() {
            return lateOnce;
        }

        CountDownLatch firstAtUpdate() {
            return firstAtUpdate;
        }

        CountDownLatch lateAtUpdate() {
            return lateAtUpdate;
        }

        CountDownLatch firstCompleted() {
            return firstCompleted;
        }

        CountDownLatch allowLate() {
            return allowLate;
        }

        AtomicReference<Long> firstConnectionId() {
            return firstConnectionId;
        }

        AtomicReference<Long> lateConnectionId() {
            return lateConnectionId;
        }

        AtomicReference<Boolean> firstAutoCommit() {
            return firstAutoCommit;
        }

        AtomicReference<Boolean> lateAutoCommit() {
            return lateAutoCommit;
        }

        List<String> observedSql() {
            return observedSql;
        }

        int firstCompletionStatus() {
            return firstCompletionStatus;
        }

        void firstCompletionStatus(int status) {
            this.firstCompletionStatus = status;
        }
    }
}
