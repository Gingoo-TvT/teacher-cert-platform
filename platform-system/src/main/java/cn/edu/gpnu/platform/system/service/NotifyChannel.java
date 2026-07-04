package cn.edu.gpnu.platform.system.service;

import java.util.List;

public interface NotifyChannel {

    String channel();

    void send(Long userId, String type, String title, String content, String bizType, String bizId);

    /**
     * Phase 44a：批量发送（同一 type/title/content/bizType/bizId，仅 userId 不同）。
     * 默认实现按 userId 逐个调用 {@link #send}，保持与旧逐行调用完全一致的行为；
     * 有能力做真批量落库的通道（如 {@code InAppNotifyChannel}）应覆盖此方法。
     */
    default void sendBatch(List<Long> userIds, String type, String title, String content, String bizType, String bizId) {
        for (Long userId : userIds) {
            send(userId, type, title, content, bizType, bizId);
        }
    }
}
