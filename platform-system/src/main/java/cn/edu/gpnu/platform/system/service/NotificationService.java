package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.system.vo.NotificationVO;

import java.util.Collection;

public interface NotificationService {

    void send(Long userId, String type, String title, String content, String bizType, String bizId);

    /**
     * Phase 44a：同一 type/title/content/bizType/bizId 批量通知多个收件人（如审核待办抄送、视频评审分配），
     * 用单次批量落库替代按 userId 逐行 insert；guard 语义（title 为空/userId 非法即跳过）与 {@link #send} 一致。
     */
    void sendBatch(Collection<Long> userIds, String type, String title, String content, String bizType, String bizId);

    PageResult<NotificationVO> list(Boolean read, Integer page, Integer size);

    long unreadCount();

    void markRead(Long id);

    void markAllRead();
}
