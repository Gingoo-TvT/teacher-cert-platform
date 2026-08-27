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
#   APP_DATABASE        —— 目标业务库名（默认 teacher_cert，与应用侧一致）
#   DB_USERNAME         —— 应用账号用户名（默认 teacher_app）
#   DB_PASSWORD         —— 应用账号密码（必须显式提供，不设默认值，拒绝空口令落库）
#
# APP_DATABASE 刻意不复用官方镜像的 MYSQL_DATABASE：官方 entrypoint 会先于本脚本消费后者，
# 使未校验的库名提前进入 SQL。这里先校验标识符，再创建库、账号和授权。
set -eo pipefail

DB_USERNAME="${DB_USERNAME:-teacher_app}"
DB_NAME="${APP_DATABASE:-teacher_cert}"

tcp_mysql_init_fail() {
  echo "[mysql-init] FATAL: $*" >&2
  exit 1
}

tcp_require_identifier() {
  local value="$1"
  local label="$2"
  local max_length="$3"
  if [[ ! "$value" =~ ^[A-Za-z0-9_]+$ ]] || (( ${#value} > max_length )); then
    tcp_mysql_init_fail "${label} 只能包含 ASCII 字母、数字、下划线，且长度不得超过 ${max_length}。"
  fi
}

tcp_mysql_escape_literal() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  value="${value//$'\b'/\\b}"
  value="${value//$'\032'/\\Z}"
  value="${value//\'/\'\'}"
  if printf '%s' "$value" | LC_ALL=C grep -q '[[:cntrl:]]'; then
    tcp_mysql_init_fail "DB_PASSWORD 含不受支持的控制字符。"
  fi
  printf '%s' "$value"
}

tcp_mysql_escape_option_value() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  value="${value//$'\b'/\\b}"
  if printf '%s' "$value" | LC_ALL=C grep -q '[[:cntrl:]]'; then
    tcp_mysql_init_fail "MYSQL_ROOT_PASSWORD 含 option file 不支持的控制字符。"
  fi
  printf '%s' "$value"
}

tcp_run_mysql() {
  if declare -F docker_process_sql >/dev/null 2>&1; then
    docker_process_sql --binary-mode
  else
    # Windows bind mount 可能把脚本呈现为 executable，届时官方 entrypoint 不会 source 本文件；
    # fallback 用权限 0600 的一次性 option file 传 root 口令，既不进入 argv，也不依赖已弃用的 MYSQL_PWD。
    (
      local option_password
      TCP_MYSQL_OPTION_FILE="$(mktemp)"
      trap 'rm -f -- "${TCP_MYSQL_OPTION_FILE}"' EXIT
      trap 'exit 129' HUP
      trap 'exit 130' INT
      trap 'exit 143' TERM
      chmod 600 "${TCP_MYSQL_OPTION_FILE}"
      option_password="$(tcp_mysql_escape_option_value "${MYSQL_ROOT_PASSWORD}")"
      printf '[client]\npassword="%s"\n' "${option_password}" > "${TCP_MYSQL_OPTION_FILE}"
      mysql --defaults-extra-file="${TCP_MYSQL_OPTION_FILE}" --protocol=socket -uroot --binary-mode
    )
  fi
}

tcp_require_identifier "$DB_USERNAME" "DB_USERNAME" 32
tcp_require_identifier "$DB_NAME" "APP_DATABASE" 64

if [[ "${DB_USERNAME,,}" == "root" ]]; then
  tcp_mysql_init_fail "DB_USERNAME 不得为 root。"
fi
if [[ -z "${DB_PASSWORD:-}" ]]; then
  tcp_mysql_init_fail "环境变量 DB_PASSWORD 未设置，拒绝创建空口令应用账号。"
fi
if [[ -z "${MYSQL_ROOT_PASSWORD:-}" ]]; then
  tcp_mysql_init_fail "环境变量 MYSQL_ROOT_PASSWORD 未设置。"
fi

DB_PASSWORD_SQL="$(tcp_mysql_escape_literal "$DB_PASSWORD")"

tcp_run_mysql <<-EOSQL
  SET SESSION sql_mode =
    TRIM(BOTH ',' FROM REPLACE(
      CONCAT(',', @@SESSION.sql_mode, ','),
      ',NO_BACKSLASH_ESCAPES,',
      ','
    ));
  CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_PASSWORD_SQL}';

  -- 最小权限：仅 ${DB_NAME} 库的 DML + Flyway 迁移所需 DDL。
  -- 明确不授予：GRANT OPTION（不可再转授）、SUPER、FILE、PROCESS（不可提权/读写宿主文件/看全局进程）、
  -- 以及 *.*（不可触达其它库，含 mysql 系统库）。
  GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
    ON \`${DB_NAME}\`.* TO '${DB_USERNAME}'@'%';

  FLUSH PRIVILEGES;
EOSQL

unset DB_PASSWORD_SQL
echo "[mysql-init] 应用账号 '${DB_USERNAME}'@'%' 已就绪（库=${DB_NAME}，最小权限非 root）。"
