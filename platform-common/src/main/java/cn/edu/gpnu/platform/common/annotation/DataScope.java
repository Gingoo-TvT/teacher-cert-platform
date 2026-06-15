package cn.edu.gpnu.platform.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解：标注列表、导出、统计等查询入口。
 * <p>
 * 切面解析当前用户范围并写入线程上下文，MyBatis-Plus 数据权限拦截器基于 alias 自动追加行级过滤：
 * 学生=本人 / 学院=授权范围 / 教务处、签发、系统=全校。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 查询主表名或别名；拦截器只对匹配的表追加过滤条件，默认使用实际表名 */
    String alias() default "";

    /** 该查询需要的权限点；为空时仅按当前用户角色范围解释 */
    String permission() default "";
}
