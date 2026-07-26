package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.entity.Notification;
import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import cn.edu.gpnu.platform.system.service.NotifyChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InAppNotifyChannel implements NotifyChannel {

    /**
     * 单条 multi-values INSERT 的最大行数。单行最坏约 5 KiB（content 1000 + title 128 码点，utf8mb4 至多
     * 4 字节/码点），500 行约 2.5 MiB，低于 MySQL 8 默认 {@code max_allowed_packet} 与本项目 64 MiB 契约
     * （见 {@code docker-compose*.yml}）；超出则按序多条语句，仍在同一事务内。
     */
    private static final int BATCH_SIZE = 500;

    private final NotificationMapper notificationMapper;

    @Override
    public String channel() {
        return "in_app";
    }

    @Override
    public void send(Long userId, String type, String title, String content, String bizType, String bizId) {
        notificationMapper.insert(newNotification(userId, type, title, content, bizType, bizId));
    }

    /**
     * Phase 44（PG-M3 整改）：恢复真批量写，且不再引入第二个数据库连接。
     *
     * <p>历史：44a 用 {@code Db.saveBatch} 做批量，它以 BATCH 执行器<b>另开 SqlSession/连接</b>、不参与外层
     * {@code @Transactional}；当本方法在外层事务内被调用（二审 {@code secondReview} →
     * {@code ReviewNotificationHelper.notifyRole} → {@code notificationService.sendBatch}）时，另开连接的插入与
     * 外层事务持有的行锁互相等待，间歇触发 {@code Lock wait timeout exceeded}（约 50s）。44f 因此退回逐行
     * insert——正确性对了，但 44a 的性能交付被撤销（审计 PG-M3）。
     *
     * <p>本次改用 {@code NotificationMapper.insertBatch}：普通 mapper 调用经 {@code SqlSessionTemplate} 复用
     * <b>事务绑定的同一 SqlSession/连接</b>，因此 ①N 次 INSERT 收敛为 ⌈N/{@value #BATCH_SIZE}⌉ 条语句；
     * ②插入与外层事务同生共死（外层回滚则通知一并回滚），不可能与本事务自身持有的锁互等。
     * 字段构造与 {@link #send} 共用 {@link #newNotification}，逐行/批量两路完全一致。
     */
    @Override
    public void sendBatch(List<Long> userIds, String type, String title, String content, String bizType, String bizId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Notification> pending = new ArrayList<>(Math.min(userIds.size(), BATCH_SIZE));
        for (Long userId : userIds) {
            pending.add(newNotification(userId, type, title, content, bizType, bizId));
            if (pending.size() == BATCH_SIZE) {
                notificationMapper.insertBatch(pending);
                pending.clear();
            }
        }
        if (!pending.isEmpty()) {
            notificationMapper.insertBatch(pending);
        }
    }

    /** 主键与审计字段留空：逐行 insert 与 insertBatch 都由 MP 的参数处理器统一填充 ASSIGN_ID 与审计字段。 */
    private Notification newNotification(Long userId, String type, String title, String content,
                                         String bizType, String bizId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setReadFlag(0);
        return notification;
    }
}
