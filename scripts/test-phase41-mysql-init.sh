#!/usr/bin/env bash
# Phase 41：不连接数据库的初始化脚本契约测试。用 stub 捕获 SQL，验证特殊口令不会改变语句结构，
# 且非法账号/库名会在任何 mysql 调用前失败。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script_path="${repo_root}/deploy/mysql-init/01-app-user.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

fail() {
  echo "[phase41-mysql-init-test] FAIL: $*" >&2
  exit 1
}

bash -n "${script_path}"

capture_file="${tmp_dir}/captured.sql"
log_file="${tmp_dir}/success.log"
special_password=$'A q\'u\\o te$()#;\nTab\tEnd\032!'

(
  export APP_DATABASE="teacher_cert_restore"
  export DB_USERNAME="teacher_app_restore"
  export DB_PASSWORD="${special_password}"
  export MYSQL_ROOT_PASSWORD="root-secret-not-for-sql"
  export PHASE41_CAPTURE_FILE="${capture_file}"
  docker_process_sql() {
    [[ "$*" == *"--binary-mode"* ]] || exit 81
    cat > "${PHASE41_CAPTURE_FILE}"
  }
  source "${script_path}"
) >"${log_file}" 2>&1

[[ -s "${capture_file}" ]] || fail "stub 未捕获 SQL"
[[ "$(grep -c '^  CREATE USER IF NOT EXISTS ' "${capture_file}")" -eq 1 ]] \
  || fail "CREATE USER 语句数量异常"
grep -Fq "CREATE DATABASE IF NOT EXISTS \`teacher_cert_restore\`" "${capture_file}" \
  || fail "缺少校验后的建库语句"
grep -Fq "TO 'teacher_app_restore'@'%'" "${capture_file}" \
  || fail "GRANT 未绑定校验后的应用账号"
grep -Fq "q''u" "${capture_file}" || fail "单引号未安全加倍"
grep -Fq '\\o te$()#;' "${capture_file}" || fail "反斜杠或 shell 特殊字符未按字面保留"
grep -Fq '\nTab\tEnd\Z!' "${capture_file}" || fail "控制字符未转换为 MySQL 转义序列"
grep -Fq "NO_BACKSLASH_ESCAPES" "${capture_file}" || fail "未固定字符串解析模式"
[[ "$(grep -c 'CREATE USER' "${capture_file}")" -eq 1 ]] \
  || fail "特殊口令改变了 SQL 语句结构"
if grep -Fq "${special_password}" "${log_file}"; then
  fail "日志泄露应用口令"
fi
if grep -Fq "root-secret-not-for-sql" "${capture_file}"; then
  fail "root 口令进入 SQL"
fi

# executable/fallback 路径：用 PATH 中的 mysql stub 验证 root 口令只进入权限 0600 的
# 一次性 option file，不进入 argv/MYSQL_PWD；这也覆盖 Windows bind mount 把 100644
# 文件呈现为 executable 的情况。
stub_dir="${tmp_dir}/stub-bin"
mkdir -p "${stub_dir}"
stub_mysql="${stub_dir}/mysql"
{
  printf '%s\n' '#!/usr/bin/env bash'
  printf '%s\n' 'printf "%s\n" "$@" > "${PHASE41_ARGS_FILE}"'
  printf '%s\n' 'printf "%s" "${MYSQL_PWD:-}" > "${PHASE41_MYSQL_PWD_FILE}"'
  printf '%s\n' 'for arg in "$@"; do'
  printf '%s\n' '  case "$arg" in'
  printf '%s\n' '    --defaults-extra-file=*)'
  printf '%s\n' '      option_file="${arg#*=}"'
  printf '%s\n' '      stat -c "%a" "${option_file}" > "${PHASE41_OPTION_MODE_FILE}"'
  printf '%s\n' '      cp "${option_file}" "${PHASE41_OPTION_CAPTURE_FILE}"'
  printf '%s\n' '      ;;'
  printf '%s\n' '  esac'
  printf '%s\n' 'done'
  printf '%s\n' 'cat > "${PHASE41_EXEC_SQL_FILE}"'
  printf '%s\n' 'exit "${PHASE41_MYSQL_EXIT_CODE:-0}"'
} > "${stub_mysql}"
chmod +x "${stub_mysql}"

fallback_args="${tmp_dir}/fallback.args"
fallback_mysql_pwd="${tmp_dir}/fallback.mysql-pwd"
fallback_option="${tmp_dir}/fallback.option"
fallback_option_mode="${tmp_dir}/fallback.option-mode"
fallback_sql="${tmp_dir}/fallback.sql"
(
  export PATH="${stub_dir}:${PATH}"
  export APP_DATABASE="teacher_cert_fallback"
  export DB_USERNAME="teacher_app_fallback"
  export DB_PASSWORD="Fallback q'u\\ote !"
  export MYSQL_ROOT_PASSWORD="fallback-root-secret"
  export PHASE41_ARGS_FILE="${fallback_args}"
  export PHASE41_MYSQL_PWD_FILE="${fallback_mysql_pwd}"
  export PHASE41_OPTION_CAPTURE_FILE="${fallback_option}"
  export PHASE41_OPTION_MODE_FILE="${fallback_option_mode}"
  export PHASE41_EXEC_SQL_FILE="${fallback_sql}"
  bash "${script_path}"
) >"${tmp_dir}/fallback.log" 2>&1

