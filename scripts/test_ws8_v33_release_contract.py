#!/usr/bin/env python3
"""WS-8/V33 停机切换文档与 CI 接线的纯静态合同。"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PHASE14 = (ROOT / "docs" / "phase-14-非功能部署验收.md").read_text(encoding="utf-8")
README = (ROOT / "README.md").read_text(encoding="utf-8")
BACKUP = (ROOT / "docs" / "备份与恢复手册.md").read_text(encoding="utf-8")
COMPOSE = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")


def require(text: str, fragments: tuple[str, ...], label: str) -> None:
    missing = [fragment for fragment in fragments if fragment not in text]
    if missing:
        raise AssertionError(f"{label} 缺少合同片段: {missing}")


start = PHASE14.index("### 3.1 WS-8 / V33")
end = PHASE14.index("<!-- WS8_V33_RELEASE_CONTRACT_END -->", start)
contract = PHASE14[start:end]
order = (
    "FREEZE_WRITES",
    "STOP_ALL_OLD",
    "BACKUP_V32",
    "MIGRATE_V33",
    "START_ALL_NEW",
    "VERIFY",
    "OPEN_TRAFFIC",
)
positions = [contract.index(f"**{token}**") for token in order]
if positions != sorted(positions):
    raise AssertionError("WS-8/V33 发布步骤顺序发生漂移")

require(
    contract,
    (
        "无旧应用写连接",
        "V33 前恢复点",
        "AFTER_MIGRATE",
        "V33-capable 进程开始执行迁移",
        "严禁 V32/旧 binary 再连接该数据库",
        "完整恢复 V33 前数据库",
        "Flyway 最高版本为 32",
    ),
    "Phase 14 WS-8/V33",
)
require(README, ("V32→V33", "WS-8 / V33", "禁止把通用 up 当作升级步骤"), "README")
require(
    BACKUP,
    ("V33 前恢复点", "校验和", "IDCARD_ENCRYPTION_KEY", "IDCARD_HMAC_PEPPER", "最高版本为 32", "旧 binary"),
    "备份与恢复手册",
)
require(COMPOSE, ("WS8_V33_OFFLINE_UPGRADE", "IDCARD_ENCRYPTION_KEY", "IDCARD_HMAC_PEPPER"), "Compose")
require(WORKFLOW, ("python3 -B scripts/test_ws8_v33_release_contract.py",), "CI")

print("WS-8/V33 release contract PASS")
