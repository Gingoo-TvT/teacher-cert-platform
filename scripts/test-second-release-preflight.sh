#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
preflight="${repo_root}/scripts/preflight-second-release.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "${tmp_dir}"' EXIT

mysql_container_id='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
mysql_image_id='sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
redis_image_id='sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'
expected_uuid='12345678-1234-1234-1234-123456789abc'
login_file="${tmp_dir}/.mylogin.cnf"
printf 'stub-login-path\n' >"${login_file}"

cat >"${tmp_dir}/docker" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
command_name="${1:-}"
shift || true
case "${command_name}" in
  context)
    [[ "${1:-}" == 'show' ]]
    printf 'stub-context\n'
    ;;
  info)
    if [[ "${1:-}" == '--format' ]]; then
      printf 'stub-engine-id\n'
    fi
    ;;
  image)
    target="${@: -1}"
    if [[ "${target}" == "${STUB_REDIS_REF}" ]]; then
      printf '%s\n' "${STUB_REDIS_IMAGE_ID}"
    else
      printf '%s\n' "${STUB_MYSQL_IMAGE_ID}"
    fi
    ;;
  inspect)
    template="${2:-}"
    target="${3:-}"
    if [[ "${template}" == '{{.Id}}|{{.Image}}' ]]; then
      printf '%s|%s\n' "${STUB_MYSQL_CONTAINER_ID}" "${STUB_MYSQL_IMAGE_ID}"
    elif [[ "${template}" == *'.Mounts'* ]]; then
      case "${target}" in
        teacher-cert-mysql-prod) printf 'teacher-cert-mysql-data\n' ;;
        teacher-cert-redis-prod) printf 'teacher-cert-redis-data\n' ;;
        teacher-cert-minio-prod) printf 'teacher-cert-minio-data\n' ;;
        teacher-cert-backend-prod) printf 'teacher-cert-video-probe-temp\n' ;;
        *) exit 8 ;;
      esac
    else
      case "${target}" in
        teacher-cert-mysql-prod)
          printf 'teacher-cert-prod|mysql|mysql:8.0.36|%s|healthy\n' "${STUB_MYSQL_IMAGE_ID}"
          ;;
        teacher-cert-redis-prod)
          printf 'teacher-cert-prod|redis|redis:7.4-alpine|sha256:%064d|healthy\n' 4
          ;;
        teacher-cert-minio-prod)
          printf 'teacher-cert-prod|minio|gingoo/teacher-cert-minio-cpu-v1:dfdfb91|sha256:%064d|healthy\n' 5
          ;;
        teacher-cert-backend-prod)
          printf 'teacher-cert-prod|backend|gingoo/teacher-cert-backend:dfdfb91|sha256:%064d|healthy\n' 6
          ;;
        teacher-cert-frontend-prod)
          printf 'teacher-cert-prod|frontend|gingoo/teacher-cert-frontend:dfdfb91|sha256:%064d|healthy\n' 7
          ;;
        *) exit 8 ;;
      esac
    fi
    ;;
  run)
    printf '%s\n' "$@" >"${STUB_DOCKER_RUN_ARGS}"
    cat >/dev/null
    printf '__PHASE44_READ_ONLY__\t1\n'
    printf '__PHASE44_TARGET__\tteacher_cert\t%s\t%s\n' \
      'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd' \
      "${STUB_SERVER_UUID}"
    printf '__PHASE44_COLLATION__\tsys_dict_item\tutf8mb4\tutf8mb4_0900_ai_ci\n'
    printf '__PHASE44_COLLATION__\tsys_dict_type\tutf8mb4\tutf8mb4_0900_ai_ci\n'
    ;;
  *) exit 9 ;;
esac
STUB
chmod +x "${tmp_dir}/docker"

