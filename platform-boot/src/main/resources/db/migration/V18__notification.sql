CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '接收用户ID',
    type        VARCHAR(64)  NOT NULL COMMENT '通知类型',
    title       VARCHAR(128) NOT NULL COMMENT '标题',
    content     VARCHAR(1000)         DEFAULT NULL COMMENT '内容',
    biz_type    VARCHAR(64)           DEFAULT NULL COMMENT '业务类型',
    biz_id      VARCHAR(64)           DEFAULT NULL COMMENT '业务ID',
    read_flag   TINYINT      NOT NULL DEFAULT 0 COMMENT '已读标记 0未读1已读',
    created_by  BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at  DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by  BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at  DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    KEY idx_notification_user_read (user_id, read_flag),
    KEY idx_notification_user_time (user_id, created_at),
    KEY idx_notification_biz (biz_type, biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内信通知';
