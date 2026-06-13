-- =============================================================
-- V1 基础表：系统参数、审计日志、文件登记 + 系统参数种子
-- 编码 utf8mb4；文本化字段一律 VARCHAR
-- =============================================================

-- 系统参数
CREATE TABLE sys_param (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    param_key   VARCHAR(128) NOT NULL COMMENT '参数键',
    param_value VARCHAR(512)          DEFAULT NULL COMMENT '参数值',
    param_type  VARCHAR(32)           DEFAULT 'string' COMMENT '类型 string/int/bool/enum',
    param_group VARCHAR(64)           DEFAULT NULL COMMENT '分组',
    description VARCHAR(255)          DEFAULT NULL COMMENT '说明',
    editable    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可编辑 1是0否',
    created_by  BIGINT                DEFAULT NULL,
    created_at  DATETIME              DEFAULT NULL,
    updated_by  BIGINT                DEFAULT NULL,
    updated_at  DATETIME              DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_param_key (param_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统参数';

-- 审计日志（追加写）
CREATE TABLE audit_log (
    id           BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    biz_type     VARCHAR(64)   DEFAULT NULL COMMENT '业务类型',
    biz_id       BIGINT        DEFAULT NULL COMMENT '业务主键',
    target       VARCHAR(128)  DEFAULT NULL COMMENT '审核对象',
    operator_id  BIGINT        DEFAULT NULL COMMENT '操作人',
    operate_time DATETIME      DEFAULT NULL COMMENT '操作时间',
    `comment`    VARCHAR(1000) DEFAULT NULL COMMENT '意见',
    old_status   VARCHAR(64)   DEFAULT NULL COMMENT '原状态',
    new_status   VARCHAR(64)   DEFAULT NULL COMMENT '新状态',
    operation    VARCHAR(64)   DEFAULT NULL COMMENT '操作',
    ip           VARCHAR(64)   DEFAULT NULL COMMENT '操作IP',
    KEY idx_audit_biz (biz_type, biz_id),
    KEY idx_audit_time (operate_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='审计日志';

-- 文件登记（MinIO 元数据）
CREATE TABLE file_object (
    id            BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    original_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
    stored_name   VARCHAR(255) DEFAULT NULL COMMENT '存储名',
    bucket        VARCHAR(128) DEFAULT NULL COMMENT '桶',
    object_key    VARCHAR(512) DEFAULT NULL COMMENT '对象键',
    `size`        BIGINT       DEFAULT NULL COMMENT '大小(字节)',
    content_type  VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
    md5           VARCHAR(64)  DEFAULT NULL COMMENT 'MD5',
    biz_type      VARCHAR(64)  DEFAULT NULL COMMENT '业务类型',
    uploader_id   BIGINT       DEFAULT NULL COMMENT '上传人',
    upload_time   DATETIME     DEFAULT NULL COMMENT '上传时间',
    created_by    BIGINT       DEFAULT NULL,
    created_at    DATETIME     DEFAULT NULL,
    updated_by    BIGINT       DEFAULT NULL,
    updated_at    DATETIME     DEFAULT NULL,
    deleted       TINYINT NOT NULL DEFAULT 0,
    KEY idx_file_md5 (md5),
    KEY idx_file_biz (biz_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='文件登记';

-- 系统参数种子（见 docs/README §6）
INSERT INTO sys_param (id, param_key, param_value, param_type, param_group, description, editable, created_at, updated_at, deleted) VALUES
(1 , 'cert.seq.scope'           , 'SCHOOL_YEAR_SEGMENT', 'enum'  , 'cert'    , '证书序列作用域 SCHOOL_YEAR_SEGMENT/SCHOOL_YEAR', 1, NOW(), NOW(), 0),
(2 , 'cert.province.code'       , '44'                 , 'string', 'cert'    , '省级行政区划代码', 1, NOW(), NOW(), 0),
(3 , 'cert.school.code'         , '10588'              , 'string', 'cert'    , '高校代码', 1, NOW(), NOW(), 0),
(4 , 'video.passLine'           , '60'                 , 'int'   , 'video'   , '视频合格线', 1, NOW(), NOW(), 0),
(5 , 'video.diffThreshold'      , '12'                 , 'int'   , 'video'   , '两评委分差阈值', 1, NOW(), NOW(), 0),
(6 , 'video.reviewerCount'      , '2'                  , 'int'   , 'video'   , '评审教师数', 1, NOW(), NOW(), 0),
(7 , 'video.durationTolerance'  , '60'                 , 'int'   , 'video'   , '视频时长容差(秒)', 1, NOW(), NOW(), 0),
(8 , 'video.arbitrate.mode'     , 'thirdExpert'        , 'enum'  , 'video'   , '复评模式 thirdExpert/collegeArbitrate', 1, NOW(), NOW(), 0),
(9 , 'video.required'           , 'true'               , 'bool'  , 'video'   , '视频是否必过才能发证', 1, NOW(), NOW(), 0),
(10, 'file.maxSize.video'       , '2147483648'         , 'int'   , 'file'    , '视频单文件上限(字节)', 1, NOW(), NOW(), 0),
(11, 'file.maxSize.material'    , '52428800'           , 'int'   , 'file'    , '材料单附件上限(字节)', 1, NOW(), NOW(), 0),
(12, 'review.return.target'     , 'FIRST_REVIEW'       , 'enum'  , 'review'  , '复审退回目标 FIRST_REVIEW/SECOND_REVIEW', 1, NOW(), NOW(), 0),
(13, 'validate.name.mode'       , 'strict'             , 'enum'  , 'validate', '姓名校验模式 strict/loose', 1, NOW(), NOW(), 0),
(14, 'validate.idcard.checksum' , 'false'              , 'bool'  , 'validate', '身份证校验码开关', 1, NOW(), NOW(), 0),
(15, 'student.autoCreateAccount', 'true'               , 'bool'  , 'student' , '导入即开通学生账号', 1, NOW(), NOW(), 0),
(16, 'student.defaultPwd'       , 'idcard6'            , 'string', 'student' , '学生初始密码规则(证件号后6位)', 1, NOW(), NOW(), 0),
(17, 'current_assessment_year'  , '2026'               , 'int'   , 'global'  , '当前考核年度', 1, NOW(), NOW(), 0);
