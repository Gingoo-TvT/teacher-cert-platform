package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.entity.Notification;
import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import cn.edu.gpnu.platform.system.service.NotificationService;
import cn.edu.gpnu.platform.system.service.NotifyChannel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 44（PG-M3 整改）：站内信批量写的<b>真实 MySQL</b> 证据。
 *
 * <p>审计 PG-M3：44a 的批量插入被 44f 回退为逐行 insert（因 {@code Db.saveBatch} 另开 SqlSession/连接、与外层
 * 事务持有的行锁互等，间歇 {@code Lock wait timeout}），但文档仍把 P1-5 记为闭环。本次改用同事务/同连接的
 * multi-values insert，需要用真实数据库证明四件事：
 * <ol>
 *   <li><b>真批量</b>：N 个收件人只准备 1 条 INSERT 语句（占位符个数＝列数×行数），不是 N 条；</li>
 *   <li><b>同连接</b>：该语句拿到的 {@code Connection} 与外层事务绑定的连接是<b>同一个对象</b>；</li>
 *   <li><b>同事务</b>：外层回滚后一行不留（{@code Db.saveBatch} 另开连接会独立提交、留下孤儿通知）；</li>
 *   <li><b>不再锁等待</b>：外层事务持有 {@code user_id} 范围锁时批量写立即完成；同一时刻从<b>另一条连接</b>
 *       插同一 {@code user_id} 必然 1205 超时——这条反例证明断言 3/4 非空转，也复刻了 44a 的事故机制。</li>
 * </ol>
 * 主键/审计字段填充与逐行 insert 完全一致，亦在此逐列核对。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
