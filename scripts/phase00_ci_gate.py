#!/usr/bin/env python3
"""Phase 0 CI gate and provenance evidence generator.

The ``run`` command is the only command that invokes Maven.  It first requires
a clean tracked worktree, removes the declared reports, runs
``mvn clean verify``, validates the fresh XML reports against an exact
suite/testcase specification, and writes a self-verifiable evidence directory.

The ``verify`` command is purely offline.  It verifies the candidate marker,
artifact hashes, exact suite/testcase sets, timestamps, exit code, target
markers, and the SHA256SUMS file in an existing evidence directory.

Raw Maven output and source XML are hashed before sanitisation.  Only the full
redacted log and sanitised XML copies enter the evidence artifact: Surefire
``<properties>`` are removed because they can contain environment secrets.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Mapping, Sequence


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_SPEC = SCRIPT_DIR / "phase00_ci_gate_spec.json"
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
GIT_SHA_RE = re.compile(r"^[0-9a-f]{40}$")
SAFE_MARKER_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:/@+=-]{0,255}$")
SENSITIVE_NAME_RE = re.compile(
    r"(?:password|passwd|pwd|secret|token|credential|access[_-]?key|secret[_-]?key)",
    re.IGNORECASE,
)
TARGET_ENV = {
    "schema": "PHASE00_TARGET_SCHEMA",
    "mysql": "PHASE00_MYSQL_SERVICE_MARKER",
    "redis": "PHASE00_REDIS_SERVICE_MARKER",
    "minio": "PHASE00_MINIO_SERVICE_MARKER",
    "runContext": "PHASE00_RUN_CONTEXT",
}


class GateError(RuntimeError):
    """A deterministic gate failure."""


def utc_now() -> str:
    return (
        dt.datetime.now(dt.timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def parse_utc(value: str, label: str) -> dt.datetime:
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (AttributeError, ValueError) as exc:
        raise GateError(f"{label} is not a valid ISO-8601 timestamp: {value!r}") from exc
    if parsed.tzinfo is None:
        raise GateError(f"{label} must include a timezone: {value!r}")
    return parsed.astimezone(dt.timezone.utc)


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def strict_int(value: str | None, label: str) -> int:
    if value is None or not re.fullmatch(r"[0-9]+", value):
        raise GateError(f"{label} is not a non-negative integer: {value!r}")
    return int(value)


def safe_relative_path(value: str, label: str) -> PurePosixPath:
    candidate = PurePosixPath(value)
    if candidate.is_absolute() or not candidate.parts or ".." in candidate.parts:
        raise GateError(f"{label} must be a safe repository-relative path: {value!r}")
    return candidate


def load_spec(path: Path) -> dict[str, Any]:
    try:
        spec = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError(f"cannot read gate spec {path}: {exc}") from exc

    if not isinstance(spec, dict) or spec.get("schemaVersion") != 1:
        raise GateError("gate spec schemaVersion must be 1")
    if not isinstance(spec.get("gateId"), str) or not spec["gateId"].strip():
        raise GateError("gate spec gateId must be non-empty")
    suites = spec.get("suites")
    if not isinstance(suites, list) or not suites:
        raise GateError("gate spec suites must be a non-empty list")

    seen_ids: set[str] = set()
    seen_suites: set[str] = set()
    seen_reports: set[str] = set()
    for index, suite in enumerate(suites):
        label = f"suites[{index}]"
        if not isinstance(suite, dict):
            raise GateError(f"{label} must be an object")
        suite_id = suite.get("id")
        class_name = suite.get("suite")
        report = suite.get("report")
        runner = suite.get("runner")
        testcases = suite.get("testcases")
        if not isinstance(suite_id, str) or not suite_id:
            raise GateError(f"{label}.id must be non-empty")
        if not isinstance(class_name, str) or not class_name:
            raise GateError(f"{label}.suite must be non-empty")
        if runner not in {"surefire", "failsafe"}:
            raise GateError(f"{label}.runner must be surefire or failsafe")
        if not isinstance(report, str):
            raise GateError(f"{label}.report must be a string")
        safe_relative_path(report, f"{label}.report")
        if not isinstance(testcases, list) or not testcases:
            raise GateError(f"{label}.testcases must be non-empty")
        if any(not isinstance(name, str) or not name for name in testcases):
            raise GateError(f"{label}.testcases contains an empty/non-string name")
        if len(testcases) != len(set(testcases)):
            raise GateError(f"{label}.testcases contains duplicate names")
        if testcases != sorted(testcases):
            raise GateError(f"{label}.testcases must be sorted for deterministic review")
        if suite_id in seen_ids:
            raise GateError(f"duplicate suite id in spec: {suite_id}")
        if class_name in seen_suites:
            raise GateError(f"duplicate suite class in spec: {class_name}")
        if report in seen_reports:
            raise GateError(f"duplicate report path in spec: {report}")
        seen_ids.add(suite_id)
        seen_suites.add(class_name)
        seen_reports.add(report)

    supporting = spec.get("supportingArtifacts", [])
    if not isinstance(supporting, list):
        raise GateError("gate spec supportingArtifacts must be a list")
    for index, artifact in enumerate(supporting):
        label = f"supportingArtifacts[{index}]"
        if not isinstance(artifact, dict):
            raise GateError(f"{label} must be an object")
        if not isinstance(artifact.get("id"), str) or not artifact["id"]:
            raise GateError(f"{label}.id must be non-empty")
        if not isinstance(artifact.get("path"), str):
            raise GateError(f"{label}.path must be a string")
        safe_relative_path(artifact["path"], f"{label}.path")
        if not isinstance(artifact.get("kind"), str) or not artifact["kind"]:
            raise GateError(f"{label}.kind must be non-empty")
        if not isinstance(artifact.get("required", False), bool):
            raise GateError(f"{label}.required must be boolean")
    return spec


def validate_candidate(actual: str, expected: str) -> None:
    actual_normalized = actual.strip().lower()
    expected_normalized = expected.strip().lower()
    if not GIT_SHA_RE.fullmatch(actual_normalized):
        raise GateError(f"actual candidate SHA is not a full 40-character SHA: {actual!r}")
    if not GIT_SHA_RE.fullmatch(expected_normalized):
        raise GateError(
            f"expected candidate SHA is not a full 40-character SHA: {expected!r}"
        )
    if actual_normalized != expected_normalized:
        raise GateError(
            "candidate SHA mismatch: "
            f"actual={actual_normalized}, expected={expected_normalized}"
        )


def read_target_markers(environment: Mapping[str, str]) -> dict[str, str]:
    markers: dict[str, str] = {}
    for key, env_name in TARGET_ENV.items():
        value = environment.get(env_name, "").strip()
        if not value:
            raise GateError(f"required non-secret target marker is missing: {env_name}")
        if not SAFE_MARKER_RE.fullmatch(value):
            raise GateError(
                f"{env_name} contains unsupported characters; use a credential-free marker"
            )
        if SENSITIVE_NAME_RE.search(value):
            raise GateError(f"{env_name} must not contain credential-like text")
        if re.search(r"://[^/@\s]+:[^/@\s]+@", value):
            raise GateError(f"{env_name} must not contain URI userinfo credentials")
        markers[key] = value
    if not re.fullmatch(r"[A-Za-z0-9_]{1,64}", markers["schema"]):
        raise GateError("PHASE00_TARGET_SCHEMA must be a 1..64 character schema name")
    return markers


class SecretRedactor:
    """Redact credential assignments, URI userinfo and known secret env values."""

    _assignment = re.compile(
        r"(?i)(\b(?:password|passwd|pwd|secret|token|credential|"
        r"access[_-]?key|secret[_-]?key)\b\s*[=:]\s*)([^\s,;]+)"
    )
    _uri_userinfo = re.compile(r"(://[^/\s:@]+:)([^@/\s]+)(@)")
    _jwt = re.compile(
        r"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b"
    )

    def __init__(self, environment: Mapping[str, str]) -> None:
        values = []
        for name, value in environment.items():
            if SENSITIVE_NAME_RE.search(name) and len(value) >= 4:
                values.append(value)
        self._known_values = sorted(set(values), key=len, reverse=True)

    def redact(self, value: str) -> str:
        redacted = value
        for secret in self._known_values:
            redacted = redacted.replace(secret, "[REDACTED_SECRET]")
        redacted = self._uri_userinfo.sub(
            r"\1[REDACTED_SECRET]\3", redacted
        )
        redacted = self._assignment.sub(
            r"\1[REDACTED_SECRET]", redacted
        )
        return self._jwt.sub("[REDACTED_SECRET]", redacted)


def run_capture(
    command: Sequence[str],
    cwd: Path,
    redactor: SecretRedactor,
) -> str:
    try:
        completed = subprocess.run(
            list(command),
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
    except OSError as exc:
        return redactor.redact(f"unavailable: {exc}")
    output = completed.stdout.decode("utf-8", errors="replace").strip()
    return redactor.redact(output)


def git_head(repo_root: Path) -> str:
    try:
        completed = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            text=True,
        )
    except OSError as exc:
        raise GateError(f"cannot execute git: {exc}") from exc
    if completed.returncode != 0:
        raise GateError(f"cannot resolve candidate SHA: {completed.stderr.strip()}")
    return completed.stdout.strip().lower()


def require_clean_tracked_worktree(repo_root: Path) -> None:
    """Bind generated evidence to HEAD, not to uncommitted tracked source."""

    try:
        completed = subprocess.run(
            ["git", "status", "--porcelain=v1", "--untracked-files=no"],
            cwd=repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        raise GateError(f"cannot inspect tracked worktree state: {exc}") from exc
    if completed.returncode != 0:
        detail = completed.stderr.decode("utf-8", errors="replace").strip()
        raise GateError(f"cannot inspect tracked worktree state: {detail}")
    dirty_entries = [entry for entry in completed.stdout.splitlines() if entry]
    if dirty_entries:
        raise GateError(
            "tracked worktree is dirty; commit or restore tracked changes before "
            f"generating candidate evidence ({len(dirty_entries)} entries)"
        )


def is_build_relevant_untracked(relative_path: str) -> bool:
    """Return whether an untracked path can alter this repository's Maven run."""

    candidate = PurePosixPath(relative_path)
    parts = candidate.parts
    if not parts:
        return False
    if candidate.name == "pom.xml" or relative_path in {"mvnw", "mvnw.cmd"}:
        return True
    if parts[0] in {".mvn", "build"}:
        return True
    return (
        len(parts) >= 3
        and parts[0].startswith("platform-")
        and parts[1] == "src"
        and parts[2] in {"main", "test"}
    )


