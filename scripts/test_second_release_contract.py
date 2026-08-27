from __future__ import annotations

import hashlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def main() -> None:
    runbook = read("docs/第二次部署发布手册.md")
    order = (
        "SECOND_RELEASE_ORDER: FREEZE_WRITES > CLOSE_PUBLIC_ENTRY > "
        "STOP_ALL_OLD > BACKUP_V32 > MIGRATE_V33_V35 > VERIFY > OPEN_TRAFFIC"
    )
    assert runbook.count(order) == 1
    assert runbook.index("先停止 frontend") < runbook.index("停止全部旧 backend/worker")
    assert runbook.index("只启动一台仍不接公开流量的新 backend") < runbook.index(
        "最后启动新 frontend"
    )
    assert "deploy/compose.existing-volumes.yml" in runbook
    assert "deploy/rollback/dfdfb91/compose.yml" in runbook
    assert "APP_IMAGE_TAG=dfdfb91 docker compose" in runbook
    assert "scripts/init-mysql-preflight-login-path.sh" in runbook
    assert runbook.count("bash scripts/verify-public-entry-closed.sh") >= 2
    assert "同源 `/api/health`" in runbook
    assert "X-Teacher-Cert-Maintenance: second-release" in runbook
    assert "000|503" not in runbook
    assert "|| true" not in runbook

    cpu_overlay = read("deploy/compose.cpu-v1.yml")
    assert "image: ${REDIS_CPU_V1_IMAGE:?" in cpu_overlay
    assert "redis:7.4-alpine@" not in cpu_overlay

    volume_overlay = read("deploy/compose.existing-volumes.yml")
    assert volume_overlay.count("external: true") == 4
    for name in (
        "TCP_RELEASE_EXPECTED_MYSQL_VOLUME",
        "TCP_RELEASE_EXPECTED_REDIS_VOLUME",
        "TCP_RELEASE_EXPECTED_MINIO_VOLUME",
        "TCP_RELEASE_EXPECTED_PROBE_VOLUME",
    ):
        assert name in volume_overlay

    rollback_compose = read("deploy/rollback/dfdfb91/compose.yml")
    assert "GET /api/health HTTP/1.0" in rollback_compose
    assert '${FRONTEND_PORT:?FRONTEND_PORT is required}:80' in rollback_compose
    assert "/api/health/readiness" not in rollback_compose
    assert "8080/healthz" not in rollback_compose

    phase44 = read("scripts/preflight-phase44-dict-identity.sh")
    target_preflight = read("scripts/preflight-second-release.sh")
    assert "PHASE44_EXPECTED_SERVER_UUID" in phase44
    assert "MySQL server UUID 不匹配" in phase44
    assert 'container:${container_id}' in read(
        "scripts/mysql-client-via-container-network.sh"
    )
    assert "TCP_RELEASE_EXPECTED_NEW_REDIS_IMAGE_ID" in target_preflight
    assert "mysql_identity_after" in target_preflight

    login_initializer = read("scripts/init-mysql-preflight-login-path.sh")
    assert '--user "${host_uid}:${host_gid}"' in login_initializer
    assert "HOME=/preflight-home" in login_initializer
    assert "stat -c '%u:%g'" in login_initializer
    assert "stat -c '%a'" in login_initializer

    public_entry_gate = read("scripts/verify-public-entry-closed.sh")
    assert "curl_status" in public_entry_gate
    assert '"${curl_status}" == "7"' in public_entry_gate
    assert '"${http_status}" == "503"' in public_entry_gate
    assert "X-Teacher-Cert-Maintenance:" in public_entry_gate
    assert 'api_probe_url="${root_probe_url%/}/api/health"' in public_entry_gate
    assert "verify_probe root 'root /'" in public_entry_gate
    assert "verify_probe api 'api /api/health'" in public_entry_gate

    public_entry_contract = read("scripts/test-verify-public-entry-closed.sh")
    assert "root-maintenance-api-business" in public_entry_contract
    assert "api /api/health 未处于固定维护态: HTTP 200" in public_entry_contract

    rollback_root = ROOT / "deploy/rollback/dfdfb91"
    expected_files = {
        "compose.cpu-v1.yml",
        "compose.yml",
        "mysql-init/01-app-user.sh",
    }
    checksum_lines = read("deploy/rollback/dfdfb91/SHA256SUMS").splitlines()
    listed: set[str] = set()
    for line in checksum_lines:
        digest, relative = line.split("  ", 1)
        payload = rollback_root / relative
        assert payload.is_file()
        assert hashlib.sha256(payload.read_bytes()).hexdigest() == digest
        listed.add(relative)
    assert listed == expected_files

    print(
        "[second-release-contract] PASS: order, Redis local tag, existing volumes, "
        "bound MySQL UUID, operator-owned login-path, root/API maintenance marker, "
        "dfdfb91 assets/checksums"
    )


if __name__ == "__main__":
    main()
