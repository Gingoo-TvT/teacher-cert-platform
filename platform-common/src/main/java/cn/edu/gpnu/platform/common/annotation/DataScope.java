package cn.edu.gpnu.platform.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解：标注查询方法，由切面按当前用户范围注入过滤条件
 * （学生=本人 / 学院=授权范围 / 教务处·签发=全校）。实现见 platform-security（Phase 2）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 数据表别名（用于拼接 SQL 条件），默认空 */
    String alias() default "";

    /** 该查询需要的权限点；为空时仅按当前用户角色范围解释 */
    String permission() default "";
}
