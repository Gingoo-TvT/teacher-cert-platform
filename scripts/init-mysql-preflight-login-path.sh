#!/usr/bin/env bash
# 使用预载 MySQL 镜像交互生成 Phase 44 只读 login-path。
# 容器进程使用当前宿主 UID/GID，生成后核对 owner、0600 和调用者可读性。
set -euo pipefail

fail() {
  echo "[mysql-preflight-login-init] FAIL: $*" >&2
  exit 1
}

mysql_image="${MYSQL_CPU_V1_IMAGE:-}"
login_user="${PHASE44_MYSQL_LOGIN_USER:-}"
login_path="${PHASE44_MYSQL_LOGIN_PATH:-phase44-preflight}"
login_dir="${PHASE44_MYSQL_LOGIN_DIR:-${HOME}/.teacher-cert-preflight}"
login_file="${login_dir}/.mylogin.cnf"

[[ -n "${mysql_image}" && "${mysql_image}" != *@* ]] \
  || fail "MYSQL_CPU_V1_IMAGE 必须是 docker load 后可直接解析的本地 tag"
[[ -n "${login_user}" ]] || fail "必须显式设置 PHASE44_MYSQL_LOGIN_USER"
[[ "${login_path}" =~ ^[A-Za-z0-9_.-]{1,64}$ ]] \
  || fail "PHASE44_MYSQL_LOGIN_PATH 格式非法"
[[ "${login_dir}" == /* ]] || fail "PHASE44_MYSQL_LOGIN_DIR 必须是绝对路径"
command -v docker >/dev/null 2>&1 || fail "未找到 docker"
command -v stat >/dev/null 2>&1 || fail "未找到 stat"

host_uid="$(id -u)"
host_gid="$(id -g)"
mkdir -p -- "${login_dir}"
chmod 700 -- "${login_dir}"

docker run --rm -it --pull never \
  --user "${host_uid}:${host_gid}" \
  --env HOME=/preflight-home \
  --mount "type=bind,src=${login_dir},dst=/preflight-home" \
  --entrypoint mysql_config_editor \
  "${mysql_image}" \
  set --login-path="${login_path}" --host=127.0.0.1 \
  --user="${login_user}" --password \
  || fail "mysql_config_editor 未成功生成 login-path"

[[ -f "${login_file}" ]] || fail "未生成 ${login_file}"
observed_owner="$(stat -c '%u:%g' -- "${login_file}")"
[[ "${observed_owner}" == "${host_uid}:${host_gid}" ]] \
  || fail "login-path owner 不匹配: expected=${host_uid}:${host_gid}, actual=${observed_owner}"
chmod 600 -- "${login_file}"
observed_mode="$(stat -c '%a' -- "${login_file}")"
[[ "${observed_mode}" == "600" ]] \
  || fail "login-path mode 必须是 600: actual=${observed_mode}"
[[ -r "${login_file}" ]] || fail "当前操作者不可读 ${login_file}"

echo "[mysql-preflight-login-init] PASS: owner=${observed_owner}, mode=${observed_mode}, file=${login_file}"