grep -Eq '^--defaults-extra-file=.+$' "${fallback_args}" \
  || fail "fallback 未使用一次性 option file"
grep -Fxq -- "--protocol=socket" "${fallback_args}" || fail "fallback 未强制使用本地 socket"
grep -Fxq -- "--binary-mode" "${fallback_args}" || fail "fallback 未启用 binary-mode"
if grep -Fq "fallback-root-secret" "${fallback_args}"; then
  fail "fallback 把 root 口令放入 argv"
fi
[[ ! -s "${fallback_mysql_pwd}" ]] || fail "fallback 仍使用已弃用的 MYSQL_PWD"
if [[ "$(uname -s)" == Linux* ]]; then
  [[ "$(<"${fallback_option_mode}")" == "600" ]] \
    || fail "fallback option file 权限不是 0600"
fi
grep -Fxq 'password="fallback-root-secret"' "${fallback_option}" \
  || fail "fallback option file 未承载 root 口令"
grep -Fq "CREATE USER IF NOT EXISTS 'teacher_app_fallback'@'%'" "${fallback_sql}" \
  || fail "fallback 未生成预期 CREATE USER"
fallback_option_path="$(sed -n 's/^--defaults-extra-file=//p' "${fallback_args}")"
[[ -n "${fallback_option_path}" && ! -e "${fallback_option_path}" ]] \
  || fail "fallback 成功后未删除一次性 option file"

# mysql 非零退出时仍必须经 EXIT trap 删除一次性 option file。
failed_args="${tmp_dir}/failed-fallback.args"
failed_option="${tmp_dir}/failed-fallback.option"
if (
  export PATH="${stub_dir}:${PATH}"
  export APP_DATABASE="teacher_cert_fallback_failure"
  export DB_USERNAME="teacher_app_fallback_failure"
  export DB_PASSWORD="Failure path app password!"
  export MYSQL_ROOT_PASSWORD="failure-path-root-secret"
  export PHASE41_ARGS_FILE="${failed_args}"
  export PHASE41_MYSQL_PWD_FILE="${tmp_dir}/failed-fallback.mysql-pwd"
  export PHASE41_OPTION_CAPTURE_FILE="${failed_option}"
  export PHASE41_OPTION_MODE_FILE="${tmp_dir}/failed-fallback.option-mode"
  export PHASE41_EXEC_SQL_FILE="${tmp_dir}/failed-fallback.sql"
  export PHASE41_MYSQL_EXIT_CODE=42
  bash "${script_path}"
) >"${tmp_dir}/failed-fallback.log" 2>&1; then
  fail "mysql 非零退出未向上传播"
fi
failed_option_path="$(sed -n 's/^--defaults-extra-file=//p' "${failed_args}")"
if [[ -z "${failed_option_path}" || -e "${failed_option_path}" ]]; then
  fail "fallback 失败后未删除一次性 option file（path=${failed_option_path:-missing}）"
fi

assert_rejected_before_mysql() {
  local case_name="$1"
  local app_database="$2"
  local db_username="$3"
  local db_password="$4"
  local called_file="${tmp_dir}/${case_name}.called"
  if (
    export APP_DATABASE="${app_database}"
    export DB_USERNAME="${db_username}"
    export DB_PASSWORD="${db_password}"
    export MYSQL_ROOT_PASSWORD="root-secret"
    export PHASE41_CALLED_FILE="${called_file}"
    docker_process_sql() {
      : > "${PHASE41_CALLED_FILE}"
    }
    source "${script_path}"
  ) >"${tmp_dir}/${case_name}.log" 2>&1; then
    fail "${case_name} 应被拒绝"
  fi
  [[ ! -e "${called_file}" ]] || fail "${case_name} 在校验失败后仍调用了 mysql"
}

assert_rejected_before_mysql "bad-user" "teacher_cert" "bad-user" "StrongPass1!"
assert_rejected_before_mysql "long-user" "teacher_cert" \
  "abcdefghijklmnopqrstuvwxyz1234567" "StrongPass1!"
assert_rejected_before_mysql "bad-database" 'teacher`cert' "teacher_app" "StrongPass1!"
assert_rejected_before_mysql "long-database" \
  "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
  "teacher_app" "StrongPass1!"
assert_rejected_before_mysql "root-user" "teacher_cert" "root" "StrongPass1!"
assert_rejected_before_mysql "empty-password" "teacher_cert" "teacher_app" ""

echo "[phase41-mysql-init-test] PASS"
