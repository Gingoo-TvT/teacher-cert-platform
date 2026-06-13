package cn.edu.gpnu.platform.security.aspect;

import cn.edu.gpnu.platform.common.annotation.DataScope;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 数据权限切面（骨架）。Phase 2 接入登录后，按当前用户范围
 * （学生=本人 / 学院=授权范围 / 教务处·签发=全校）向查询注入 SQL 过滤条件。
 */
@Slf4j
@Aspect
@Component
public class DataScopeAspect {

    @Pointcut("@annotation(dataScope)")
    public void scope(DataScope dataScope) {
    }

    @Around(value = "scope(dataScope)", argNames = "pjp,dataScope")
    public Object around(ProceedingJoinPoint pjp, DataScope dataScope) throws Throwable {
        // TODO(Phase 2): 解析当前用户范围并注入查询条件，alias=dataScope.alias()
        if (log.isDebugEnabled()) {
            log.debug("DataScope 拦截 {} alias={}", pjp.getSignature(), dataScope.alias());
        }
        return pjp.proceed();
    }
}
