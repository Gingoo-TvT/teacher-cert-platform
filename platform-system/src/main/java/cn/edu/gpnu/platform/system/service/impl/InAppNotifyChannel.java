package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.Notification;
import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import cn.edu.gpnu.platform.system.service.NotifyChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        // Phase 44f（修复 44a 引入的锁等待）：改回同事务逐行 insert，字段构造与 send() 完全一致。
        // 44a 曾用 Db.saveBatch 合并为单条批量 insert，但它以 BATCH 执行器另开一个 SqlSession/连接，不参与
        // 外层事务；当本方法在外层 @Transactional 内被调用（二审 secondReview → ReviewNotificationHelper
        // .notifyRole → notificationService.sendBatch），另开连接的批量 insert 与外层事务持有的行锁互相等待，
        // 间歇性触发 "Lock wait timeout exceeded"。收件人为单学院某角色成员、扇出很小，逐行 insert 走标准
        // insert（ASSIGN_ID 主键 + 审计字段自动填充）在同一连接/事务内完成、零竞争。
        for (Long userId : userIds) {
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
}
