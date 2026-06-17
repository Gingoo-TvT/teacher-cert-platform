CREATE TABLE IF NOT EXISTS backup_record (
    id             BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    backup_type    VARCHAR(32)  NOT NULL COMMENT '备份类型 mysql/minio/full',
    status         VARCHAR(32)  NOT NULL COMMENT '状态 PENDING/RUNNING/COMPLETED/FAILED',
    scope          VARCHAR(128)          DEFAULT NULL COMMENT '备份范围',
    storage_uri    VARCHAR(512)          DEFAULT NULL COMMENT '备份存放位置或演练记录位置',
    started_at     DATETIME              DEFAULT NULL COMMENT '开始时间',
    finished_at    DATETIME              DEFAULT NULL COMMENT '结束时间',
    operator_id    BIGINT                DEFAULT NULL COMMENT '操作人',
    remark         VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    error_message  VARCHAR(500)          DEFAULT NULL COMMENT '错误信息',
    created_by     BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at     DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by     BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at     DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    KEY idx_backup_operator_time (operator_id, started_at),
    KEY idx_backup_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '备份与恢复演练记录';
