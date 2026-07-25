#!/usr/bin/env bash
# Phase 41 真实恢复 runner 的纯静态契约测试。
# mysql、mvn 与 uname 均由临时 stub 接管；本脚本不连接网络、数据库或对象存储。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runner="${repo_root}/scripts/test-phase41-backup-restore-real.sh"
tmp_dir="$(mktemp -d)"
stub_dir="${tmp_dir}/bin"
rsa_key="${tmp_dir}/server-rsa-public-key.pem"
datasource_password='phase41-contract-$-db-password-sentinel'
minio_secret_key='phase41-contract-$-minio-secret-sentinel'

cleanup() {
  rm -rf -- "${tmp_dir}"
}
trap cleanup EXIT

mkdir -p "${stub_dir}"
printf '%s\n' 'contract-only-public-key' > "${rsa_key}"

cat > "${stub_dir}/uname" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' 'Linux'
STUB

cat > "${stub_dir}/mysql" <<'STUB'
#!/usr/bin/env bash
: "${PHASE41_MYSQL_MARKER:?}"
[[ "$#" -eq 3 \
  && "$1" == "--no-login-paths" \
  && "$2" == "--no-defaults" \
  && "$3" == "--version" ]] \
  || { echo "unexpected mysql invocation" >&2; exit 91; }
[[ ! -e "${PHASE41_MYSQL_MARKER}" ]] \
  || { echo "mysql stub invoked more than once" >&2; exit 92; }
printf '%s\n' 'called' > "${PHASE41_MYSQL_MARKER}"
printf '%s\n' 'mysql  Ver 8.4.10 for Linux on x86_64'
STUB

cat > "${stub_dir}/mvn" <<'STUB'
#!/usr/bin/env bash
: "${PHASE41_MVN_MARKER:?}"
[[ ! -e "${PHASE41_MVN_MARKER}" ]] \
  || { echo "Maven stub invoked more than once" >&2; exit 93; }
printf '%s\n' 'called' > "${PHASE41_MVN_MARKER}"
STUB

chmod +x "${stub_dir}/uname" "${stub_dir}/mysql" "${stub_dir}/mvn"

cat > "${stub_dir}/network-command-must-not-run" <<'STUB'
#!/usr/bin/env bash
echo "unexpected network-capable command" >&2
exit 94
STUB
chmod +x "${stub_dir}/network-command-must-not-run"
for command_name in curl redis-cli mc nc docker mysqladmin; do
  cp "${stub_dir}/network-command-must-not-run" "${stub_dir}/${command_name}"
done

run_contract_case() {
  local name="$1"
  local jdbc_url="$2"
  local output="${tmp_dir}/${name}.log"
  local marker="${tmp_dir}/${name}.mvn-called"
  local mysql_marker="${tmp_dir}/${name}.mysql-called"
  local status

  set +e
  (
    unset PHASE41_RUN_TOKEN
    PATH="${stub_dir}:/usr/bin:/bin" \
    MVN="${stub_dir}/mvn" \
    PHASE41_MYSQL_CLI="${stub_dir}/mysql" \
    PHASE41_MVN_MARKER="${marker}" \
    PHASE41_MYSQL_MARKER="${mysql_marker}" \
    PHASE41_ALLOW_BACKUP_RESTORE_TEST=1 \
    PHASE41_ISOLATED_ENVIRONMENT_ACK=1 \
    PHASE41_DEDICATED_TARGETS_ACK=1 \
    SPRING_DATASOURCE_URL="${jdbc_url}" \
    SPRING_DATASOURCE_USERNAME="phase41_contract" \
    SPRING_DATASOURCE_PASSWORD="${datasource_password}" \
    SPRING_DATA_REDIS_HOST="127.0.0.1" \
    SPRING_DATA_REDIS_PORT="6379" \
    MINIO_ENDPOINT="http://127.0.0.1:9000" \
    MINIO_ACCESS_KEY="contract-only-access" \
    MINIO_SECRET_KEY="${minio_secret_key}" \
    MINIO_BUCKET="teacher-cert-p41-contract" \
      bash "${runner}"
  ) > "${output}" 2>&1
  status=$?
  set -e

  CONTRACT_STATUS="${status}"
  CONTRACT_OUTPUT="${output}"
  CONTRACT_MARKER="${marker}"
  CONTRACT_MYSQL_MARKER="${mysql_marker}"

  for secret in "${datasource_password}" "${minio_secret_key}"; do
    if grep -Fq -- "${secret}" "${output}"; then
      echo "${name}: secret sentinel leaked to output" >&2
      exit 1
    fi
  done
}

expect_success() {
  local name="$1"
  local jdbc_url="$2"
  local expected_mode="$3"

  run_contract_case "${name}" "${jdbc_url}"
  [[ "${CONTRACT_STATUS}" -eq 0 ]] \
    || { sed -n '1,120p' "${CONTRACT_OUTPUT}" >&2; exit 1; }
  [[ -f "${CONTRACT_MARKER}" ]] \
    || { echo "${name}: Maven stub was not called" >&2; exit 1; }
  [[ -f "${CONTRACT_MYSQL_MARKER}" ]] \
    || { echo "${name}: mysql version stub was not called" >&2; exit 1; }
  grep -Fq "JDBC 冷缓存认证模式: ${expected_mode}" "${CONTRACT_OUTPUT}" \
    || { sed -n '1,120p' "${CONTRACT_OUTPUT}" >&2; exit 1; }
  grep -Fq "[phase41-backup-restore-real] PASS" "${CONTRACT_OUTPUT}" \
    || { sed -n '1,120p' "${CONTRACT_OUTPUT}" >&2; exit 1; }
}

