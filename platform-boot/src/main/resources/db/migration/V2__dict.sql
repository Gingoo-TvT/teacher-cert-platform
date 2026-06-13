-- =============================================================
-- V2 字典、区划、任教学科、组织与培养目标联动表
-- 编码 utf8mb4；学校代码/专业代码/区划代码等标识字段一律 VARCHAR
-- =============================================================

-- 字典类型
CREATE TABLE sys_dict_type (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    type_code   VARCHAR(64)  NOT NULL COMMENT '字典类型编码',
    type_name   VARCHAR(128) NOT NULL COMMENT '字典类型名称',
    description VARCHAR(255)          DEFAULT NULL COMMENT '说明',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    created_by  BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at  DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by  BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at  DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_dict_type_code (type_code),
    KEY idx_sys_dict_type_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='字典类型';

-- 字典项
CREATE TABLE sys_dict_item (
    id           BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    type_code    VARCHAR(64)  NOT NULL COMMENT '字典类型编码',
    item_code    VARCHAR(128) NOT NULL COMMENT '字典项编码',
    item_value   VARCHAR(255) NOT NULL COMMENT '字典项显示值',
    parent_code  VARCHAR(128)          DEFAULT NULL COMMENT '父级字典项编码',
    sort         INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    year_version VARCHAR(16)  NOT NULL DEFAULT 'GLOBAL' COMMENT '年度版本，GLOBAL表示全局通用',
    ext_json     JSON                  DEFAULT NULL COMMENT '扩展配置JSON',
    created_by   BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at   DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by   BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at   DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_dict_item_type_code_year (type_code, item_code, year_version),
    KEY idx_sys_dict_item_type_status (type_code, status),
    KEY idx_sys_dict_item_parent (type_code, parent_code),
    KEY idx_sys_dict_item_year (year_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='字典项';

-- 行政区划（省/市/区县三级）
CREATE TABLE sys_region (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    code        VARCHAR(12)  NOT NULL COMMENT '行政区划代码',
    name        VARCHAR(128) NOT NULL COMMENT '行政区划名称',
    parent_code VARCHAR(12)           DEFAULT NULL COMMENT '父级行政区划代码',
    level       TINYINT      NOT NULL COMMENT '层级 1省2市3区县',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    created_by  BIGINT               DEFAULT NULL COMMENT '创建人',
    created_at  DATETIME             DEFAULT NULL COMMENT '创建时间',
    updated_by  BIGINT               DEFAULT NULL COMMENT '更新人',
    updated_at  DATETIME             DEFAULT NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_region_code (code),
    KEY idx_sys_region_parent (parent_code),
    KEY idx_sys_region_level (level, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='行政区划';

-- 任教学科标准库
CREATE TABLE teaching_subject (
    id            BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    segment_code  VARCHAR(64)  NOT NULL COMMENT '任教学段编码',
    category_node VARCHAR(128)          DEFAULT NULL COMMENT '类别节点编码',
    subject_code  VARCHAR(128) NOT NULL COMMENT '任教学科编码',
    subject_name  VARCHAR(255) NOT NULL COMMENT '任教学科名称',
    is_category   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否类别节点 1是0否',
    keyword       VARCHAR(512)          DEFAULT NULL COMMENT '检索关键词',
    year_version  VARCHAR(16)  NOT NULL DEFAULT 'GLOBAL' COMMENT '年度版本，GLOBAL表示全局通用',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    created_by    BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at    DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by    BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at    DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_teaching_subject_code_year (subject_code, year_version),
    KEY idx_teaching_subject_segment (segment_code, status, year_version),
    KEY idx_teaching_subject_category (segment_code, category_node, is_category),
    KEY idx_teaching_subject_name (subject_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='任教学科标准库';

-- 学院
CREATE TABLE sys_college (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    code        VARCHAR(64)  NOT NULL COMMENT '学院编码',
    name        VARCHAR(128) NOT NULL COMMENT '学院名称',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    created_by  BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at  DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by  BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at  DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_college_code (code),
    KEY idx_sys_college_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='学院';

-- 专业
CREATE TABLE sys_major (
    id                     BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    college_id             BIGINT       NOT NULL COMMENT '学院ID',
    internal_major_code    VARCHAR(64)  NOT NULL COMMENT '校内专业代码',
    internal_major_name    VARCHAR(128) NOT NULL COMMENT '校内专业名称',
    second_discipline_code VARCHAR(64)           DEFAULT NULL COMMENT '二级学科代码',
    second_discipline_name VARCHAR(128)          DEFAULT NULL COMMENT '二级学科名称',
    pilot_scope_flag       TINYINT      NOT NULL DEFAULT 0 COMMENT '试点范围标识 1是0否',
    year_version           VARCHAR(16)  NOT NULL DEFAULT 'GLOBAL' COMMENT '年度版本，GLOBAL表示全局通用',
    sort                   INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status                 TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    created_by             BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at             DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by             BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at             DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted                TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_major_code_year (internal_major_code, year_version),
    KEY idx_sys_major_college (college_id, status),
    KEY idx_sys_major_discipline (second_discipline_code),
    KEY idx_sys_major_year (year_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='专业';

-- 专业与培养目标多对多配置
CREATE TABLE major_training_goal (
    id                 BIGINT      NOT NULL PRIMARY KEY COMMENT '主键',
    major_id           BIGINT      NOT NULL COMMENT '专业ID',
    training_goal_code VARCHAR(64) NOT NULL COMMENT '培养目标编码',
    sort               INT         NOT NULL DEFAULT 0 COMMENT '排序',
    status             TINYINT     NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    created_by         BIGINT               DEFAULT NULL COMMENT '创建人',
    created_at         DATETIME             DEFAULT NULL COMMENT '创建时间',
    updated_by         BIGINT               DEFAULT NULL COMMENT '更新人',
    updated_at         DATETIME             DEFAULT NULL COMMENT '更新时间',
    deleted            TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_major_training_goal (major_id, training_goal_code),
    KEY idx_major_training_goal_major (major_id, status),
    KEY idx_major_training_goal_code (training_goal_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='专业培养目标';

-- 培养目标联动配置
CREATE TABLE training_goal_config (
    id                                    BIGINT      NOT NULL PRIMARY KEY COMMENT '主键',
    training_goal_code                    VARCHAR(64) NOT NULL COMMENT '培养目标编码',
    default_segment                       VARCHAR(64)          DEFAULT NULL COMMENT '默认任教学段编码',
    allowed_segments_json                 JSON                 DEFAULT NULL COMMENT '允许任教学段编码JSON数组',
    default_internship_location           VARCHAR(64)          DEFAULT NULL COMMENT '默认实习地点编码',
    allowed_internship_locations_json     JSON                 DEFAULT NULL COMMENT '允许实习地点编码JSON数组',
    status                                TINYINT     NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    created_by                            BIGINT               DEFAULT NULL COMMENT '创建人',
    created_at                            DATETIME             DEFAULT NULL COMMENT '创建时间',
    updated_by                            BIGINT               DEFAULT NULL COMMENT '更新人',
    updated_at                            DATETIME             DEFAULT NULL COMMENT '更新时间',
    deleted                               TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_training_goal_config_code (training_goal_code),
    KEY idx_training_goal_config_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='培养目标联动配置';
