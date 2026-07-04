package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.api.PageQuery;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.Notification;
import cn.edu.gpnu.platform.system.mapper.NotificationMapper;
import cn.edu.gpnu.platform.system.service.NotificationService;
import cn.edu.gpnu.platform.system.service.NotifyChannel;
import cn.edu.gpnu.platform.system.vo.NotificationVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final List<NotifyChannel> channels;

    @Override
    public void send(Long userId, String type, String title, String content, String bizType, String bizId) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(title)) {
            return;
        }
        for (NotifyChannel channel : channels) {
            try {
                channel.send(userId, safe(type, "SYSTEM", 64), safe(title, "通知", 128),
                        trim(content, 1000), trim(bizType, 64), trim(bizId, 64));
            } catch (Exception e) {
                log.warn("通知通道发送失败: channel={}, userId={}, title={}", channel.channel(), userId, title, e);
            }
        }
    }

    @Override
    public void sendBatch(Collection<Long> userIds, String type, String title, String content, String bizType, String bizId) {
        if (userIds == null || userIds.isEmpty() || !StringUtils.hasText(title)) {
            return;
        }
        List<Long> validIds = userIds.stream().filter(id -> id != null && id > 0).toList();
        if (validIds.isEmpty()) {
            return;
        }
        for (NotifyChannel channel : channels) {
            try {
                channel.sendBatch(validIds, safe(type, "SYSTEM", 64), safe(title, "通知", 128),
                        trim(content, 1000), trim(bizType, 64), trim(bizId, 64));
            } catch (Exception e) {
                log.warn("通知通道批量发送失败: channel={}, count={}, title={}", channel.channel(), validIds.size(), title, e);
            }
        }
    }

    // Phase 44e（P1-1 真分页 rollout · 变体 B'）：本人通知列表，范围过滤（eq userId）本就在 wrapper 里，
    // 直接套用无 @DataScope 的分页配方即可——selectPage 天然带上 eq(userId)，total/当页都已是「本人」范围内的真实结果。
    @Override
    public PageResult<NotificationVO> list(Boolean read, Integer page, Integer size) {
        Long userId = currentUserId();
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                .orderByDesc(Notification::getId);
        if (read != null) {
            wrapper.eq(Notification::getReadFlag, read ? 1 : 0);
        }
        Page<Notification> result = notificationMapper.selectPage(PageQuery.of(page, size), wrapper);
        List<NotificationVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public long unreadCount() {
        Long userId = currentUserId();
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0));
        return count == null ? 0 : count;
    }

    @Override
    public void markRead(Long id) {
        if (id == null) {
            throw new BizException("通知ID不能为空");
        }
        Long userId = currentUserId();
        int updated = notificationMapper.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .set(Notification::getReadFlag, 1));
        if (updated == 0) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该通知");
        }
    }

    @Override
    public void markAllRead() {
        Long userId = currentUserId();
        notificationMapper.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0)
                .set(Notification::getReadFlag, 1));
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        return userId;
    }

    private NotificationVO toVO(Notification entity) {
        NotificationVO vo = new NotificationVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setType(entity.getType());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setReadFlag(entity.getReadFlag());
        vo.setCreatedAt(entity.getCreatedAt() == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(entity.getCreatedAt()));
        return vo;
    }

    private String safe(String value, String fallback, int max) {
        String text = StringUtils.hasText(value) ? value.trim() : fallback;
        return trim(text, max);
    }

    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.codePointCount(0, text.length()) <= max
                ? text
                : text.substring(0, text.offsetByCodePoints(0, max));
    }
}
