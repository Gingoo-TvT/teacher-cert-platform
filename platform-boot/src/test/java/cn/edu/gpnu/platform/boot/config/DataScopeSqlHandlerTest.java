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
 * <p>权威 T-DS-1 已按 R10 对齐现行合同：学院集合使用 {@code college_id IN (...)}，SCHOOL
 * 不追加范围条件，空集合与 NONE 失败关闭；此处另补 SELF / ASSIGNED 与别名分支。
 * 这是纯 SQL handler 断言；注解、上下文、Mapper 与分页插件的组合链见
 * {@link DataScopeMapperChainTest}，二者均不需要外部容器。
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
