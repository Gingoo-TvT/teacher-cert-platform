#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gate="${repo_root}/scripts/verify-public-entry-closed.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "${tmp_dir}"' EXIT

cat >"${tmp_dir}/curl" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
headers=''
body=''
url=''
while (($#)); do
  case "$1" in
    --dump-header)
      headers="${2:-}"
      shift 2
      ;;
    --output)
      body="${2:-}"
      shift 2
      ;;
    --connect-timeout|--max-time|--write-out)
      shift 2
      ;;
    *)
      url="$1"
      shift
      ;;
  esac
done
if [[ "${url}" == */api/health ]]; then
  response_prefix='API'
else
  response_prefix='ROOT'
fi
curl_status_name="STUB_${response_prefix}_CURL_STATUS"
http_status_name="STUB_${response_prefix}_HTTP_STATUS"
header_name="STUB_${response_prefix}_MAINTENANCE_HEADER"
curl_status="${!curl_status_name}"
http_status="${!http_status_name}"
maintenance_header="${!header_name}"
printf '%s\n' "${url}" >>"${STUB_URL_LOG}"
if [[ -n "${headers}" ]]; then
  printf 'HTTP/1.1 %s Stub\r\n' "${http_status}" >"${headers}"
  [[ -z "${maintenance_header}" ]] \
    || printf '%s\r\n' "${maintenance_header}" >>"${headers}"
  printf '\r\n' >>"${headers}"
fi
[[ -z "${body}" ]] || printf '%s\n' "${STUB_BODY:-stub}" >"${body}"
printf '%s' "${http_status}"
exit "${curl_status}"
STUB
chmod +x "${tmp_dir}/curl"

run_case() {
  local name="$1"
  local root_curl_status="$2"
  local root_http_status="$3"
  local root_header="$4"
  local api_curl_status="$5"
  local api_http_status="$6"
  local api_header="$7"
  local allow_refused="$8"
  : >"${tmp_dir}/${name}.urls"
  PATH="${tmp_dir}:${PATH}" \
  STUB_ROOT_CURL_STATUS="${root_curl_status}" \
  STUB_ROOT_HTTP_STATUS="${root_http_status}" \
  STUB_ROOT_MAINTENANCE_HEADER="${root_header}" \
  STUB_API_CURL_STATUS="${api_curl_status}" \
  STUB_API_HTTP_STATUS="${api_http_status}" \
  STUB_API_MAINTENANCE_HEADER="${api_header}" \
  STUB_URL_LOG="${tmp_dir}/${name}.urls" \
  TCP_RELEASE_PUBLIC_PROBE_URL='https://cert.example.edu/' \
  TCP_RELEASE_ALLOW_CONNECTION_REFUSED="${allow_refused}" \
    bash "${gate}" >"${tmp_dir}/${name}.txt" 2>&1
}

marker='X-Teacher-Cert-Maintenance: second-release'
run_case exact-marker 0 503 "${marker}" 0 503 "${marker}" 0
grep -Fq 'PASS: root and API entry are both closed' "${tmp_dir}/exact-marker.txt"
[[ "$(wc -l <"${tmp_dir}/exact-marker.urls" | tr -d ' ')" == "2" ]]
grep -Fxq 'https://cert.example.edu/' "${tmp_dir}/exact-marker.urls"
grep -Fxq 'https://cert.example.edu/api/health' "${tmp_dir}/exact-marker.urls"

run_case authorized-refusal 7 000 '' 7 000 '' 1
grep -Fq 'root / authorized connection refusal' "${tmp_dir}/authorized-refusal.txt"
grep -Fq 'api /api/health authorized connection refusal' "${tmp_dir}/authorized-refusal.txt"

for spec in \
  'refusal-without-ack|7|000||7|000||0' \
  'dns-failure|6|000||0|503|X-Teacher-Cert-Maintenance: second-release|1' \
  'tls-failure|60|000||0|503|X-Teacher-Cert-Maintenance: second-release|1' \
  'timeout|28|000||0|503|X-Teacher-Cert-Maintenance: second-release|1' \
  'plain-503|0|503||0|503|X-Teacher-Cert-Maintenance: second-release|0' \
  'wrong-marker|0|503|X-Teacher-Cert-Maintenance: other|0|503|X-Teacher-Cert-Maintenance: second-release|0' \
  'business-200|0|200|X-Teacher-Cert-Maintenance: second-release|0|503|X-Teacher-Cert-Maintenance: second-release|0' \
  'root-maintenance-api-business|0|503|X-Teacher-Cert-Maintenance: second-release|0|200|X-Teacher-Cert-Maintenance: second-release|0'; do
  IFS='|' read -r name root_curl root_http root_header api_curl api_http api_header allow_refused <<<"${spec}"
  if run_case "${name}" "${root_curl}" "${root_http}" "${root_header}" \
    "${api_curl}" "${api_http}" "${api_header}" "${allow_refused}"; then
    echo "[public-entry-closed-contract] FAIL: ${name} unexpectedly passed" >&2
    exit 1
  fi
done

grep -Fq 'curl exit 6' "${tmp_dir}/dns-failure.txt"
grep -Fq 'curl exit 60' "${tmp_dir}/tls-failure.txt"
grep -Fq 'curl exit 28' "${tmp_dir}/timeout.txt"
grep -Fq '缺少固定维护 marker' "${tmp_dir}/plain-503.txt"
grep -Fq 'api /api/health 未处于固定维护态: HTTP 200' "${tmp_dir}/root-maintenance-api-business.txt"
[[ "$(wc -l <"${tmp_dir}/root-maintenance-api-business.urls" | tr -d ' ')" == "2" ]]

echo '[public-entry-closed-contract] PASS: root and /api/health both close; root maintenance with API 200 is rejected'
