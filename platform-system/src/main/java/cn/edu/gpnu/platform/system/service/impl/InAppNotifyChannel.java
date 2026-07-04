package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.Notification;
import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import cn.edu.gpnu.platform.system.service.NotifyChannel;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public void sendBatch(List<Long> userIds, String type, String title, String content, String bizType, String bizId) {
        // Phase 44a：N 个收件人从 N 次单行 insert 合并为 1 次批量 insert；每行字段构造与 send() 完全一致。
        List<Notification> notifications = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setBizType(bizType);
            notification.setBizId(bizId);
            notification.setReadFlag(0);
            notifications.add(notification);
        }
        Db.saveBatch(notifications);
    }
}
