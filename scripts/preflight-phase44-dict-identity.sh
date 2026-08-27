#!/usr/bin/env bash
# Phase 44 棕地字典 identity 发布前只读 preflight。
#
# 安全边界：
# - 只允许用户/独立复核者在已授权目标执行，Codex 不执行本脚本；
# - 连接账号必须是只读账号，凭据只从 mysql_config_editor 创建的 login-path 读取；
# - 脚本只在 READ ONLY 一致性事务内查询 information_schema、sys_dict_type 与 sys_dict_item，
#   不修复、不 UPDATE、不执行 Flyway；发现问题一律失败关闭并交由独立迁移处理。
set -euo pipefail

mysql_cli="${PHASE44_MYSQL_CLI:-mysql}"
mysql_config_editor="${PHASE44_MYSQL_CONFIG_EDITOR:-mysql_config_editor}"
login_path="${PHASE44_MYSQL_LOGIN_PATH:-phase44-preflight}"
expected_collation="${PHASE44_EXPECTED_COLLATION:-utf8mb4_0900_ai_ci}"
expected_server_uuid="${PHASE44_EXPECTED_SERVER_UUID:-}"
tmp_dir=""
mysql_command=()

fail() {
  echo "[phase44-dict-identity-preflight] FAIL: $*" >&2
  exit 1
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "必须显式设置 ${name}"
}

cleanup() {
  [[ -z "${tmp_dir}" ]] || rm -rf -- "${tmp_dir}"
}
trap cleanup EXIT

[[ "${PHASE44_ALLOW_IDENTITY_PREFLIGHT:-}" == "1" ]] \
  || fail "必须显式设置 PHASE44_ALLOW_IDENTITY_PREFLIGHT=1"
[[ "${PHASE44_AUTHORIZED_TARGET_ACK:-}" == "1" ]] \
  || fail "必须显式确认 PHASE44_AUTHORIZED_TARGET_ACK=1"
[[ "${PHASE44_READ_ONLY_ACCOUNT_ACK:-}" == "1" ]] \
  || fail "必须显式确认 login-path 使用目标库只读账号"
[[ -z "${MYSQL_PWD:-}" ]] \
  || fail "拒绝 MYSQL_PWD；请使用 mysql_config_editor login-path 安全录入凭据"

require_env PHASE44_MYSQL_HOST
require_env PHASE44_MYSQL_PORT
require_env PHASE44_MYSQL_DATABASE
require_env PHASE44_EXPECTED_SERVER_UUID

case "${PHASE44_MYSQL_HOST}" in
  localhost|127.0.0.1) ;;
  *) fail "PHASE44_MYSQL_HOST 必须是 localhost/127.0.0.1；第二次发布由一次性客户端共享已核验 MySQL 容器的网络命名空间" ;;
