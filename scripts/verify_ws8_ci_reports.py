#!/usr/bin/env python3
"""验证 WS-8 精确 CI selector 的测试报告，并生成可归档摘要。"""

from __future__ import annotations

import json
import os
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE_DIR = ROOT / "target" / "ws8-ci-evidence"
EXPECTED_REPORTS = (
    (
        ROOT / "platform-security" / "target" / "surefire-reports"
        / "TEST-cn.edu.gpnu.platform.security.service.IdCardProtectionServiceTest.xml",
        "cn.edu.gpnu.platform.security.service.IdCardProtectionServiceTest",
        "surefire",
    ),
    (
        ROOT / "platform-boot" / "target" / "surefire-reports"
        / "TEST-cn.edu.gpnu.platform.boot.config.RuntimeProfileGuardTest.xml",
        "cn.edu.gpnu.platform.boot.config.RuntimeProfileGuardTest",
        "surefire",
    ),
    *(
        (
            ROOT / "platform-boot" / "target" / "failsafe-reports" / f"TEST-{suite}.xml",
            suite,
            "failsafe",
        )
        for suite in (
            "cn.edu.gpnu.platform.boot.V33IdCardProtectionMigrationIT",
            "cn.edu.gpnu.platform.boot.Phase3StudentIT",
            "cn.edu.gpnu.platform.boot.Phase10ExchangeIT",
            "cn.edu.gpnu.platform.boot.Phase41BackupIT",
        )
    ),
)


def integer_attribute(root: ET.Element, name: str, report: Path) -> int:
    try:
        return int(root.attrib[name])
    except (KeyError, ValueError) as exception:
        raise ValueError(f"{report}: testsuite 缺少合法 {name} 计数") from exception


def main() -> int:
    reports_dir = EVIDENCE_DIR / "reports"
    reports_dir.mkdir(parents=True, exist_ok=True)
    suites = []
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}

    for report, expected_name, runner in EXPECTED_REPORTS:
        if not report.is_file():
            raise FileNotFoundError(f"WS-8 selector 未生成预期报告: {report.relative_to(ROOT)}")
        root = ET.parse(report).getroot()
        actual_name = root.attrib.get("name")
        if actual_name != expected_name:
            raise ValueError(
                f"{report.relative_to(ROOT)}: suite={actual_name!r}, 期望 {expected_name!r}"
            )
        counts = {
            name: integer_attribute(root, name, report)
            for name in ("tests", "failures", "errors", "skipped")
        }
        if counts["tests"] <= 0:
            raise ValueError(f"{expected_name}: selector 匹配测试数为 0")
        if any(counts[name] != 0 for name in ("failures", "errors", "skipped")):
            raise ValueError(f"{expected_name}: 非全绿或存在 skip: {counts}")
        for name, value in counts.items():
            totals[name] += value
        suites.append({"name": expected_name, "runner": runner, **counts})
        shutil.copy2(report, reports_dir / report.name)

    summary = {
        "schemaVersion": 1,
        "candidateSha": os.environ.get("GITHUB_SHA", "local-unbound"),
        "runId": os.environ.get("GITHUB_RUN_ID"),
        "runAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "suites": suites,
        "totals": totals,
    }
    (EVIDENCE_DIR / "test-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        "WS-8 CI selector PASS: "
        f"{len(suites)} suites / {totals['tests']} tests / 0 failure / 0 error / 0 skip"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (FileNotFoundError, ValueError, ET.ParseError) as exception:
        print(f"WS-8 CI selector FAIL: {exception}", file=sys.stderr)
        raise SystemExit(1) from exception
