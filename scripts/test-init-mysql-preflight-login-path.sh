#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
initializer="${repo_root}/scripts/init-mysql-preflight-login-path.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "${tmp_dir}"' EXIT

cat >"${tmp_dir}/docker" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
[[ "${1:-}" == "run" ]]
shift
printf '%s\n' "$@" >"${STUB_ARGS_FILE}"
[[ "${STUB_DOCKER_FAIL:-0}" != "1" ]] || exit 17
mount_spec=''
while (($#)); do
  case "$1" in
    --mount)
      mount_spec="${2:-}"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
host_dir="${mount_spec#*src=}"
host_dir="${host_dir%%,dst=*}"
[[ -n "${host_dir}" && "${host_dir}" != "${mount_spec}" ]]
printf 'stub-login-path\n' >"${host_dir}/.mylogin.cnf"
chmod 644 "${host_dir}/.mylogin.cnf"
STUB
chmod +x "${tmp_dir}/docker"

cat >"${tmp_dir}/chmod" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${STUB_CHMOD_LOG}"
exec /usr/bin/chmod "$@"
STUB
chmod +x "${tmp_dir}/chmod"

cat >"${tmp_dir}/stat" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == '-c' ]]; then
  case "${2:-}" in
    '%u:%g') printf '%s\n' "${STUB_HOST_OWNER}"; exit 0 ;;
    '%a') printf '600\n'; exit 0 ;;
  esac
fi
exec /usr/bin/stat "$@"
STUB
chmod +x "${tmp_dir}/stat"

login_dir="${tmp_dir}/login"
output="$(PATH="${tmp_dir}:${PATH}" \
  STUB_ARGS_FILE="${tmp_dir}/args.txt" \
  STUB_CHMOD_LOG="${tmp_dir}/chmod.txt" \
  STUB_HOST_OWNER="$(id -u):$(id -g)" \
  MYSQL_CPU_V1_IMAGE='mysql:8.0.36' \
  PHASE44_MYSQL_LOGIN_USER='phase44_reader' \
  PHASE44_MYSQL_LOGIN_PATH='phase44-preflight' \
  PHASE44_MYSQL_LOGIN_DIR="${login_dir}" \
    bash "${initializer}")"

[[ "${output}" == *'[mysql-preflight-login-init] PASS:'* ]]
[[ "$(stat -c '%u:%g' "${login_dir}/.mylogin.cnf")" == "$(id -u):$(id -g)" ]]
grep -Fq -- "600 -- ${login_dir}/.mylogin.cnf" "${tmp_dir}/chmod.txt"
grep -Fxq -- '--pull' "${tmp_dir}/args.txt"
grep -Fxq -- 'never' "${tmp_dir}/args.txt"
grep -Fxq -- '--user' "${tmp_dir}/args.txt"
grep -Fxq -- "$(id -u):$(id -g)" "${tmp_dir}/args.txt"
grep -Fxq -- 'HOME=/preflight-home' "${tmp_dir}/args.txt"
grep -Fxq -- '--password' "${tmp_dir}/args.txt"

if PATH="${tmp_dir}:${PATH}" \
  STUB_ARGS_FILE="${tmp_dir}/fail-args.txt" \
  STUB_CHMOD_LOG="${tmp_dir}/fail-chmod.txt" \
  STUB_HOST_OWNER="$(id -u):$(id -g)" \
  STUB_DOCKER_FAIL=1 \
  MYSQL_CPU_V1_IMAGE='mysql:8.0.36' \
  PHASE44_MYSQL_LOGIN_USER='phase44_reader' \
  PHASE44_MYSQL_LOGIN_DIR="${tmp_dir}/failed-login" \
    bash "${initializer}" >"${tmp_dir}/failure.txt" 2>&1; then
  echo '[mysql-preflight-login-init-contract] FAIL: docker failure unexpectedly passed' >&2
  exit 1
fi
grep -Fq 'mysql_config_editor 未成功生成 login-path' "${tmp_dir}/failure.txt"

echo '[mysql-preflight-login-init-contract] PASS: host UID/GID, HOME, mode/readability, docker failure propagation'
