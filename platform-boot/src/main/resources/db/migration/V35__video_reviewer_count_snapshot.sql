-- 功能完整性 R2：评审人数按 video_review 冻结，参数调整只影响新建评审。
-- MySQL DDL 非事务，先查 information_schema，保证 repair 后可安全重跑。
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'video_review'
       AND column_name = 'reviewer_count') = 0,
    'ALTER TABLE video_review ADD COLUMN reviewer_count INT DEFAULT NULL COMMENT ''本轮冻结的普通评审教师人数'' AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 已有至少 2 个任务时按现有集合冻结，包括旧参数页曾允许形成的 >10 人在途记录；
-- 历史单评审按迁移时合法参数扩到至少 2，由业务指派仅追加缺少的评审教师并保留已提交任务；
-- 尚未指派记录取迁移时有效参数。新建评审仍由应用层限制为 2..10。
UPDATE video_review review
LEFT JOIN (
    SELECT video_review_id, COUNT(*) AS task_count
    FROM video_review_task
    WHERE deleted = 0 AND reviewer_role = 'REVIEWER'
    GROUP BY video_review_id
) tasks ON tasks.video_review_id = review.id
LEFT JOIN sys_param reviewer_param
    ON reviewer_param.param_key = 'video.reviewerCount' AND reviewer_param.deleted = 0
SET review.reviewer_count = CASE
    WHEN tasks.task_count >= 2 THEN tasks.task_count
    WHEN tasks.task_count = 1 AND CAST(reviewer_param.param_value AS UNSIGNED) BETWEEN 2 AND 2147483647
        THEN CAST(reviewer_param.param_value AS UNSIGNED)
    WHEN tasks.task_count = 1 THEN 2
    WHEN CAST(reviewer_param.param_value AS UNSIGNED) BETWEEN 2 AND 2147483647
        THEN CAST(reviewer_param.param_value AS UNSIGNED)
    ELSE 2
END
WHERE review.reviewer_count IS NULL;

-- 历史快照已经冻结；归一当前参数只影响之后新建的评审，不回写历史 reviewer_count。
UPDATE sys_param
SET param_value = CASE
        WHEN CAST(param_value AS SIGNED) < 2 THEN '2'
        WHEN CAST(param_value AS SIGNED) > 10 THEN '10'
        ELSE param_value
    END,
    updated_at = NOW()
WHERE param_key = 'video.reviewerCount'
  AND deleted = 0
  AND (CAST(param_value AS SIGNED) < 2 OR CAST(param_value AS SIGNED) > 10);

ALTER TABLE video_review
    MODIFY COLUMN reviewer_count INT NOT NULL DEFAULT 2
        COMMENT '本轮冻结的普通评审教师人数；参数调整仅影响新建评审';
