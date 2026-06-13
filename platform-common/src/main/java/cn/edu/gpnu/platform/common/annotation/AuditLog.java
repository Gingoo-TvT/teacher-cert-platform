package cn.edu.gpnu.platform.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解：标注的方法成功后写 audit_log（操作人/时间/前后状态/IP）。
 * 切面实现见 platform-security（Phase 0/2）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 业务类型，如 student/material/cert */
    String bizType();

    /** 操作，如 firstReview/secondReview/generate/void */
    String operation();
}
