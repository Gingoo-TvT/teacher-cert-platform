package cn.edu.gpnu.platform.boot.aspect;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.support.AuditIp;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogAspectTest {

    @Test
    void auditFailurePropagatesAfterSuccessfulTarget() throws Throwable {
        AuditLogService service = mock(AuditLogService.class);
        AuditIp auditIp = mock(AuditIp.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);
        AuditLogAspect aspect = new AuditLogAspect(
                service, auditIp, new TransactionTemplate(transactionManager));
        AuditLog annotation = AuditedTarget.class.getDeclaredMethod("call").getAnnotation(AuditLog.class);
        IllegalStateException failure = new IllegalStateException("audit unavailable");
        when(joinPoint.proceed()).thenReturn("success");
        when(auditIp.clientIp()).thenReturn("203.0.113.27");
        doThrow(failure).when(service).record(any(SysAuditLog.class));

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation)).isSameAs(failure);
        verify(joinPoint).proceed();
        verify(transactionManager).rollback(status);
        verify(transactionManager, never()).commit(status);
    }

    @Test
    void defaultModeCommitsTargetAndAuditTogether() throws Throwable {
        AuditLogService service = mock(AuditLogService.class);
        AuditIp auditIp = mock(AuditIp.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);
        when(joinPoint.proceed()).thenReturn("success");
        AuditLogAspect aspect = new AuditLogAspect(
                service, auditIp, new TransactionTemplate(transactionManager));
        AuditLog annotation = AuditedTarget.class.getDeclaredMethod("call").getAnnotation(AuditLog.class);

        assertThat(aspect.around(joinPoint, annotation)).isEqualTo("success");

        verify(service).record(any(SysAuditLog.class));
        verify(transactionManager).commit(status);
    }

    @Test
    void defaultModeRollsBackAndDoesNotWriteSuccessAuditWhenTargetFails() throws Throwable {
        AuditLogService service = mock(AuditLogService.class);
        AuditIp auditIp = mock(AuditIp.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);
        IllegalStateException failure = new IllegalStateException("target rejected");
        when(joinPoint.proceed()).thenThrow(failure);
        AuditLogAspect aspect = new AuditLogAspect(
                service, auditIp, new TransactionTemplate(transactionManager));
        AuditLog annotation = AuditedTarget.class.getDeclaredMethod("call").getAnnotation(AuditLog.class);

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation)).isSameAs(failure);

        verify(service, never()).record(any(SysAuditLog.class));
        verify(transactionManager).rollback(status);
        verify(transactionManager, never()).commit(status);
    }

    @Test
    void beforeModeBlocksExternalBoundaryWhenAuditFails() throws Throwable {
        AuditLogService service = mock(AuditLogService.class);
        AuditIp auditIp = mock(AuditIp.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        AuditLogAspect aspect = new AuditLogAspect(
                service, auditIp, new TransactionTemplate(transactionManager));
        AuditLog annotation = AuditedTarget.class.getDeclaredMethod("external").getAnnotation(AuditLog.class);
        IllegalStateException failure = new IllegalStateException("audit unavailable");
        doThrow(failure).when(service).record(any(SysAuditLog.class));

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation)).isSameAs(failure);

        verify(joinPoint, never()).proceed();
        verify(transactionManager, never()).getTransaction(any());
    }

    private static final class AuditedTarget {

        @AuditLog(bizType = "test", operation = "write")
        private void call() {
        }

        @AuditLog(bizType = "test", operation = "external", before = true)
        private void external() {
        }
    }
}
