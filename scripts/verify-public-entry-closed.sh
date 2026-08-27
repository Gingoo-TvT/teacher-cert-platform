#!/usr/bin/env bash
# 从独立客户端验证第二次部署页面入口和同源 API 入口都已进入固定维护态。
# 每个探针只接受带专用 marker 的 503；明确授权时可接受 curl exit 7（连接拒绝）。
set -euo pipefail

fail() {
  echo "[public-entry-closed] FAIL: $*" >&2
  exit 1
}

root_probe_url="${TCP_RELEASE_PUBLIC_PROBE_URL:-}"
allow_connection_refused="${TCP_RELEASE_ALLOW_CONNECTION_REFUSED:-0}"

[[ "${root_probe_url}" == https://* && "${root_probe_url}" != *'<'* && "${root_probe_url}" != *'>'* ]] \
  || fail "TCP_RELEASE_PUBLIC_PROBE_URL 必须是已授权的完整 HTTPS URL"
[[ "${allow_connection_refused}" == "0" || "${allow_connection_refused}" == "1" ]] \
  || fail "TCP_RELEASE_ALLOW_CONNECTION_REFUSED 只能是 0 或 1"
command -v curl >/dev/null 2>&1 || fail "未找到 curl"

api_probe_url="${root_probe_url%/}/api/health"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "${tmp_dir}"' EXIT

verify_probe() {
  local probe_key="$1"
  local probe_label="$2"
  local probe_url="$3"
  local headers="${tmp_dir}/${probe_key}-headers.txt"
  local body="${tmp_dir}/${probe_key}-body.txt"
  local http_status
  local curl_status

  set +e
  http_status="$(curl --silent --show-error \
    --connect-timeout 5 --max-time 10 \
    --dump-header "${headers}" --output "${body}" \
    --write-out '%{http_code}' "${probe_url}")"
  curl_status=$?
  set -e

  if ((curl_status != 0)); then
    if [[ "${curl_status}" == "7" && "${allow_connection_refused}" == "1" ]]; then
      echo "[public-entry-closed] PASS: ${probe_label} authorized connection refusal (curl exit 7)"
      return 0
    fi
    fail "${probe_label} 传输检查失败: curl exit ${curl_status}; DNS/TLS/超时不得视为入口关闭"
  fi

  [[ "${http_status}" == "503" ]] \
    || fail "${probe_label} 未处于固定维护态: HTTP ${http_status:-<missing>}"
  tr -d '\r' <"${headers}" \
    | grep -Eiq '^X-Teacher-Cert-Maintenance:[[:space:]]*second-release[[:space:]]*$' \
    || fail "${probe_label} HTTP 503 缺少固定维护 marker: X-Teacher-Cert-Maintenance: second-release"

  echo "[public-entry-closed] PASS: ${probe_label} HTTP 503 with exact maintenance marker"
}

verify_probe root 'root /' "${root_probe_url}"
verify_probe api 'api /api/health' "${api_probe_url}"
echo '[public-entry-closed] PASS: root and API entry are both closed'
