#!/usr/bin/env bash
# Phase 41 MySQL 8.4 真实首次初始化回环。
#
# 安全边界：
# - 本脚本会创建/删除两个带专用 label、digest 固定的 mysql:8.4 一次性容器及其匿名卷；
# - 只允许在 Linux CI 或获授权的隔离 Docker 环境由人工/独立复核执行；
# - Codex 不执行本脚本；本地共享 MySQL、生产库和已有数据卷均不在目标范围。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
init_script="${repo_root}/deploy/mysql-init/01-app-user.sh"
tmp_dir="$(mktemp -d)"
suffix="${GITHUB_RUN_ID:-local}_$$_${RANDOM}"
containers=()

fail() {
  echo "[phase41-mysql-init-real] FAIL: $*" >&2
  exit 1
}

cleanup_container() {
  local name="$1"
  local owned
  owned="$(docker inspect --format '{{ index .Config.Labels "teacher-cert.phase41-init-test" }}' \
    "${name}" 2>/dev/null || true)"
  if [[ "${owned}" == "true" ]]; then
    docker rm --force --volumes "${name}" >/dev/null
  fi
}

cleanup() {
  local name
  for name in "${containers[@]:-}"; do
    cleanup_container "${name}"
  done
  rm -rf -- "${tmp_dir}"
}
trap cleanup EXIT

escape_option_value() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  value="${value//$'\b'/\\b}"
  printf '%s' "${value}"
}

wait_until_ready() {
  local name="$1"
  local attempt
  local readiness_log="${tmp_dir}/${name}.readiness.log"
  for attempt in {1..90}; do
    if [[ "$(docker inspect --format '{{.State.Running}}' "${name}" 2>/dev/null || true)" != "true" ]]; then
      docker logs "${name}" >&2 || true
      fail "${name} 在首次初始化期间退出"
    fi
    docker logs "${name}" > "${readiness_log}" 2>&1 || true
    if grep -F "MySQL init process done. Ready for start up." \
        "${readiness_log}" >/dev/null \
        && docker exec "${name}" mysqladmin ping --silent >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done
  docker logs "${name}" >&2 || true
  fail "${name} 在 90 秒内未就绪"
}

