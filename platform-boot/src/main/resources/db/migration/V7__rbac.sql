-- =============================================================
-- V7 RBAC 表：用户、角色、权限、角色授权、用户数据范围
-- 编码 utf8mb4；工号/学号等标识字段一律 VARCHAR
-- =============================================================

-- 用户
CREATE TABLE IF NOT EXISTS sys_user (
    id                  BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    username            VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password_hash       VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
    real_name           VARCHAR(128) NOT NULL COMMENT '真实姓名',
    work_no             VARCHAR(64)           DEFAULT NULL COMMENT '工号',
    email               VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
    phone               VARCHAR(32)           DEFAULT NULL COMMENT '手机号',
    status              VARCHAR(32)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态 ENABLED/LOCKED/DISABLED',
    user_type           VARCHAR(32)  NOT NULL COMMENT '用户类型 STAFF/STUDENT',
    college_id          BIGINT                DEFAULT NULL COMMENT '所属学院ID',
    student_id          BIGINT                DEFAULT NULL COMMENT '关联学生ID',
    last_login_at       DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    must_change_pwd     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否首次强制改密 1是0否',
    failed_login_count  INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until        DATETIME              DEFAULT NULL COMMENT '锁定截止时间',
    created_by          BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by          BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at          DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_work_no (work_no),
    UNIQUE KEY uk_sys_user_student_id (student_id),
    KEY idx_sys_user_college (college_id),
    KEY idx_sys_user_status (status),
    KEY idx_sys_user_type (user_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户';

-- 角色
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    code        VARCHAR(64)  NOT NULL COMMENT '角色编码',
    name        VARCHAR(128) NOT NULL COMMENT '角色名称',
    description VARCHAR(255)          DEFAULT NULL COMMENT '说明',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    created_by  BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at  DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by  BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at  DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_role_code (code),
    KEY idx_sys_role_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统角色';

-- 用户角色关系
CREATE TABLE IF NOT EXISTS sys_user_role (
    id         BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    user_id    BIGINT NOT NULL COMMENT '用户ID',
    role_id    BIGINT NOT NULL COMMENT '角色ID',
    created_by BIGINT          DEFAULT NULL COMMENT '创建人',
    created_at DATETIME        DEFAULT NULL COMMENT '创建时间',
    updated_by BIGINT          DEFAULT NULL COMMENT '更新人',
    updated_at DATETIME        DEFAULT NULL COMMENT '更新时间',
    deleted    TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_user (user_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户角色关系';

-- 权限点
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
    code        VARCHAR(128) NOT NULL COMMENT '权限点编码',
    name        VARCHAR(128) NOT NULL COMMENT '权限名称',
    type        VARCHAR(32)  NOT NULL COMMENT '类型 menu/button/data',
    parent_id   BIGINT                DEFAULT NULL COMMENT '父权限ID',
    path        VARCHAR(255)          DEFAULT NULL COMMENT '前端路由或资源路径',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用0停用',
    created_by  BIGINT                DEFAULT NULL COMMENT '创建人',
    created_at  DATETIME              DEFAULT NULL COMMENT '创建时间',
    updated_by  BIGINT                DEFAULT NULL COMMENT '更新人',
    updated_at  DATETIME              DEFAULT NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_permission_code (code),
    KEY idx_sys_permission_parent (parent_id),
    KEY idx_sys_permission_type (type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统权限点';

-- 角色权限关系
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    role_id       BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    scope_type    VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '矩阵范围 NONE/SELF/COLLEGE/SCHOOL/SYSTEM/ASSIGNED',
    created_by    BIGINT          DEFAULT NULL COMMENT '创建人',
    created_at    DATETIME        DEFAULT NULL COMMENT '创建时间',
    updated_by    BIGINT          DEFAULT NULL COMMENT '更新人',
    updated_at    DATETIME        DEFAULT NULL COMMENT '更新时间',
    deleted       TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_role (role_id),
    KEY idx_sys_role_permission_perm (permission_id),
    KEY idx_sys_role_permission_scope (scope_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色权限关系';

-- 用户数据范围（授权学院/专业范围）
CREATE TABLE IF NOT EXISTS sys_user_data_scope (
    id         BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    user_id    BIGINT NOT NULL COMMENT '用户ID',
    college_id BIGINT          DEFAULT NULL COMMENT '授权学院ID',
    major_id   BIGINT          DEFAULT NULL COMMENT '授权专业ID',
    created_by BIGINT          DEFAULT NULL COMMENT '创建人',
    created_at DATETIME        DEFAULT NULL COMMENT '创建时间',
    updated_by BIGINT          DEFAULT NULL COMMENT '更新人',
    updated_at DATETIME        DEFAULT NULL COMMENT '更新时间',
    deleted    TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常1删除',
    UNIQUE KEY uk_sys_user_data_scope (user_id, college_id, major_id),
    KEY idx_sys_user_data_scope_user (user_id),
    KEY idx_sys_user_data_scope_college (college_id),
    KEY idx_sys_user_data_scope_major (major_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户数据范围';
