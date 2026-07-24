package cn.edu.gpnu.platform.system.service;

import org.springframework.stereotype.Component;

/**
 * 学院父行锁的确定性交错观察点。生产实现为空；集成测试可替换该 bean 编排锁等待与提交顺序。
 */
@Component
public class CollegeParentLockHook {

    public void beforeLock(CollegeParentGuard.Operation operation, Long collegeId) {
        // 生产不注入行为。
    }

    public void afterLock(CollegeParentGuard.Operation operation, Long collegeId, Integer status) {
        // 生产不注入行为。
    }
}