esac
[[ "${PHASE44_MYSQL_PORT}" =~ ^[0-9]{1,5}$ ]] \
  && (( 10#${PHASE44_MYSQL_PORT} >= 1 && 10#${PHASE44_MYSQL_PORT} <= 65535 )) \
  || fail "PHASE44_MYSQL_PORT 必须是 1..65535"
[[ "${PHASE44_MYSQL_DATABASE}" =~ ^[A-Za-z0-9_]{1,64}$ ]] \
  || fail "PHASE44_MYSQL_DATABASE 必须是 1..64 位字母、数字或下划线"
[[ "${login_path}" =~ ^[A-Za-z0-9_.-]{1,64}$ ]] \
  || fail "PHASE44_MYSQL_LOGIN_PATH 只允许 1..64 位字母、数字、点、下划线或连字符"
[[ "${expected_collation}" =~ ^[A-Za-z0-9_]{1,64}$ ]] \
  || fail "PHASE44_EXPECTED_COLLATION 格式非法"
[[ "${expected_server_uuid}" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]] \
  || fail "PHASE44_EXPECTED_SERVER_UUID 格式非法"
expected_server_uuid="${expected_server_uuid,,}"

if [[ "${PHASE44_MYSQL_CLI_BASH_SCRIPT_ACK:-}" == "1" ]]; then
  [[ -f "${mysql_cli}" && -r "${mysql_cli}" ]] \
    || fail "PHASE44_MYSQL_CLI 指向的 Bash wrapper 不可读: ${mysql_cli}"
  command -v bash >/dev/null 2>&1 || fail "未找到 bash"
  mysql_command=(bash "${mysql_cli}")
else
  command -v "${mysql_cli}" >/dev/null 2>&1 || fail "未找到 mysql 客户端: ${mysql_cli}"
  mysql_command=("${mysql_cli}")
fi
if [[ -n "${PHASE44_MYSQL_LOGIN_FILE:-}" ]]; then
  [[ "${PHASE44_MYSQL_LOGIN_FILE}" == /* \
      && -f "${PHASE44_MYSQL_LOGIN_FILE}" \
      && -r "${PHASE44_MYSQL_LOGIN_FILE}" ]] \
    || fail "PHASE44_MYSQL_LOGIN_FILE 必须是目标机上可读的绝对路径"
else
  command -v "${mysql_config_editor}" >/dev/null 2>&1 \
    || fail "未找到 mysql_config_editor，无法使用安全 login-path"
  "${mysql_config_editor}" print "--login-path=${login_path}" >/dev/null 2>&1 \
    || fail "login-path ${login_path} 不存在；请先用 mysql_config_editor 交互式录入只读账号"
fi

tmp_dir="$(mktemp -d)"
raw_output="${tmp_dir}/identity-preflight.tsv"
mysql_error="${tmp_dir}/mysql.stderr"

if ! "${mysql_command[@]}" --no-defaults "--login-path=${login_path}" \
    --protocol=TCP \
    "--host=${PHASE44_MYSQL_HOST}" \
    "--port=${PHASE44_MYSQL_PORT}" \
    "--database=${PHASE44_MYSQL_DATABASE}" \
    --connect-timeout=10 \
    --default-character-set=utf8mb4 \
    --batch --raw --skip-column-names >"${raw_output}" 2>"${mysql_error}" <<'SQL'
SET SESSION TRANSACTION READ ONLY;
START TRANSACTION WITH CONSISTENT SNAPSHOT;

SELECT '__PHASE44_READ_ONLY__', @@SESSION.transaction_read_only;

SELECT
  '__PHASE44_TARGET__',
  DATABASE(),
  SHA2(CURRENT_USER(), 256),
  @@server_uuid;

SELECT
  '__PHASE44_COLLATION__',
  TABLE_NAME,
  CHARACTER_SET_NAME,
  COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME = 'type_code'
  AND TABLE_NAME IN ('sys_dict_type', 'sys_dict_item')
ORDER BY TABLE_NAME;

WITH physical_codes AS (
  SELECT 'sys_dict_type' AS source_table, type_code
  FROM sys_dict_type
  UNION ALL
  SELECT 'sys_dict_item' AS source_table, type_code
  FROM sys_dict_item
)
SELECT DISTINCT
  '__PHASE44_INVALID__',
  source_table,
  HEX(type_code),
  CHAR_LENGTH(type_code)
FROM physical_codes
WHERE CHAR_LENGTH(TRIM(type_code)) NOT BETWEEN 1 AND 64
   OR REGEXP_LIKE(TRIM(type_code), '[^A-Za-z0-9_]', 'c')
ORDER BY source_table, HEX(type_code);

WITH physical_codes AS (
  SELECT 'sys_dict_type' AS source_table, type_code
  FROM sys_dict_type
  UNION ALL
  SELECT 'sys_dict_item' AS source_table, type_code
  FROM sys_dict_item
)
SELECT DISTINCT
  '__PHASE44_NONCANONICAL__',
  source_table,
  HEX(type_code),
  HEX(LOWER(TRIM(type_code)))
FROM physical_codes
WHERE CHAR_LENGTH(TRIM(type_code)) BETWEEN 1 AND 64
  AND NOT REGEXP_LIKE(TRIM(type_code), '[^A-Za-z0-9_]', 'c')
  AND HEX(type_code) <> HEX(LOWER(TRIM(type_code)))
ORDER BY source_table, HEX(type_code);

WITH binary_physical_codes AS (
  SELECT BINARY type_code AS raw_code
  FROM sys_dict_type
  UNION
  SELECT BINARY type_code AS raw_code
  FROM sys_dict_item
),
canonical_codes AS (
  SELECT
    CONVERT(raw_code USING utf8mb4) AS physical_code,
    LOWER(TRIM(CONVERT(raw_code USING utf8mb4))) AS canonical_code
  FROM binary_physical_codes
  WHERE CHAR_LENGTH(TRIM(CONVERT(raw_code USING utf8mb4))) BETWEEN 1 AND 64
    AND NOT REGEXP_LIKE(
      TRIM(CONVERT(raw_code USING utf8mb4)),
      '[^A-Za-z0-9_]',
      'c'
    )
)
SELECT
  '__PHASE44_COLLISION__',
  HEX(canonical_code),
  COUNT(*),
  GROUP_CONCAT(
    HEX(physical_code)
    ORDER BY HEX(physical_code)
    SEPARATOR ','
  )
FROM canonical_codes
GROUP BY canonical_code
HAVING COUNT(*) > 1
ORDER BY HEX(canonical_code);

ROLLBACK;
SQL
then
  sed -n '1,40p' "${mysql_error}" >&2
  fail "MySQL 只读 preflight 执行失败"
fi

read_only_value="$(
  awk -F '\t' '$1 == "__PHASE44_READ_ONLY__" { print $2 }' "${raw_output}"
)"
[[ "${read_only_value}" == "1" ]] \
  || fail "目标会话没有进入 READ ONLY 模式"

target_count="$(
  awk -F '\t' '$1 == "__PHASE44_TARGET__" { count++ } END { print count + 0 }' \
    "${raw_output}"
)"
target_database="$(
  awk -F '\t' '$1 == "__PHASE44_TARGET__" { print $2 }' "${raw_output}"
)"
target_user_sha256="$(
  awk -F '\t' '$1 == "__PHASE44_TARGET__" { print $3 }' "${raw_output}"
)"
target_server_uuid="$(
  awk -F '\t' '$1 == "__PHASE44_TARGET__" { print $4 }' "${raw_output}"
)"
[[ "${target_count}" == "1" ]] \
  || fail "目标 marker 必须精确出现一次，实际 ${target_count} 次"
[[ "${target_database}" == "${PHASE44_MYSQL_DATABASE}" ]] \
  || fail "目标 marker schema 不匹配：expected=${PHASE44_MYSQL_DATABASE}, actual=${target_database:-<missing>}"
[[ "${target_user_sha256}" =~ ^[0-9A-Fa-f]{64}$ ]] \
  || fail "目标 marker 的只读主体摘要缺失或格式非法"
[[ "${target_server_uuid}" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]] \
  || fail "目标 marker 的 MySQL server UUID 缺失或格式非法"
[[ "${target_server_uuid,,}" == "${expected_server_uuid}" ]] \
  || fail "目标 marker 的 MySQL server UUID 不匹配：expected=${expected_server_uuid}, actual=${target_server_uuid,,}"

collation_count="$(
  awk -F '\t' '$1 == "__PHASE44_COLLATION__" { count++ } END { print count + 0 }' \
    "${raw_output}"
)"
collation_mismatch_count="$(
  awk -F '\t' -v expected="${expected_collation}" '
    $1 == "__PHASE44_COLLATION__" && ($3 != "utf8mb4" || $4 != expected) {
      count++
    }
    END { print count + 0 }
  ' "${raw_output}"
)"
invalid_count="$(
  awk -F '\t' '$1 == "__PHASE44_INVALID__" { count++ } END { print count + 0 }' \
    "${raw_output}"
)"
noncanonical_count="$(
  awk -F '\t' '$1 == "__PHASE44_NONCANONICAL__" { count++ } END { print count + 0 }' \
    "${raw_output}"
)"
collision_count="$(
  awk -F '\t' '$1 == "__PHASE44_COLLISION__" { count++ } END { print count + 0 }' \
    "${raw_output}"
)"

echo "[phase44-dict-identity-preflight] target: database=${target_database}, currentUserSha256=${target_user_sha256,,}, serverUuid=${target_server_uuid,,}"
echo "[phase44-dict-identity-preflight] type_code 列:"
awk -F '\t' '
  $1 == "__PHASE44_COLLATION__" {
    printf "  %s.type_code charset=%s collation=%s\n", $2, $3, $4
  }
' "${raw_output}"

if (( invalid_count > 0 )); then
  echo "[phase44-dict-identity-preflight] 非法 typeCode（表、物理值 HEX、字符数）:"
  awk -F '\t' '
    $1 == "__PHASE44_INVALID__" {
      printf "  %s hex=%s chars=%s\n", $2, $3, $4
    }
  ' "${raw_output}"
fi

if (( noncanonical_count > 0 )); then
  echo "[phase44-dict-identity-preflight] 非 canonical typeCode（表、物理值 HEX、canonical HEX）:"
  awk -F '\t' '
    $1 == "__PHASE44_NONCANONICAL__" {
      printf "  %s physical_hex=%s canonical_hex=%s\n", $2, $3, $4
    }
  ' "${raw_output}"
fi

if (( collision_count > 0 )); then
  echo "[phase44-dict-identity-preflight] canonical collision（canonical HEX、变体数、物理值 HEX）:"
  awk -F '\t' '
    $1 == "__PHASE44_COLLISION__" {
      printf "  canonical_hex=%s variants=%s physical_hex=%s\n", $2, $3, $4
    }
  ' "${raw_output}"
fi

if (( collation_count != 2 \
    || collation_mismatch_count > 0 \
    || invalid_count > 0 \
    || noncanonical_count > 0 \
    || collision_count > 0 )); then
  fail "preflight 未通过：columns=${collation_count}/2, collation_mismatch=${collation_mismatch_count}, invalid=${invalid_count}, noncanonical=${noncanonical_count}, collision=${collision_count}；禁止发布，必要修复必须走独立 Flyway"
fi

echo "[phase44-dict-identity-preflight] PASS: columns=2/2, collation=${expected_collation}, invalid=0, noncanonical=0, collision=0"