run_case() {
  local mode="$1"
  local file_mode="$2"
  local expected_entrypoint_action="$3"
  local name="teacher-cert-p41-init-${mode}-${suffix}"
  local database="p41_init_${mode}"
  local username="p41_app_${mode}"
  local root_password="P41-Disposable-Root-Only!"
  local app_password=$'P41 q\'u\\ote$()#; Bang!'
  local case_dir="${tmp_dir}/${mode}"
  local mounted_script="${case_dir}/01-app-user.sh"
  local env_file="${case_dir}/container.env"
  local client_file="${case_dir}/client.cnf"
  local grants
  local show_grants
  local identity
  local server_version

  mkdir -p "${case_dir}"
  cp "${init_script}" "${mounted_script}"
  chmod "${file_mode}" "${mounted_script}"
  {
    printf 'MYSQL_ROOT_PASSWORD=%s\n' "${root_password}"
    printf 'APP_DATABASE=%s\n' "${database}"
    printf 'DB_USERNAME=%s\n' "${username}"
    printf 'DB_PASSWORD=%s\n' "${app_password}"
  } > "${env_file}"
  chmod 600 "${env_file}"

  containers+=("${name}")
  docker run --detach \
    --name "${name}" \
    --label teacher-cert.phase41-init-test=true \
    --env-file "${env_file}" \
    --volume "${mounted_script}:/docker-entrypoint-initdb.d/01-app-user.sh:ro" \
    mysql:8.4@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb >/dev/null
  wait_until_ready "${name}"

  docker logs "${name}" > "${case_dir}/container.log" 2>&1
  grep -Fq "${expected_entrypoint_action} /docker-entrypoint-initdb.d/01-app-user.sh" \
    "${case_dir}/container.log" \
    || fail "${mode} 未走官方 entrypoint 的 ${expected_entrypoint_action} 路径"
  if grep -Fq "${app_password}" "${case_dir}/container.log"; then
    fail "${mode} 容器日志泄露应用口令"
  fi

  {
    printf '[client]\n'
    printf 'user="%s"\n' "${username}"
    printf 'password="%s"\n' "$(escape_option_value "${app_password}")"
    printf 'database="%s"\n' "${database}"
    printf 'protocol=socket\n'
  } > "${client_file}"
  chmod 600 "${client_file}"
  docker cp "${client_file}" "${name}:/tmp/phase41-client.cnf" >/dev/null
  docker exec "${name}" chmod 600 /tmp/phase41-client.cnf

  server_version="$(docker exec "${name}" \
    mysql --defaults-file=/tmp/phase41-client.cnf --batch --skip-column-names \
    --execute 'SELECT VERSION()')"
  [[ "${server_version}" == 8.4.* ]] || fail "${mode} 实际服务端不是 MySQL 8.4"

  identity="$(docker exec "${name}" \
    mysql --defaults-file=/tmp/phase41-client.cnf --batch --skip-column-names \
    --execute 'SELECT CONCAT(DATABASE(), "|", CURRENT_USER())')"
  [[ "${identity}" == "${database}|${username}@%" ]] \
    || fail "${mode} 特殊口令真实认证或目标库绑定失败"

  grants="$(docker exec "${name}" \
    mysql --defaults-file=/tmp/phase41-client.cnf --batch --skip-column-names \
    --execute "
      SELECT COALESCE(
        GROUP_CONCAT(PRIVILEGE_TYPE ORDER BY PRIVILEGE_TYPE SEPARATOR ','),
        ''
      )
      FROM information_schema.SCHEMA_PRIVILEGES
      WHERE GRANTEE = CONCAT(QUOTE(SUBSTRING_INDEX(CURRENT_USER(), '@', 1)), '@',
                             QUOTE(SUBSTRING_INDEX(CURRENT_USER(), '@', -1)))
        AND TABLE_SCHEMA = DATABASE();
      SELECT COUNT(*)
      FROM information_schema.USER_PRIVILEGES
      WHERE GRANTEE = CONCAT(QUOTE(SUBSTRING_INDEX(CURRENT_USER(), '@', 1)), '@',
                             QUOTE(SUBSTRING_INDEX(CURRENT_USER(), '@', -1)))
        AND PRIVILEGE_TYPE <> 'USAGE';
      SELECT COUNT(*)
      FROM information_schema.SCHEMA_PRIVILEGES
      WHERE GRANTEE = CONCAT(QUOTE(SUBSTRING_INDEX(CURRENT_USER(), '@', 1)), '@',
                             QUOTE(SUBSTRING_INDEX(CURRENT_USER(), '@', -1)))
        AND IS_GRANTABLE = 'YES';
    ")"
  [[ "${grants}" == $'ALTER,CREATE,DELETE,DROP,INDEX,INSERT,REFERENCES,SELECT,UPDATE\n0\n0' ]] \
    || fail "${mode} 最小权限集合、全局权限或 GRANT OPTION 不符合预期"
  show_grants="$(docker exec "${name}" \
    mysql --defaults-file=/tmp/phase41-client.cnf --batch --skip-column-names \
    --execute 'SHOW GRANTS FOR CURRENT_USER')"
  [[ "$(printf '%s\n' "${show_grants}" | wc -l)" -eq 2 ]] \
    || fail "${mode} 存在预期两条之外的全局/对象/角色授权"
  printf '%s\n' "${show_grants}" \
    | grep -Fx "GRANT USAGE ON *.* TO \`${username}\`@\`%\`" >/dev/null \
    || fail "${mode} 全局 USAGE 基线异常"
  printf '%s\n' "${show_grants}" \
    | grep -Fx "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER ON \`${database}\`.* TO \`${username}\`@\`%\`" >/dev/null \
    || fail "${mode} schema 最小权限语句异常"

  cleanup_container "${name}"
  echo "[phase41-mysql-init-real] ${mode}: PASS"
}

[[ "$(uname -s)" == Linux* ]] \
  || fail "真实 entrypoint 文件权限分支仅在 Linux CI/隔离 Docker 主机执行"
command -v docker >/dev/null 2>&1 || fail "未找到 docker"
[[ -f "${init_script}" ]] || fail "未找到初始化脚本"
if [[ "${GITHUB_ACTIONS:-}" != "true" \
    && "${PHASE41_ALLOW_REAL_DOCKER_TEST:-}" != "1" ]]; then
  fail "非 GitHub CI 必须显式设置 PHASE41_ALLOW_REAL_DOCKER_TEST=1"
fi

run_case "sourced" "0644" "sourcing"
run_case "executable" "0755" "running"

echo "[phase41-mysql-init-real] PASS"
