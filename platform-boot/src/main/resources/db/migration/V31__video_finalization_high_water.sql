-- WS-3 第四轮：把 V30 当前 token 升级为数据库权威、永久不回退的 fencing 世代高水位。
-- 已有 MERGING 会话的正值原样保留；未认领会话从 0 开始。后续任何状态都不得再清空或回退该列。
UPDATE video_upload_session
SET finalization_token = 0
WHERE finalization_token IS NULL;

ALTER TABLE video_upload_session
    MODIFY COLUMN finalization_token BIGINT NOT NULL DEFAULT 0
        COMMENT '定稿fencing世代高水位；每次认领在行锁内递增且永久不回退';
