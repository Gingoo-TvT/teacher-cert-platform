#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
wrapper="${repo_root}/scripts/mysql-client-via-container-network.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "${tmp_dir}"' EXIT

container_id='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
image_id='sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
login_file="${tmp_dir}/.mylogin.cnf"
printf 'stub-login-path\n' >"${login_file}"

cat >"${tmp_dir}/docker" <<'STUB'
#!/usr/bin/env bash
case "${1:-}" in
  inspect)
    printf '%s|%s\n' "${STUB_OBSERVED_CONTAINER_ID}" "${STUB_OBSERVED_IMAGE_ID}"
    ;;
  image)
    printf '%s\n' "${STUB_OBSERVED_IMAGE_ID}"
    ;;
  run)
    printf '%s\n' "$@" >"${STUB_ARGS_FILE}"
    cat >"${STUB_STDIN_FILE}"
    printf 'stub-client-output\n'
    ;;
  *) exit 9 ;;
esac
STUB
chmod +x "${tmp_dir}/docker"

output="$({
  printf 'SELECT 1;\n'
} | PATH="${tmp_dir}:${PATH}" \
  STUB_OBSERVED_CONTAINER_ID="${container_id}" \
  STUB_OBSERVED_IMAGE_ID="${image_id}" \
  STUB_ARGS_FILE="${tmp_dir}/args.txt" \
  STUB_STDIN_FILE="${tmp_dir}/stdin.txt" \
  PHASE44_MYSQL_CONTAINER_ID="${container_id}" \
  PHASE44_MYSQL_CLIENT_IMAGE_ID="${image_id}" \
  PHASE44_MYSQL_LOGIN_FILE="${login_file}" \
    bash "${wrapper}" --no-defaults --login-path=phase44-preflight --host=127.0.0.1 --port=3306)"

[[ "${output}" == 'stub-client-output' ]]
grep -Fxq -- '--rm' "${tmp_dir}/args.txt"
grep -Fxq -- '-i' "${tmp_dir}/args.txt"
grep -Fxq -- "container:${container_id}" "${tmp_dir}/args.txt"
grep -Fxq -- "type=bind,src=${login_file},dst=/root/.mylogin.cnf,readonly" "${tmp_dir}/args.txt"
grep -Fxq -- '--entrypoint' "${tmp_dir}/args.txt"
grep -Fxq -- 'mysql' "${tmp_dir}/args.txt"
grep -Fxq -- '--host=127.0.0.1' "${tmp_dir}/args.txt"
grep -Fxq 'SELECT 1;' "${tmp_dir}/stdin.txt"

if PATH="${tmp_dir}:${PATH}" \
  STUB_OBSERVED_CONTAINER_ID='cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc' \
  STUB_OBSERVED_IMAGE_ID="${image_id}" \
  PHASE44_MYSQL_CONTAINER_ID="${container_id}" \
  PHASE44_MYSQL_CLIENT_IMAGE_ID="${image_id}" \
  PHASE44_MYSQL_LOGIN_FILE="${login_file}" \
    bash "${wrapper}" --version >"${tmp_dir}/mismatch.txt" 2>&1; then
  echo '[mysql-container-network-contract] FAIL: changed container unexpectedly passed' >&2
  exit 1
fi
grep -Fq 'MySQL 容器或镜像身份已变化' "${tmp_dir}/mismatch.txt"

echo '[mysql-container-network-contract] PASS: exact container network, login-path bind, stdin, changed identity failure'
