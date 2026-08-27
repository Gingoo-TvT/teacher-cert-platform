#!/usr/bin/env bash
# 第二次部署的只读目标预检。读取第一版容器/卷/镜像身份，并用一个自然退出的 --rm MySQL
# 客户端共享已核验 MySQL 容器的网络命名空间，调用 Phase 44 只读数据库预检。
# 不 pull/build/up/stop/down，不读取容器环境变量，也不修改数据库或数据卷。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
phase44_preflight="${repo_root}/scripts/preflight-phase44-dict-identity.sh"
mysql_network_client="${repo_root}/scripts/mysql-client-via-container-network.sh"

fail() {
  echo "[second-release-preflight] FAIL: $*" >&2
  exit 1
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "必须显式设置 ${name}"
}

[[ "${TCP_RELEASE_ALLOW_PREFLIGHT:-}" == "1" ]] \
  || fail "必须显式设置 TCP_RELEASE_ALLOW_PREFLIGHT=1"
[[ "${TCP_RELEASE_AUTHORIZED_TARGET_ACK:-}" == "1" ]] \
  || fail "必须显式确认 TCP_RELEASE_AUTHORIZED_TARGET_ACK=1"

for required in \
  TCP_RELEASE_EXPECTED_PROJECT \
  TCP_RELEASE_EXPECTED_OLD_TAG \
  TCP_RELEASE_EXPECTED_MYSQL_VOLUME \
  TCP_RELEASE_EXPECTED_REDIS_VOLUME \
  TCP_RELEASE_EXPECTED_MINIO_VOLUME \
  TCP_RELEASE_EXPECTED_PROBE_VOLUME \
  TCP_RELEASE_EXPECTED_MYSQL_SERVER_UUID \
  TCP_RELEASE_EXPECTED_NEW_REDIS_IMAGE_ID \
  TCP_RELEASE_CANDIDATE_FINGERPRINT \
  TCP_RELEASE_CARRIER_SHA \
  TCP_RELEASE_EVIDENCE_DIR \
  REDIS_CPU_V1_IMAGE \
  PHASE44_MYSQL_LOGIN_FILE; do
  require_env "${required}"
done

[[ "${TCP_RELEASE_EXPECTED_PROJECT}" =~ ^[A-Za-z0-9_.-]{1,128}$ ]] \
  || fail "TCP_RELEASE_EXPECTED_PROJECT 格式非法"
[[ "${TCP_RELEASE_EXPECTED_OLD_TAG}" =~ ^[A-Za-z0-9_.-]{1,128}$ ]] \
  || fail "TCP_RELEASE_EXPECTED_OLD_TAG 格式非法"
[[ "${TCP_RELEASE_CANDIDATE_FINGERPRINT}" =~ ^[0-9a-f]{64}$ ]] \
  || fail "TCP_RELEASE_CANDIDATE_FINGERPRINT 必须是 64 位小写 SHA-256"
[[ "${TCP_RELEASE_CARRIER_SHA}" =~ ^[0-9a-f]{40}$ ]] \
  || fail "TCP_RELEASE_CARRIER_SHA 必须是 40 位小写 Git SHA"
[[ "${TCP_RELEASE_EXPECTED_MYSQL_SERVER_UUID}" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]] \
  || fail "TCP_RELEASE_EXPECTED_MYSQL_SERVER_UUID 格式非法"
[[ "${TCP_RELEASE_EXPECTED_NEW_REDIS_IMAGE_ID}" =~ ^sha256:[0-9a-f]{64}$ ]] \
  || fail "TCP_RELEASE_EXPECTED_NEW_REDIS_IMAGE_ID 必须是完整小写 image ID"
[[ "${REDIS_CPU_V1_IMAGE}" != *@* ]] \
  || fail "REDIS_CPU_V1_IMAGE 必须是 docker load 后可直接解析的本地 tag，不能是 digest 引用"
