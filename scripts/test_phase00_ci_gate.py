#!/usr/bin/env python3
"""Pure-offline contract tests for ``phase00_ci_gate.py``."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import time
import unittest
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch

import phase00_ci_gate as gate


class Phase00CiGateTest(unittest.TestCase):
    def setUp(self) -> None:
        # Avoid tempfile.TemporaryDirectory here: MSYS Python applies a POSIX
        # 0700 mode that can make the resulting Windows directory inaccessible
        # to the same sandboxed process. A normal workspace directory is
        # portable across GitHub Linux and managed Windows runners.
        self.repo = gate.SCRIPT_DIR.parent / (
            ".phase00-gate-test-" + uuid.uuid4().hex
        )
        self.repo.mkdir()
        self.addCleanup(self.cleanup_fixture_directory)
        self.spec = gate.load_spec(gate.DEFAULT_SPEC)
        self.write_all_reports()

    def cleanup_fixture_directory(self) -> None:
        if self.repo.exists():
            shutil.rmtree(self.repo, ignore_errors=False)
        self.assertFalse(
            self.repo.exists(),
            f"offline fixture directory was not removed: {self.repo}",
        )

    def report_path(self, suite: dict[str, object]) -> Path:
        return self.repo / Path(str(suite["report"]))

    def suite_by_id(self, suite_id: str) -> dict[str, object]:
        return next(
            suite for suite in self.spec["suites"] if suite["id"] == suite_id
        )

    def write_suite(
        self,
        suite: dict[str, object],
        *,
        suite_name: str | None = None,
        names: list[str] | None = None,
        failures: int = 0,
        errors: int = 0,
        skipped: int = 0,
        add_status_node: str | None = None,
        include_secret_properties: bool = False,
    ) -> None:
        expected_names = list(suite["testcases"])
        observed_names = names if names is not None else expected_names
        class_name = suite_name or str(suite["suite"])
        root = ET.Element(
            "testsuite",
            {
                "name": class_name,
                "tests": str(len(observed_names)),
                "failures": str(failures),
                "errors": str(errors),
                "skipped": str(skipped),
                "time": "0.001",
            },
        )
        if include_secret_properties:
            properties = ET.SubElement(root, "properties")
            ET.SubElement(
                properties,
                "property",
                {"name": "example.password", "value": "fixture-password"},
            )
        for index, name in enumerate(observed_names):
            testcase = ET.SubElement(
                root,
                "testcase",
                {"classname": class_name, "name": name, "time": "0.001"},
            )
            if add_status_node and index == 0:
                ET.SubElement(testcase, add_status_node)
        path = self.report_path(suite)
        path.parent.mkdir(parents=True, exist_ok=True)
        ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)

    def write_all_reports(self) -> None:
        for suite in self.spec["suites"]:
            self.write_suite(suite)

    def test_spec_locks_all_required_suites_and_twenty_five_cases(self) -> None:
        counts = {
            suite["suite"]: len(suite["testcases"])
            for suite in self.spec["suites"]
        }
        self.assertEqual(
            counts,
            {
                "cn.edu.gpnu.platform.boot.Phase00ScaffoldIT": 6,
                "cn.edu.gpnu.platform.boot.Phase00ParameterMatrixIT": 1,
                "cn.edu.gpnu.platform.boot.config.DataScopeSqlHandlerTest": 9,
                "cn.edu.gpnu.platform.boot.config.DataScopeMapperChainTest": 5,
                "cn.edu.gpnu.platform.file.service.impl.FileServiceUploadContractTest": 2,
                "cn.edu.gpnu.platform.boot.config.ApiDocumentationSecurityProfileTest": 2,
            },
        )
        self.assertEqual(sum(counts.values()), 25)

    def test_exact_fresh_reports_pass(self) -> None:
        results = gate.validate_suite_reports(self.repo, self.spec, fresh_after_ns=0)
        self.assertEqual(len(results), 6)
        self.assertEqual(sum(result["observedTests"] for result in results), 25)

    def test_missing_xml_fails(self) -> None:
        self.report_path(self.spec["suites"][0]).unlink()
        with self.assertRaisesRegex(gate.GateError, "required XML is missing"):
            gate.validate_suite_reports(self.repo, self.spec, fresh_after_ns=0)

    def test_missing_testcase_fails(self) -> None:
        suite = self.spec["suites"][0]
        self.write_suite(suite, names=list(suite["testcases"])[:-1])
        with self.assertRaisesRegex(gate.GateError, "counts mismatch"):
            gate.validate_suite_reports(self.repo, self.spec, fresh_after_ns=0)

    def test_nonzero_skip_fails_even_when_nodes_exist(self) -> None:
        suite = self.spec["suites"][1]
        self.write_suite(
            suite,
            skipped=1,
            add_status_node="skipped",
        )
        with self.assertRaisesRegex(gate.GateError, "counts mismatch"):
            gate.validate_suite_reports(self.repo, self.spec, fresh_after_ns=0)

    def test_wrong_suite_name_fails(self) -> None:
        suite = self.spec["suites"][0]
        self.write_suite(suite, suite_name=str(suite["suite"]) + "Renamed")
        with self.assertRaisesRegex(gate.GateError, "suite mismatch"):
            gate.validate_suite_reports(self.repo, self.spec, fresh_after_ns=0)

    def test_duplicate_testcase_fails(self) -> None:
        suite = self.suite_by_id("data-scope-handler-unit")
        names = list(suite["testcases"])
        names[-1] = names[0]
        self.write_suite(suite, names=names)
        with self.assertRaisesRegex(gate.GateError, "duplicate testcase"):
            gate.validate_suite_reports(self.repo, self.spec, fresh_after_ns=0)

    def test_stale_xml_fails(self) -> None:
        suite = self.spec["suites"][0]
        path = self.report_path(suite)
        os.utime(path, (1, 1))
        with self.assertRaisesRegex(gate.GateError, "is stale"):
            gate.validate_suite_reports(
                self.repo,
                self.spec,
                fresh_after_ns=time.time_ns(),
            )

    def test_old_report_cleanup_is_exact_and_preserves_unrelated_xml(self) -> None:
        first_report = self.report_path(self.spec["suites"][0])
        first_text = gate.report_text_path(first_report)
        self.assertIsNotNone(first_text)
        first_text.write_text("old result", encoding="utf-8")
        summary = self.repo / Path(
            self.spec["supportingArtifacts"][0]["path"]
        )
        summary.parent.mkdir(parents=True, exist_ok=True)
        summary.write_text("<failsafe-summary/>", encoding="utf-8")
        unrelated = first_report.parent / "TEST-unrelated.xml"
        unrelated.write_text("<testsuite/>", encoding="utf-8")

        removed = gate.remove_declared_reports(self.repo, self.spec)

        self.assertIn(self.spec["suites"][0]["report"], removed)
        self.assertFalse(first_report.exists())
        self.assertFalse(first_text.exists())
        self.assertFalse(summary.exists())
        self.assertTrue(unrelated.exists())

    def test_failsafe_summary_counts_are_enforced(self) -> None:
        summary_path = self.repo / Path(
            self.spec["supportingArtifacts"][0]["path"]
        )
        summary_path.parent.mkdir(parents=True, exist_ok=True)

        def write_summary(*, completed: int, skipped: int) -> None:
            root = ET.Element("failsafe-summary")
            for key, value in (
                ("completed", completed),
                ("errors", 0),
                ("failures", 0),
                ("skipped", skipped),
                ("flakes", 0),
            ):
                ET.SubElement(root, key).text = str(value)
            ET.ElementTree(root).write(
                summary_path, encoding="utf-8", xml_declaration=True
            )

        write_summary(completed=7, skipped=0)
        counts = gate.inspect_failsafe_summary(
            summary_path,
            minimum_completed=7,
            fresh_after_ns=0,
        )
        self.assertEqual(counts["completed"], 7)
        write_summary(completed=6, skipped=0)
        with self.assertRaisesRegex(gate.GateError, "below the required"):
            gate.inspect_failsafe_summary(
                summary_path,
                minimum_completed=7,
                fresh_after_ns=0,
            )
        write_summary(completed=7, skipped=1)
        with self.assertRaisesRegex(gate.GateError, "not fully green"):
            gate.inspect_failsafe_summary(
                summary_path,
                minimum_completed=7,
                fresh_after_ns=0,
            )

    def test_wrong_candidate_sha_fails(self) -> None:
        with self.assertRaisesRegex(gate.GateError, "candidate SHA mismatch"):
            gate.validate_candidate("a" * 40, "b" * 40)

    def test_dirty_tracked_worktree_fails(self) -> None:
        completed = subprocess.CompletedProcess(
            args=["git", "status"],
            returncode=0,
            stdout=b" M platform-file/src/main/java/Example.java\n",
            stderr=b"",
        )
        with patch.object(gate.subprocess, "run", return_value=completed):
            with self.assertRaisesRegex(gate.GateError, "tracked worktree is dirty"):
                gate.require_clean_tracked_worktree(self.repo)

    def test_untracked_maven_input_fails_but_audit_material_is_ignored(self) -> None:
        completed = subprocess.CompletedProcess(
            args=["git", "ls-files"],
            returncode=0,
            stdout=(
                b"docs/audit-remediation-plan.md\0"
                b"platform-file/src/test/java/InjectedTest.java\0"
            ),
            stderr=b"",
        )
        with patch.object(gate.subprocess, "run", return_value=completed):
            with self.assertRaisesRegex(gate.GateError, "untracked Maven input"):
                gate.require_no_build_relevant_untracked(self.repo)

        audit_only = subprocess.CompletedProcess(
            args=["git", "ls-files"],
            returncode=0,
            stdout=b"docs/audit-remediation-plan.md\0.claude/audit.txt\0",
            stderr=b"",
        )
        with patch.object(gate.subprocess, "run", return_value=audit_only):
            self.assertEqual(gate.require_no_build_relevant_untracked(self.repo), 2)

    def test_unavailable_tool_version_fails(self) -> None:
        with patch.object(
            gate,
            "run_capture",
            side_effect=["git version 2", "unavailable: java", "Apache Maven 3"],
        ):
            with self.assertRaisesRegex(gate.GateError, "tool versions are unavailable"):
                gate.build_tool_versions(
                    self.repo,
                    "mvn",
                    gate.SecretRedactor({}),
                )

    def test_wrong_artifact_sha_fails(self) -> None:
        evidence = self.repo / "evidence"
        evidence.mkdir()
        artifact = evidence / "report.xml"
        artifact.write_text("<testsuite/>", encoding="utf-8")
        records = [
            {
                "path": "report.xml",
                "kind": "fixture",
                "sha256": "0" * 64,
                "bytes": artifact.stat().st_size,
            }
        ]
        with self.assertRaisesRegex(gate.GateError, "artifact SHA-256 mismatch"):
            gate.verify_artifact_records(evidence, records)

    def test_checksums_reject_unlisted_extra_file(self) -> None:
        evidence = self.repo / "checksums"
        evidence.mkdir()
        artifact = evidence / "artifact.txt"
        manifest = evidence / "manifest.json"
        extra = evidence / "unlisted.txt"
        artifact.write_text("artifact", encoding="utf-8")
        manifest.write_text("{}", encoding="utf-8")
        extra.write_text("must fail", encoding="utf-8")
        gate.write_checksums(evidence, [artifact, manifest])
        with self.assertRaisesRegex(gate.GateError, "unlisted files"):
            gate.verify_checksums_file(
                evidence,
                expected_paths={"artifact.txt", "manifest.json"},
            )

    def test_checksums_reject_declared_artifact_omitted_from_sum_file(self) -> None:
        evidence = self.repo / "missing-checksum"
        evidence.mkdir()
        artifact = evidence / "artifact.txt"
        manifest = evidence / "manifest.json"
        artifact.write_text("artifact", encoding="utf-8")
        manifest.write_text("{}", encoding="utf-8")
        gate.write_checksums(evidence, [manifest])
        with self.assertRaisesRegex(gate.GateError, "coverage is not exact"):
            gate.verify_checksums_file(
                evidence,
                expected_paths={"artifact.txt", "manifest.json"},
            )

    def test_self_verification_failure_rewrites_pass_manifest_to_fail(self) -> None:
        evidence = self.repo / "self-verify"
        evidence.mkdir()
        candidate = "a" * 40
        now = gate.utc_now()
        manifest = {
            "schemaVersion": 1,
            "gateId": self.spec["gateId"],
            "status": "PASS",
            "candidateSha": candidate,
            "expectedCandidateSha": candidate,
            "source": {
                "trackedWorktreeCleanAtStart": True,
                "trackedWorktreeCleanAtEnd": True,
                "buildRelevantUntrackedAbsentAtStart": True,
                "buildRelevantUntrackedAbsentAtEnd": True,
                "ignoredNonBuildUntrackedAtStart": 0,
                "ignoredNonBuildUntrackedAtEnd": 0,
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
                "command": ["mvn", "-B", "-ntp", "clean", "verify"],
                "startedAtUtc": now,
                "endedAtUtc": now,
                "startedEpochNs": 1,
                "endedEpochNs": 2,
                "exitCode": 0,
                "cleanLifecycleRequired": True,
            },
            "tools": {
                "git": "git version fixture",
                "java": "java fixture",
                "maven": "maven fixture",
                "python": "python fixture",
            },
            "targets": {
                "schema": "teacher_cert",
                "mysql": "mysql:fixture",
                "redis": "redis:fixture",
                "minio": "minio:fixture",
                "runContext": "offline:selftest",
            },
            "suites": [],
            "supportingArtifacts": [],
            "artifacts": [],
            "errors": [],
            "security": {"containsSecrets": False},
        }
        verified, error = gate.self_verify_or_downgrade(
            evidence_dir=evidence,
            spec_path=gate.DEFAULT_SPEC,
            expected_candidate=candidate,
            manifest=manifest,
        )
        self.assertFalse(verified)
        self.assertIn("self-verification failed", error)
        persisted = json.loads(
            (evidence / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(persisted["status"], "FAIL")
        self.assertTrue(
            any("self-verification failed" in item for item in persisted["errors"])
        )
        gate.verify_checksums_file(
            evidence,
            expected_paths={"manifest.json"},
        )

    def test_xml_sanitiser_removes_properties_and_secret_values(self) -> None:
        suite = self.spec["suites"][0]
        self.write_suite(suite, include_secret_properties=True)
        source = self.report_path(suite)
        destination = self.repo / "evidence" / "suite.xml"
        redactor = gate.SecretRedactor({"EXAMPLE_PASSWORD": "fixture-password"})
        source_hash, artifact_hash, redactions = gate.sanitize_xml(
            source, destination, redactor
        )
        self.assertRegex(source_hash, r"^[0-9a-f]{64}$")
        self.assertEqual(artifact_hash, gate.sha256_file(destination))
        content = destination.read_text(encoding="utf-8")
        self.assertNotIn("fixture-password", content)
        self.assertNotIn("<properties", content)
        self.assertTrue(
            any(item.startswith("testsuite-properties-removed") for item in redactions)
        )

    def test_secret_redactor_covers_env_assignment_uri_and_jwt(self) -> None:
        redactor = gate.SecretRedactor(
            {
                "JWT_SECRET": "known-secret-value",
                "SPRING_DATASOURCE_PASSWORD": "root123",
            }
        )
        value = (
            "secret=known-secret-value "
            "jdbc=mysql://user:another-secret@localhost/db "
            "driver echoed bare value root123 "
            "eyJabcdefgh.ijklmnop.qrstuvwx"
        )
        redacted = redactor.redact(value)
        self.assertNotIn("known-secret-value", redacted)
        self.assertNotIn("another-secret", redacted)
        self.assertNotIn("root123", redacted)
        self.assertNotIn("eyJabcdefgh", redacted)
        self.assertIn("[REDACTED_SECRET]", redacted)

    def test_spec_rejects_duplicate_declared_testcase(self) -> None:
        broken = json.loads(json.dumps(self.spec))
        broken["suites"][0]["testcases"][1] = broken["suites"][0]["testcases"][0]
        path = self.repo / "broken-spec.json"
        path.write_text(json.dumps(broken), encoding="utf-8")
        with self.assertRaisesRegex(gate.GateError, "duplicate names"):
            gate.load_spec(path)


if __name__ == "__main__":
    unittest.main(verbosity=2)
