#!/usr/bin/env python3
"""WS-7 release-supply-chain contract; standard library only."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SHA256_REF = re.compile(r"@sha256:[0-9a-f]{64}$")
ACTION_SHA = re.compile(r"^\s*(?:-\s*)?uses:\s+[^@\s]+@([0-9a-f]{40})(?:\s+#.*)?$")
FINAL_WS7_FINGERPRINT = "d5ea9863739de05449d8b2d2110a1075b121aa8745bd824c53f6e45359ce9e24"
FINAL_WS7_MANIFEST = "teacher-cert-ws7-hosted-ci-gate-remediation-candidate-2026-08-11.json"
FINAL_WS7_RUN_ID = "31507732334"
FINAL_WS7_VERDICT = "INDEPENDENT_STAGE_PASS"
ARCHIVED_WS7_REVIEWED_FINGERPRINT = "b8055c66ff1472955a0ad2556175ed0cbc747d0c48434820d290fa7fbbda223b"
ARCHIVED_WS7_STAGE_MANIFEST = "teacher-cert-ws7-stage-candidate-2026-08-11.json"
ARCHIVED_WS7_REMEDIATION_MANIFEST = (
    "teacher-cert-ws7-continuation-remediation-r2-candidate-2026-08-11.json"
)


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def section_between(content: str, start_marker: str, end_marker: str, context: str) -> str:
    start = content.find(start_marker)
    require(start >= 0, f"{context} start marker is missing")
    end = content.find(end_marker, start + len(start_marker))
    require(end >= 0, f"{context} end marker is missing")
    return content[start:end]


def mapping_block(content: str, key: str, indent: int) -> str:
    """Return one YAML mapping block without accepting a matching comment elsewhere."""
    lines = content.splitlines()
    marker = f"{' ' * indent}{key}:"
    start = next((index for index, line in enumerate(lines) if line == marker), None)
    require(start is not None, f"YAML mapping {key!r} is missing")
    end = len(lines)
    for index in range(start + 1, len(lines)):
        line = lines[index]
        if line.strip() and len(line) - len(line.lstrip()) <= indent:
            end = index
            break
    return "\n".join(lines[start:end])


def step_block(job: str, name: str) -> str:
    lines = job.splitlines()
    marker = f"      - name: {name}"
    start = next((index for index, line in enumerate(lines) if line == marker), None)
    require(start is not None, f"CI step {name!r} is missing")
    end = len(lines)
    for index in range(start + 1, len(lines)):
        if lines[index].startswith("      - "):
            end = index
            break
    return "\n".join(lines[start:end])


def require_yaml_image(content: str, image: str, context: str) -> None:
    pattern = rf"^\s*image:\s*{re.escape(image)}@sha256:[0-9a-f]{{64}}\s*$"
    require(re.search(pattern, content, re.MULTILINE) is not None,
            f"{context} must pin {image} to a full SHA-256 digest")


def require_ws7_history(section: str, context: str, required_tokens: tuple[str, ...]) -> None:
    """Validate the bounded WS-7 archive without freezing the active project stage."""
    for token in required_tokens:
        require(token in section, f"{context} must retain WS-7 evidence token {token!r}")
    require("artifact" in section, f"{context} must retain the WS-7 Hosted artifact boundary")
    require("WS-8" in section, f"{context} must retain the WS-7 to WS-8 handoff boundary")
    require(re.search(r"(?<!不)构成项目 GO", section) is None
            and re.search(r"(?<!不)等于项目 GO", section) is None,
            f"{context} must retain the non-GO release boundary")


def require_ws7_history_rejects_tampering(
    section: str,
    context: str,
    required_tokens: tuple[str, ...],
) -> None:
    """Keep an executable counterexample for identity, artifact and verdict drift."""
    for token in (*required_tokens, "artifact", "WS-8"):
        tampered = section.replace(token, "TAMPERED", 1)
        rejected = False
        try:
            require_ws7_history(tampered, context, required_tokens)
        except AssertionError:
            rejected = True
        require(rejected, f"{context} tampering counterexample must fail for {token!r}")


def dockerfile_contract(relative_path: str, expected_user: str) -> None:
    content = read(relative_path)
    from_lines = [line.strip() for line in content.splitlines() if line.strip().startswith("FROM ")]
    require(from_lines, f"{relative_path} must contain FROM instructions")
    for line in from_lines:
        image = line.split()[1]
        require(SHA256_REF.search(image) is not None, f"{relative_path} has tag-only FROM: {image}")
        require(":latest" not in image, f"{relative_path} must not use latest")

    runtime = content[content.rfind("\nFROM ") :]
    require(re.search(rf"^USER\s+{re.escape(expected_user)}$", runtime, re.MULTILINE) is not None,
            f"{relative_path} runtime USER must be {expected_user}")
    require(re.search(r"^USER\s+(?:0|root)(?::(?:0|root))?$", runtime, re.MULTILINE) is None,
            f"{relative_path} runtime must not be root")


def action_contract(relative_path: str) -> None:
    for number, line in enumerate(read(relative_path).splitlines(), start=1):
        if re.match(r"^\s*(?:-\s*)?uses:", line):
            require(ACTION_SHA.match(line) is not None,
                    f"{relative_path}:{number} action must use a 40-character commit SHA")


def main() -> None:
    dockerfile_contract("Dockerfile", "10001:10001")
    dockerfile_contract("frontend/Dockerfile", "101:101")

    backend_dockerfile = read("Dockerfile")
    frontend_dockerfile = read("frontend/Dockerfile")
    frontend_dockerignore = read("frontend/.dockerignore")
    nginx = read("frontend/nginx.conf")
    compose = read("docker-compose.yml")
    env_example = read(".env.example")
    ci = read(".github/workflows/ci.yml")
    identity_failure_test = read("scripts/test_ws7_image_identity_failure.sh")
    phase41_mysql = read("scripts/test-phase41-mysql-init-real.sh")
    handoff = read("HANDOFF.md")
    progress = read("PROGRESS.md")
    current_plan = read("docs/CURRENT-EXECUTION-PLAN.md")
    readme = read("README.md")
    audit_plan = read("docs/audit-remediation-plan.md")
    launch_plan = read("docs/launch-readiness-plan.md")
    phase14 = read("docs/phase-14-非功能部署验收.md")
    stage_submission = read("docs/reviews/ws-07-stage-submission-2026-08-11.md")
    remediation_submission = read("docs/reviews/ws-07-remediation-submission-2026-08-11.md")

    require("/var/lib/teacher-cert/video-probe" in backend_dockerfile,
            "backend image must prepare the non-root probe directory")
    require("chown -R 10001:10001 /app /var/lib/teacher-cert" in backend_dockerfile,
            "backend image must make app and probe paths writable by runtime user")
    require("EXPOSE 8080 8443" in frontend_dockerfile,
            "frontend image must expose unprivileged HTTP/TLS ports")
    require("chown -R nginx:nginx /etc/nginx/conf.d /var/cache/nginx /run" in frontend_dockerfile,
            "frontend image must make the Nginx PID and cache paths writable")
    ignored_frontend_paths = {line.strip() for line in frontend_dockerignore.splitlines()
                              if line.strip() and not line.lstrip().startswith("#")}
    require({"node_modules", "dist", ".env"}.issubset(ignored_frontend_paths),
            "frontend Docker context must exclude host dependencies, build output and local secrets")
    require("http://127.0.0.1:8080/healthz" in frontend_dockerfile,
            "frontend image healthcheck must use the unprivileged HTTP port")
    require(re.search(r"^\s*listen 8080;$", nginx, re.MULTILINE) is not None,
            "nginx HTTP listener must use 8080")
    require(re.search(r"^\s*listen 8443 ssl;$", nginx, re.MULTILINE) is not None,
            "nginx TLS listener must use 8443")
    require(re.search(r"^\s*listen (?:80|443)(?:\s|;)", nginx, re.MULTILINE) is None,
            "non-root nginx must not bind privileged ports")

    immutable_compose_images = (
        "mysql:8.4",
        "redis:7.4-alpine",
        "minio/minio:RELEASE.2025-04-22T22-12-26Z",
    )
    for image in immutable_compose_images:
        require_yaml_image(compose, image, "production compose")
    require("${APP_IMAGE_TAG:?APP_IMAGE_TAG must be an immutable source revision tag}" in compose,
            "application image tag must be explicitly supplied")
    require("${APP_IMAGE_TAG:-latest}" not in compose, "production compose must not default to latest")
    require('"${FRONTEND_PORT:-80}:8080"' in compose, "compose HTTP mapping must target 8080")
    require('"${FRONTEND_HTTPS_PORT:-443}:8443"' in compose, "compose TLS mapping must target 8443")
    require("http://127.0.0.1:8080/healthz" in compose,
            "compose frontend healthcheck must target 8080")
    require(re.search(r'^\s*- "\$\{TLS_CERTIFICATE_GID:\?[^}]+\}"$', compose, re.MULTILINE) is not None,
            "frontend must join the dedicated host certificate group")
    require(re.search(r"^APP_IMAGE_TAG=sha-(?!.*latest).+$", env_example, re.MULTILINE) is not None,
            ".env.example must require a source-revision image tag")
    require("TLS_CERTIFICATE_GID=REPLACE_WITH_NUMERIC_DEDICATED_HOST_CERT_GROUP_GID" in env_example,
            ".env.example must require the operator to supply a dedicated host certificate group GID")
    require(re.search(r"^TLS_CERTIFICATE_GID=101$", env_example, re.MULTILINE) is None,
            ".env.example must not reuse the container nginx primary GID as a host certificate group example")
    require("证书读取组的名称、数字 GID 和成员" in readme
            and "它与容器 nginx" in readme,
            "deployment guide must distinguish the host certificate group from container GID 101")

    ws7_history_sections = (
        ("HANDOFF WS-7 archive", section_between(
            handoff, "- WS-7 最终候选 manifest", "\n- WS-8",
            "HANDOFF WS-7 archive"),
         (FINAL_WS7_MANIFEST, FINAL_WS7_FINGERPRINT, FINAL_WS7_RUN_ID, FINAL_WS7_VERDICT,
          "双 SPDX", "镜像身份", "SHA256SUMS", "只放行 WS-8", "项目 GO")),
        ("PROGRESS WS-7 archive", section_between(
            progress, "- `WS-7` 最终候选 fingerprint", "\n- `WS-6` 第二轮候选",
            "PROGRESS WS-7 archive"),
         (FINAL_WS7_FINGERPRINT, FINAL_WS7_RUN_ID, FINAL_WS7_VERDICT,
          "双 SPDX", "镜像身份", "校验和", "仅放行 WS-8", "NO-GO")),
        ("CURRENT plan WS-7 archive", section_between(
            current_plan, "| WS-7/STAGE", "\n| WS-8/STAGE", "CURRENT plan WS-7 archive"),
         ("d5ea9863...9e24", FINAL_WS7_RUN_ID, FINAL_WS7_VERDICT,
          "双 SPDX", "镜像身份", "校验和", "只放行 WS-8", "项目 GO")),
        ("README WS-7 archive", section_between(
            readme, "Phase 1~53", "\nWS-8", "README WS-7 archive"),
         ("d5ea9863...9e24", FINAL_WS7_RUN_ID, "阶段 PASS",
          "双 SPDX", "镜像身份", "校验和", "只放行 WS-8", "项目发布 GO")),
        ("audit plan WS-7 archive", section_between(
            audit_plan, "### WS-7", "\n### WS-8", "audit plan WS-7 archive"),
         ("d5ea9863...9e24", FINAL_WS7_RUN_ID, FINAL_WS7_VERDICT,
          "双 SPDX", "镜像身份", "校验和", "只放行", "项目 GO")),
        ("launch plan WS-7 archive", section_between(
            launch_plan, "### WS-7", "\n### WS-8", "launch plan WS-7 archive"),
         ("d5ea9863...9e24", FINAL_WS7_RUN_ID, FINAL_WS7_VERDICT,
          "双 SPDX", "镜像身份", "校验和", "只放行 WS-8", "项目 GO")),
        ("Phase 14 WS-7 archive", section_between(
            phase14, "- [x] WS-7", "\n- [", "Phase 14 WS-7 archive"),
         ("d5ea9863...9e24", FINAL_WS7_RUN_ID, FINAL_WS7_VERDICT,
          "双 SPDX", "镜像身份", "校验和", "只放行 WS-8", "项目级发布 GO")),
    )
    for context, section, required_tokens in ws7_history_sections:
        require_ws7_history(section, context, required_tokens)

    handoff_history = ws7_history_sections[0]
    require_ws7_history_rejects_tampering(
        handoff_history[1], handoff_history[0], handoff_history[2]
    )
    for token in (
        "feature/ws07-supply-chain",
        ARCHIVED_WS7_REVIEWED_FINGERPRINT,
        ARCHIVED_WS7_REMEDIATION_MANIFEST,
        "CHANGES_REQUESTED",
        "0 Critical / 0 High / 2 Medium / 1 Low",
        "PARTIAL_REMEDIATION_VERIFIED",
        "Hosted",
        "artifact",
        "WS-8 不启动",
    ):
        require(token in remediation_submission,
                f"historical WS-7 remediation submission must retain {token!r}")
    require("INDEPENDENT_INCREMENTAL_PASS" not in remediation_submission,
            "historical WS-7 remediation submission must not gain a conflicting PASS verdict")
    for token in (
        "feature/ws07-supply-chain",
        ARCHIVED_WS7_REVIEWED_FINGERPRINT,
        ARCHIVED_WS7_STAGE_MANIFEST,
        "CHANGES_REQUESTED",
    ):
        require(token in stage_submission,
                f"historical WS-7 stage submission must retain {token!r}")
    require("hosted CI 或等价隔离 runner" not in stage_submission,
            "WS-7 submission must not weaken the Phase 14 Hosted CI evidence gate")

    workflows = sorted({
        *(ROOT / ".github" / "workflows").glob("*.yml"),
        *(ROOT / ".github" / "workflows").glob("*.yaml"),
    })
    for workflow in workflows:
        action_contract(str(workflow.relative_to(ROOT)).replace("\\", "/"))

    require_yaml_image(ci, "mysql:8.4", "CI service")
    require_yaml_image(ci, "redis:7", "CI service")
    minio_server = re.findall(
        r"^\s*minio/minio:RELEASE\.2025-04-22T22-12-26Z@sha256:[0-9a-f]{64}\s+server /data\s*$",
        ci,
        re.MULTILINE,
    )
    minio_client = re.findall(
        r"^\s*minio/mc:RELEASE\.2025-04-08T15-39-49Z@sha256:[0-9a-f]{64}\s+\\\s*$",
        ci,
        re.MULTILINE,
    )
    require(len(minio_server) == 1, "CI must pin the MinIO server image on its docker run line")
    require(len(minio_client) == 2, "CI must pin both MinIO client docker run lines")
    require(re.search(
        r"^\s*mysql:8\.4@sha256:[0-9a-f]{64}\s+>/dev/null\s*$",
        phase41_mysql,
        re.MULTILINE,
    ) is not None, "manual Phase 41 CI script must pin its MySQL image digest")

    container_job = mapping_block(ci, "container-images", 2)
    require(re.search(r"^\s{4}needs:\s*\[phase41-runner-contract, backend, frontend\]\s*$",
                      container_job, re.MULTILINE) is not None,
            "final image job must wait for all existing quality jobs")
    require("BACKEND_IMAGE: teacher-cert-platform/backend:sha-${{ github.sha }}" in container_job,
            "backend final image tag must bind github.sha")
    require("FRONTEND_IMAGE: teacher-cert-platform/frontend:sha-${{ github.sha }}" in container_job,
            "frontend final image tag must bind github.sha")
    require(container_job.count("anchore/sbom-action@e22c389904149dbc22b58101806040fa8d37a610") == 2,
            "CI must generate one pinned SBOM for each final image")
    backend_sbom = step_block(container_job, "生成后端镜像 SBOM")
    frontend_sbom = step_block(container_job, "生成前端镜像 SBOM")
    for block, image_env, output in (
        (backend_sbom, "BACKEND_IMAGE", "backend.spdx.json"),
        (frontend_sbom, "FRONTEND_IMAGE", "frontend.spdx.json"),
    ):
        require(f"image: ${{{{ env.{image_env} }}}}" in block,
                f"{output} must be generated from {image_env}")
        require(f"output-file: target/ws7-supply-chain/{output}" in block,
                f"{output} output path must be fixed")
        require("syft-version: v1.51.0" in block, "SBOM generator version must be fixed")
    require(container_job.count("docker run --rm --entrypoint id") == 2
            and container_job.count("-ne 0") >= 2,
            "CI must execute both images and reject UID 0")
    identity_step = step_block(container_job, "验证默认运行身份并记录不可变镜像 ID")
    identity_contract = (
        "backend_image_id=\"$(docker image inspect --format '{{.Id}}' \"$BACKEND_IMAGE\")\"",
        "frontend_image_id=\"$(docker image inspect --format '{{.Id}}' \"$FRONTEND_IMAGE\")\"",
        "[[ \"$backend_image_id\" =~ ^sha256:[0-9a-f]{64}$ ]]",
        "[[ \"$frontend_image_id\" =~ ^sha256:[0-9a-f]{64}$ ]]",
        "printf 'backend.image.id=%s\\n' \"$backend_image_id\"",
        "printf 'frontend.image.id=%s\\n' \"$frontend_image_id\"",
    )
    for expected_line in identity_contract:
        require(expected_line in identity_step,
                "CI image identity step must fail closed before writing a validated image ID")
    require(identity_step.count("$(docker image inspect") == 2,
            "CI image inspection must only occur in the two fail-propagating assignments")
    require("bash scripts/test_ws7_image_identity_failure.sh" in ci,
            "CI must run the image-inspect failure propagation regression")
    require("return 17" in identity_failure_test
            and '[[ ! -e "$probe_file" ]]' in identity_failure_test,
            "image identity regression must reject inspect failure before artifact creation")
    require("sha256sum --check SHA256SUMS" in container_job,
            "CI must self-verify artifact checksums")
    require("trivy " not in ci.lower() and "npm audit" not in ci.lower() and "dependency-check" not in ci.lower(),
            "WS-7 minimal CI must not add vulnerability scanning")

    print("WS-7 supply-chain contract: PASS")


if __name__ == "__main__":
    main()
