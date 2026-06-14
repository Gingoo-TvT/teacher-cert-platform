package cn.edu.gpnu.platform.security.aspect;

import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 数据权限切面：解析当前用户范围并写入线程上下文，service/mapper 基于上下文追加查询条件。
 */
@Slf4j
@Aspect
@Component
public class DataScopeAspect {

    private final DataScopeService dataScopeService;

    public DataScopeAspect(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Pointcut("@annotation(dataScope)")
    public void scope(DataScope dataScope) {
    }

    @Around(value = "scope(dataScope)", argNames = "pjp,dataScope")
    public Object around(ProceedingJoinPoint pjp, DataScope dataScope) throws Throwable {
        DataScopeContext.Scope scope = dataScopeService.resolve(dataScope.permission());
        DataScopeContext.set(scope);
        if (log.isDebugEnabled()) {
            log.debug("DataScope 拦截 {} alias={} permission={} scope={}",
                    pjp.getSignature(), dataScope.alias(), dataScope.permission(), scope.getScopeType());
        }
        try {
            return pjp.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }
}
