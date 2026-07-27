package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.security.aspect.DataScopeAspect;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BaseExecutor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-DS-1 组合链证据：真实 Mapper 代理经 {@link DataScopeAspect} 写入 context，随后由生产
 * {@link MybatisPlusConfig} 的 DataPermission/Pagination 插件改写 count 与 data SQL。
 *
 * <p>底层 executor 仅代替外部数据库并记录最终 BoundSql；Mapper、注解切面、MyBatis Executor 插件链、
 * 分页 count/data 两次查询均为生产同类对象，不直接调用 {@link DataScopeSqlHandler}。
 */
class DataScopeMapperChainTest {

    private static final List<ScopedUserRow> ALL_ROWS = List.of(
            new ScopedUserRow(1L, "college-201", 201L, 9101L),
            new ScopedUserRow(2L, "college-202", 202L, 9201L),
            new ScopedUserRow(3L, "college-203", 203L, 9301L)
    );

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    @Test
    void collegeScopeFlowsThroughAliasAndBothPaginationStatements() {
        DataScopeContext.Scope scope = scope(DataScopeContext.ScopeType.COLLEGE);
        scope.setCollegeIds(new LinkedHashSet<>(List.of(201L, 202L)));

        try (Harness harness = new Harness(scope)) {
            QueryResult result = harness.query();

            assertThat(harness.scopeService.permission()).isEqualTo("system:user:manage");
            assertThat(result.scopeType()).isEqualTo(DataScopeContext.ScopeType.COLLEGE);
            assertThat(result.alias()).isEqualTo("su");
            assertThat(harness.executor.countSql())
                    .contains("su.college_id IN (201, 202)")
                    .doesNotContain("LIMIT");
            assertThat(harness.executor.dataSql())
                    .contains("su.college_id IN (201, 202)")
                    .contains("LIMIT");
            assertThat(result.total()).isEqualTo(2);
            assertThat(result.rows()).extracting(ScopedUserRow::collegeId)
                    .containsExactly(201L, 202L);
            assertThat(DataScopeContext.get()).as("切面退出后必须清理线程上下文").isNull();
        }
    }

    @Test
    void schoolScopeLeavesCountAndDataSqlUnrestricted() {
        try (Harness harness = new Harness(scope(DataScopeContext.ScopeType.SCHOOL))) {
            QueryResult result = harness.query();

            assertThat(result.total()).isEqualTo(3);
            assertThat(result.rows()).extracting(ScopedUserRow::id)
                    .containsExactly(1L, 2L);
            assertThat(harness.executor.countSql())
                    .doesNotContain("college_id IN", "student_id =", "id = -1");
            assertThat(harness.executor.dataSql())
                    .doesNotContain("college_id IN", "student_id =", "id = -1")
                    .contains("LIMIT");
        }
    }

    @Test
    void selfScopeUsesStudentIdentityInCountAndDataSql() {
        DataScopeContext.Scope scope = scope(DataScopeContext.ScopeType.SELF);
        scope.setStudentId(9101L);

        try (Harness harness = new Harness(scope)) {
            QueryResult result = harness.query();

            assertThat(result.total()).isEqualTo(1);
            assertThat(result.rows()).extracting(ScopedUserRow::studentId)
                    .containsExactly(9101L);
            assertThat(harness.executor.countSql()).contains("su.student_id = 9101");
            assertThat(harness.executor.dataSql()).contains("su.student_id = 9101");
        }
    }

    @Test
    void noneScopeFailsClosedInCountAndDataSql() {
        try (Harness harness = new Harness(scope(DataScopeContext.ScopeType.NONE))) {
            QueryResult result = harness.query();

            assertThat(result.total()).isZero();
            assertThat(result.rows()).isEmpty();
            assertThat(harness.executor.countSql()).contains("su.id = -1");
            assertThat(harness.executor.hasDataSql())
                    .as("count=0 时分页插件必须跳过无意义的数据查询")
                    .isFalse();
        }
    }

    @Test
    void emptyCollegeScopeFailsClosedInsteadOfBecomingSchoolWide() {
        try (Harness harness = new Harness(scope(DataScopeContext.ScopeType.COLLEGE))) {
            QueryResult result = harness.query();

            assertThat(result.total()).isZero();
            assertThat(result.rows()).isEmpty();
            assertThat(harness.executor.countSql()).contains("su.id = -1");
            assertThat(harness.executor.hasDataSql())
                    .as("空学院集合的 count=0 必须阻止后续数据查询")
                    .isFalse();
        }
    }

    private DataScopeContext.Scope scope(DataScopeContext.ScopeType type) {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(type);
        return scope;
    }

    interface ScopedUserMapper {

        @Select("""
                SELECT su.id, su.username, su.college_id, su.student_id
                  FROM sys_user su
                 WHERE su.deleted = 0
                 ORDER BY su.id
                """)
        List<ScopedUserRow> selectScoped(Page<ScopedUserRow> page);
    }

    static class ScopedQueryService {

        private final ScopedUserMapper mapper;

        ScopedQueryService(ScopedUserMapper mapper) {
            this.mapper = mapper;
        }