@Import(Phase44NotificationBatchIT.NotificationInsertProbeTestConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class Phase44NotificationBatchIT {

    private static final String BIZ_TYPE = "phase44batch";
    private static final String TYPE = "REVIEW_TODO";
    private static final String TITLE = "Phase44 批量通知";
    private static final String CONTENT = "批量插入回归";
    /** notification 无外键，用远离种子数据的固定区间即可，避免与其它 IT 的用户数据互相干扰。 */
    private static final long BASE_USER_ID = 944_000_000_001L;
    /** 列清单固定 12 列（不含 DDL 默认的 deleted）；占位符个数＝12×行数即证明是 multi-values。 */
    private static final int COLUMNS_PER_ROW = 12;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private List<NotifyChannel> channels;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private NotificationInsertProbe probe;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetFixture() {
        deleteFixtureRows();
        probe.reset();
    }

    @AfterEach
    void cleanUp() {
        deleteFixtureRows();
        probe.reset();
    }

    @Test
    void sendBatchIssuesOneMultiValuesInsertOnTheTransactionConnection() {
        List<Long> recipients = recipients(3);
        AtomicReference<Connection> transactionConnection = new AtomicReference<>();

        probe.arm();
        transactionTemplate.executeWithoutResult(status -> {
            transactionConnection.set(DataSourceUtils.getConnection(dataSource));
            notificationService.sendBatch(recipients, TYPE, TITLE, CONTENT, BIZ_TYPE, "1");
        });

        List<PreparedInsert> inserts = probe.captured();
        assertThat(inserts).as("3 个收件人应只准备 1 条 INSERT，而不是 3 条").hasSize(1);
        assertThat(inserts.get(0).placeholders())
                .as("占位符个数应为 12 列 × 3 行，证明是单条 multi-values 语句")
                .isEqualTo(COLUMNS_PER_ROW * 3);
        assertThat(inserts.get(0).connection())
                .as("批量 INSERT 必须复用外层事务绑定的同一连接（44a 的另开连接正是锁等待成因）")
                .isSameAs(transactionConnection.get());

        assertThat(fixtureRows()).hasSize(3);
        assertThat(fixtureRows()).extracting(Notification::getUserId).containsExactlyInAnyOrderElementsOf(recipients);
    }

    @Test
    void sendBatchChunksLargeFanOutAndStaysInOneTransaction() {
        // 大扇出：1001 个收件人 → 3 条语句（500/500/1），全部在同一事务/同一连接内。
        List<Long> recipients = recipients(1001);
        AtomicReference<Connection> transactionConnection = new AtomicReference<>();

        probe.arm();
        transactionTemplate.executeWithoutResult(status -> {
            transactionConnection.set(DataSourceUtils.getConnection(dataSource));
            notificationService.sendBatch(recipients, TYPE, TITLE, CONTENT, BIZ_TYPE, "2");
        });

        List<PreparedInsert> inserts = probe.captured();
        assertThat(inserts).hasSize(3);
        assertThat(inserts).extracting(PreparedInsert::placeholders)
                .containsExactly(COLUMNS_PER_ROW * 500, COLUMNS_PER_ROW * 500, COLUMNS_PER_ROW);
        assertThat(inserts).allSatisfy(insert ->
                assertThat(insert.connection()).isSameAs(transactionConnection.get()));
        assertThat(fixtureRows()).hasSize(1001);
    }

    @Test
    void sendBatchRollsBackWithTheOuterTransaction() {
        // 决定性的「同事务」证据：另开连接的实现（44a）会独立提交，回滚后仍留下通知行。
        List<Long> recipients = recipients(4);

        transactionTemplate.executeWithoutResult(status -> {
            notificationService.sendBatch(recipients, TYPE, TITLE, CONTENT, BIZ_TYPE, "3");
            assertThat(fixtureRows()).as("事务内应可见").hasSize(4);
            status.setRollbackOnly();
        });

        assertThat(fixtureRows()).as("外层回滚后不得留下任何通知行").isEmpty();
    }

    @Test
    @Timeout(90)
    void batchInsertDoesNotWaitWhileOuterTransactionHoldsUserRowLocks() {
        long userId = BASE_USER_ID + 90;
        insertCommittedFixtureRow(userId, "4");

        transactionTemplate.executeWithoutResult(status -> {
            // 外层事务持有该 user_id 在 idx_notification_user_read 上的记录/间隙锁（模拟二审事务持锁）。
            jdbcTemplate.queryForList("SELECT id FROM notification WHERE user_id = ? FOR UPDATE", userId);

            // 反例：另开连接插同一 user_id —— 必须锁等待超时（这正是 44a 的事故机制）。
            SQLException contention = insertFromSeparateConnection(userId);
            assertThat(contention != null)
                    .as("另开连接在同一 user_id 上插入应被外层事务的锁阻塞")
                    .isTrue();
            assertThat(contention.getErrorCode())
                    .as("应为 ER_LOCK_WAIT_TIMEOUT(1205)，实际: %s", contention.getMessage())
                    .isEqualTo(1205);

            // 正例：本实现走事务绑定连接 —— 立即完成，不与自身持有的锁互等。
            long startedAt = System.nanoTime();
            inAppChannel().sendBatch(List.of(userId), TYPE, TITLE, CONTENT, BIZ_TYPE, "4");
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

            assertThat(elapsedMillis).as("同连接批量写不得出现锁等待").isLessThan(5_000L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notification WHERE user_id = ? AND biz_type = ?",
                    Long.class, userId, BIZ_TYPE))
                    .as("已提交的种子行 + 本次批量行")
                    .isEqualTo(2L);
        });

        assertThat(fixtureRows()).hasSize(2);
    }

    @Test
    void batchRowsCarryTheSameGeneratedKeysAndAuditFieldsAsPerRowInsert() {
        long perRowUser = BASE_USER_ID + 700;
        long batchUser = BASE_USER_ID + 701;

        notificationService.send(perRowUser, TYPE, TITLE, CONTENT, BIZ_TYPE, "5");
        notificationService.sendBatch(List.of(batchUser), TYPE, TITLE, CONTENT, BIZ_TYPE, "5");

        Notification perRow = onlyRowOf(perRowUser);
        Notification batched = onlyRowOf(batchUser);

        assertThat(batched.getId()).as("ASSIGN_ID 主键必须由 MP 参数处理器填充").isNotNull().isPositive();
        assertThat(batched.getCreatedAt()).isNotNull();
        assertThat(batched.getUpdatedAt()).isNotNull();
        assertThat(batched.getCreatedBy()).isEqualTo(perRow.getCreatedBy());
        assertThat(batched.getUpdatedBy()).isEqualTo(perRow.getUpdatedBy());
        assertThat(batched.getType()).isEqualTo(perRow.getType());
        assertThat(batched.getTitle()).isEqualTo(perRow.getTitle());
        assertThat(batched.getContent()).isEqualTo(perRow.getContent());
        assertThat(batched.getBizType()).isEqualTo(perRow.getBizType());
        assertThat(batched.getBizId()).isEqualTo(perRow.getBizId());
        assertThat(batched.getReadFlag()).isZero();
        // deleted 未出现在列清单里，必须由 DDL DEFAULT 0 落到 0（否则逻辑删除语义会漂移）。
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM notification WHERE id = ?", Integer.class, batched.getId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM notification WHERE id = ?", Integer.class, perRow.getId()))
                .isZero();
    }

    private NotifyChannel inAppChannel() {
        return channels.stream()
                .filter(channel -> "in_app".equals(channel.channel()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少 in_app 通道"));
    }

    private List<Long> recipients(int count) {
        return LongStream.range(0, count).map(offset -> BASE_USER_ID + offset).boxed().toList();
    }

    private List<Notification> fixtureRows() {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getBizType, BIZ_TYPE));
    }

    private Notification onlyRowOf(long userId) {
        List<Notification> rows = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getBizType, BIZ_TYPE)
                .eq(Notification::getUserId, userId));
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private void insertCommittedFixtureRow(long userId, String bizId) {
        jdbcTemplate.update("INSERT INTO notification (id, user_id, type, title, content, biz_type, biz_id,"
                        + " read_flag, created_by, created_at, updated_by, updated_at, deleted)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, NOW(), 0, NOW(), 0)",
                seedId(userId), userId, TYPE, TITLE, CONTENT, BIZ_TYPE, bizId);
    }

    /** 从连接池另取一条连接插同一 user_id；1 秒锁等待上限，返回捕获到的异常（未被阻塞时返回 null）。 */
    private SQLException insertFromSeparateConnection(long userId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION innodb_lock_wait_timeout = 1");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO notification (id, user_id, type, title, content, biz_type, biz_id,"
                            + " read_flag, created_by, created_at, updated_by, updated_at, deleted)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, NOW(), 0, NOW(), 0)")) {
                insert.setLong(1, seedId(userId) + 1);
                insert.setLong(2, userId);
                insert.setString(3, TYPE);
                insert.setString(4, TITLE);
                insert.setString(5, CONTENT);
                insert.setString(6, BIZ_TYPE);
                insert.setString(7, "contender");
                insert.executeUpdate();
                return null;
            } catch (SQLException e) {
                return e;
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("反例连接执行失败", e);
        }
    }

    private long seedId(long userId) {
        return 944_000_000_000_000L + userId;
    }

    private void deleteFixtureRows() {
        jdbcTemplate.update("DELETE FROM notification WHERE biz_type = ?", BIZ_TYPE);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NotificationInsertProbeTestConfiguration {

        @Bean
        NotificationInsertProbe notificationInsertProbe() {
            return new NotificationInsertProbe();
        }
    }

    /**
     * 记录 {@code notification} 的 INSERT 在 {@code StatementHandler.prepare} 边界上的真实语句与连接。
     * 只有 armed 时记录，避免其它 IT 的通知写入混入。
     */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
            args = {Connection.class, Integer.class}))
    static final class NotificationInsertProbe implements Interceptor {

        private final List<PreparedInsert> captured = new CopyOnWriteArrayList<>();
        private volatile boolean armed;

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            if (armed) {
                StatementHandler handler = (StatementHandler) invocation.getTarget();
                String sql = handler.getBoundSql().getSql()
                        .replaceAll("\\s+", " ")
                        .trim()
                        .toLowerCase(Locale.ROOT);
                if (sql.startsWith("insert into notification")) {
                    captured.add(new PreparedInsert(sql, countPlaceholders(sql),
                            (Connection) invocation.getArgs()[0]));
                }
            }
            return invocation.proceed();
        }

        void arm() {
            captured.clear();
            armed = true;
        }

        void reset() {
            armed = false;
            captured.clear();
        }

        List<PreparedInsert> captured() {
            return new ArrayList<>(captured);
        }

        private static int countPlaceholders(String sql) {
            int count = 0;
            for (int i = 0; i < sql.length(); i++) {
                if (sql.charAt(i) == '?') {
                    count++;
                }
            }
            return count;
        }
    }

    record PreparedInsert(String sql, int placeholders, Connection connection) {
    }
}
