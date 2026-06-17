package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.system.vo.NotificationVO;

public interface NotificationService {

    void send(Long userId, String type, String title, String content, String bizType, String bizId);

    PageResult<NotificationVO> list(Boolean read);

    long unreadCount();

    void markRead(Long id);

    void markAllRead();
}
