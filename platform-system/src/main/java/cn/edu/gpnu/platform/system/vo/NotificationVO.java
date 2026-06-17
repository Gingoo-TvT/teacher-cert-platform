package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

@Data
public class NotificationVO {

    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String bizType;
    private String bizId;
    private Integer readFlag;
    private String createdAt;
}
