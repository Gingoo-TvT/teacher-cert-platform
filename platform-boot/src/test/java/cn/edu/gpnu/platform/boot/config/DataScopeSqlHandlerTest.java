package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.common.context.DataScopeContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 退回整改（U-004）：`docs/phase-00-脚手架.md` §8-8 / T-DS-1 的可重复证据——
 * {@code @DataScope} 解析出的线程上下文经 {@link DataScopeSqlHandler} 生成的 SQL 条件必须正确。
 *
 * <p>T-DS-1 的原始口径：COLLEGE 范围用户查询 → SQL 含 {@code college_id = ?}；ALL 范围 → 无范围条件。
 * 现行实现按集合注入（{@code college_id IN (...)}），全校/系统范围返回 null（不追加条件），此处按
 * 现行合同断言，并补齐 SELF / ASSIGNED / NONE（fail-closed 拒绝）与别名不匹配等关键分支。
 * 这是纯 SQL 改写断言，不需要容器或数据库，任何一次 {@code mvn test} 都会重放。
 */
class DataScopeSqlHandlerTest {

    private final DataScopeSqlHandler handler = new DataScopeSqlHandler();

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    @Test
    void collegeScopeAppendsCollegeInCondition() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.COLLEGE);
        scope.setCollegeIds(Set.of(201L));
        DataScopeContext.set(scope);

        Expression expression = handler.getSqlSegment(new Table("student"), null, "ms.id");

        assertThat(expression).as("COLLEGE 范围必须注入学院条件").isNotNull();
        assertThat(expression.toString()).isEqualTo("student.college_id IN (201)");
    }

    @Test
    void schoolWideScopeAppendsNoCondition() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.SCHOOL);
        DataScopeContext.set(scope);

        assertThat(handler.getSqlSegment(new Table("student"), null, "ms.id"))
                .as("全校范围不追加任何条件")
                .isNull();
    }

    @Test
    void missingContextAppendsNoCondition() {
        assertThat(handler.getSqlSegment(new Table("student"), null, "ms.id"))
                .as("无 @DataScope 上下文的查询不受影响")
                .isNull();
    }

    @Test
    void selfScopePinsToOwnStudentRow() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.SELF);
        scope.setStudentId(9101L);
        DataScopeContext.set(scope);

        Expression expression = handler.getSqlSegment(new Table("process_material"), null, "ms.id");

        assertThat(expression).isNotNull();
        assertThat(expression.toString()).isEqualTo("process_material.student_id = 9101");
    }

    @Test
    void assignedScopePinsToReviewerColumn() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.ASSIGNED);
        scope.setUserId(3005L);
        DataScopeContext.set(scope);

        Expression expression = handler.getSqlSegment(new Table("video_review_task"), null, "ms.id");

        assertThat(expression).isNotNull();
        assertThat(expression.toString()).isEqualTo("video_review_task.reviewer_id = 3005");
    }

    @Test
    void emptyCollegeSetFailsClosedInsteadOfLeakingAllRows() {
        // 范围解析结果为空集合时必须 fail-closed（id = -1），绝不能退化为「无条件＝全量可见」。
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.COLLEGE);
        DataScopeContext.set(scope);

        Expression expression = handler.getSqlSegment(new Table("student"), null, "ms.id");

        assertThat(expression).isNotNull();
        assertThat(expression.toString()).isEqualTo("student.id = -1");
    }

    @Test
    void noneScopeDeniesEverything() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.NONE);
        DataScopeContext.set(scope);

        Expression expression = handler.getSqlSegment(new Table("certificate"), null, "ms.id");

        assertThat(expression).isNotNull();
        assertThat(expression.toString()).isEqualTo("certificate.id = -1");
    }

    @Test
    void unrelatedTableIsLeftUntouched() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.COLLEGE);
        scope.setCollegeIds(Set.of(201L));
        DataScopeContext.set(scope);

        assertThat(handler.getSqlSegment(new Table("sys_param"), null, "ms.id"))
                .as("未注册范围规则的表不追加条件")
                .isNull();
    }

    @Test
    void aliasMismatchSkipsInjectionAndAliasMatchUsesAlias() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.COLLEGE);
        scope.setCollegeIds(Set.of(201L));
        scope.setAlias("s");
        DataScopeContext.set(scope);

        assertThat(handler.getSqlSegment(new Table("training_profile"), null, "ms.id"))
                .as("@DataScope(alias=\"s\") 只作用于别名/表名匹配的表")
                .isNull();

        Table aliased = new Table("student");
        aliased.setAlias(new net.sf.jsqlparser.expression.Alias("s"));
        Expression expression = handler.getSqlSegment(aliased, null, "ms.id");
        assertThat(expression).isNotNull();
        assertThat(expression.toString()).isEqualTo("s.college_id IN (201)");
    }
}
