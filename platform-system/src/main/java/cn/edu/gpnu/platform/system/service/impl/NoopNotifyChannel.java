package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.system.service.NotifyChannel;
import org.springframework.stereotype.Component;

/**
 * 预留邮件/短信通道扩展点；一期不发送外部消息。
 */
@Component
public class NoopNotifyChannel implements NotifyChannel {

    @Override
    public String channel() {
        return "noop";
    }

    @Override
    public void send(Long userId, String type, String title, String content, String bizType, String bizId) {
        // 外部通道后续接入，站内信由 InAppNotifyChannel 落库。
    }
}
