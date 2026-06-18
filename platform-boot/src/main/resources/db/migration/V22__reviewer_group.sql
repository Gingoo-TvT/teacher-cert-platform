CREATE TABLE IF NOT EXISTS reviewer_group (
    id                         BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    college_id                 BIGINT        NOT NULL COMMENT '所属学院ID，用于数据范围',
    name                       VARCHAR(100)  NOT NULL COMMENT '评审组名称',
    status                     VARCHAR(32)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态 ENABLED/DISABLED',
    created_by                 BIGINT                 DEFAULT NULL COMMENT '创建人',
    created_at                 DATETIME               DEFAULT NULL COMMENT '创建时间',
    updated_by                 BIGINT                 DEFAULT NULL COMMENT '更新人',
    updated_at                 DATETIME               DEFAULT NULL COMMENT '更新时间',
    deleted                    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_reviewer_group_college_name (college_id, name, deleted),
    KEY idx_reviewer_group_college (college_id),
    KEY idx_reviewer_group_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频评审组';

CREATE TABLE IF NOT EXISTS reviewer_group_member (
    id                         BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    group_id                   BIGINT        NOT NULL COMMENT '评审组ID',
    reviewer_user_id           BIGINT        NOT NULL COMMENT '评审教师用户ID',
    created_by                 BIGINT                 DEFAULT NULL COMMENT '创建人',
    created_at                 DATETIME               DEFAULT NULL COMMENT '创建时间',
    updated_by                 BIGINT                 DEFAULT NULL COMMENT '更新人',
    updated_at                 DATETIME               DEFAULT NULL COMMENT '更新时间',
    deleted                    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_reviewer_group_member (group_id, reviewer_user_id, deleted),
    KEY idx_reviewer_group_member_group (group_id),
    KEY idx_reviewer_group_member_user (reviewer_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频评审组成员';