[[ "${PHASE44_MYSQL_LOGIN_FILE}" == /* \
    && -f "${PHASE44_MYSQL_LOGIN_FILE}" \
    && -r "${PHASE44_MYSQL_LOGIN_FILE}" ]] \
  || fail "PHASE44_MYSQL_LOGIN_FILE 必须是目标机上可读的绝对路径"
for volume_name in \
  "${TCP_RELEASE_EXPECTED_MYSQL_VOLUME}" \
  "${TCP_RELEASE_EXPECTED_REDIS_VOLUME}" \
  "${TCP_RELEASE_EXPECTED_MINIO_VOLUME}" \
  "${TCP_RELEASE_EXPECTED_PROBE_VOLUME}"; do
  [[ "${volume_name}" =~ ^[A-Za-z0-9_.-]{1,255}$ ]] \
    || fail "预期卷名格式非法: ${volume_name}"
done

command -v docker >/dev/null 2>&1 || fail "未找到 docker"
command -v sha256sum >/dev/null 2>&1 || fail "未找到 sha256sum"
docker info >/dev/null 2>&1 || fail "Docker Engine 不可读"
docker_context="$(docker context show)"
docker_engine_id="$(docker info --format '{{.ID}}')"
[[ -n "${docker_context}" ]] || fail "Docker context marker 为空"
[[ -n "${docker_engine_id}" ]] || fail "Docker Engine ID marker 为空"
redis_image_id="$(docker image inspect --format '{{.Id}}' "${REDIS_CPU_V1_IMAGE}")" \
  || fail "找不到预载 Redis 本地 tag: ${REDIS_CPU_V1_IMAGE}"
[[ "${redis_image_id}" == "${TCP_RELEASE_EXPECTED_NEW_REDIS_IMAGE_ID}" ]] \
  || fail "预载 Redis image ID 不匹配: expected=${TCP_RELEASE_EXPECTED_NEW_REDIS_IMAGE_ID}, actual=${redis_image_id:-<missing>}"
[[ ! -e "${TCP_RELEASE_EVIDENCE_DIR}" ]] \
  || fail "证据目录已存在，拒绝覆盖: ${TCP_RELEASE_EVIDENCE_DIR}"
mkdir -p -- "${TCP_RELEASE_EVIDENCE_DIR}"

marker="${TCP_RELEASE_EVIDENCE_DIR}/docker-target-marker.txt"
phase44_output="${TCP_RELEASE_EVIDENCE_DIR}/phase44-dict-identity-preflight.txt"

{
  printf 'schema=teacher-cert-second-release-target-v1\n'
  printf 'capturedAtUtc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'host=%s\n' "$(hostname)"
  printf 'docker.context=%s\n' "${docker_context}"
  printf 'docker.engine.id=%s\n' "${docker_engine_id}"
  printf 'expected.project=%s\n' "${TCP_RELEASE_EXPECTED_PROJECT}"
  printf 'expected.old.tag=%s\n' "${TCP_RELEASE_EXPECTED_OLD_TAG}"
  printf 'candidate.fingerprint=%s\n' "${TCP_RELEASE_CANDIDATE_FINGERPRINT}"
  printf 'carrier.sha=%s\n' "${TCP_RELEASE_CARRIER_SHA}"
  printf 'new.redis.imageRef=%s\n' "${REDIS_CPU_V1_IMAGE}"
  printf 'new.redis.imageId=%s\n' "${redis_image_id}"
} >"${marker}"

check_container() {
  local container="$1"
  local expected_service="$2"
  local expected_mount="$3"
  local expected_volume="$4"
  local observed
  local project service image_ref image_id health volume

  observed="$(docker inspect --format \
    '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.Config.Image}}|{{.Image}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "${container}")" || fail "找不到第一版容器: ${container}"
  IFS='|' read -r project service image_ref image_id health <<<"${observed}"
  [[ "${project}" == "${TCP_RELEASE_EXPECTED_PROJECT}" ]] \
    || fail "${container} Compose project 不匹配: ${project}"
  [[ "${service}" == "${expected_service}" ]] \
    || fail "${container} service label 不匹配: ${service}"
  [[ "${health}" == "healthy" ]] \
    || fail "${container} 当前不是 healthy: ${health}"

  if [[ -n "${expected_mount}" ]]; then
    volume="$(docker inspect --format \
      "{{range .Mounts}}{{if eq .Destination \"${expected_mount}\"}}{{.Name}}{{end}}{{end}}" \
      "${container}")"
    [[ "${volume}" == "${expected_volume}" ]] \
      || fail "${container} 数据卷不匹配: expected=${expected_volume}, actual=${volume:-<missing>}"
  else
    volume="-"
  fi

  if [[ "${expected_service}" == "backend" || "${expected_service}" == "frontend" ]]; then
    [[ "${image_ref}" == *":${TCP_RELEASE_EXPECTED_OLD_TAG}" ]] \
      || fail "${container} 不是预期第一版 tag: ${image_ref}"
  fi

  printf 'container=%s service=%s project=%s imageRef=%s imageId=%s health=%s volume=%s\n' \
    "${container}" "${service}" "${project}" "${image_ref}" "${image_id}" "${health}" "${volume}" \
    >>"${marker}"
}

check_container teacher-cert-mysql-prod mysql /var/lib/mysql "${TCP_RELEASE_EXPECTED_MYSQL_VOLUME}"
check_container teacher-cert-redis-prod redis /data "${TCP_RELEASE_EXPECTED_REDIS_VOLUME}"
check_container teacher-cert-minio-prod minio /data "${TCP_RELEASE_EXPECTED_MINIO_VOLUME}"
check_container teacher-cert-backend-prod backend /var/lib/teacher-cert/video-probe "${TCP_RELEASE_EXPECTED_PROBE_VOLUME}"
check_container teacher-cert-frontend-prod frontend '' ''

# 将一次性客户端固定到上方刚核验的完整 MySQL container/image ID；127.0.0.1:3306
# 此时属于该容器的网络命名空间，而不是宿主端口。
mysql_identity_before="$(docker inspect --format '{{.Id}}|{{.Image}}' teacher-cert-mysql-prod)" \
  || fail "无法读取第一版 MySQL 完整身份"
IFS='|' read -r mysql_container_id_before mysql_image_id_before <<<"${mysql_identity_before}"
[[ "${mysql_container_id_before}" =~ ^[0-9a-f]{64}$ \
    && "${mysql_image_id_before}" =~ ^sha256:[0-9a-f]{64}$ ]] \
  || fail "第一版 MySQL 完整 container/image ID 格式非法"
printf 'mysql.bound.containerId=%s\nmysql.bound.imageId=%s\nmysql.expected.serverUuid=%s\n' \
  "${mysql_container_id_before}" \
  "${mysql_image_id_before}" \
  "${TCP_RELEASE_EXPECTED_MYSQL_SERVER_UUID,,}" >>"${marker}"

# Phase 44 会验证 DATABASE()、脱敏 CURRENT_USER() 摘要和授权 server UUID 精确相等，
# 且只在 READ ONLY 一致性事务查询。
if ! PHASE44_MYSQL_CLI="${mysql_network_client}" \
  PHASE44_MYSQL_CLI_BASH_SCRIPT_ACK=1 \
  PHASE44_MYSQL_CONTAINER_ID="${mysql_container_id_before}" \
  PHASE44_MYSQL_CLIENT_IMAGE_ID="${mysql_image_id_before}" \
  PHASE44_MYSQL_HOST=127.0.0.1 \
  PHASE44_MYSQL_PORT=3306 \
  PHASE44_EXPECTED_SERVER_UUID="${TCP_RELEASE_EXPECTED_MYSQL_SERVER_UUID}" \
    bash "${phase44_preflight}" >"${phase44_output}" 2>&1; then
  sed -n '1,80p' "${phase44_output}" >&2
  fail "Phase 44 目标 identity preflight 未通过"
fi
grep -Fq '[phase44-dict-identity-preflight] target:' "${phase44_output}" \
  || fail "Phase 44 输出缺少 target marker"
grep -Fq '[phase44-dict-identity-preflight] PASS:' "${phase44_output}" \
  || fail "Phase 44 输出缺少 PASS marker"

mysql_identity_after="$(docker inspect --format '{{.Id}}|{{.Image}}' teacher-cert-mysql-prod)" \
  || fail "Phase 44 查询后无法再次读取 MySQL 身份"
[[ "${mysql_identity_after}" == "${mysql_identity_before}" ]] \
  || fail "Phase 44 查询期间 MySQL 容器或镜像被替换"
printf 'mysql.bound.identityAfter=%s\n' "${mysql_identity_after}" >>"${marker}"

(
  cd "${TCP_RELEASE_EVIDENCE_DIR}"
  sha256sum docker-target-marker.txt phase44-dict-identity-preflight.txt >SHA256SUMS
  sha256sum --check SHA256SUMS
)

echo "[second-release-preflight] PASS: 第一版五容器/四卷、Redis 本地镜像与绑定 MySQL 容器的 Phase 44 marker 已归档"
echo "[second-release-preflight] evidence=${TCP_RELEASE_EVIDENCE_DIR}"
