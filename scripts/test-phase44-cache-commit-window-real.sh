#!/usr/bin/env bash
# Phase 44 字典缓存提交窗口真实 MySQL/Redis 门禁。
#
# 安全边界：
# - 本脚本不会启动或删除 Docker/服务，但目标 IT 会在指定 MySQL 中创建/删除
#   p44_commit_window* 固定夹具，并在指定 Redis 中创建/删除对应测试键；
# - 只允许用户或独立复核者在明确授权、全新、隔离且可销毁的回环环境执行；
# - Codex 不执行本脚本；严禁指向生产、共享开发库或包含真实业务数据的依赖。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mvn_cmd="${MVN:-mvn}"
suite_simple="Phase44CacheCommitWindowIT"
suite_class="cn.edu.gpnu.platform.boot.${suite_simple}"
report_dir="${repo_root}/platform-boot/target/failsafe-reports"
report_xml="${report_dir}/TEST-${suite_class}.xml"
report_txt="${report_dir}/${suite_class}.txt"
summary_xml="${report_dir}/failsafe-summary.xml"

fail() {
  echo "[phase44-cache-commit-window-real] FAIL: $*" >&2
  exit 1
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "必须显式设置 ${name}"
}

is_loopback_hostport() {
  local authority="$1"
  local port
  [[ "${authority}" =~ ^(localhost|127\.0\.0\.1):([0-9]{1,5})$ ]] || return 1
  port="${BASH_REMATCH[2]}"
  (( 10#${port} >= 1 && 10#${port} <= 65535 ))
}

find_python3() {
  if [[ -n "${PYTHON:-}" ]]; then
    command -v "${PYTHON}" >/dev/null 2>&1 \
      || fail "未找到 PYTHON 指定的解释器: ${PYTHON}"
    python_cmd="${PYTHON}"
  elif command -v python3 >/dev/null 2>&1; then
    python_cmd="python3"
  elif command -v python >/dev/null 2>&1; then
    python_cmd="python"
  else
    fail "缺少 Python 3，无法对 Failsafe XML 做结构化计数校验"
  fi
  "${python_cmd}" -c \
    'import sys; raise SystemExit(0 if sys.version_info >= (3, 8) else 1)' \
    || fail "XML 校验要求 Python 3.8+"
}

[[ "${PHASE44_ALLOW_CACHE_COMMIT_WINDOW_IT:-}" == "1" ]] \
  || fail "必须显式设置 PHASE44_ALLOW_CACHE_COMMIT_WINDOW_IT=1"
[[ "${PHASE44_ISOLATED_ENVIRONMENT_ACK:-}" == "1" ]] \
  || fail "必须显式确认 PHASE44_ISOLATED_ENVIRONMENT_ACK=1"
[[ "${PHASE44_DEDICATED_TARGETS_ACK:-}" == "1" ]] \
  || fail "必须显式确认 MySQL/Redis 均为本轮专用、可销毁目标"

require_env SPRING_DATASOURCE_URL
require_env SPRING_DATASOURCE_USERNAME
require_env SPRING_DATASOURCE_PASSWORD
require_env SPRING_DATA_REDIS_HOST
require_env SPRING_DATA_REDIS_PORT
require_env SPRING_DATA_REDIS_DATABASE

jdbc_authority="${SPRING_DATASOURCE_URL#jdbc:mysql://}"
[[ "${jdbc_authority}" != "${SPRING_DATASOURCE_URL}" && "${jdbc_authority}" == */* ]] \
  || fail "SPRING_DATASOURCE_URL 必须是单一 MySQL JDBC URL"
jdbc_hostport="${jdbc_authority%%/*}"
is_loopback_hostport "${jdbc_hostport}" \
  || fail "SPRING_DATASOURCE_URL 必须指向 localhost/127.0.0.1 的隔离 MySQL"
jdbc_path_and_query="${jdbc_authority#*/}"
jdbc_database="${jdbc_path_and_query%%\?*}"
[[ "${jdbc_database}" =~ ^[A-Za-z0-9_]{1,64}$ ]] \
  || fail "MySQL schema 名必须是 1..64 位字母、数字或下划线"

case "${SPRING_DATA_REDIS_HOST}" in
  localhost|127.0.0.1) ;;
  *) fail "SPRING_DATA_REDIS_HOST 必须是 localhost/127.0.0.1" ;;
esac
[[ "${SPRING_DATA_REDIS_PORT}" =~ ^[0-9]{1,5}$ ]] \
  && (( 10#${SPRING_DATA_REDIS_PORT} >= 1 && 10#${SPRING_DATA_REDIS_PORT} <= 65535 )) \
  || fail "SPRING_DATA_REDIS_PORT 必须是 1..65535"
[[ "${SPRING_DATA_REDIS_DATABASE}" =~ ^[0-9]{1,3}$ ]] \
  && (( 10#${SPRING_DATA_REDIS_DATABASE} <= 255 )) \
  || fail "SPRING_DATA_REDIS_DATABASE 必须是 0..255"

command -v "${mvn_cmd}" >/dev/null 2>&1 || fail "未找到 Maven: ${mvn_cmd}"
find_python3

# 精确移除本 suite 的旧报告，避免错误类名或零测试复用历史 XML 假绿。
rm -f -- "${report_xml}" "${report_txt}" "${summary_xml}"

echo "[phase44-cache-commit-window-real] MySQL: ${jdbc_hostport}/${jdbc_database}"
echo "[phase44-cache-commit-window-real] Redis: ${SPRING_DATA_REDIS_HOST}:${SPRING_DATA_REDIS_PORT}/${SPRING_DATA_REDIS_DATABASE}"
echo "[phase44-cache-commit-window-real] 将运行唯一 suite ${suite_class} 并要求精确 16/16"

"${mvn_cmd}" -f "${repo_root}/pom.xml" -B -ntp -o \
  -pl platform-boot -am \
  "-Dit.test=${suite_simple}" \
  "-Dfailsafe.failIfNoSpecifiedTests=false" \
  verify

[[ -f "${report_xml}" ]] \
  || fail "目标 suite 没有生成 XML；错误 suite 或零测试不得通过"
[[ -f "${summary_xml}" ]] \
  || fail "platform-boot 没有生成 failsafe-summary.xml"

"${python_cmd}" - "${report_xml}" "${summary_xml}" "${suite_class}" "${suite_simple}" <<'PY'
from __future__ import annotations

import hashlib
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPORT = Path(sys.argv[1])
SUMMARY = Path(sys.argv[2])
SUITE_CLASS = sys.argv[3]
SUITE_SIMPLE = sys.argv[4]
EXPECTED = 16


def fail(message: str) -> None:
    print(f"[phase44-cache-commit-window-real] FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def parse_int(value: str | None, label: str) -> int:
    try:
        return int(value or "")
    except ValueError:
        fail(f"{label} 不是整数: {value!r}")


try:
    suite_root = ET.parse(REPORT).getroot()
    summary_root = ET.parse(SUMMARY).getroot()
except (OSError, ET.ParseError) as exc:
    fail(f"报告 XML 无法解析: {exc}")

if local_name(suite_root.tag) != "testsuite":
    fail(f"目标报告根元素不是 testsuite: {suite_root.tag!r}")
if suite_root.get("name") != SUITE_CLASS:
    fail(f"目标 suite 错误: {suite_root.get('name')!r}")

suite_counts = {
    key: parse_int(suite_root.get(key), f"testsuite@{key}")
    for key in ("tests", "failures", "errors", "skipped")
}
expected_suite_counts = {
    "tests": EXPECTED,
    "failures": 0,
    "errors": 0,
    "skipped": 0,
}
if suite_counts != expected_suite_counts:
    fail(f"目标 suite 计数不是 16/16 全绿: {suite_counts}")

testcases = [
    element for element in suite_root
    if local_name(element.tag) == "testcase"
]
if len(testcases) != EXPECTED:
    fail(f"testcase 节点数不是 {EXPECTED}: {len(testcases)}")

identities: set[tuple[str, str]] = set()
test_names: set[str] = set()
for testcase in testcases:
    classname = testcase.get("classname") or ""
    name = testcase.get("name") or ""
    if classname != SUITE_CLASS or not name:
        fail(f"发现错误 suite 或空测试名: classname={classname!r}, name={name!r}")
    identity = (classname, name)
    if identity in identities:
        fail(f"发现重复 testcase: {identity!r}")
    identities.add(identity)
    test_names.add(name)
    bad_children = {
        local_name(child.tag)
        for child in testcase
        if local_name(child.tag) in {"failure", "error", "skipped"}
    }
    if bad_children:
        fail(f"{name} 含失败/错误/跳过节点: {sorted(bad_children)}")

required_fourth_round_cases = {
    "ownerLossCannotPublishUncommittedValueBeforeCommitRollback",
    "crashPendingRemainsFailClosedUntilRecoveryTtlExpires",
}
missing_fourth_round_cases = required_fourth_round_cases - test_names
if missing_fourth_round_cases:
    fail(
        "报告不是第四轮 owner-loss/P-TTL 源码的 fresh run，缺少 testcase: "
        f"{sorted(missing_fourth_round_cases)}"
    )

properties: dict[str, str] = {}
for element in suite_root.iter():
    if local_name(element.tag) == "property" and element.get("name"):
        properties[element.get("name", "")] = element.get("value", "")
if properties.get("it.test") != SUITE_SIMPLE:
    fail(f"XML 未证明精确 it.test selector: {properties.get('it.test')!r}")
if properties.get("failsafe.failIfNoSpecifiedTests", "").lower() != "false":
    fail("XML 未证明 reactor 零匹配保护参数已显式设为 false")

if local_name(summary_root.tag) != "failsafe-summary":
    fail(f"summary 根元素错误: {summary_root.tag!r}")
summary_values = {
    local_name(child.tag): (child.text or "").strip()
    for child in summary_root
}
summary_counts = {
    key: parse_int(summary_values.get(key), f"failsafe-summary/{key}")
    for key in ("completed", "failures", "errors", "skipped")
}
expected_summary_counts = {
    "completed": EXPECTED,
    "failures": 0,
    "errors": 0,
    "skipped": 0,
}
if summary_counts != expected_summary_counts:
    fail(f"Failsafe summary 不是 completed=16 全绿: {summary_counts}")
if "flakes" in summary_values and parse_int(summary_values["flakes"], "failsafe-summary/flakes") != 0:
    fail(f"Failsafe summary 含 flakes: {summary_values['flakes']}")

digest = hashlib.sha256(REPORT.read_bytes()).hexdigest()
print(
    "[phase44-cache-commit-window-real] XML verified: "
    f"suite={SUITE_CLASS}, tests=16, failures=0, errors=0, skipped=0, "
    f"completed=16, sha256={digest}"
)
PY

echo "[phase44-cache-commit-window-real] PASS"
