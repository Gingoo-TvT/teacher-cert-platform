CREATE TABLE IF NOT EXISTS video_upload_session (
    id                         BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    upload_id                  VARCHAR(64)   NOT NULL COMMENT '上传会话ID',
    student_id                 BIGINT        NOT NULL COMMENT '学生ID',
    college_id                 BIGINT        NOT NULL COMMENT '所属学院ID，用于数据范围',
    assessment_year            VARCHAR(16)   NOT NULL COMMENT '考核年度（文本）',
    file_md5                   VARCHAR(64)   NOT NULL COMMENT '整文件MD5',
    file_name                  VARCHAR(255)  NOT NULL COMMENT '原始文件名',
    file_size                  BIGINT        NOT NULL COMMENT '文件大小(字节)',
    content_type               VARCHAR(128)           DEFAULT NULL COMMENT 'MIME类型',
    chunk_size                 BIGINT        NOT NULL COMMENT '分片大小(字节)',
    total_chunks               INT           NOT NULL COMMENT '总分片数',
    uploaded_chunks            INT           NOT NULL DEFAULT 0 COMMENT '已上传分片数',
    uploaded_bytes             BIGINT        NOT NULL DEFAULT 0 COMMENT '已上传字节数',
    duration_seconds           INT                    DEFAULT NULL COMMENT '视频时长秒',
    status                     VARCHAR(32)   NOT NULL DEFAULT 'UPLOADING' COMMENT '状态 UPLOADING/MERGED/VALIDATION_FAILED/FAST_HIT',
    file_id                    BIGINT                 DEFAULT NULL COMMENT '合并后的文件ID',
    validation_message         VARCHAR(500)           DEFAULT NULL COMMENT '校验结果信息',
    created_by                 BIGINT                 DEFAULT NULL COMMENT '创建人',
    created_at                 DATETIME               DEFAULT NULL COMMENT '创建时间',
    updated_by                 BIGINT                 DEFAULT NULL COMMENT '更新人',
    updated_at                 DATETIME               DEFAULT NULL COMMENT '更新时间',
    deleted                    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_video_upload_id (upload_id),
    KEY idx_video_upload_student (student_id, assessment_year),
    KEY idx_video_upload_md5 (file_md5),
    KEY idx_video_upload_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频分片上传会话';

CREATE TABLE IF NOT EXISTS video_upload_chunk (
    id                         BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    upload_id                  VARCHAR(64)   NOT NULL COMMENT '上传会话ID',
    chunk_index                INT           NOT NULL COMMENT '分片序号，从0开始',
    chunk_md5                  VARCHAR(64)   NOT NULL COMMENT '分片MD5',
    chunk_size                 BIGINT        NOT NULL COMMENT '分片大小(字节)',
    object_key                 VARCHAR(512)  NOT NULL COMMENT 'MinIO分片对象Key',
    uploaded_at                DATETIME      NOT NULL COMMENT '上传时间',
    created_by                 BIGINT                 DEFAULT NULL COMMENT '创建人',
    created_at                 DATETIME               DEFAULT NULL COMMENT '创建时间',
    updated_by                 BIGINT                 DEFAULT NULL COMMENT '更新人',
    updated_at                 DATETIME               DEFAULT NULL COMMENT '更新时间',
    deleted                    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_video_chunk_upload_index (upload_id, chunk_index, deleted),
    KEY idx_video_chunk_upload (upload_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频上传分片';

CREATE TABLE IF NOT EXISTS video_review (
    id                         BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    student_id                 BIGINT        NOT NULL COMMENT '学生ID',
    college_id                 BIGINT        NOT NULL COMMENT '所属学院ID，用于数据范围',
    assessment_year            VARCHAR(16)   NOT NULL COMMENT '考核年度（文本）',
    video_file_id              BIGINT                 DEFAULT NULL COMMENT '视频文件ID',
    video_file_name            VARCHAR(255)           DEFAULT NULL COMMENT '视频文件名',
    file_md5                   VARCHAR(64)            DEFAULT NULL COMMENT '文件MD5',
    duration_seconds           INT                    DEFAULT NULL COMMENT '视频时长秒',
    format_check               VARCHAR(32)            DEFAULT NULL COMMENT '格式校验结果 PASS/FAIL',
    validation_message         VARCHAR(500)           DEFAULT NULL COMMENT '校验结果信息',
    status                     VARCHAR(32)   NOT NULL DEFAULT 'WAIT_UPLOAD' COMMENT '状态 WAIT_UPLOAD/VALIDATING/WAIT_REVIEW/REVIEWING/NEED_REVIEW/REVIEW_COMPLETED/CONFIRMED/VALIDATION_FAILED',
    final_score                INT                    DEFAULT NULL COMMENT '终分',
    final_conclusion           VARCHAR(32)            DEFAULT NULL COMMENT '最终结论 PASS/FAIL',
    arbitrate_reviewer         BIGINT                 DEFAULT NULL COMMENT '第三专家或仲裁人',
    arbitrate_mode             VARCHAR(32)            DEFAULT NULL COMMENT '复评模式 thirdExpert/collegeArbitrate',
    confirmed_by               BIGINT                 DEFAULT NULL COMMENT '确认人',
    confirmed_at               DATETIME               DEFAULT NULL COMMENT '确认时间',
    locked                     TINYINT       NOT NULL DEFAULT 0 COMMENT '是否锁定 1是0否',
    created_by                 BIGINT                 DEFAULT NULL COMMENT '创建人',
    created_at                 DATETIME               DEFAULT NULL COMMENT '创建时间',
    updated_by                 BIGINT                 DEFAULT NULL COMMENT '更新人',
    updated_at                 DATETIME               DEFAULT NULL COMMENT '更新时间',
    deleted                    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_video_student_year (student_id, assessment_year, deleted),
    KEY idx_video_college (college_id),
    KEY idx_video_status (status),
    KEY idx_video_file (video_file_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教学能力视频评审';

CREATE TABLE IF NOT EXISTS video_review_task (
    id                         BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    video_review_id            BIGINT        NOT NULL COMMENT '视频评审ID',
    student_id                 BIGINT        NOT NULL COMMENT '学生ID，用于本人数据范围',
    college_id                 BIGINT        NOT NULL COMMENT '所属学院ID，用于学院数据范围',
    reviewer_id                BIGINT        NOT NULL COMMENT '评审教师用户ID',
    reviewer_role              VARCHAR(32)   NOT NULL DEFAULT 'REVIEWER' COMMENT '评审角色 REVIEWER/THIRD_EXPERT/ARBITRATOR',
    score                      INT                    DEFAULT NULL COMMENT '总分',
    dimension_scores_json      JSON                   DEFAULT NULL COMMENT '维度评分JSON',
    comment                    VARCHAR(1000)          DEFAULT NULL COMMENT '评审意见',
    conclusion                 VARCHAR(32)            DEFAULT NULL COMMENT '结论 PASS/FAIL',
    submitted                  TINYINT       NOT NULL DEFAULT 0 COMMENT '是否提交 1是0否',
    submit_time                DATETIME               DEFAULT NULL COMMENT '提交时间',
    created_by                 BIGINT                 DEFAULT NULL COMMENT '创建人',
    created_at                 DATETIME               DEFAULT NULL COMMENT '创建时间',
    updated_by                 BIGINT                 DEFAULT NULL COMMENT '更新人',
    updated_at                 DATETIME               DEFAULT NULL COMMENT '更新时间',
    deleted                    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_video_task_reviewer (video_review_id, reviewer_id, deleted),
    KEY idx_video_task_review (video_review_id),
    KEY idx_video_task_reviewer (reviewer_id, submitted),
    KEY idx_video_task_student (student_id),
    KEY idx_video_task_college (college_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教学能力视频评审任务';

INSERT INTO sys_param
(id, param_key, param_value, param_type, param_group, description, editable, created_at, updated_at, deleted)
VALUES
(130000000000000101, 'file.maxSize.video', '2147483648', 'long', 'video', '视频单文件最大字节数，确认单#6=2GB', 1, NOW(), NOW(), 0),
(130000000000000102, 'video.passLine', '60', 'int', 'video', '视频评审合格线，确认单#7=60', 1, NOW(), NOW(), 0),
(130000000000000103, 'video.diffThreshold', '12', 'int', 'video', '两评委分差阈值，确认单#8=12', 1, NOW(), NOW(), 0),
(130000000000000104, 'video.required', 'true', 'boolean', 'video', '视频是否必过才能发证，确认单#9=必过', 1, NOW(), NOW(), 0),
(130000000000000105, 'video.arbitrate.mode', 'thirdExpert', 'string', 'video', '视频复评模式，确认单#10=第三专家', 1, NOW(), NOW(), 0),
(130000000000000106, 'video.reviewerCount', '2', 'int', 'video', '视频评审教师人数，确认单#11=2', 1, NOW(), NOW(), 0),
(130000000000000107, 'video.durationTarget', '900', 'int', 'video', '视频目标时长秒，15分钟', 1, NOW(), NOW(), 0),
(130000000000000108, 'video.durationTolerance', '60', 'int', 'video', '视频时长容差秒，默认60', 1, NOW(), NOW(), 0),
(130000000000000109, 'video.presign.expirySeconds', '300', 'int', 'video', '视频播放预签名有效期秒', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    param_value = VALUES(param_value),
    param_type = VALUES(param_type),
    param_group = VALUES(param_group),
    description = VALUES(description),
    editable = VALUES(editable),
    updated_at = NOW(),
    deleted = 0;

-- TODO: 待学校确认(确认单#13)：9维视频评分细则由学校模板确认；当前给 Phase 1 已有 9 维补权重，权重和满分均可通过字典维护。
INSERT INTO sys_dict_item
(id, type_code, item_code, item_value, parent_code, sort, status, year_version, ext_json, created_at, updated_at, deleted)
VALUES
(300000000000017001, 'video_score_dimension', 'lesson_design', '说课设计', NULL, 1, 1, 'GLOBAL', JSON_OBJECT('weight', 10, 'maxScore', 10), NOW(), NOW(), 0),
(300000000000017002, 'video_score_dimension', 'teaching_objective', '教学目标', NULL, 2, 1, 'GLOBAL', JSON_OBJECT('weight', 10, 'maxScore', 10), NOW(), NOW(), 0),
(300000000000017003, 'video_score_dimension', 'key_difficulty', '重难点', NULL, 3, 1, 'GLOBAL', JSON_OBJECT('weight', 10, 'maxScore', 10), NOW(), NOW(), 0),
(300000000000017004, 'video_score_dimension', 'teaching_implementation', '教学实施', NULL, 4, 1, 'GLOBAL', JSON_OBJECT('weight', 15, 'maxScore', 15), NOW(), NOW(), 0),
(300000000000017005, 'video_score_dimension', 'classroom_organization', '课堂组织', NULL, 5, 1, 'GLOBAL', JSON_OBJECT('weight', 10, 'maxScore', 10), NOW(), NOW(), 0),
(300000000000017006, 'video_score_dimension', 'subject_literacy', '学科素养', NULL, 6, 1, 'GLOBAL', JSON_OBJECT('weight', 10, 'maxScore', 10), NOW(), NOW(), 0),
(300000000000017007, 'video_score_dimension', 'language_expression', '语言表达', NULL, 7, 1, 'GLOBAL', JSON_OBJECT('weight', 10, 'maxScore', 10), NOW(), NOW(), 0),
(300000000000017008, 'video_score_dimension', 'courseware_blackboard', '课件/板书', NULL, 8, 1, 'GLOBAL', JSON_OBJECT('weight', 10, 'maxScore', 10), NOW(), NOW(), 0),
(300000000000017009, 'video_score_dimension', 'teaching_reflection', '教学反思', NULL, 9, 1, 'GLOBAL', JSON_OBJECT('weight', 15, 'maxScore', 15), NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    item_value = VALUES(item_value),
    sort = VALUES(sort),
    status = VALUES(status),
    year_version = VALUES(year_version),
    ext_json = VALUES(ext_json),
    updated_at = NOW(),
    deleted = 0;
