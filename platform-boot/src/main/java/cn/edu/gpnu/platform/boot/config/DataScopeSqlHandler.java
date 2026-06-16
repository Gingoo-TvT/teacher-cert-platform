package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.common.context.DataScopeContext;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 @DataScope 解析出的线程上下文转换为 SQL WHERE 片段。
 */
public class DataScopeSqlHandler implements MultiDataPermissionHandler {

    private static final long DENY_ID = -1L;
    private static final Map<String, Rule> TABLE_RULES = tableRules();

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        DataScopeContext.Scope scope = DataScopeContext.get();
        if (scope == null || scope.allSchool()) {
            return null;
        }
        Rule rule = ruleFor(table, scope);
        if (rule == null) {
            return null;
        }
        return switch (scope.getScopeType()) {
            case COLLEGE -> collegeExpression(table, rule, scope);
            case SELF -> selfExpression(table, rule, scope);
            case ASSIGNED, NONE -> denyExpression(table, rule);
            default -> null;
        };
    }

    private Rule ruleFor(Table table, DataScopeContext.Scope scope) {
        String tableName = normalize(table.getName());
        String tableAlias = table.getAlias() == null ? null : normalize(table.getAlias().getName());
        String requestedAlias = normalize(scope.getAlias());
        if (StringUtils.hasText(requestedAlias)
                && !requestedAlias.equals(tableName)
                && !requestedAlias.equals(tableAlias)) {
            return null;
        }
        return TABLE_RULES.get(tableName);
    }

    private Expression collegeExpression(Table table, Rule rule, DataScopeContext.Scope scope) {
        if (rule.majorColumn != null && !scope.getMajorIds().isEmpty()) {
            return inExpression(column(table, rule.majorColumn), scope.getMajorIds());
        }
        if (rule.collegeColumn != null && !scope.getCollegeIds().isEmpty()) {
            return inExpression(column(table, rule.collegeColumn), scope.getCollegeIds());
        }
        return denyExpression(table, rule);
    }

    private Expression selfExpression(Table table, Rule rule, DataScopeContext.Scope scope) {
        if (rule.studentColumn != null && scope.getStudentId() != null) {
            return equalsExpression(column(table, rule.studentColumn), scope.getStudentId());
        }
        if (rule.userColumn != null && scope.getUserId() != null) {
            return equalsExpression(column(table, rule.userColumn), scope.getUserId());
        }
        return denyExpression(table, rule);
    }

    private Expression denyExpression(Table table, Rule rule) {
        if (rule.idColumn != null) {
            return equalsExpression(column(table, rule.idColumn), DENY_ID);
        }
        if (rule.collegeColumn != null) {
            return equalsExpression(column(table, rule.collegeColumn), DENY_ID);
        }
        if (rule.studentColumn != null) {
            return equalsExpression(column(table, rule.studentColumn), DENY_ID);
        }
        return equalsExpression(column(table, "id"), DENY_ID);
    }

    private InExpression inExpression(Column column, Set<Long> ids) {
        List<LongValue> values = ids.stream().map(LongValue::new).toList();
        return new InExpression(column, new ParenthesedExpressionList<>(new ExpressionList<>(values)));
    }

    private EqualsTo equalsExpression(Column column, Long value) {
        if (value == null) {
            return new EqualsTo(column, new NullValue());
        }
        return new EqualsTo(column, new LongValue(value));
    }

    private Column column(Table table, String columnName) {
        Table scopedTable = new Table(tableQualifier(table));
        return new Column(scopedTable, columnName);
    }

    private String tableQualifier(Table table) {
        if (table.getAlias() != null && StringUtils.hasText(table.getAlias().getName())) {
            return table.getAlias().getName();
        }
        return table.getName();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static Map<String, Rule> tableRules() {
        Map<String, Rule> rules = new LinkedHashMap<>();
        rules.put("sys_user", new Rule("id", "college_id", null, "student_id", "id"));
        rules.put("student", new Rule("id", "college_id", null, "id", null));
        rules.put("training_profile", new Rule("id", "college_id", null, "student_id", null));
        rules.put("process_material", new Rule("id", "college_id", null, "student_id", null));
        rules.put("sys_college", new Rule("id", "id", null, null, null));
        rules.put("sys_major", new Rule("id", "college_id", "id", null, null));
        rules.put("major_training_goal", new Rule("id", null, "major_id", null, null));
        return Map.copyOf(rules);
    }

    private record Rule(String idColumn, String collegeColumn, String majorColumn, String studentColumn, String userColumn) {
    }
}
