#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
preflight="${repo_root}/scripts/preflight-phase44-dict-identity.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "${tmp_dir}"' EXIT

cat >"${tmp_dir}/mysql_config_editor" <<'STUB'
#!/usr/bin/env bash
[[ "${1:-}" == "print" ]]
STUB

cat >"${tmp_dir}/mysql" <<'STUB'
#!/usr/bin/env bash
cat >/dev/null
case "${STUB_CASE:-pass}" in
  client-failure)
    echo "stub mysql failure" >&2
    exit 9
    ;;
esac

printf '__PHASE44_READ_ONLY__\t1\n'
if [[ "${STUB_CASE:-pass}" != "missing-target" ]]; then
  database="teacher_cert"
  server_uuid='12345678-1234-1234-1234-123456789abc'
  [[ "${STUB_CASE:-pass}" != "wrong-schema" ]] || database="wrong_schema"
  [[ "${STUB_CASE:-pass}" != "wrong-uuid" ]] || server_uuid='aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
  printf '__PHASE44_TARGET__\t%s\t%s\t%s\n' \
    "${database}" \
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
    "${server_uuid}"
fi
if [[ "${STUB_CASE:-pass}" == "duplicate-target" ]]; then
  printf '__PHASE44_TARGET__\tteacher_cert\t%s\t%s\n' \
    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
    '87654321-4321-4321-4321-cba987654321'
fi
printf '__PHASE44_COLLATION__\tsys_dict_item\tutf8mb4\tutf8mb4_0900_ai_ci\n'
printf '__PHASE44_COLLATION__\tsys_dict_type\tutf8mb4\tutf8mb4_0900_ai_ci\n'
STUB

chmod +x "${tmp_dir}/mysql" "${tmp_dir}/mysql_config_editor"

run_case() {
  local case_name="$1"
  local output_file="$2"
  local expected_server_uuid='12345678-1234-1234-1234-123456789abc'
  [[ "${case_name}" != "missing-expected-uuid" ]] || expected_server_uuid=''
  STUB_CASE="${case_name}" \
  PHASE44_MYSQL_CLI="${tmp_dir}/mysql" \
  PHASE44_MYSQL_CONFIG_EDITOR="${tmp_dir}/mysql_config_editor" \
  PHASE44_ALLOW_IDENTITY_PREFLIGHT=1 \
  PHASE44_AUTHORIZED_TARGET_ACK=1 \
  PHASE44_READ_ONLY_ACCOUNT_ACK=1 \
  PHASE44_MYSQL_HOST=127.0.0.1 \
  PHASE44_MYSQL_PORT=3306 \
  PHASE44_MYSQL_DATABASE=teacher_cert \
  PHASE44_EXPECTED_SERVER_UUID="${expected_server_uuid}" \
    bash "${preflight}" >"${output_file}" 2>&1
}

pass_output="${tmp_dir}/pass.txt"
run_case pass "${pass_output}"
grep -Fq 'target: database=teacher_cert' "${pass_output}"
grep -Fq 'serverUuid=12345678-1234-1234-1234-123456789abc' "${pass_output}"
grep -Fq 'PASS: columns=2/2' "${pass_output}"

expect_failure() {
  local case_name="$1"
  local expected="$2"
  local output_file="${tmp_dir}/${case_name}.txt"
  if run_case "${case_name}" "${output_file}"; then
    echo "[phase44-preflight-contract] FAIL: ${case_name} unexpectedly passed" >&2
    exit 1
  fi
  grep -Fq "${expected}" "${output_file}"
}

expect_failure wrong-schema '目标 marker schema 不匹配'
expect_failure wrong-uuid '目标 marker 的 MySQL server UUID 不匹配'
expect_failure missing-expected-uuid '必须显式设置 PHASE44_EXPECTED_SERVER_UUID'
expect_failure missing-target '目标 marker 必须精确出现一次，实际 0 次'
expect_failure duplicate-target '目标 marker 必须精确出现一次，实际 2 次'
expect_failure client-failure 'MySQL 只读 preflight 执行失败'

echo '[phase44-preflight-contract] PASS: target marker, exact/missing/wrong UUID, wrong/missing/duplicate target, client failure'
