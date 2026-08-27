#!/usr/bin/env bash
# 在一次性客户端中连接已 inspect 的 MySQL 容器网络命名空间。
# 不发布宿主 3306；客户端自然退出并由 --rm 删除，凭据仅从只读挂载的 login-path 文件读取。
set -euo pipefail

fail() {
  echo "[mysql-container-network-client] FAIL: $*" >&2
  exit 1
}

container_id="${PHASE44_MYSQL_CONTAINER_ID:-}"
client_image_id="${PHASE44_MYSQL_CLIENT_IMAGE_ID:-}"
login_file="${PHASE44_MYSQL_LOGIN_FILE:-}"

[[ "${container_id}" =~ ^[0-9a-f]{64}$ ]] \
  || fail "PHASE44_MYSQL_CONTAINER_ID 必须是完整 64 位小写 container ID"
[[ "${client_image_id}" =~ ^sha256:[0-9a-f]{64}$ ]] \
  || fail "PHASE44_MYSQL_CLIENT_IMAGE_ID 必须是完整 image ID"
[[ "${login_file}" == /* && -f "${login_file}" && -r "${login_file}" ]] \
  || fail "PHASE44_MYSQL_LOGIN_FILE 必须是目标机上可读的绝对路径"
command -v docker >/dev/null 2>&1 || fail "未找到 docker"

observed="$(docker inspect --format '{{.Id}}|{{.Image}}' "${container_id}")" \
  || fail "无法读取已核验 MySQL 容器"
[[ "${observed}" == "${container_id}|${client_image_id}" ]] \
  || fail "MySQL 容器或镜像身份已变化：${observed:-<missing>}"
observed_image_id="$(docker image inspect --format '{{.Id}}' "${client_image_id}")" \
  || fail "无法读取 MySQL 客户端镜像"
[[ "${observed_image_id}" == "${client_image_id}" ]] \
  || fail "MySQL 客户端镜像 ID 不匹配：${observed_image_id:-<missing>}"

exec docker run --rm -i \
  --network "container:${container_id}" \
  --read-only \
  --mount "type=bind,src=${login_file},dst=/root/.mylogin.cnf,readonly" \
  --entrypoint mysql \
  "${client_image_id}" \
  "$@"
