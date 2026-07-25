#!/usr/bin/env bash
# Phase 41 TCP_SQL_V2 真实 mysql 8.4 CLI 恢复门禁。
#
# 安全边界：
# - 本脚本不会启动或删除容器，但 Phase41BackupIT 会在本机回环 MySQL 创建并删除
#   teacher_cert_p41_source_* / teacher_cert_p41_restore_* 两个唯一 scratch schema，
#   并在预先存在的 Phase 41 专用 MinIO bucket 内创建后删除一个独占前缀测试对象；
# - 只允许用户或独立复核者在明确授权的 Linux/WSL 隔离环境执行；
# - 严禁指向生产、共享开发库或包含真实业务数据的依赖。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mvn_cmd="${MVN:-mvn}"

fail() {
  echo "[phase41-backup-restore-real] FAIL: $*" >&2
  exit 1
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "必须显式设置 ${name}"
}

is_loopback_hostport() {
  local authority="$1"
  local port
  [[ "${authority}" =~ ^(localhost|127\.0\.0\.1):([0-9]{1,5})$ ]] || return 1
  port="${BASH_REMATCH[2]}"
  (( 10#${port} >= 1 && 10#${port} <= 65535 ))
}

[[ "$(uname -s)" == Linux* ]] \
  || fail "真实 mysql CLI 门禁只允许在 Linux/WSL 隔离环境执行"
[[ "${PHASE41_ALLOW_BACKUP_RESTORE_TEST:-}" == "1" ]] \
  || fail "必须显式设置 PHASE41_ALLOW_BACKUP_RESTORE_TEST=1"
[[ "${PHASE41_ISOLATED_ENVIRONMENT_ACK:-}" == "1" ]] \
  || fail "必须显式确认 PHASE41_ISOLATED_ENVIRONMENT_ACK=1"
[[ "${PHASE41_DEDICATED_TARGETS_ACK:-}" == "1" ]] \
  || fail "必须显式确认 MySQL/Redis/MinIO 与 bucket 均为本轮专用测试目标"

require_env SPRING_DATASOURCE_URL
require_env SPRING_DATASOURCE_USERNAME
require_env SPRING_DATASOURCE_PASSWORD
require_env SPRING_DATA_REDIS_HOST
require_env SPRING_DATA_REDIS_PORT
require_env MINIO_ENDPOINT
require_env MINIO_ACCESS_KEY
require_env MINIO_SECRET_KEY
require_env MINIO_BUCKET

mysql_cli="${PHASE41_MYSQL_CLI:-mysql}"
command -v "${mysql_cli}" >/dev/null 2>&1 \
  || fail "未找到 mysql 客户端: ${mysql_cli}"
"${mysql_cli}" --no-login-paths --no-defaults --version \
  | grep -Eiq 'Ver[[:space:]]+8\.4\.' \
  || fail "mysql 客户端必须是 8.4.x，且须支持隔离默认 option/login-path"
command -v "${mvn_cmd}" >/dev/null 2>&1 || fail "未找到 Maven: ${mvn_cmd}"

jdbc_authority="${SPRING_DATASOURCE_URL#jdbc:mysql://}"
[[ "${jdbc_authority}" != "${SPRING_DATASOURCE_URL}" && "${jdbc_authority}" == */* ]] \
  || fail "SPRING_DATASOURCE_URL 必须是单一 MySQL JDBC URL"
jdbc_hostport="${jdbc_authority%%/*}"
is_loopback_hostport "${jdbc_hostport}" \
  || fail "SPRING_DATASOURCE_URL 必须指向 localhost/127.0.0.1 的隔离 MySQL"
case "${SPRING_DATA_REDIS_HOST}" in
  localhost|127.0.0.1) ;;
  *) fail "SPRING_DATA_REDIS_HOST 必须是 localhost/127.0.0.1" ;;
esac
[[ "${SPRING_DATA_REDIS_PORT}" =~ ^[0-9]{1,5}$ ]] \
  && (( 10#${SPRING_DATA_REDIS_PORT} >= 1 && 10#${SPRING_DATA_REDIS_PORT} <= 65535 )) \
  || fail "SPRING_DATA_REDIS_PORT 必须是 1..65535"
[[ "${MINIO_ENDPOINT}" =~ ^https?://(localhost|127\.0\.0\.1):([0-9]{1,5})/?$ ]] \
  || fail "MINIO_ENDPOINT 必须是单一 localhost/127.0.0.1 URL"
minio_port="${BASH_REMATCH[2]}"
(( 10#${minio_port} >= 1 && 10#${minio_port} <= 65535 )) \
  || fail "MINIO_ENDPOINT 必须指向 localhost/127.0.0.1 的隔离 MinIO"
[[ "${MINIO_BUCKET}" =~ ^teacher-cert-p41-[a-z0-9][a-z0-9-]{2,50}$ ]] \
  || fail "MINIO_BUCKET 必须是预先存在的 teacher-cert-p41-* 专用测试桶"

[[ -z "${PHASE41_RUN_TOKEN:-}" ]] \
  || fail "拒绝复用 PHASE41_RUN_TOKEN；每次门禁必须生成新的独占前缀"
run_token="$(date -u +%Y%m%dT%H%M%SZ)-$$_${RANDOM}"
[[ "${run_token}" =~ ^[A-Za-z0-9_-]{8,64}$ ]] \
  || fail "PHASE41_RUN_TOKEN 只允许 8..64 位字母、数字、下划线或连字符"

export PHASE41_REQUIRE_MYSQL_CLI_RESTORE=1
export PHASE41_MYSQL_CLI="${mysql_cli}"
export PLATFORM_BACKUP_PREFIX="db-backup/phase41-cli/${run_token}/"

echo "[phase41-backup-restore-real] 将创建并精确删除两个 scratch schema 与专用桶独占前缀下的一个测试对象"
"${mvn_cmd}" -f "${repo_root}/pom.xml" -B -ntp \
  -pl platform-boot -am \
  "-Dit.test=Phase41BackupIT" \
  "-Dfailsafe.failIfNoSpecifiedTests=false" \
  verify

echo "[phase41-backup-restore-real] PASS"
