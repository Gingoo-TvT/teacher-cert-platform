package cn.edu.gpnu.platform.exchange.support;

import org.springframework.stereotype.Component;

/**
 * 导入与回滚并发交错观察点。生产实现为空；集成测试用它确定性编排行锁与提交顺序。
 */
@Component
public class ExchangeImportHook {

    public void afterBatchLocked(Long batchId, Integer rowNo) {
        // 生产不注入行为。
    }

    public void beforeRollbackLock(Long batchId) {
        // 生产不注入行为。
    }

    public void afterRowCommitted(Long batchId, Integer rowNo) {
        // 生产不注入行为。
    }
}
