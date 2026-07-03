package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.common.exception.BizException;

import java.util.Arrays;

public enum ExchangeBatchStatus {
    PREVALIDATED,
    /**
     * 过渡态（Phase 42.2）：confirmImport 开头原子认领 PREVALIDATED→IMPORTING，杜绝两次并发确认重复导入；
     * 导入正常结束再翻 IMPORTED/FAILED。若进程在导入中途崩溃，批次会残留此态，rollback 可回收其已提交的部分导入行。
     */
    IMPORTING,
    IMPORTED,
    ROLLED_BACK,
    PARTIAL_ROLLBACK,
    FAILED,
    EXPORTED;

    public static ExchangeBatchStatus of(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BizException("未知批次状态: " + value));
    }
}
