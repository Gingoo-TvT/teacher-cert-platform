package cn.edu.gpnu.platform.boot.aspect;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.support.AuditIp;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 审计切面：普通数据库写与审计同事务提交；不能共同回滚的外部边界先审计再执行。
 * 前后状态/对象可由业务后续通过上下文补充；此处记录操作人、类型、操作、IP。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final AuditIp auditIp;
    private final TransactionTemplate transactionTemplate;

    @Pointcut("@annotation(auditLog)")
    public void audit(AuditLog auditLog) {
    }

    @Around(value = "audit(auditLog)", argNames = "pjp,auditLog")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        if (auditLog.before()) {
            record(auditLog);
            return pjp.proceed();
        }
        try {
            return transactionTemplate.execute(status -> {
                try {
                    Object result = pjp.proceed();
                    record(auditLog);
                    return result;
                } catch (Throwable throwable) {
                    throw new AuditedInvocationException(throwable);
                }
            });
        } catch (AuditedInvocationException exception) {
            throw exception.getCause();
        }
    }

    private void record(AuditLog auditLog) {
        SysAuditLog entity = new SysAuditLog();
        entity.setBizType(auditLog.bizType());
        entity.setOperation(auditLog.operation());
        entity.setOperatorId(UserContext.getUserIdOrSystem());
        entity.setIp(auditIp.clientIp());
        auditLogService.record(entity);
    }

    private static final class AuditedInvocationException extends RuntimeException {

        private AuditedInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
