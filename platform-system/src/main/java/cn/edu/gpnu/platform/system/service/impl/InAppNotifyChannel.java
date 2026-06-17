package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.Notification;
import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import cn.edu.gpnu.platform.system.service.NotifyChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InAppNotifyChannel implements NotifyChannel {

    private final NotificationMapper notificationMapper;

    @Override
    public String channel() {
        return "in_app";
    }

    @Override
    public void send(Long userId, String type, String title, String content, String bizType, String bizId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setReadFlag(0);
        notificationMapper.insert(notification);
    }
}
