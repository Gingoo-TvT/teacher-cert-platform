package cn.edu.gpnu.platform.boot.aspect;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.support.AuditIp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 审计切面：@AuditLog 标注方法成功执行后写 audit_log。
 * 前后状态/对象可由业务后续通过上下文补充；此处记录操作人、类型、操作、IP。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    @Pointcut("@annotation(auditLog)")
    public void audit(AuditLog auditLog) {
    }

    @Around(value = "audit(auditLog)", argNames = "pjp,auditLog")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        Object result = pjp.proceed();
        try {
            SysAuditLog entity = new SysAuditLog();
            entity.setBizType(auditLog.bizType());
            entity.setOperation(auditLog.operation());
            entity.setOperatorId(UserContext.getUserIdOrSystem());
            entity.setIp(AuditIp.clientIp());
            auditLogService.record(entity);
        } catch (Exception e) {
            log.warn("写审计日志失败: {}", e.getMessage());
        }
        return result;
    }
}