        @DataScope(alias = "su", permission = "system:user:manage")
        public QueryResult query() {
            DataScopeContext.Scope active = DataScopeContext.get();
            Page<ScopedUserRow> page = new Page<>(1, 2);
            List<ScopedUserRow> rows = mapper.selectScoped(page);
            return new QueryResult(page.getTotal(), rows, active.getScopeType(), active.getAlias());
        }
    }

    record ScopedUserRow(Long id, String username, Long collegeId, Long studentId) {
    }

    record QueryResult(long total, List<ScopedUserRow> rows,
                       DataScopeContext.ScopeType scopeType, String alias) {
    }

    static final class StubDataScopeService implements DataScopeService {

        private final DataScopeContext.Scope scope;
        private String permission;

        StubDataScopeService(DataScopeContext.Scope scope) {
            this.scope = scope;
        }

        @Override
        public DataScopeContext.Scope resolve(String permissionCode) {
            permission = permissionCode;
            return scope;
        }

        @Override
        public boolean hasAllSchoolScope(Long userId, String permissionCode) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public boolean hasSystemScope(Long userId, String permissionCode) {
            throw new UnsupportedOperationException("not used by this test");
        }

        String permission() {
            return permission;
        }
    }

    static final class Harness implements AutoCloseable {

        private final CapturingExecutor executor;
        private final StubDataScopeService scopeService;
        private final SqlSession session;
        private final ScopedQueryService service;

        Harness(DataScopeContext.Scope scope) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(ScopedUserMapper.class);
            configuration.addInterceptor(new MyBatisPlusConfig().mybatisPlusInterceptor());

            executor = new CapturingExecutor(configuration);
            Executor pluginExecutor = executor;
            for (Interceptor interceptor : configuration.getInterceptors()) {
                pluginExecutor = (Executor) interceptor.plugin(pluginExecutor);
            }
            executor.setExecutorWrapper(pluginExecutor);
            session = new DefaultSqlSession(configuration, pluginExecutor, false);

            scopeService = new StubDataScopeService(scope);
            ScopedQueryService target = new ScopedQueryService(session.getMapper(ScopedUserMapper.class));
            AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
            proxyFactory.addAspect(new DataScopeAspect(scopeService));
            service = proxyFactory.getProxy();
        }

        QueryResult query() {
            return service.query();
        }

        @Override
        public void close() {
            session.close();
        }
    }

    static final class CapturingExecutor extends BaseExecutor {

        private final List<String> sql = new ArrayList<>();

        CapturingExecutor(MybatisConfiguration configuration) {
            super(configuration, new NoOpTransaction());
        }

        @Override
        protected int doUpdate(MappedStatement mappedStatement, Object parameter) {
            throw new UnsupportedOperationException("read-only test executor");
        }

        @Override
        protected List<BatchResult> doFlushStatements(boolean rollback) {
            return List.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <E> List<E> doQuery(MappedStatement mappedStatement, Object parameter,
                                      RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
            String finalSql = normalizeSql(boundSql.getSql());
            sql.add(finalSql);
            List<ScopedUserRow> visible = visibleRows(finalSql);
            if (finalSql.toLowerCase(Locale.ROOT).contains("count(")) {
                return (List<E>) List.of((long) visible.size());
            }
            if (finalSql.toLowerCase(Locale.ROOT).contains(" limit ")) {
                visible = visible.stream().limit(2).toList();
            }
            return (List<E>) visible;
        }

        @Override
        protected <E> Cursor<E> doQueryCursor(MappedStatement mappedStatement, Object parameter,
                                              RowBounds rowBounds, BoundSql boundSql) {
            throw new UnsupportedOperationException("cursor not used by this test");
        }

        String countSql() {
            return sql.stream()
                    .filter(statement -> statement.toLowerCase(Locale.ROOT).contains("count("))
                    .findFirst()
                    .orElseThrow();
        }

        String dataSql() {
            return sql.stream()
                    .filter(statement -> !statement.toLowerCase(Locale.ROOT).contains("count("))
                    .findFirst()
                    .orElseThrow();
        }

        boolean hasDataSql() {
            return sql.stream().anyMatch(statement -> !statement.toLowerCase(Locale.ROOT).contains("count("));
        }

        private List<ScopedUserRow> visibleRows(String statement) {
            String normalized = statement.toLowerCase(Locale.ROOT);
            if (normalized.contains("su.id = -1")) {
                return List.of();
            }
            if (normalized.contains("su.student_id = 9101")) {
                return ALL_ROWS.stream().filter(row -> row.studentId().equals(9101L)).toList();
            }
            if (normalized.matches(".*su\\.college_id\\s+in\\s*\\(201\\s*,\\s*202\\).*")) {
                return ALL_ROWS.stream().filter(row -> Set.of(201L, 202L).contains(row.collegeId())).toList();
            }
            return ALL_ROWS;
        }

        private String normalizeSql(String statement) {
            return statement.replaceAll("\\s+", " ").trim();
        }
    }

    static final class NoOpTransaction implements Transaction {

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("no external database");
        }

        @Override
        public void commit() {
            // no-op
        }

        @Override
        public void rollback() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public Integer getTimeout() throws SQLException {
            return null;
        }
    }
}
