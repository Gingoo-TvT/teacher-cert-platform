package cn.edu.gpnu.platform.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解：普通数据库写与业务同事务记录 audit_log；
 * 无法共同回滚的外部边界可在业务前先记录（操作人/时间/前后状态/IP）。
 * 切面实现见 platform-boot。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 业务类型，如 student/material/cert */
    String bizType();

    /** 操作，如 firstReview/secondReview/generate/void */
    String operation();

    /**
     * 是否在业务执行前先固化审计。
     *
     * <p>仅用于文件流、对象存储、Redis 或独立子事务等无法与主库审计原子回滚的边界；
     * 普通数据库写保持默认值，由切面把业务与审计包在同一事务内。</p>
     */
    boolean before() default false;
}