run_case() {
  local case_name="$1"
  local observed_redis_id="$2"
  local observed_server_uuid="$3"
  local evidence_dir="${tmp_dir}/evidence-${case_name}"
  local output_file="${tmp_dir}/${case_name}.txt"
  PATH="${tmp_dir}:${PATH}" \
  STUB_REDIS_REF='teacher-cert-platform/redis-offline:sha-eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee' \
  STUB_REDIS_IMAGE_ID="${observed_redis_id}" \
  STUB_MYSQL_CONTAINER_ID="${mysql_container_id}" \
  STUB_MYSQL_IMAGE_ID="${mysql_image_id}" \
  STUB_SERVER_UUID="${observed_server_uuid}" \
  STUB_DOCKER_RUN_ARGS="${tmp_dir}/docker-run-${case_name}.txt" \
  TCP_RELEASE_ALLOW_PREFLIGHT=1 \
  TCP_RELEASE_AUTHORIZED_TARGET_ACK=1 \
  TCP_RELEASE_EXPECTED_PROJECT='teacher-cert-prod' \
  TCP_RELEASE_EXPECTED_OLD_TAG='dfdfb91' \
  TCP_RELEASE_EXPECTED_MYSQL_VOLUME='teacher-cert-mysql-data' \
  TCP_RELEASE_EXPECTED_REDIS_VOLUME='teacher-cert-redis-data' \
  TCP_RELEASE_EXPECTED_MINIO_VOLUME='teacher-cert-minio-data' \
  TCP_RELEASE_EXPECTED_PROBE_VOLUME='teacher-cert-video-probe-temp' \
  TCP_RELEASE_EXPECTED_MYSQL_SERVER_UUID="${expected_uuid}" \
  TCP_RELEASE_EXPECTED_NEW_REDIS_IMAGE_ID="${redis_image_id}" \
  TCP_RELEASE_CANDIDATE_FINGERPRINT='eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee' \
  TCP_RELEASE_CARRIER_SHA='ffffffffffffffffffffffffffffffffffffffff' \
  TCP_RELEASE_EVIDENCE_DIR="${evidence_dir}" \
  REDIS_CPU_V1_IMAGE='teacher-cert-platform/redis-offline:sha-eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee' \
  PHASE44_ALLOW_IDENTITY_PREFLIGHT=1 \
  PHASE44_AUTHORIZED_TARGET_ACK=1 \
  PHASE44_READ_ONLY_ACCOUNT_ACK=1 \
  PHASE44_MYSQL_LOGIN_PATH='phase44-preflight' \
  PHASE44_MYSQL_LOGIN_FILE="${login_file}" \
  PHASE44_MYSQL_DATABASE='teacher_cert' \
    bash "${preflight}" >"${output_file}" 2>&1
}

run_case pass "${redis_image_id}" "${expected_uuid}"
grep -Fq '[second-release-preflight] PASS:' "${tmp_dir}/pass.txt"
grep -Fxq -- "container:${mysql_container_id}" "${tmp_dir}/docker-run-pass.txt"
grep -Fxq -- '--host=127.0.0.1' "${tmp_dir}/docker-run-pass.txt"
grep -Fxq -- '--port=3306' "${tmp_dir}/docker-run-pass.txt"
grep -Fq "new.redis.imageId=${redis_image_id}" "${tmp_dir}/evidence-pass/docker-target-marker.txt"
grep -Fq "mysql.bound.containerId=${mysql_container_id}" "${tmp_dir}/evidence-pass/docker-target-marker.txt"
grep -Fq "mysql.expected.serverUuid=${expected_uuid}" "${tmp_dir}/evidence-pass/docker-target-marker.txt"
(cd "${tmp_dir}/evidence-pass" && sha256sum --check SHA256SUMS >/dev/null)

if run_case wrong-redis \
  'sha256:9999999999999999999999999999999999999999999999999999999999999999' \
  "${expected_uuid}"; then
  echo '[second-release-preflight-contract] FAIL: wrong Redis image ID unexpectedly passed' >&2
  exit 1
fi
grep -Fq '预载 Redis image ID 不匹配' "${tmp_dir}/wrong-redis.txt"

if run_case wrong-uuid "${redis_image_id}" 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'; then
  echo '[second-release-preflight-contract] FAIL: wrong MySQL server UUID unexpectedly passed' >&2
  exit 1
fi
grep -Fq 'MySQL server UUID 不匹配' "${tmp_dir}/wrong-uuid.txt"

echo '[second-release-preflight-contract] PASS: no host 3306, bound container, exact UUID, Redis image ID, evidence closure'
