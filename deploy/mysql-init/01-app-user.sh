#!/bin/bash
# Phase 41.1（P0-5）：生产最小权限应用账号初始化。
#
# 用途：由官方 mysql 镜像的 docker-entrypoint.sh 在数据卷"首次"初始化时自动执行
# （挂载到容器 /docker-entrypoint-initdb.d/，仅空数据卷首次启动生效；已存在数据卷不会重跑）。
# 建 teacher_app 专用账号，替换 backend 服务原先直连 root，遵循最小权限原则。
#
# 为何是 .sh 而不是纯 .sql：官方 mysql 镜像对 initdb.d 下的 .sql 文件按字面内容执行、
# 不做任何变量替换；若把密码写进 .sql 就只能硬编码明文。.sh 脚本由 shell 解释执行，
# 可读取容器 environment（与 mysql 服务本身共享 env），从而让密码真正来自环境变量、
# 不落地为库内硬编码明文——满足"密码取环境变量占位，勿硬编码"的要求。
#
# 依赖的环境变量（由生产 docker-compose.yml 的 mysql 服务 environment 注入，见该文件）：
#   MYSQL_ROOT_PASSWORD —— root 密码（镜像自身要求，用于本脚本以 root 建用户/授权）
#   MYSQL_DATABASE      —— 目标业务库名（默认 teacher_cert，与应用侧一致）
#   DB_USERNAME         —— 应用账号用户名（默认 teacher_app）
#   DB_PASSWORD         —— 应用账号密码（必须显式提供，不设默认值，拒绝空/弱口令落库）
#
# 注意：密码经 shell 变量插值进 SQL 文本，请避免在 DB_PASSWORD 中使用单引号/反斜杠等
# 需要转义的字符（纯字母数字+常见符号即可），避免注入到 heredoc 后语法错误。
set -euo pipefail

DB_USERNAME="${DB_USERNAME:-teacher_app}"
DB_NAME="${MYSQL_DATABASE:-teacher_cert}"

if [ -z "${DB_PASSWORD:-}" ]; then
  echo "[mysql-init] FATAL: 环境变量 DB_PASSWORD 未设置，拒绝以空/默认密码创建应用账号 '${DB_USERNAME}'。" >&2
  echo "[mysql-init]        请在生产 .env 中设置 DB_PASSWORD 后重新创建数据卷/重启。" >&2
  exit 1
fi

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
  CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_PASSWORD}';

  -- 最小权限：仅 ${DB_NAME} 库的 DML + Flyway 迁移所需 DDL。
  -- 明确不授予：GRANT OPTION（不可再转授）、SUPER、FILE、PROCESS（不可提权/读写宿主文件/看全局进程）、
  -- 以及 *.*（不可触达其它库，含 mysql 系统库）。
  GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
    ON \`${DB_NAME}\`.* TO '${DB_USERNAME}'@'%';

  FLUSH PRIVILEGES;
EOSQL

echo "[mysql-init] 应用账号 '${DB_USERNAME}'@'%' 已就绪（库=${DB_NAME}，最小权限非 root）。"