expect_failure() {
  local name="$1"
  local jdbc_url="$2"
  local expected_message="$3"

  run_contract_case "${name}" "${jdbc_url}"
  [[ "${CONTRACT_STATUS}" -ne 0 ]] \
    || { echo "${name}: expected failure" >&2; exit 1; }
  [[ ! -e "${CONTRACT_MARKER}" ]] \
    || { echo "${name}: Maven stub must not run after preflight failure" >&2; exit 1; }
  [[ ! -e "${CONTRACT_MYSQL_MARKER}" ]] \
    || { echo "${name}: mysql must not run after JDBC authentication preflight failure" >&2; exit 1; }
  grep -Fq "${expected_message}" "${CONTRACT_OUTPUT}" \
    || { sed -n '1,120p' "${CONTRACT_OUTPUT}" >&2; exit 1; }
}

jdbc_base='jdbc:mysql://127.0.0.1:3306/teacher_cert_p41'
auth_failure='必须为冷认证缓存显式配置'

expect_success allow-public-key \
  "${jdbc_base}?useSSL=false&allowPublicKeyRetrieval=true" \
  loopback-public-key-retrieval
expect_success mixed-case-public-key \
  "${jdbc_base}?useUnicode=true&ALLOWPUBLICKEYRETRIEVAL=TrUe&useSSL=false" \
  loopback-public-key-retrieval
expect_success verified-tls \
  "${jdbc_base}?sslmode=verify_identity" \
  verified-tls
expect_success verified-ca \
  "${jdbc_base}?useUnicode=true&sslMode=VERIFY_CA&serverTimezone=Asia%2FShanghai" \
  verified-tls
expect_success controlled-rsa-key \
  "${jdbc_base}?serverrsapublickeyfile=${rsa_key}&useSSL=false" \
  controlled-server-rsa-key
expect_success disabled-public-key-with-rsa \
  "${jdbc_base}?allowPublicKeyRetrieval=false&serverRSAPublicKeyFile=${rsa_key}" \
  controlled-server-rsa-key
expect_success required-tls-with-public-key \
  "${jdbc_base}?sslMode=REQUIRED&allowPublicKeyRetrieval=true" \
  loopback-public-key-retrieval

expect_failure no-query \
  "${jdbc_base}" \
  "${auth_failure}"
expect_failure empty-query \
  "${jdbc_base}?" \
  "${auth_failure}"
expect_failure missing-auth-contract \
  "${jdbc_base}?useSSL=false" \
  "${auth_failure}"
expect_failure public-key-disabled \
  "${jdbc_base}?allowPublicKeyRetrieval=false&useSSL=false" \
  "${auth_failure}"
expect_failure public-key-empty \
  "${jdbc_base}?allowPublicKeyRetrieval=" \
  "allowPublicKeyRetrieval 只允许 true 或 false"
expect_failure public-key-invalid \
  "${jdbc_base}?allowPublicKeyRetrieval=yes" \
  "allowPublicKeyRetrieval 只允许 true 或 false"
expect_failure disabled-tls \
  "${jdbc_base}?sslMode=DISABLED" \
  "${auth_failure}"
expect_failure preferred-tls \
  "${jdbc_base}?sslMode=PREFERRED" \
  "${auth_failure}"
expect_failure unverified-tls \
  "${jdbc_base}?sslMode=REQUIRED" \
  "${auth_failure}"
expect_failure unknown-tls \
  "${jdbc_base}?sslMode=STRICT" \
  "sslMode 必须使用 Connector/J 明确支持的枚举值"
expect_failure empty-rsa-key \
  "${jdbc_base}?serverRSAPublicKeyFile=" \
  "serverRSAPublicKeyFile 必须是受控、可读的绝对文件路径"
expect_failure relative-rsa-key \
  "${jdbc_base}?serverRSAPublicKeyFile=relative-key.pem" \
  "serverRSAPublicKeyFile 必须是受控、可读的绝对文件路径"
expect_failure directory-rsa-key \
  "${jdbc_base}?serverRSAPublicKeyFile=${tmp_dir}" \
  "serverRSAPublicKeyFile 必须是受控、可读的绝对文件路径"
expect_failure missing-rsa-key \
  "${jdbc_base}?serverRSAPublicKeyFile=/phase41-contract/missing.pem" \
  "serverRSAPublicKeyFile 必须是受控、可读的绝对文件路径"
expect_failure duplicate-public-key-setting \
  "${jdbc_base}?allowPublicKeyRetrieval=true&ALLOWPUBLICKEYRETRIEVAL=false" \
  "不得重复 allowPublicKeyRetrieval"
expect_failure duplicate-tls-setting \
  "${jdbc_base}?sslMode=VERIFY_CA&SSLMODE=VERIFY_IDENTITY" \
  "不得重复 sslMode"
expect_failure duplicate-rsa-setting \
  "${jdbc_base}?serverRSAPublicKeyFile=${rsa_key}&SERVERRSAPUBLICKEYFILE=${rsa_key}" \
  "不得重复 serverRSAPublicKeyFile"
expect_failure encoded-query-key \
  "${jdbc_base}?%61llowPublicKeyRetrieval=true" \
  "query key 必须是未编码 ASCII 标识符"
expect_failure missing-equals \
  "${jdbc_base}?allowPublicKeyRetrieval" \
  "每个 query 参数都必须使用 key=value"

echo "[phase41-backup-restore-contract] PASS"