def require_no_build_relevant_untracked(repo_root: Path) -> int:
    """Reject untracked Maven inputs while allowing unrelated audit material."""

    try:
        completed = subprocess.run(
            ["git", "ls-files", "--others", "--exclude-standard", "-z"],
            cwd=repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        raise GateError(f"cannot inspect untracked worktree inputs: {exc}") from exc
    if completed.returncode != 0:
        detail = completed.stderr.decode("utf-8", errors="replace").strip()
        raise GateError(f"cannot inspect untracked worktree inputs: {detail}")
    paths = [
        value.decode("utf-8", errors="surrogateescape")
        for value in completed.stdout.split(b"\0")
        if value
    ]
    build_inputs = sorted(path for path in paths if is_build_relevant_untracked(path))
    if build_inputs:
        raise GateError(
            "untracked Maven input can alter candidate evidence; commit or remove "
            f"build-relevant files before running ({len(build_inputs)} entries)"
        )
    return len(paths)


def report_text_path(report_path: Path) -> Path | None:
    name = report_path.name
    if not name.startswith("TEST-") or not name.endswith(".xml"):
        return None
    return report_path.with_name(name[len("TEST-") : -len(".xml")] + ".txt")


def remove_declared_reports(
    repo_root: Path,
    spec: Mapping[str, Any],
) -> list[str]:
    candidates: list[Path] = []
    for suite in spec["suites"]:
        report = repo_root / PurePosixPath(suite["report"])
        candidates.append(report)
        text_report = report_text_path(report)
        if text_report is not None:
            candidates.append(text_report)
    for artifact in spec.get("supportingArtifacts", []):
        candidates.append(repo_root / PurePosixPath(artifact["path"]))

    removed: list[str] = []
    for path in candidates:
        if path.is_symlink():
            raise GateError(f"refusing to remove symlinked report: {path}")
        if path.exists():
            if not path.is_file():
                raise GateError(f"declared report path is not a file: {path}")
            path.unlink()
            try:
                removed.append(path.relative_to(repo_root).as_posix())
            except ValueError:
                removed.append(str(path))
    return sorted(removed)


def inspect_suite_report(
    report_path: Path,
    suite_spec: Mapping[str, Any],
    fresh_after_ns: int | None,
) -> dict[str, Any]:
    class_name = suite_spec["suite"]
    expected_names = list(suite_spec["testcases"])
    if not report_path.exists():
        raise GateError(f"required XML is missing: {suite_spec['report']}")
    if report_path.is_symlink() or not report_path.is_file():
        raise GateError(f"required XML must be a regular non-symlink file: {report_path}")
    stat = report_path.stat()
    if fresh_after_ns is not None and stat.st_mtime_ns < fresh_after_ns - 2_000_000_000:
        raise GateError(
            f"required XML predates this run and is stale: {suite_spec['report']}"
        )
    try:
        root = ET.fromstring(report_path.read_bytes())
    except (OSError, ET.ParseError) as exc:
        raise GateError(f"cannot parse required XML {suite_spec['report']}: {exc}") from exc
    if local_name(root.tag) != "testsuite":
        raise GateError(f"{suite_spec['report']} root is not testsuite")
    if root.get("name") != class_name:
        raise GateError(
            f"suite mismatch for {suite_spec['id']}: "
            f"observed={root.get('name')!r}, expected={class_name!r}"
        )

    expected_count = len(expected_names)
    counts = {
        key: strict_int(root.get(key), f"{suite_spec['id']}@{key}")
        for key in ("tests", "failures", "errors", "skipped")
    }
    expected_counts = {
        "tests": expected_count,
        "failures": 0,
        "errors": 0,
        "skipped": 0,
    }
    if counts != expected_counts:
        raise GateError(
            f"{suite_spec['id']} counts mismatch: "
            f"observed={counts}, expected={expected_counts}"
        )

    testcases = [
        element for element in root if local_name(element.tag) == "testcase"
    ]
    if len(testcases) != expected_count:
        raise GateError(
            f"{suite_spec['id']} testcase node count mismatch: "
            f"observed={len(testcases)}, expected={expected_count}"
        )
    observed_names: list[str] = []
    identities: set[tuple[str, str]] = set()
    for testcase in testcases:
        observed_class = testcase.get("classname") or ""
        observed_name = testcase.get("name") or ""
        if observed_class != class_name or not observed_name:
            raise GateError(
                f"{suite_spec['id']} has wrong classname/empty testcase: "
                f"classname={observed_class!r}, name={observed_name!r}"
            )
        identity = (observed_class, observed_name)
        if identity in identities:
            raise GateError(f"{suite_spec['id']} has duplicate testcase: {identity!r}")
        identities.add(identity)
        bad_children = sorted(
            {
                local_name(child.tag)
                for child in testcase
                if local_name(child.tag) in {"failure", "error", "skipped"}
            }
        )
        if bad_children:
            raise GateError(
                f"{suite_spec['id']} testcase {observed_name!r} "
                f"contains status nodes: {bad_children}"
            )
        observed_names.append(observed_name)

    observed_sorted = sorted(observed_names)
    if observed_sorted != expected_names:
        missing = sorted(set(expected_names) - set(observed_names))
        unexpected = sorted(set(observed_names) - set(expected_names))
        raise GateError(
            f"{suite_spec['id']} exact testcase set mismatch: "
            f"missing={missing}, unexpected={unexpected}"
        )
    return {
        "id": suite_spec["id"],
        "runner": suite_spec["runner"],
        "suite": class_name,
        "expectedTests": expected_count,
        "observedTests": counts["tests"],
        "failures": counts["failures"],
        "errors": counts["errors"],
        "skipped": counts["skipped"],
        "testcases": observed_sorted,
        "sourceReport": suite_spec["report"],
        "sourceMtimeNs": stat.st_mtime_ns,
    }


def validate_suite_reports(
    repo_root: Path,
    spec: Mapping[str, Any],
    fresh_after_ns: int | None,
) -> list[dict[str, Any]]:
    results = []
    for suite in spec["suites"]:
        report_path = repo_root / PurePosixPath(suite["report"])
        results.append(inspect_suite_report(report_path, suite, fresh_after_ns))
    return results


def inspect_failsafe_summary(
    path: Path,
    *,
    minimum_completed: int,
    fresh_after_ns: int | None,
) -> dict[str, int]:
    if not path.exists():
        raise GateError(f"required Failsafe summary is missing: {path}")
    if path.is_symlink() or not path.is_file():
        raise GateError(f"Failsafe summary must be a regular non-symlink file: {path}")
    if (
        fresh_after_ns is not None
        and path.stat().st_mtime_ns < fresh_after_ns - 2_000_000_000
    ):
        raise GateError(f"Failsafe summary predates this run and is stale: {path}")
    try:
        root = ET.fromstring(path.read_bytes())
    except (OSError, ET.ParseError) as exc:
        raise GateError(f"cannot parse Failsafe summary {path}: {exc}") from exc
    if local_name(root.tag) != "failsafe-summary":
        raise GateError(f"Failsafe summary root is wrong: {root.tag!r}")
    values = {
        local_name(child.tag): (child.text or "").strip() for child in root
    }
    counts = {
        key: strict_int(values.get(key), f"failsafe-summary/{key}")
        for key in ("completed", "failures", "errors", "skipped")
    }
    counts["flakes"] = strict_int(
        values.get("flakes", "0"), "failsafe-summary/flakes"
    )
    if counts["completed"] < minimum_completed:
        raise GateError(
            "Failsafe summary completed count is below the required Phase 0 "
            f"minimum: observed={counts['completed']}, minimum={minimum_completed}"
        )
    non_green = {
        key: counts[key] for key in ("failures", "errors", "skipped", "flakes")
    }
    if any(non_green.values()):
        raise GateError(f"Failsafe summary is not fully green: {non_green}")
    return counts


def stream_maven(
    command: Sequence[str],
    cwd: Path,
    redactor: SecretRedactor,
    safe_log_path: Path,
) -> tuple[int, str]:
    raw_digest = hashlib.sha256()
    try:
        process = subprocess.Popen(
            list(command),
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
    except OSError as exc:
        safe_log_path.write_text(
            redactor.redact(f"unable to start Maven: {exc}\n"), encoding="utf-8"
        )
        return 127, sha256_bytes(str(exc).encode("utf-8"))

    assert process.stdout is not None
    with safe_log_path.open("wb") as safe_log:
        for raw_line in iter(process.stdout.readline, b""):
            raw_digest.update(raw_line)
            safe_text = redactor.redact(
                raw_line.decode("utf-8", errors="replace")
            )
            safe_bytes = safe_text.encode("utf-8")
            safe_log.write(safe_bytes)
            sys.stdout.write(safe_text)
            sys.stdout.flush()
    process.stdout.close()
    return process.wait(), raw_digest.hexdigest()


def sanitize_xml(
    source: Path,
    destination: Path,
    redactor: SecretRedactor,
) -> tuple[str, str, list[str]]:
    raw = source.read_bytes()
    source_digest = sha256_bytes(raw)
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as exc:
        raise GateError(f"cannot sanitise invalid XML {source}: {exc}") from exc

    removed_properties = 0
    for parent in root.iter():
        for child in list(parent):
            if local_name(child.tag) == "properties":
                parent.remove(child)
                removed_properties += 1
    for element in root.iter():
        for name, value in list(element.attrib.items()):
            element.set(name, redactor.redact(value))
        if element.text:
            element.text = redactor.redact(element.text)
        if element.tail:
            element.tail = redactor.redact(element.tail)
    if hasattr(ET, "indent"):
        ET.indent(root, space="  ")
    destination.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(
        destination,
        encoding="utf-8",
        xml_declaration=True,
        short_empty_elements=True,
    )
    redactions = ["credential-patterns"]
    if removed_properties:
        redactions.append(f"testsuite-properties-removed:{removed_properties}")
    return source_digest, sha256_file(destination), redactions


def prepare_evidence_dir(path: Path) -> None:
    if path.exists():
        raise GateError(
            f"evidence directory already exists; stale artifacts are rejected: {path}"
        )
    path.mkdir(parents=True, exist_ok=False)


def artifact_record(
    evidence_dir: Path,
    path: Path,
    *,
    kind: str,
    source_path: str | None = None,
    source_sha256: str | None = None,
    redactions: Sequence[str] = (),
) -> dict[str, Any]:
    relative = path.relative_to(evidence_dir).as_posix()
    record: dict[str, Any] = {
        "path": relative,
        "kind": kind,
        "sha256": sha256_file(path),
        "bytes": path.stat().st_size,
    }
    if source_path is not None:
        record["sourcePath"] = source_path
    if source_sha256 is not None:
        if not SHA256_RE.fullmatch(source_sha256):
            raise GateError(f"invalid source SHA-256 for {relative}")
        record["sourceSha256"] = source_sha256
        record["sourceRetained"] = False
    if redactions:
        record["redactions"] = list(redactions)
    return record


def write_checksums(evidence_dir: Path, artifact_paths: Iterable[Path]) -> Path:
    checksum_path = evidence_dir / "SHA256SUMS"
    lines = []
    for path in sorted(artifact_paths, key=lambda item: item.as_posix()):
        relative = path.relative_to(evidence_dir).as_posix()
        lines.append(f"{sha256_file(path)}  {relative}\n")
    checksum_path.write_text("".join(lines), encoding="utf-8", newline="\n")
    return checksum_path


def persist_manifest(
    evidence_dir: Path,
    manifest: Mapping[str, Any],
) -> Path:
    manifest_path = evidence_dir / "manifest.json"
    manifest_path.write_text(
        json.dumps(manifest, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    artifact_files = []
    for index, record in enumerate(manifest.get("artifacts", [])):
        path_text = record.get("path") if isinstance(record, dict) else None
        if not isinstance(path_text, str):
            raise GateError(f"manifest artifacts[{index}] has no path")
        artifact_files.append(
            evidence_dir
            / safe_relative_path(path_text, f"manifest artifacts[{index}].path")
        )
    artifact_files.append(manifest_path)
    write_checksums(evidence_dir, artifact_files)
    return manifest_path


def self_verify_or_downgrade(
    *,
    evidence_dir: Path,
    spec_path: Path,
    expected_candidate: str,
    manifest: dict[str, Any],
) -> tuple[bool, str | None]:
    """Persist, verify, and never leave a false PASS manifest behind."""

    persist_manifest(evidence_dir, manifest)
    try:
        verify_evidence(
            evidence_dir=evidence_dir,
            spec_path=spec_path,
            expected_candidate=expected_candidate,
        )
    except GateError as exc:
        error = f"evidence self-verification failed: {exc}"
        manifest["status"] = "FAIL"
        errors = manifest.setdefault("errors", [])
        if not isinstance(errors, list):
            errors = []
            manifest["errors"] = errors
        errors.append(error)
        persist_manifest(evidence_dir, manifest)
        return False, error
    return True, None


def build_tool_versions(
    repo_root: Path,
    maven_executable: str,
    redactor: SecretRedactor,
) -> dict[str, str]:
    versions = {
        "git": run_capture(["git", "--version"], repo_root, redactor),
        "java": run_capture(["java", "-version"], repo_root, redactor),
        "maven": run_capture([maven_executable, "--version"], repo_root, redactor),
        "python": redactor.redact(sys.version.replace("\n", " ")),
    }
    unavailable = sorted(
        name
        for name, value in versions.items()
        if not value or value.lower().startswith("unavailable:")
    )
    if unavailable:
        raise GateError(f"required tool versions are unavailable: {unavailable}")
    return versions


def expected_candidate_from_args(value: str | None) -> str:
    expected = (value or os.environ.get("PHASE00_EXPECTED_CANDIDATE_SHA", "")).strip()
    if not expected:
        raise GateError(
            "expected candidate SHA is required via --expected-candidate-sha "
            "or PHASE00_EXPECTED_CANDIDATE_SHA"
        )
    return expected.lower()


def run_gate(args: argparse.Namespace) -> int:
    repo_root = args.repo_root.resolve()
    spec_path = args.spec.resolve()
    evidence_dir = args.evidence_dir.resolve()
    spec = load_spec(spec_path)
    expected_candidate = expected_candidate_from_args(args.expected_candidate_sha)
    actual_candidate = git_head(repo_root)
    validate_candidate(actual_candidate, expected_candidate)
    require_clean_tracked_worktree(repo_root)
    ignored_untracked_at_start = require_no_build_relevant_untracked(repo_root)
    targets = read_target_markers(os.environ)
    prepare_evidence_dir(evidence_dir)

    redactor = SecretRedactor(os.environ)
    tool_versions = build_tool_versions(repo_root, args.maven, redactor)
    removed_reports = remove_declared_reports(repo_root, spec)
    maven_command = [args.maven, "-B", "-ntp", "clean", "verify"]
    started_at = utc_now()
    started_ns = time.time_ns()
    safe_log_fd, safe_log_name = tempfile.mkstemp(
        prefix="phase00-maven-", suffix=".log"
    )
    os.close(safe_log_fd)
    safe_log_temp = Path(safe_log_name)
    errors: list[str] = []
    suite_results: list[dict[str, Any]] = []
    artifacts: list[dict[str, Any]] = []
    try:
        maven_exit_code, raw_log_sha256 = stream_maven(
            maven_command, repo_root, redactor, safe_log_temp
        )
        ended_ns = time.time_ns()
        ended_at = utc_now()
        if maven_exit_code != 0:
            errors.append(f"Maven clean verify exited with {maven_exit_code}")
        source_clean_at_end = True
        ignored_untracked_at_end = ignored_untracked_at_start
        try:
            require_clean_tracked_worktree(repo_root)
            ignored_untracked_at_end = require_no_build_relevant_untracked(repo_root)
        except GateError as exc:
            source_clean_at_end = False
            errors.append(f"post-run source cleanliness failed: {exc}")
        try:
            suite_results = validate_suite_reports(repo_root, spec, started_ns)
        except GateError as exc:
            errors.append(str(exc))

        logs_dir = evidence_dir / "logs"
        logs_dir.mkdir(parents=True, exist_ok=True)
        evidence_log = logs_dir / "maven-clean-verify.log"
        shutil.copyfile(safe_log_temp, evidence_log)
        artifacts.append(
            artifact_record(
                evidence_dir,
                evidence_log,
                kind="complete-redacted-maven-log",
                source_sha256=raw_log_sha256,
                redactions=(
                    "known-sensitive-environment-values",
                    "credential-assignments",
                    "uri-userinfo",
                    "jwt-shaped-values",
                ),
            )
        )

        xml_dir = evidence_dir / "xml"
        suite_artifact_by_id: dict[str, str] = {}
        for suite in spec["suites"]:
            source = repo_root / PurePosixPath(suite["report"])
            if not source.exists() or not source.is_file() or source.is_symlink():
                continue
            destination = xml_dir / f"{suite['id']}.xml"
            try:
                source_hash, artifact_hash, redactions = sanitize_xml(
                    source, destination, redactor
                )
                record = artifact_record(
                    evidence_dir,
                    destination,
                    kind="sanitised-surefire-failsafe-xml",
                    source_path=suite["report"],
                    source_sha256=source_hash,
                    redactions=redactions,
                )
                if record["sha256"] != artifact_hash:
                    raise GateError(f"post-write XML hash drift for {suite['id']}")
                artifacts.append(record)
                suite_artifact_by_id[suite["id"]] = record["path"]
            except GateError as exc:
                errors.append(str(exc))

        supporting_results: list[dict[str, Any]] = []
        minimum_failsafe_completed = sum(
            len(suite["testcases"])
            for suite in spec["suites"]
            if suite["runner"] == "failsafe"
        )
        for support in spec.get("supportingArtifacts", []):
            source = repo_root / PurePosixPath(support["path"])
            result = {
                "id": support["id"],
                "kind": support["kind"],
                "sourcePath": support["path"],
                "required": support.get("required", False),
                "present": source.exists() and source.is_file() and not source.is_symlink(),
            }
            if not result["present"]:
                if result["required"]:
                    errors.append(f"required supporting artifact is missing: {support['path']}")
                supporting_results.append(result)
                continue
            if support["kind"] == "maven-summary-xml":
                try:
                    result["counts"] = inspect_failsafe_summary(
                        source,
                        minimum_completed=minimum_failsafe_completed,
                        fresh_after_ns=started_ns,
                    )
                except GateError as exc:
                    errors.append(str(exc))
            destination = xml_dir / f"support-{support['id']}.xml"
            try:
                source_hash, artifact_hash, redactions = sanitize_xml(
                    source, destination, redactor
                )
                record = artifact_record(
                    evidence_dir,
                    destination,
                    kind=support["kind"],
                    source_path=support["path"],
                    source_sha256=source_hash,
                    redactions=redactions,
                )
                if record["sha256"] != artifact_hash:
                    raise GateError(f"post-write supporting XML hash drift for {support['id']}")
                artifacts.append(record)
                result["artifactPath"] = record["path"]
                result["sourceSha256"] = source_hash
            except GateError as exc:
                errors.append(str(exc))
            supporting_results.append(result)

        for result in suite_results:
            artifact_path = suite_artifact_by_id.get(result["id"])
            if artifact_path:
                result["evidenceArtifact"] = artifact_path

        status = "PASS" if maven_exit_code == 0 and not errors else "FAIL"
        manifest: dict[str, Any] = {
            "schemaVersion": 1,
            "gateId": spec["gateId"],
            "status": status,
            "candidateSha": actual_candidate,
            "expectedCandidateSha": expected_candidate,
            "source": {
                "trackedWorktreeCleanAtStart": True,
                "trackedWorktreeCleanAtEnd": source_clean_at_end,
                "buildRelevantUntrackedAbsentAtStart": True,
                "buildRelevantUntrackedAbsentAtEnd": source_clean_at_end,
                "ignoredNonBuildUntrackedAtStart": ignored_untracked_at_start,
                "ignoredNonBuildUntrackedAtEnd": ignored_untracked_at_end,
                "statusCommand": [
                    "git",
                    "status",
                    "--porcelain=v1",
                    "--untracked-files=no",
                ],
                "untrackedCommand": [
                    "git",
                    "ls-files",
                    "--others",
                    "--exclude-standard",
                    "-z",
                ],
            },
            "run": {
                "command": maven_command,
                "workingDirectory": ".",
                "startedAtUtc": started_at,
                "endedAtUtc": ended_at,
                "startedEpochNs": started_ns,
                "endedEpochNs": ended_ns,
                "exitCode": maven_exit_code,
                "oldDeclaredReportsRemoved": removed_reports,
                "cleanLifecycleRequired": True,
            },
            "tools": tool_versions,
            "targets": targets,
            "suites": suite_results,
            "supportingArtifacts": supporting_results,
            "artifacts": artifacts,
            "errors": errors,
            "security": {
                "containsSecrets": False,
                "rawSourcesRetained": False,
                "xmlPropertiesRemoved": True,
                "logRedacted": True,
            },
        }
        if status == "PASS":
            verified, verification_error = self_verify_or_downgrade(
                evidence_dir=evidence_dir,
                spec_path=spec_path,
                expected_candidate=expected_candidate,
                manifest=manifest,
            )
            if not verified:
                print(f"[phase00-ci-gate] FAIL: {verification_error}", file=sys.stderr)
                return 1
            print(
                "[phase00-ci-gate] PASS: "
                f"{len(suite_results)} exact suites, "
                f"{sum(item['observedTests'] for item in suite_results)} testcases, "
                f"candidate={actual_candidate}"
            )
            return 0
        persist_manifest(evidence_dir, manifest)
        print("[phase00-ci-gate] FAIL:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    finally:
        safe_log_temp.unlink(missing_ok=True)


def read_manifest(evidence_dir: Path) -> dict[str, Any]:
    manifest_path = evidence_dir / "manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError(f"cannot read evidence manifest: {exc}") from exc
    if not isinstance(manifest, dict) or manifest.get("schemaVersion") != 1:
        raise GateError("evidence manifest schemaVersion must be 1")
    return manifest


def verify_checksums_file(
    evidence_dir: Path,
    expected_paths: set[str],
) -> None:
    checksum_path = evidence_dir / "SHA256SUMS"
    try:
        lines = checksum_path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise GateError(f"cannot read SHA256SUMS: {exc}") from exc
    if not lines:
        raise GateError("SHA256SUMS is empty")
    seen: set[str] = set()
    for line in lines:
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if not match:
            raise GateError(f"invalid SHA256SUMS line: {line!r}")
        digest, relative_text = match.groups()
        relative = safe_relative_path(relative_text, "SHA256SUMS path")
        if relative_text in seen:
            raise GateError(f"duplicate SHA256SUMS path: {relative_text}")
        seen.add(relative_text)
        path = evidence_dir / relative
        if not path.is_file() or path.is_symlink():
            raise GateError(f"SHA256SUMS artifact missing/non-regular: {relative_text}")
        actual = sha256_file(path)
        if actual != digest:
            raise GateError(
                f"SHA256SUMS mismatch for {relative_text}: "
                f"actual={actual}, expected={digest}"
            )
    if seen != expected_paths:
        missing = sorted(expected_paths - seen)
        unexpected = sorted(seen - expected_paths)
        raise GateError(
            "SHA256SUMS coverage is not exact: "
            f"missing={missing}, unexpected={unexpected}"
        )
    actual_files: set[str] = set()
    for path in evidence_dir.rglob("*"):
        if path.is_symlink():
            raise GateError(f"evidence directory contains a symlink: {path}")
        if path.is_file():
            actual_files.add(path.relative_to(evidence_dir).as_posix())
    expected_files = expected_paths | {"SHA256SUMS"}
    if actual_files != expected_files:
        missing = sorted(expected_files - actual_files)
        unexpected = sorted(actual_files - expected_files)
        raise GateError(
            "evidence directory contains missing/unlisted files: "
            f"missing={missing}, unexpected={unexpected}"
        )


def verify_artifact_records(
    evidence_dir: Path,
    artifacts: Any,
) -> dict[str, dict[str, Any]]:
    if not isinstance(artifacts, list) or not artifacts:
        raise GateError("manifest artifacts must be a non-empty list")
    records: dict[str, dict[str, Any]] = {}
    for index, record in enumerate(artifacts):
        if not isinstance(record, dict):
            raise GateError(f"artifacts[{index}] must be an object")
        path_text = record.get("path")
        digest = record.get("sha256")
        source_digest = record.get("sourceSha256")
        if not isinstance(path_text, str):
            raise GateError(f"artifacts[{index}].path must be a string")
        relative = safe_relative_path(path_text, f"artifacts[{index}].path")
        if path_text in records:
            raise GateError(f"duplicate artifact record: {path_text}")
        if not isinstance(digest, str) or not SHA256_RE.fullmatch(digest):
            raise GateError(f"invalid artifact SHA-256 for {path_text}")
        if source_digest is not None and (
            not isinstance(source_digest, str) or not SHA256_RE.fullmatch(source_digest)
        ):
            raise GateError(f"invalid source SHA-256 for {path_text}")
        path = evidence_dir / relative
        if not path.is_file() or path.is_symlink():
            raise GateError(f"artifact missing/non-regular: {path_text}")
        actual = sha256_file(path)
        if actual != digest:
            raise GateError(
                f"artifact SHA-256 mismatch for {path_text}: "
                f"actual={actual}, expected={digest}"
            )
        if path.stat().st_size != record.get("bytes"):
            raise GateError(f"artifact byte count mismatch for {path_text}")
        records[path_text] = record
    return records


def verify_evidence(
    evidence_dir: Path,
    spec_path: Path,
    expected_candidate: str,
) -> dict[str, Any]:
    spec = load_spec(spec_path)
    manifest = read_manifest(evidence_dir)
    validate_candidate(str(manifest.get("candidateSha", "")), expected_candidate)
    if manifest.get("expectedCandidateSha") != expected_candidate:
        raise GateError("manifest expectedCandidateSha does not match verifier input")
    if manifest.get("gateId") != spec["gateId"]:
        raise GateError("manifest gateId does not match gate spec")
    if manifest.get("status") != "PASS":
        raise GateError(f"evidence gate status is not PASS: {manifest.get('status')!r}")
    source = manifest.get("source")
    if (
        not isinstance(source, dict)
        or source.get("trackedWorktreeCleanAtStart") is not True
        or source.get("trackedWorktreeCleanAtEnd") is not True
        or source.get("buildRelevantUntrackedAbsentAtStart") is not True
        or source.get("buildRelevantUntrackedAbsentAtEnd") is not True
        or not isinstance(source.get("ignoredNonBuildUntrackedAtStart"), int)
        or not isinstance(source.get("ignoredNonBuildUntrackedAtEnd"), int)
        or source.get("statusCommand")
        != ["git", "status", "--porcelain=v1", "--untracked-files=no"]
        or source.get("untrackedCommand")
        != ["git", "ls-files", "--others", "--exclude-standard", "-z"]
    ):
        raise GateError("evidence does not prove candidate source cleanliness")
    run = manifest.get("run")
    if not isinstance(run, dict) or run.get("exitCode") != 0:
        raise GateError("evidence Maven exitCode must be 0")
    command = run.get("command")
    if (
        not isinstance(command, list)
        or len(command) != 5
        or command[1:] != ["-B", "-ntp", "clean", "verify"]
        or Path(str(command[0])).name.lower()
        not in {"mvn", "mvn.cmd", "mvnw", "mvnw.cmd"}
    ):
        raise GateError(f"unexpected evidence command: {command!r}")
    if run.get("cleanLifecycleRequired") is not True:
        raise GateError("evidence must declare the clean lifecycle")
    started = parse_utc(run.get("startedAtUtc"), "run.startedAtUtc")
    ended = parse_utc(run.get("endedAtUtc"), "run.endedAtUtc")
    if ended < started:
        raise GateError("run endedAtUtc predates startedAtUtc")
    started_ns = run.get("startedEpochNs")
    ended_ns = run.get("endedEpochNs")
    if (
        not isinstance(started_ns, int)
        or not isinstance(ended_ns, int)
        or ended_ns < started_ns
    ):
        raise GateError("run epoch nanoseconds are missing or reversed")

    targets = manifest.get("targets")
    if not isinstance(targets, dict) or set(targets) != set(TARGET_ENV):
        raise GateError("manifest target marker set is incomplete")
    read_target_markers(
        {TARGET_ENV[key]: str(value) for key, value in targets.items()}
    )
    tools = manifest.get("tools")
    if (
        not isinstance(tools, dict)
        or set(tools) != {"git", "java", "maven", "python"}
        or any(not isinstance(value, str) or not value for value in tools.values())
        or any(value.lower().startswith("unavailable:") for value in tools.values())
    ):
        raise GateError("manifest tool versions are incomplete")
    security = manifest.get("security")
    if (
        not isinstance(security, dict)
        or security.get("containsSecrets") is not False
        or security.get("rawSourcesRetained") is not False
        or security.get("xmlPropertiesRemoved") is not True
        or security.get("logRedacted") is not True
    ):
        raise GateError("manifest does not assert a secret-free artifact")

    records = verify_artifact_records(evidence_dir, manifest.get("artifacts"))
    verify_checksums_file(
        evidence_dir,
        expected_paths=set(records) | {"manifest.json"},
    )
    log_records = [
        record
        for record in records.values()
        if record.get("kind") == "complete-redacted-maven-log"
    ]
    if len(log_records) != 1:
        raise GateError("evidence must contain exactly one complete Maven log")

    observed_suites = manifest.get("suites")
    if not isinstance(observed_suites, list):
        raise GateError("manifest suites must be a list")
    by_id = {
        item.get("id"): item for item in observed_suites if isinstance(item, dict)
    }
    if set(by_id) != {suite["id"] for suite in spec["suites"]}:
        raise GateError("manifest exact suite ID set does not match gate spec")
    for suite in spec["suites"]:
        result = by_id[suite["id"]]
        if result.get("suite") != suite["suite"]:
            raise GateError(f"manifest suite class mismatch for {suite['id']}")
        if result.get("testcases") != suite["testcases"]:
            raise GateError(f"manifest testcase set mismatch for {suite['id']}")
        if any(result.get(key) != 0 for key in ("failures", "errors", "skipped")):
            raise GateError(f"manifest non-green suite result for {suite['id']}")
        if result.get("observedTests") != len(suite["testcases"]):
            raise GateError(f"manifest test count mismatch for {suite['id']}")
        evidence_path = result.get("evidenceArtifact")
        if not isinstance(evidence_path, str) or evidence_path not in records:
            raise GateError(f"manifest suite artifact missing for {suite['id']}")
        inspect_suite_report(
            evidence_dir / safe_relative_path(evidence_path, "suite evidence path"),
            suite,
            fresh_after_ns=None,
        )

    support_results = manifest.get("supportingArtifacts")
    if not isinstance(support_results, list):
        raise GateError("manifest supportingArtifacts must be a list")
    support_by_id = {
        item.get("id"): item for item in support_results if isinstance(item, dict)
    }
    support_specs = {
        item["id"]: item for item in spec.get("supportingArtifacts", [])
    }
    if set(support_by_id) != set(support_specs):
        raise GateError("manifest supporting artifact ID set does not match gate spec")
    minimum_failsafe_completed = sum(
        len(suite["testcases"])
        for suite in spec["suites"]
        if suite["runner"] == "failsafe"
    )
    for support_id, support_spec in support_specs.items():
        result = support_by_id[support_id]
        if support_spec.get("required", False) and result.get("present") is not True:
            raise GateError(f"required supporting artifact is absent: {support_id}")
        if result.get("present") is not True:
            continue
        artifact_path = result.get("artifactPath")
        if not isinstance(artifact_path, str) or artifact_path not in records:
            raise GateError(f"supporting artifact evidence is missing: {support_id}")
        if support_spec["kind"] == "maven-summary-xml":
            parsed_counts = inspect_failsafe_summary(
                evidence_dir
                / safe_relative_path(artifact_path, "supporting evidence path"),
                minimum_completed=minimum_failsafe_completed,
                fresh_after_ns=None,
            )
            if result.get("counts") != parsed_counts:
                raise GateError(
                    f"supporting summary count drift in manifest: {support_id}"
                )
            if (
                result.get("sourceSha256")
                != records[artifact_path].get("sourceSha256")
            ):
                raise GateError(
                    f"supporting summary source hash drift in manifest: {support_id}"
                )

    if manifest.get("errors") != []:
        raise GateError("PASS manifest must contain an empty errors list")
    return manifest


def verify_gate(args: argparse.Namespace) -> int:
    expected = expected_candidate_from_args(args.expected_candidate_sha)
    verify_evidence(
        evidence_dir=args.evidence_dir.resolve(),
        spec_path=args.spec.resolve(),
        expected_candidate=expected,
    )
    print(
        "[phase00-ci-gate] evidence verified: "
        f"candidate={expected}, dir={args.evidence_dir.resolve()}"
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    run_parser = subparsers.add_parser(
        "run", help="run Maven clean verify, validate reports, and create evidence"
    )
    run_parser.add_argument(
        "--repo-root",
        type=Path,
        default=SCRIPT_DIR.parent,
        help="repository root (default: parent of scripts/)",
    )
    run_parser.add_argument("--spec", type=Path, default=DEFAULT_SPEC)
    run_parser.add_argument("--evidence-dir", type=Path, required=True)
    run_parser.add_argument("--expected-candidate-sha")
    run_parser.add_argument("--maven", default=os.environ.get("MVN", "mvn"))
    run_parser.set_defaults(handler=run_gate)

    verify_parser = subparsers.add_parser(
        "verify", help="offline verification of an existing evidence directory"
    )
    verify_parser.add_argument("--spec", type=Path, default=DEFAULT_SPEC)
    verify_parser.add_argument("--evidence-dir", type=Path, required=True)
    verify_parser.add_argument("--expected-candidate-sha")
    verify_parser.set_defaults(handler=verify_gate)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return int(args.handler(args))
    except GateError as exc:
        print(f"[phase00-ci-gate] FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
