package cn.edu.gpnu.platform.system.service;

public interface NotifyChannel {

    String channel();

    void send(Long userId, String type, String title, String content, String bizType, String bizId);
}
