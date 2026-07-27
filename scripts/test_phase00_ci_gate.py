#!/usr/bin/env python3
"""Pure-offline contract tests for ``phase00_ci_gate.py``."""

from __future__ import annotations

import copy
from contextlib import contextmanager
import json
import os
import shutil
import subprocess
import time
import unittest
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path
from types import SimpleNamespace
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
        self.identity_issued_at = gate.utc_now().split(".")[0] + "Z"
        self.write_all_reports()

    def cleanup_fixture_directory(self) -> None:
        if self.repo.exists():
            gate.remove_directory_tree(self.repo)
        self.assertFalse(
            self.repo.exists(),
            f"offline fixture directory was not removed: {self.repo}",
        )

    @contextmanager
    def mocked_candidate_snapshot(
        self,
        repo_root: Path,
        candidate: str,
        temporary_parent: Path | None = None,
    ):
        self.assertEqual(repo_root, self.repo.resolve(strict=True))
        self.assertEqual(temporary_parent, self.repo)
        entries = self.mocked_git_tree_entries()
        yield gate.SourceSnapshot(
            root=self.repo,
            candidate_sha=candidate,
            tree_sha="b" * 40,
            manifest_sha256=gate.git_tree_manifest_sha256(entries),
            file_count=len(entries),
        )

    def mocked_git_tree_entries(self) -> list[gate.GitTreeEntry]:
        return [
            gate.GitTreeEntry(
                "100644",
                "d" * 40,
                gate.PurePosixPath("pom.xml"),
            )
        ]

    def report_path(
        self,
        suite: dict[str, object],
        root: Path | None = None,
    ) -> Path:
        return (self.repo if root is None else root) / Path(str(suite["report"]))

    def suite_by_id(self, suite_id: str) -> dict[str, object]:
        return next(
            suite for suite in self.spec["suites"] if suite["id"] == suite_id
        )

    def target_environment(self) -> dict[str, str]:
        return {
            "PHASE00_EXPECTED_CANDIDATE_SHA": "d" * 40,
            "PHASE00_TARGET_SCHEMA": "teacher_cert",
            "PHASE00_MYSQL_SERVICE_MARKER": "mysql://127.0.0.1:3306/teacher_cert",
            "PHASE00_REDIS_SERVICE_MARKER": "redis://127.0.0.1:6379/0",
            "PHASE00_MINIO_SERVICE_MARKER": "http://127.0.0.1:9000/teacher-cert",
            "PHASE00_RUN_CONTEXT": "offline:selftest:1",
            "SPRING_DATASOURCE_URL": (
                "jdbc:mysql://127.0.0.1:3306/teacher_cert"
                "?useUnicode=true&characterEncoding=utf8"
                "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                "&useSSL=false"
            ),
            "SPRING_DATASOURCE_USERNAME": "root",
            "SPRING_DATASOURCE_PASSWORD": "root123",
            "SPRING_DATA_REDIS_HOST": "127.0.0.1",
            "SPRING_DATA_REDIS_PORT": "6379",
            "SPRING_DATA_REDIS_DATABASE": "0",
            "MINIO_ENDPOINT": "http://127.0.0.1:9000",
            "MINIO_PUBLIC_ENDPOINT": "http://127.0.0.1:9000",
            "MINIO_ACCESS_KEY": "minioadmin",
            "MINIO_SECRET_KEY": "minioadmin123",
            "MINIO_BUCKET": "teacher-cert",
            "PHASE00_EXPECTED_MYSQL_SERVER_UUID": (
                "123e4567-e89b-12d3-a456-426614174000"
            ),
            "PHASE00_EXPECTED_REDIS_RUN_ID": "b" * 40,
            "PHASE00_MINIO_IDENTITY_OBJECT": (
                ".phase00-target/identity-offline-1.json"
            ),
            "PHASE00_EXPECTED_MINIO_IDENTITY_SHA256": "c" * 64,
            "PHASE00_EXPECTED_MINIO_IDENTITY_NONCE": "e" * 64,
            "PHASE00_EXPECTED_MINIO_IDENTITY_ISSUED_AT": (
                self.identity_issued_at
            ),
        }

    def target_contract(
        self,
        candidate: str = "a" * 40,
    ) -> tuple[dict[str, object], dict[str, object], dict[str, object]]:
        environment = self.target_environment()
        declared, configured = gate.read_target_configuration(environment)
        expected = gate.read_expected_identities(environment, candidate)
        return declared, configured, expected

    def runtime_identity(self, candidate: str = "a" * 40) -> dict[str, object]:
        environment = self.target_environment()
        return {
            "schemaVersion": self.spec["targetEvidence"]["schemaVersion"],
            "producerSuite": self.spec["targetEvidence"]["producerSuite"],
            "candidateSha": candidate,
            "runContext": environment["PHASE00_RUN_CONTEXT"],
            "mysql": {
                "database": "teacher_cert",
                "version": "8.0.46",
                "serverUuid": environment[
                    "PHASE00_EXPECTED_MYSQL_SERVER_UUID"
                ],
            },
            "redis": {
                "version": "7.4.9",
                "runId": environment["PHASE00_EXPECTED_REDIS_RUN_ID"],
                "database": 0,
            },
            "minio": {
                "endpoint": "http://127.0.0.1:9000",
                "bucket": "teacher-cert",
                "identityObject": environment[
                    "PHASE00_MINIO_IDENTITY_OBJECT"
                ],
                "identitySha256": environment[
                    "PHASE00_EXPECTED_MINIO_IDENTITY_SHA256"
                ],
                "identityNonce": environment[
                    "PHASE00_EXPECTED_MINIO_IDENTITY_NONCE"
                ],
                "identityIssuedAt": environment[
                    "PHASE00_EXPECTED_MINIO_IDENTITY_ISSUED_AT"
                ],
                "instanceFingerprintSha256": (
                    gate.derive_minio_instance_fingerprint(
                        "123e4567-e89b-12d3-a456-426614174000",
                        environment["PHASE00_EXPECTED_MINIO_IDENTITY_NONCE"],
                    )
                ),
            },
            "freshness": {
                "mysqlTableCountBefore": 0,
                "redisDatabaseSizeBefore": 0,
                "minioObjectCountBefore": 1,
                "minioUnexpectedObjectCountBefore": 0,
            },
        }

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
        root_directory: Path | None = None,
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
        path = self.report_path(suite, root_directory)
        path.parent.mkdir(parents=True, exist_ok=True)
        ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)

    def write_all_reports(self, root_directory: Path | None = None) -> None:
        for suite in self.spec["suites"]:
            self.write_suite(suite, root_directory=root_directory)

    def test_spec_locks_all_required_suites_and_thirty_three_cases(self) -> None:
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
                "cn.edu.gpnu.platform.boot.Phase00TargetGuardInitializerTest": 8,
            },
        )
        self.assertEqual(sum(counts.values()), 33)
        self.assertEqual(
            self.spec["targetEvidence"],
            {
                "path": "platform-boot/target/phase00-target-identity.json",
                "producerSuite": "cn.edu.gpnu.platform.boot.Phase00ScaffoldIT",
                "preflightPath": (
                    "platform-boot/target/phase00-target-preflight.json"
                ),
                "preflightProducer": (
                    "cn.edu.gpnu.platform.boot.Phase00TargetPreflight"
                ),
                "schemaVersion": 4,
                "mysqlVersionPattern": r"^8\..+$",
                "redisVersionPattern": r"^7\..+$",
                "minioIdentityMode": gate.MINIO_IDENTITY_MODE,
                "required": True,
            },
        )
        nonce = "b" * 64
        deployment_id = "123e4567-e89b-12d3-a456-426614174000"
        fingerprint = gate.derive_minio_instance_fingerprint(
            deployment_id,
            nonce,
        )
        self.assertEqual(
            fingerprint,
            "7fe3df67cf424bb6bf35a90cd42f348da3d7855a20b0f55f9d3b612cdaea2c1e",
        )
        self.assertNotEqual(
            fingerprint,
            gate.derive_minio_instance_fingerprint(
                "223e4567-e89b-12d3-a456-426614174000",
                nonce,
            ),
        )
        self.assertNotEqual(
            fingerprint,
            gate.derive_minio_instance_fingerprint(
                deployment_id,
                "c" * 64,
            ),
        )

    def test_target_configuration_binds_markers_to_actual_services(self) -> None:
        declared, configured = gate.read_target_configuration(
            self.target_environment()
        )
        self.assertEqual(
            gate.comparable_service(declared["mysql"]),
            configured["mysql"],
        )
        self.assertEqual(
            gate.comparable_service(declared["redis"]),
            configured["redis"],
        )
        self.assertEqual(
            gate.comparable_service(declared["minio"]),
            configured["minio"],
        )
        self.assertEqual(configured["minioPublic"], configured["minio"])
        self.assertEqual(declared["schema"], "teacher_cert")

    def test_target_configuration_rejects_every_marker_config_mismatch(self) -> None:
        mutations = (
            ("PHASE00_MYSQL_SERVICE_MARKER", "mysql://127.0.0.2:3306/teacher_cert"),
            ("PHASE00_TARGET_SCHEMA", "other_schema"),
            ("PHASE00_REDIS_SERVICE_MARKER", "redis://127.0.0.1:6379/1"),
            (
                "PHASE00_MINIO_SERVICE_MARKER",
                "http://127.0.0.1:9000/other-bucket",
            ),
            ("MINIO_PUBLIC_ENDPOINT", "http://127.0.0.2:9000"),
        )
        for name, value in mutations:
            with self.subTest(name=name):
                environment = self.target_environment()
                environment[name] = value
                with self.assertRaisesRegex(gate.GateError, "does not match"):
                    gate.read_target_configuration(environment)

    def test_mysql_configuration_rejects_ambiguous_or_sensitive_urls(self) -> None:
        invalid = (
            "jdbc:mysql://user:password@127.0.0.1:3306/teacher_cert",
            "jdbc:mysql://127.0.0.1:3306,127.0.0.2:3306/teacher_cert",
            "jdbc:mysql:loadbalance://127.0.0.1:3306/teacher_cert",
            "jdbc:mysql:replication://127.0.0.1:3306/teacher_cert",
            "jdbc:mysql://127.0.0.1:3306/teacher_cert?password=not-safe",
            "jdbc:mysql://127.0.0.1:3306/teacher_cert?useSSL=false&USEssl=true",
            "jdbc:mysql://127.0.0.1:3306/teacher_cert?socketFactory=evil.Factory",
            "jdbc:mysql://127.0.0.1:3306/teacher_cert?unknownTargetOption=true",
        )
        for value in invalid:
            with self.subTest(value=value):
                with self.assertRaises(gate.GateError):
                    gate.parse_mysql_configuration(value)

    def test_target_uris_reject_encoded_or_malformed_separators(self) -> None:
        invalid_mysql_markers = (
            "mysql://127.0.0.1:3306/teacher%2Fcert",
            "mysql://127.0.0.1:3306/teacher%5ccert",
            "mysql://127.0.0.1:3306/teacher%00cert",
            "mysql://127.0.0.1:3306/%74eacher_cert",
            "mysql://127.0.0.1:3306/teacher%ZZcert",
        )
        for value in invalid_mysql_markers:
            with self.subTest(value=value):
                with self.assertRaises(gate.GateError):
                    gate.parse_mysql_marker(value)
        with self.assertRaises(gate.GateError):
            gate.parse_mysql_configuration(
                "jdbc:mysql://127.0.0.1:3306/teacher%2Fcert"
            )
        with self.assertRaises(gate.GateError):
            gate.parse_minio_marker(
                "http://127.0.0.1:9000/teacher%5Ccert"
            )

    def test_target_uri_normalization_supports_ipv6_and_default_ports(self) -> None:
        environment = self.target_environment()
        environment.update(
            {
                "PHASE00_MYSQL_SERVICE_MARKER": (
                    "mysql://[2001:db8::1]/teacher_cert"
                ),
                "SPRING_DATASOURCE_URL": (
                    "jdbc:mysql://[2001:0db8:0:0:0:0:0:1]/teacher_cert"
                ),
                "PHASE00_REDIS_SERVICE_MARKER": "redis://[::1]/0",
                "SPRING_DATA_REDIS_HOST": "::1",
                "PHASE00_MINIO_SERVICE_MARKER": (
                    "https://[2001:db8::2]/teacher-cert"
                ),
                "MINIO_ENDPOINT": "https://[2001:0db8:0:0:0:0:0:2]",
                "MINIO_PUBLIC_ENDPOINT": "https://[2001:db8::2]",
            }
        )
        declared, configured = gate.read_target_configuration(environment)
        self.assertEqual(declared["mysql"]["marker"], "mysql://[2001:db8::1]:3306/teacher_cert")
        self.assertEqual(configured["redis"]["host"], "::1")
        self.assertEqual(declared["minio"]["marker"], "https://[2001:db8::2]:443/teacher-cert")
        runtime = self.runtime_identity()
        runtime["minio"]["endpoint"] = "https://[2001:0db8:0:0:0:0:0:2]:443"
        gate.validate_runtime_identity(
            runtime,
            self.spec["targetEvidence"],
            declared,
            configured,
            gate.read_expected_identities(environment, "a" * 40),
        )

    def test_expected_service_identities_are_strict_and_candidate_is_forced(self) -> None:
        environment = self.target_environment()
        expected = "a" * 40
        identities = gate.read_expected_identities(environment, expected)
        self.assertEqual(identities["candidateSha"], expected)
        child = gate.build_maven_environment(environment, expected)
        self.assertEqual(child["PHASE00_EXPECTED_CANDIDATE_SHA"], expected)
        self.assertEqual(
            environment["PHASE00_EXPECTED_CANDIDATE_SHA"],
            "d" * 40,
            "the immutable start environment must not be modified",
        )

        invalid = (
            ("PHASE00_EXPECTED_MYSQL_SERVER_UUID", "not-a-uuid"),
            ("PHASE00_EXPECTED_REDIS_RUN_ID", "a" * 39),
            ("PHASE00_MINIO_IDENTITY_OBJECT", "../identity.json"),
            ("PHASE00_EXPECTED_MINIO_IDENTITY_SHA256", "a" * 63),
            ("PHASE00_EXPECTED_MINIO_IDENTITY_NONCE", "a" * 63),
            (
                "PHASE00_EXPECTED_MINIO_IDENTITY_ISSUED_AT",
                "2026-07-27T00:00:00+00:00",
            ),
        )
        for name, value in invalid:
            with self.subTest(name=name):
                broken = self.target_environment()
                broken[name] = value
                with self.assertRaises(gate.GateError):
                    gate.read_expected_identities(broken, expected)

    def test_preflight_command_is_isolated_before_formal_verify(self) -> None:
        self.assertEqual(
            gate.preflight_maven_command("mvn"),
            [
                "mvn",
                "-B",
                "-ntp",
                "-pl",
                "platform-boot",
                "-am",
                "-Dtest=__phase00_no_unit_tests__",
                "-Dsurefire.failIfNoSpecifiedTests=false",
                "-Dit.test=Phase00TargetPreflight",
                "-Dfailsafe.failIfNoSpecifiedTests=false",
                "clean",
                "verify",
            ],
        )
        self.assertEqual(
            gate.formal_maven_command("mvn"),
            [
                "mvn",
                "-B",
                "-ntp",
                (
                    "-Dtest=DataScopeSqlHandlerTest,DataScopeMapperChainTest,"
                    "FileServiceUploadContractTest,ApiDocumentationSecurityProfileTest,"
                    "Phase00TargetGuardInitializerTest"
                ),
                "-Dsurefire.failIfNoSpecifiedTests=false",
                "-Dit.test=Phase00ScaffoldIT,Phase00ParameterMatrixIT",
                "-Dfailsafe.failIfNoSpecifiedTests=false",
                "clean",
                "verify",
            ],
        )

    def test_failed_preflight_never_starts_formal_verify(self) -> None:
        spec_path = self.repo / "scripts" / "phase00_ci_gate_spec.json"
        spec_path.parent.mkdir(parents=True)
        spec_path.write_bytes(gate.DEFAULT_SPEC.read_bytes())
        evidence = self.repo / "evidence"
        candidate = "a" * 40
        started_ns = time.time_ns()
        preflight_run = {
            "command": gate.preflight_maven_command("mvn"),
            "workingDirectory": ".",
            "startedAtUtc": gate.utc_now(),
            "endedAtUtc": gate.utc_now(),
            "startedEpochNs": started_ns,
            "endedEpochNs": started_ns + 1,
            "exitCode": 1,
            "cleanLifecycleRequired": True,
        }
        arguments = SimpleNamespace(
            repo_root=self.repo,
            spec=spec_path,
            evidence_dir=evidence,
            expected_candidate_sha=candidate,
            maven="mvn",
        )
        environment = self.target_environment()
        with (
            patch.dict(os.environ, environment, clear=True),
            patch.object(
                gate,
                "immutable_candidate_snapshot",
                new=self.mocked_candidate_snapshot,
            ),
            patch.object(gate, "git_head", return_value=candidate),
            patch.object(gate, "git_tree_sha", return_value="b" * 40),
            patch.object(
                gate,
                "git_tree_entries",
                return_value=self.mocked_git_tree_entries(),
            ),
            patch.object(gate, "require_clean_tracked_worktree"),
            patch.object(
                gate,
                "require_no_build_relevant_untracked",
                return_value=0,
            ),
            patch.object(
                gate,
                "build_tool_versions",
                return_value={
                    "git": "git fixture",
                    "java": "java fixture",
                    "maven": "maven fixture",
                    "python": "python fixture",
                },
            ),
            patch.object(
                gate,
                "capture_maven_run",
                return_value=(preflight_run, b"preflight failed\n", "f" * 64),
            ),
            patch.object(gate, "verify_materialised_git_tree"),
            patch.object(gate, "stream_maven") as formal_verify,
        ):
            self.assertEqual(gate.run_gate(arguments), 1)
        formal_verify.assert_not_called()
        manifest = json.loads(
            (evidence / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(manifest["status"], "FAIL")
        self.assertFalse(manifest["run"]["executed"])
        self.assertEqual(manifest["preflight"]["exitCode"], 1)

    def test_verifier_rejects_live_spec_that_differs_from_candidate_blob(
        self,
    ) -> None:
        spec_path = self.repo / "scripts" / "phase00_ci_gate_spec.json"
        spec_path.parent.mkdir(parents=True)
        candidate_spec = gate.DEFAULT_SPEC.read_bytes()
        weakened = json.loads(candidate_spec.decode("utf-8"))
        weakened["gateId"] = str(weakened["gateId"]) + "-weakened"
        spec_path.write_text(json.dumps(weakened), encoding="utf-8")
        unused_evidence = self.repo / "unused-evidence"
        unused_evidence.mkdir()

        with (
            patch.object(gate, "SCRIPT_DIR", self.repo / "scripts"),
            patch.object(
                gate,
                "read_candidate_git_blob",
                return_value=candidate_spec,
            ),
        ):
            with self.assertRaisesRegex(
                gate.GateError,
                "differs from the expected candidate Git blob",
            ):
                gate.verify_evidence(
                    unused_evidence,
                    spec_path,
                    "a" * 40,
                )

    def test_mocked_success_finalizes_after_source_cleanup_and_ignores_print_failure(
        self,
    ) -> None:
        spec_path = self.repo / "scripts" / "phase00_ci_gate_spec.json"
        spec_path.parent.mkdir(parents=True)
        spec_path.write_bytes(gate.DEFAULT_SPEC.read_bytes())
        evidence = self.repo / "evidence-pass"
        candidate = "a" * 40
        arguments = SimpleNamespace(
            repo_root=self.repo,
            spec=spec_path,
            evidence_dir=evidence,
            expected_candidate_sha=candidate,
            maven="mvn",
        )
        environment = self.target_environment()
        runtime = self.runtime_identity(candidate)
        preflight = copy.deepcopy(runtime)
        preflight["producerSuite"] = self.spec["targetEvidence"][
            "preflightProducer"
        ]
        snapshot_root = self.repo / "immutable-source"
        snapshot_spec = (
            snapshot_root / "scripts" / "phase00_ci_gate_spec.json"
        )
        snapshot_spec.parent.mkdir(parents=True)
        snapshot_spec.write_bytes(gate.DEFAULT_SPEC.read_bytes())
        observed_build_roots: list[Path] = []
        lifecycle_events: list[str] = []
        source_snapshot_cleanup_complete = False
        final_summary_print_attempted = False

        @contextmanager
        def fake_snapshot(
            repo_root: Path,
            requested_candidate: str,
            temporary_parent: Path | None = None,
        ):
            nonlocal source_snapshot_cleanup_complete
            self.assertEqual(repo_root, self.repo.resolve(strict=True))
            self.assertEqual(requested_candidate, candidate)
            self.assertEqual(temporary_parent, self.repo)
            lifecycle_events.append("source-enter")
            try:
                yield gate.SourceSnapshot(
                    root=snapshot_root,
                    candidate_sha=candidate,
                    tree_sha="b" * 40,
                    manifest_sha256=gate.git_tree_manifest_sha256(
                        self.mocked_git_tree_entries()
                    ),
                    file_count=len(self.mocked_git_tree_entries()),
                )
            finally:
                ready_directories = [
                    child
                    for child in self.repo.iterdir()
                    if child.is_dir() and ".ready-" in child.name
                ]
                self.assertEqual(len(ready_directories), 1)
                self.assertFalse(
                    (ready_directories[0] / "manifest.json").exists()
                )
                self.assertFalse(
                    (ready_directories[0] / "SHA256SUMS").exists()
                )
                source_snapshot_cleanup_complete = True
                lifecycle_events.append("source-cleanup")

        def fake_preflight(
            command: list[str],
            cwd: Path,
            redactor: gate.SecretRedactor,
            child_environment: dict[str, str],
        ) -> tuple[dict[str, object], bytes, str]:
            del redactor
            observed_build_roots.append(cwd)
            self.assertEqual(
                child_environment["PHASE00_EXPECTED_CANDIDATE_SHA"],
                candidate,
            )
            self.assertEqual(child_environment["SPRING_PROFILES_ACTIVE"], "dev")
            source = cwd / Path(self.spec["targetEvidence"]["preflightPath"])
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text(json.dumps(preflight), encoding="utf-8")
            started_ns = time.time_ns()
            now = gate.utc_now()
            return (
                {
                    "command": command,
                    "workingDirectory": ".",
                    "startedAtUtc": now,
                    "endedAtUtc": now,
                    "startedEpochNs": started_ns,
                    "endedEpochNs": started_ns + 1,
                    "exitCode": 0,
                    "cleanLifecycleRequired": True,
                },
                b"mocked preflight success\n",
                "e" * 64,
            )

        def fake_formal_verify(
            command: list[str],
            cwd: Path,
            redactor: gate.SecretRedactor,
            safe_log_path: Path,
            child_environment: dict[str, str],
        ) -> tuple[int, str]:
            del command, redactor
            observed_build_roots.append(cwd)
            self.assertEqual(
                child_environment["PHASE00_EXPECTED_CANDIDATE_SHA"],
                candidate,
            )
            safe_log_path.write_text("mocked formal success\n", encoding="utf-8")
            self.write_all_reports(cwd)
            summary = cwd / Path(
                self.spec["supportingArtifacts"][0]["path"]
            )
            summary.parent.mkdir(parents=True, exist_ok=True)
            root = ET.Element("failsafe-summary")
            for key, value in (
                ("completed", 7),
                ("errors", 0),
                ("failures", 0),
                ("skipped", 0),
                ("flakes", 0),
            ):
                ET.SubElement(root, key).text = str(value)
            ET.ElementTree(root).write(
                summary,
                encoding="utf-8",
                xml_declaration=True,
            )
            runtime_source = cwd / Path(self.spec["targetEvidence"]["path"])
            runtime_source.write_text(json.dumps(runtime), encoding="utf-8")
            return 0, "f" * 64

        def fake_rematerialize(
            repo_root: Path,
            snapshot: gate.SourceSnapshot,
            expected_candidate: str,
            expected_entries: list[gate.GitTreeEntry],
        ) -> None:
            del repo_root, expected_entries
            self.assertEqual(snapshot.root, snapshot_root)
            self.assertEqual(expected_candidate, candidate)
            generated_module = snapshot_root / "platform-boot"
            if generated_module.exists():
                gate.remove_directory_tree(generated_module)

        original_self_verify = gate.self_verify_or_downgrade

        def verify_only_after_source_cleanup(**kwargs):
            self.assertTrue(source_snapshot_cleanup_complete)
            self.assertEqual(lifecycle_events[-1], "source-cleanup")
            lifecycle_events.append("self-verify")
            return original_self_verify(**kwargs)

        original_print = print

        def fail_only_final_pass_summary(*values, **kwargs):
            nonlocal final_summary_print_attempted
            if (
                values
                and isinstance(values[0], str)
                and values[0].startswith("[phase00-ci-gate] PASS:")
            ):
                final_summary_print_attempted = True
                raise ValueError("published stdout is unavailable")
            return original_print(*values, **kwargs)

        with (
            patch.dict(os.environ, environment, clear=True),
            patch.object(gate, "SCRIPT_DIR", self.repo / "scripts"),
            patch.object(
                gate,
                "immutable_candidate_snapshot",
                new=fake_snapshot,
            ),
            patch.object(gate, "git_head", return_value=candidate),
            patch.object(gate, "git_tree_sha", return_value="b" * 40),
            patch.object(
                gate,
                "git_tree_entries",
                return_value=self.mocked_git_tree_entries(),
            ),
            patch.object(
                gate,
                "read_candidate_git_blob",
                return_value=gate.DEFAULT_SPEC.read_bytes(),
            ),
            patch.object(gate, "require_clean_tracked_worktree"),
            patch.object(
                gate,
                "require_no_build_relevant_untracked",
                return_value=0,
            ),
            patch.object(
                gate,
                "build_tool_versions",
                return_value={
                    "git": "git fixture",
                    "java": "java fixture",
                    "maven": "maven fixture",
                    "python": "python fixture",
                },
            ),
            patch.object(
                gate,
                "capture_maven_run",
                side_effect=fake_preflight,
            ),
            patch.object(gate, "verify_materialised_git_tree"),
            patch.object(
                gate,
                "rematerialise_candidate_snapshot",
                side_effect=fake_rematerialize,
            ),
            patch.object(
                gate,
                "stream_maven",
                side_effect=fake_formal_verify,
            ),
            patch.object(
                gate,
                "self_verify_or_downgrade",
                side_effect=verify_only_after_source_cleanup,
            ),
            patch(
                "builtins.print",
                side_effect=fail_only_final_pass_summary,
            ),
        ):
            self.assertEqual(gate.run_gate(arguments), 0)
        self.assertEqual(
            lifecycle_events,
            ["source-enter", "source-cleanup", "self-verify"],
        )
        self.assertTrue(final_summary_print_attempted)
        manifest = json.loads(
            (evidence / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(manifest["status"], "PASS")
        self.assertEqual(
            manifest["targets"]["preflight"]["producerSuite"],
            self.spec["targetEvidence"]["preflightProducer"],
        )
        self.assertEqual(
            gate.identity_binding_view(manifest["targets"]["preflight"]),
            gate.identity_binding_view(manifest["targets"]["runtime"]),
        )
        self.assertEqual(
            observed_build_roots,
            [snapshot_root, snapshot_root],
        )
        self.assertEqual(
            manifest["source"]["snapshotTreeSha"],
            "b" * 40,
        )
        self.assertTrue(manifest["source"]["snapshotVerifiedAfterPreflight"])
        self.assertTrue(manifest["source"]["snapshotRematerializedBeforeFormal"])
        self.assertTrue(manifest["source"]["snapshotVerifiedAfterFormal"])
        self.assertFalse(
            any(
                ".ready-" in child.name or child.name.startswith(".phase00-source-")
                for child in self.repo.iterdir()
            )
        )

        evidence_snapshot = gate.capture_evidence_snapshot(evidence)
        evidence_root = evidence.absolute()
        original_open = Path.open
        original_read_bytes = Path.read_bytes
        original_read_text = Path.read_text
        original_stat = Path.stat
        original_sha256_file = gate.sha256_file

        def reject_evidence_path(path: Path, operation: str) -> None:
            if path.absolute().is_relative_to(evidence_root):
                raise AssertionError(
                    f"evidence path reopened by {operation}: {path}"
                )

        def guarded_open(path: Path, *args: object, **kwargs: object):
            reject_evidence_path(path, "Path.open")
            return original_open(path, *args, **kwargs)

        def guarded_read_bytes(path: Path) -> bytes:
            reject_evidence_path(path, "Path.read_bytes")
            return original_read_bytes(path)

        def guarded_read_text(
            path: Path,
            *args: object,
            **kwargs: object,
        ) -> str:
            reject_evidence_path(path, "Path.read_text")
            return original_read_text(path, *args, **kwargs)

        def guarded_stat(path: Path, *args: object, **kwargs: object):
            reject_evidence_path(path, "Path.stat")
            return original_stat(path, *args, **kwargs)

        def guarded_sha256_file(path: Path) -> str:
            reject_evidence_path(path, "sha256_file")
            return original_sha256_file(path)

        with (
            patch.object(gate, "SCRIPT_DIR", self.repo / "scripts"),
            patch.object(gate, "git_tree_sha", return_value="b" * 40),
            patch.object(
                gate,
                "git_tree_entries",
                return_value=self.mocked_git_tree_entries(),
            ),
            patch.object(
                gate,
                "read_candidate_git_blob",
                return_value=gate.DEFAULT_SPEC.read_bytes(),
            ),
            patch.object(Path, "open", guarded_open),
            patch.object(Path, "read_bytes", guarded_read_bytes),
            patch.object(Path, "read_text", guarded_read_text),
            patch.object(Path, "stat", guarded_stat),
            patch.object(gate, "sha256_file", guarded_sha256_file),
        ):
            self.assertEqual(
                gate._verify_evidence_snapshot(
                    evidence_snapshot,
                    spec_path,
                    candidate,
                )["status"],
                "PASS",
            )

        def assert_rejected(mutated: dict[str, object], message: str) -> None:
            gate.persist_manifest(evidence, mutated)
            with (
                patch.object(gate, "SCRIPT_DIR", self.repo / "scripts"),
                patch.object(gate, "git_tree_sha", return_value="b" * 40),
                patch.object(
                    gate,
                    "git_tree_entries",
                    return_value=self.mocked_git_tree_entries(),
                ),
                patch.object(
                    gate,
                    "read_candidate_git_blob",
                    return_value=gate.DEFAULT_SPEC.read_bytes(),
                ),
            ):
                with self.assertRaisesRegex(gate.GateError, message):
                    gate.verify_evidence(evidence, spec_path, candidate)

        nested_mutations = (
            ("source", lambda value: value["source"].update(unexpected=True)),
            (
                "preflight",
                lambda value: value["preflight"].update(unexpected=True),
            ),
            ("run", lambda value: value["run"].update(unexpected=True)),
            (
                "gate-spec",
                lambda value: value["gateSpec"].update(unexpected=True),
            ),
            (
                "security",
                lambda value: value["security"].update(unexpected=True),
            ),
            (
                "suite",
                lambda value: value["suites"][0].update(unexpected=True),
            ),
            (
                "support",
                lambda value: value["supportingArtifacts"][0].update(
                    unexpected=True
                ),
            ),
            (
                "artifact",
                lambda value: value["artifacts"][0].update(unexpected=True),
            ),
            (
                "artifact-cross-shape",
                lambda value: next(
                    record
                    for record in value["artifacts"]
                    if record["path"] == "spec/phase00_ci_gate_spec.json"
                ).update(redactions=[]),
            ),
        )
        for label, mutate in nested_mutations:
            with self.subTest(nested_manifest_shape=label):
                mutated = copy.deepcopy(manifest)
                mutate(mutated)
                assert_rejected(mutated, "unexpected")

        extra_artifact = evidence / "extra.txt"
        extra_artifact.write_text("not declared by the gate", encoding="utf-8")
        with_extra_artifact = copy.deepcopy(manifest)
        with_extra_artifact["artifacts"].append(
            gate.artifact_record(
                evidence,
                extra_artifact,
                kind="undeclared-fixture",
            )
        )
        with_extra_artifact["security"]["secretScan"]["artifactCount"] = len(
            with_extra_artifact["artifacts"]
        )
        assert_rejected(with_extra_artifact, "artifact path set is not exact")

    def test_alternate_spring_and_maven_override_channels_fail_closed(self) -> None:
        invalid_names = (
            "SPRING_APPLICATION_JSON",
            "SPRING_CONFIG_IMPORT",
            "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "_JAVA_OPTIONS",
            "MAVEN_OPTS",
            "MAVEN_ARGS",
            "SPRING_DATA_REDIS_URL",
            "SPRING_DATA_REDIS_SENTINEL_MASTER",
            "SPRING_DATA_REDIS_CLUSTER_NODES",
            "SPRING_DATASOURCE_JNDI_NAME",
            "SPRING_DATASOURCE_HIKARI_JDBC_URL",
            "SPRING_DATASOURCE_HIKARI_DATA_SOURCE_PROPERTIES_URL",
            "SPRING_FLYWAY_URL",
            "SPRING_FLYWAY_USER",
            "SPRING_FLYWAY_PASSWORD",
            "SPRING_FLYWAY_DEFAULT_SCHEMA",
            "SPRING_FLYWAY_SCHEMAS",
            "SPRING_FLYWAY_INIT_SQLS",
            "SPRING_FLYWAY_DRIVER_CLASS_NAME",
            "SPRING_FLYWAY_JDBC_PROPERTIES_SESSION_VARIABLES",
            "SPRING_FLYWAY_LOCATIONS",
            "SPRING_FLYWAY_PLACEHOLDERS_TARGET_SCHEMA",
            "SPRING_SQL_INIT_MODE",
            "SPRING_SQL_INIT_SCHEMA_LOCATIONS",
            "SPRING_SQL_INIT_DATA_LOCATIONS",
            "spring.flyway.url",
            "spring-flyway-default-schema",
            "spring_flyway_init_sqls",
            "spring.flyway.jdbcProperties.sessionVariables",
            "spring.flyway.locations",
            "spring.flyway.placeholders.targetSchema",
            "spring.sql.init.mode",
            "spring-sql-init-schema-locations",
            "spring.profiles.include",
            "spring-profiles-default",
        )
        for name in invalid_names:
            with self.subTest(name=name):
                environment = self.target_environment()
                environment[name] = "non-empty-override"
                with self.assertRaisesRegex(
                    gate.GateError,
                    "override channels",
                ):
                    gate.validate_override_channels(environment)
        blank_environment = self.target_environment()
        blank_environment["SPRING_FLYWAY_URL"] = ""
        with self.assertRaisesRegex(gate.GateError, "override channels"):
            gate.validate_override_channels(blank_environment)
        environment = self.target_environment()
        environment["SPRING_PROFILES_ACTIVE"] = "prod"
        with self.assertRaisesRegex(gate.GateError, "exactly dev"):
            gate.validate_override_channels(environment)

        policy = gate.validate_override_channels(self.target_environment())
        self.assertEqual(policy["springProfilesActive"], "dev")
        child = gate.build_maven_environment(
            self.target_environment(),
            "a" * 40,
        )
        self.assertEqual(child["SPRING_PROFILES_ACTIVE"], "dev")

    def test_relaxed_binding_aliases_cannot_redirect_the_formal_context(self) -> None:
        aliases = (
            ("spring.datasource.url", "jdbc:mysql://wrong:3306/shared"),
            ("spring-datasource-url", "jdbc:mysql://wrong:3306/shared"),
            ("spring_datasource_url", "jdbc:mysql://wrong:3306/shared"),
            ("spring.datasourceUrl", "jdbc:mysql://wrong:3306/shared"),
            ("spring.data.redis.host", "wrong"),
            ("spring-data-redis-port", "6380"),
            ("minio.endpoint", "http://wrong:9000"),
            ("minio-access-key", "wrong"),
            ("spring.profiles.active", "prod"),
        )
        for name, value in aliases:
            with self.subTest(name=name):
                environment = self.target_environment()
                environment[name] = value
                with self.assertRaisesRegex(gate.GateError, "relaxed-binding"):
                    gate.validate_override_channels(environment)
                with self.assertRaisesRegex(gate.GateError, "relaxed-binding"):
                    gate.build_maven_environment(environment, "a" * 40)

        self.assertEqual(
            gate.normalize_environment_property_name("SPRING_DATASOURCE_URL"),
            gate.normalize_environment_property_name("spring.datasource-url"),
        )
        self.assertEqual(
            gate.environment_property_equivalence_key("SPRING_DATASOURCE_URL"),
            gate.environment_property_equivalence_key("spring.datasourceUrl"),
        )

    def test_target_evidence_spec_is_strict(self) -> None:
        mutations = []
        extra = copy.deepcopy(self.spec)
        extra["targetEvidence"]["unexpected"] = True
        mutations.append(extra)
        missing = copy.deepcopy(self.spec)
        del missing["targetEvidence"]["producerSuite"]
        mutations.append(missing)
        wrong_suite = copy.deepcopy(self.spec)
        wrong_suite["targetEvidence"]["producerSuite"] = "unknown.Suite"
        mutations.append(wrong_suite)
        optional = copy.deepcopy(self.spec)
        optional["targetEvidence"]["required"] = False
        mutations.append(optional)
        unsafe = copy.deepcopy(self.spec)
        unsafe["targetEvidence"]["path"] = "../identity.json"
        mutations.append(unsafe)

        for index, broken in enumerate(mutations):
            with self.subTest(index=index):
                path = self.repo / f"broken-target-spec-{index}.json"
                path.write_text(json.dumps(broken), encoding="utf-8")
                with self.assertRaises(gate.GateError):
                    gate.load_spec(path)

    def test_exact_fresh_reports_pass(self) -> None:
        results = gate.validate_suite_reports(self.repo, self.spec, fresh_after_ns=0)
        self.assertEqual(len(results), 7)
        self.assertEqual(sum(result["observedTests"] for result in results), 33)

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

    def test_runtime_identity_exact_schema_and_bindings_pass(self) -> None:
        declared, configured, expected = self.target_contract()
        observed = self.runtime_identity()
        self.assertEqual(
            gate.validate_runtime_identity(
                observed,
                self.spec["targetEvidence"],
                declared,
                configured,
                expected,
            ),
            observed,
        )

    def test_runtime_identity_rejects_extra_missing_and_duplicate_fields(self) -> None:
        declared, configured, expected = self.target_contract()
        extra = self.runtime_identity()
        extra["unexpected"] = True
        missing = self.runtime_identity()
        del missing["redis"]["runId"]
        for observed in (extra, missing):
            with self.subTest(fields=observed.keys()):
                with self.assertRaisesRegex(gate.GateError, "fields are not exact"):
                    gate.validate_runtime_identity(
                        observed,
                        self.spec["targetEvidence"],
                        declared,
                        configured,
                        expected,
                    )
        duplicate = (
            b'{"schemaVersion":1,"schemaVersion":1,'
            b'"producerSuite":"fixture"}'
        )
        with self.assertRaisesRegex(gate.GateError, "duplicate JSON field"):
            gate.strict_json_bytes(duplicate, "duplicate fixture")

        legacy_headers = self.runtime_identity()
        legacy_headers["minio"]["server"] = "MinIO"
        legacy_headers["minio"]["deploymentId"] = (
            "123e4567-e89b-12d3-a456-426614174000"
        )
        with self.assertRaisesRegex(gate.GateError, "fields are not exact"):
            gate.validate_runtime_identity(
                legacy_headers,
                self.spec["targetEvidence"],
                declared,
                configured,
                expected,
            )

    def test_runtime_identity_rejects_candidate_and_run_context_drift(self) -> None:
        declared, configured, expected = self.target_contract()
        mutations = (
            ("candidateSha", "d" * 40),
            ("runContext", "offline:selftest:other"),
        )
        for name, value in mutations:
            with self.subTest(name=name):
                observed = self.runtime_identity()
                observed[name] = value
                with self.assertRaisesRegex(gate.GateError, "mismatch"):
                    gate.validate_runtime_identity(
                        observed,
                        self.spec["targetEvidence"],
                        declared,
                        configured,
                        expected,
                    )

    def test_runtime_identity_rejects_every_service_identity_mismatch(self) -> None:
        declared, configured, expected = self.target_contract()
        mutations = (
            ("mysql", "database", "other_schema"),
            (
                "mysql",
                "serverUuid",
                "223e4567-e89b-12d3-a456-426614174000",
            ),
            ("mysql", "version", "5.7.44"),
            ("redis", "runId", "e" * 40),
            ("redis", "database", 1),
            ("redis", "version", "6.2.0"),
            ("minio", "endpoint", "http://127.0.0.2:9000"),
            ("minio", "bucket", "other-bucket"),
            (
                "minio",
                "identityObject",
                ".phase00-target/identity-other.json",
            ),
            ("minio", "identitySha256", "e" * 64),
            ("minio", "identityNonce", "f" * 64),
            ("minio", "identityIssuedAt", "2020-01-01T00:00:00Z"),
            ("minio", "instanceFingerprintSha256", None),
        )
        for service, field, value in mutations:
            with self.subTest(service=service, field=field):
                observed = self.runtime_identity()
                observed[service][field] = value
                with self.assertRaises(gate.GateError):
                    gate.validate_runtime_identity(
                        observed,
                        self.spec["targetEvidence"],
                        declared,
                        configured,
                        expected,
                    )

    def test_runtime_identity_requires_exact_prewrite_empty_state(self) -> None:
        declared, configured, expected = self.target_contract()
        mutations = (
            ("mysqlTableCountBefore", 1),
            ("redisDatabaseSizeBefore", 1),
            ("minioObjectCountBefore", 0),
            ("minioObjectCountBefore", 2),
            ("minioUnexpectedObjectCountBefore", 1),
            ("redisDatabaseSizeBefore", True),
        )
        for name, value in mutations:
            with self.subTest(name=name, value=value):
                observed = self.runtime_identity()
                observed["freshness"][name] = value
                with self.assertRaisesRegex(gate.GateError, "freshness"):
                    gate.validate_runtime_identity(
                        observed,
                        self.spec["targetEvidence"],
                        declared,
                        configured,
                        expected,
                    )

    def test_preflight_identity_issued_at_is_bound_to_gate_start(self) -> None:
        identity = self.runtime_identity()
        started_ns = time.time_ns()
        gate.validate_preflight_identity_freshness(identity, started_ns)

        stale = copy.deepcopy(identity)
        stale["minio"]["identityIssuedAt"] = "2020-01-01T00:00:00Z"
        with self.assertRaisesRegex(gate.GateError, "outside the allowed"):
            gate.validate_preflight_identity_freshness(stale, started_ns)

        future = copy.deepcopy(identity)
        future_time = (
            gate.dt.datetime.now(gate.dt.timezone.utc)
            + gate.dt.timedelta(minutes=3)
        ).strftime("%Y-%m-%dT%H:%M:%SZ")
        future["minio"]["identityIssuedAt"] = future_time
        with self.assertRaisesRegex(gate.GateError, "outside the allowed"):
            gate.validate_preflight_identity_freshness(future, started_ns)

    def test_runtime_identity_must_exactly_match_preflight_identity(self) -> None:
        preflight = self.runtime_identity()
        preflight["producerSuite"] = self.spec["targetEvidence"][
            "preflightProducer"
        ]
        runtime = self.runtime_identity()
        gate.require_matching_preflight_and_runtime(preflight, runtime)

        runtime["minio"]["instanceFingerprintSha256"] = "0" * 64
        with self.assertRaisesRegex(gate.GateError, "exactly match preflight"):
            gate.require_matching_preflight_and_runtime(preflight, runtime)

    def test_runtime_identity_source_must_be_fresh_regular_and_stable(self) -> None:
        source = self.repo / Path(self.spec["targetEvidence"]["path"])
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            json.dumps(self.runtime_identity(), sort_keys=True),
            encoding="utf-8",
        )
        started_ns = time.time_ns()
        os.utime(source, (1, 1))
        with self.assertRaisesRegex(gate.GateError, "stale"):
            gate.read_regular_json_source(
                self.repo,
                self.spec["targetEvidence"]["path"],
                "runtime identity fixture",
                fresh_after_ns=started_ns,
            )
        source.write_text(
            json.dumps(self.runtime_identity(), sort_keys=True),
            encoding="utf-8",
        )
        observed, content, digest, metadata = gate.read_regular_json_source(
            self.repo,
            self.spec["targetEvidence"]["path"],
            "runtime identity fixture",
            fresh_after_ns=started_ns,
        )
        self.assertEqual(observed, self.runtime_identity())
        self.assertEqual(digest, gate.sha256_bytes(content))
        self.assertEqual(metadata["bytes"], len(content))

    def test_offline_verifier_reparses_archived_runtime_identity(self) -> None:
        candidate = "a" * 40
        declared, configured, expected = self.target_contract(candidate)
        runtime = self.runtime_identity(candidate)
        preflight = copy.deepcopy(runtime)
        preflight["producerSuite"] = self.spec["targetEvidence"][
            "preflightProducer"
        ]
        evidence = self.repo / "runtime-evidence"
        records = {}

        def archive(
            identity: dict[str, object],
            artifact_path: str,
            kind: str,
            source_path: str,
        ) -> tuple[str, int]:
            artifact = evidence / Path(artifact_path)
            artifact.parent.mkdir(parents=True, exist_ok=True)
            content = (
                json.dumps(identity, sort_keys=True, separators=(",", ":"))
                + "\n"
            ).encode("utf-8")
            artifact.write_bytes(content)
            digest = gate.sha256_bytes(content)
            records[artifact_path] = gate.artifact_record(
                evidence,
                artifact,
                kind=kind,
                source_path=source_path,
                source_sha256=digest,
                source_retained=True,
            )
            return digest, len(content)

        preflight_digest, preflight_bytes = archive(
            preflight,
            gate.PREFLIGHT_IDENTITY_ARTIFACT,
            gate.PREFLIGHT_IDENTITY_KIND,
            self.spec["targetEvidence"]["preflightPath"],
        )
        runtime_digest, runtime_bytes = archive(
            runtime,
            gate.TARGET_IDENTITY_ARTIFACT,
            gate.TARGET_IDENTITY_KIND,
            self.spec["targetEvidence"]["path"],
        )
        started_ns = time.time_ns()
        targets = {
            "declared": declared,
            "configured": configured,
            "expected": expected,
            "preflight": preflight,
            "runtime": runtime,
            "evidence": {
                "preflight": {
                    "sourcePath": self.spec["targetEvidence"]["preflightPath"],
                    "artifactPath": gate.PREFLIGHT_IDENTITY_ARTIFACT,
                    "sourceSha256": preflight_digest,
                    "sourceMtimeNs": started_ns,
                    "sourceBytes": preflight_bytes,
                },
                "runtime": {
                    "sourcePath": self.spec["targetEvidence"]["path"],
                    "artifactPath": gate.TARGET_IDENTITY_ARTIFACT,
                    "sourceSha256": runtime_digest,
                    "sourceMtimeNs": started_ns,
                    "sourceBytes": runtime_bytes,
                },
            },
            "constraints": gate.validate_override_channels(
                self.target_environment()
            ),
        }
        evidence_snapshot = gate.capture_evidence_snapshot(evidence)
        self.assertEqual(
            gate.verify_runtime_target_artifact(
                evidence=evidence_snapshot,
                targets_value=targets,
                target_spec=self.spec["targetEvidence"],
                expected_candidate=candidate,
                records=records,
                preflight_started_ns=started_ns,
                runtime_started_ns=started_ns,
            ),
            runtime,
        )

        bad_hash = copy.deepcopy(targets)
        bad_hash["evidence"]["runtime"]["sourceSha256"] = "0" * 64
        with self.assertRaises(gate.GateError):
            gate.verify_runtime_target_artifact(
                evidence=evidence_snapshot,
                targets_value=bad_hash,
                target_spec=self.spec["targetEvidence"],
                expected_candidate=candidate,
                records=records,
                preflight_started_ns=started_ns,
                runtime_started_ns=started_ns,
            )

        bad_runtime = copy.deepcopy(targets)
        bad_runtime["runtime"]["mysql"]["database"] = "other_schema"
        with self.assertRaisesRegex(gate.GateError, "differs from the archived"):
            gate.verify_runtime_target_artifact(
                evidence=evidence_snapshot,
                targets_value=bad_runtime,
                target_spec=self.spec["targetEvidence"],
                expected_candidate=candidate,
                records=records,
                preflight_started_ns=started_ns,
                runtime_started_ns=started_ns,
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
        target_identity = self.repo / Path(self.spec["targetEvidence"]["path"])
        target_identity.parent.mkdir(parents=True, exist_ok=True)
        target_identity.write_text("stale identity", encoding="utf-8")
        preflight_identity = self.repo / Path(
            self.spec["targetEvidence"]["preflightPath"]
        )
        preflight_identity.write_text("stale preflight", encoding="utf-8")

        removed = gate.remove_declared_reports(self.repo, self.spec)

        self.assertIn(self.spec["suites"][0]["report"], removed)
        self.assertFalse(first_report.exists())
        self.assertFalse(first_text.exists())
        self.assertFalse(summary.exists())
        self.assertFalse(target_identity.exists())
        self.assertFalse(preflight_identity.exists())
        self.assertIn(self.spec["targetEvidence"]["path"], removed)
        self.assertIn(self.spec["targetEvidence"]["preflightPath"], removed)
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

    def test_clean_candidate_head_stays_stable_across_maven_window(self) -> None:
        candidate = "a" * 40
        with patch.object(gate, "git_head", return_value=candidate):
            self.assertEqual(
                gate.bind_end_candidate(self.repo, candidate, candidate),
                candidate,
            )
        gate.verify_candidate_attestation(
            {
                "candidateSha": candidate,
                "expectedCandidateSha": candidate,
                "source": {
                    "startHead": candidate,
                    "afterPreflightHead": candidate,
                    "endHead": candidate,
                },
            },
            candidate,
        )

    def test_source_git_commands_remove_all_git_environment_redirects(self) -> None:
        with patch.dict(
            os.environ,
            {
                "PATH": "fixture-path",
                "GIT_DIR": "outside",
                "GIT_WORK_TREE": "outside",
                "GIT_CONFIG_COUNT": "1",
                "GIT_TRACE": "1",
            },
            clear=True,
        ):
            isolated = gate.isolated_git_environment()
        self.assertEqual(isolated["PATH"], "fixture-path")
        self.assertEqual(isolated["GIT_NO_REPLACE_OBJECTS"], "1")
        self.assertFalse(
            any(
                name.startswith("GIT_")
                and name != "GIT_NO_REPLACE_OBJECTS"
                for name in isolated
            )
        )

    def test_clean_candidate_head_drift_across_maven_window_fails(self) -> None:
        start = "a" * 40
        end = "b" * 40
        with patch.object(gate, "git_head", return_value=end):
            with self.assertRaisesRegex(gate.GateError, "HEAD drifted"):
                gate.bind_end_candidate(self.repo, start, start)
        with self.assertRaisesRegex(gate.GateError, "HEAD drifted"):
            gate.verify_candidate_attestation(
                {
                    "candidateSha": start,
                    "expectedCandidateSha": start,
                    "source": {
                        "startHead": start,
                        "afterPreflightHead": start,
                        "endHead": end,
                    },
                },
                start,
            )

    def test_immutable_snapshot_uses_exact_commit_and_removes_git_metadata(
        self,
    ) -> None:
        source_repo = self.repo / "snapshot-source"
        source_repo.mkdir()

        def git(*arguments: str) -> str:
            completed = subprocess.run(
                ["git", *arguments],
                cwd=source_repo,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
                text=True,
            )
            self.assertEqual(
                completed.returncode,
                0,
                completed.stderr or completed.stdout,
            )
            return completed.stdout.strip()

        git("init")
        tracked = source_repo / "tracked.txt"
        tracked.write_text("candidate A\n", encoding="utf-8")
        git("add", "tracked.txt")
        git(
            "-c",
            "user.name=Phase00 Fixture",
            "-c",
            "user.email=phase00@example.invalid",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "-m",
            "candidate A",
        )
        candidate_a = git("rev-parse", "HEAD")
        tree_a = git("rev-parse", f"{candidate_a}^{{tree}}")

        tracked.write_text("candidate B\n", encoding="utf-8")
        git("add", "tracked.txt")
        git(
            "-c",
            "user.name=Phase00 Fixture",
            "-c",
            "user.email=phase00@example.invalid",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "-m",
            "candidate B",
        )
        self.assertNotEqual(git("rev-parse", "HEAD"), candidate_a)
        info_attributes = source_repo / ".git" / "info" / "attributes"
        info_attributes.write_text(
            "tracked.txt export-ignore export-subst\n",
            encoding="utf-8",
        )

        snapshot_path: Path | None = None
        with gate.immutable_candidate_snapshot(
            source_repo,
            candidate_a,
        ) as snapshot:
            snapshot_path = snapshot.root
            self.assertEqual(snapshot.candidate_sha, candidate_a)
            self.assertEqual(snapshot.tree_sha, tree_a)
            entries = gate.git_tree_entries(source_repo, candidate_a)
            self.assertEqual(
                snapshot.manifest_sha256,
                gate.git_tree_manifest_sha256(entries),
            )
            self.assertEqual(snapshot.file_count, len(entries))
            self.assertEqual(
                (snapshot.root / "tracked.txt").read_text(encoding="utf-8"),
                "candidate A\n",
            )
            self.assertFalse((snapshot.root / ".git").exists())
        self.assertIsNotNone(snapshot_path)
        self.assertFalse(snapshot_path.exists())

    def test_candidate_blob_reader_ignores_weakened_live_worktree_spec(
        self,
    ) -> None:
        source_repo = self.repo / "candidate-spec-source"
        source_repo.mkdir()

        def git(*arguments: str) -> str:
            completed = subprocess.run(
                ["git", *arguments],
                cwd=source_repo,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
                text=True,
            )
            self.assertEqual(
                completed.returncode,
                0,
                completed.stderr or completed.stdout,
            )
            return completed.stdout.strip()

        git("init")
        spec_path = source_repo / "scripts" / "phase00_ci_gate_spec.json"
        spec_path.parent.mkdir(parents=True)
        candidate_spec = gate.DEFAULT_SPEC.read_bytes()
        spec_path.write_bytes(candidate_spec)
        git("add", "scripts/phase00_ci_gate_spec.json")
        git(
            "-c",
            "user.name=Phase00 Fixture",
            "-c",
            "user.email=phase00@example.invalid",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "-m",
            "candidate gate spec",
        )
        candidate = git("rev-parse", "HEAD")

        weakened = json.loads(candidate_spec.decode("utf-8"))
        weakened["gateId"] = str(weakened["gateId"]) + "-weakened"
        spec_path.write_text(json.dumps(weakened), encoding="utf-8")

        observed = gate.read_candidate_git_blob(
            source_repo,
            candidate,
            "scripts/phase00_ci_gate_spec.json",
            "candidate gate spec",
        )
        self.assertEqual(observed, candidate_spec)
        self.assertNotEqual(observed, spec_path.read_bytes())

    def test_candidate_git_tree_rejects_links_and_unsafe_windows_paths(
        self,
    ) -> None:
        object_sha = b"a" * 40
        regular = b"100644 blob " + object_sha + b"\tmodule/pom.xml\0"
        self.assertEqual(
            gate.parse_git_tree_entries(regular),
            [
                gate.GitTreeEntry(
                    "100644",
                    "a" * 40,
                    gate.PurePosixPath("module/pom.xml"),
                )
            ],
        )
        for name, mode in (
            ("../escape", "100644"),
            (r"module\escape", "100644"),
            ("module/report.xml:stream", "100644"),
            ("module/CON.txt", "100644"),
            ("module/CON .txt", "100644"),
            ("module/CONIN$.txt", "100644"),
            ("module/COM¹.txt", "100644"),
            ("module/link", "120000"),
        ):
            with self.subTest(name=name):
                with self.assertRaises(gate.GateError):
                    gate.parse_git_tree_entries(
                        mode.encode("ascii")
                        + b" blob "
                        + object_sha
                        + b"\t"
                        + name.encode("utf-8")
                        + b"\0"
                    )
        with self.assertRaisesRegex(gate.GateError, "case-colliding"):
            gate.parse_git_tree_entries(
                b"100644 blob "
                + object_sha
                + b"\tModule/pom.xml\0"
                + b"100644 blob "
                + object_sha
                + b"\tmodule/POM.xml\0"
            )
        with self.assertRaisesRegex(gate.GateError, "case-colliding"):
            gate.parse_git_tree_entries(
                b"100644 blob "
                + object_sha
                + b"\tModule/first.txt\0"
                + b"100644 blob "
                + object_sha
                + b"\tmodule/second.txt\0"
            )

    def test_snapshot_attestation_rebinds_candidate_tree_and_spec(self) -> None:
        candidate = "a" * 40
        tree = "b" * 40
        spec_sha = "c" * 64
        entries = self.mocked_git_tree_entries()
        manifest_sha = gate.git_tree_manifest_sha256(entries)
        source = {
            "snapshotMode": "git-object-tree",
            "snapshotCandidateSha": candidate,
            "snapshotTreeSha": tree,
            "snapshotManifestSha256": manifest_sha,
            "snapshotFileCount": len(entries),
            "snapshotSpecSha256": spec_sha,
            "snapshotContainsGitMetadata": False,
        }
        with (
            patch.object(gate, "git_tree_sha", return_value=tree),
            patch.object(gate, "git_tree_entries", return_value=entries),
        ):
            self.assertEqual(
                gate.verify_source_snapshot_attestation(
                    source,
                    self.repo,
                    candidate,
                    spec_sha,
                ),
                {
                    "mode": "git-object-tree",
                    "candidateSha": candidate,
                    "treeSha": tree,
                    "manifestSha256": manifest_sha,
                    "fileCount": len(entries),
                },
            )
            for mutated in (
                {**source, "snapshotTreeSha": "e" * 40},
                {**source, "snapshotManifestSha256": "e" * 64},
                {**source, "snapshotFileCount": len(entries) + 1},
                {**source, "snapshotSpecSha256": "e" * 64},
                {**source, "snapshotContainsGitMetadata": True},
            ):
                with self.subTest(mutated=mutated):
                    with self.assertRaises(gate.GateError):
                        gate.verify_source_snapshot_attestation(
                            mutated,
                            self.repo,
                            candidate,
                            spec_sha,
                        )

    def test_temp_cleanup_never_chmods_link_or_reparse_entries(self) -> None:
        temporary = self.repo / "cleanup-reparse-fixture"
        temporary.mkdir()
        linked = temporary / "outside-link"
        linked.write_text("fixture", encoding="utf-8")
        regular = temporary / "regular"
        regular.write_text("fixture", encoding="utf-8")
        original_detector = gate.path_is_link_or_reparse

        def fake_detector(path: Path) -> bool:
            return path == linked or original_detector(path)

        with (
            patch.object(
                gate,
                "path_is_link_or_reparse",
                side_effect=fake_detector,
            ),
            patch.object(gate.os, "chmod", wraps=gate.os.chmod) as chmod,
        ):
            gate.remove_directory_tree(temporary)
        self.assertFalse(temporary.exists())
        chmod_paths = [Path(call.args[0]) for call in chmod.call_args_list]
        self.assertNotIn(linked, chmod_paths)

        replaced_root = self.repo / "cleanup-replaced-root"
        replaced_root.mkdir()
        with patch.object(
            gate,
            "path_is_link_or_reparse",
            side_effect=lambda path: path == replaced_root,
        ):
            with self.assertRaisesRegex(gate.GateError, "changed into a link"):
                gate.remove_directory_tree(replaced_root)
        self.assertTrue(replaced_root.exists())

    def test_evidence_paths_reject_windows_and_ambiguous_segments(self) -> None:
        invalid_paths = (
            "",
            ".",
            "./manifest.json",
            "../escape.xml",
            "logs/../escape.xml",
            "logs/./report.xml",
            "logs//report.xml",
            "logs/",
            "/absolute/report.xml",
            "//server/share/report.xml",
            r"..\escape.xml",
            r"C:\outside\report.xml",
            "C:/outside/report.xml",
            r"\\server\share\report.xml",
            r"xml\report.xml",
            r"xml/..\report.xml",
            "xml/report.xml:stream",
            "xml/CON.txt",
            "xml/COM1 .log",
            "xml/report.xml.",
            "xml/report.xml ",
        )
        for value in invalid_paths:
            with self.subTest(value=value):
                with self.assertRaises(gate.GateError):
                    gate.safe_relative_path(value, "fixture evidence path")
        self.assertEqual(
            gate.safe_relative_path("xml/report.xml", "fixture evidence path"),
            gate.PurePosixPath("xml/report.xml"),
        )

    def test_windows_validated_reader_is_issued_only_after_boundary_validation(
        self,
    ) -> None:
        root = gate._normalise_windows_handle_path(
            r"C:\safe\evidence"
        )
        inside = gate._normalise_windows_handle_path(
            r"C:\safe\evidence\report.xml"
        )
        events: list[str] = []

        def inspect_handle(
            handle: int,
        ) -> tuple[int, tuple[int, int], int, int]:
            self.assertEqual(handle, 101)
            events.append("inspect")
            return 0, (11, 22), 33, 44

        def get_final_path(handle: int) -> str:
            self.assertEqual(handle, 101)
            events.append("final-path")
            return inside

        def validate_boundary(**arguments: object) -> None:
            events.append("boundary")
            gate.validate_windows_evidence_handle_boundary(**arguments)

        def reader(
            validated: gate.ValidatedWindowsEvidenceHandle,
            opened_final: str,
        ) -> str:
            events.append("reader")
            self.assertEqual(
                validated,
                gate.ValidatedWindowsEvidenceHandle(
                    handle=101,
                    attributes=0,
                    file_id=(11, 22),
                    size=33,
                    mtime_ns=44,
                ),
            )
            self.assertEqual(opened_final, inside)
            return "read-result"

        self.assertEqual(
            gate.consume_validated_windows_evidence_handle(
                root_final=root,
                handle=101,
                expected_directory=False,
                label="fixture",
                inspect_handle=inspect_handle,
                get_final_path=get_final_path,
                reader=reader,
                boundary_validator=validate_boundary,
            ),
            "read-result",
        )
        self.assertEqual(
            events,
            ["inspect", "final-path", "boundary", "reader"],
        )

        failures = (
            (
                "outside",
                0,
                gate._normalise_windows_handle_path(
                    r"C:\outside\secret.xml"
                ),
            ),
            ("reparse", 0x400, inside),
        )
        for message, attributes, opened_final in failures:
            with self.subTest(failure=message):
                failure_events: list[str] = []

                def inspect_failure(
                    _handle: int,
                ) -> tuple[int, tuple[int, int], int, int]:
                    failure_events.append("inspect")
                    return attributes, (11, 22), 33, 44

                def final_failure(_handle: int) -> str:
                    failure_events.append("final-path")
                    return opened_final

                def validate_failure(**arguments: object) -> None:
                    failure_events.append("boundary")
                    gate.validate_windows_evidence_handle_boundary(
                        **arguments
                    )

                def forbidden_reader(
                    _validated: gate.ValidatedWindowsEvidenceHandle,
                    _opened_final: str,
                ) -> None:
                    failure_events.append("reader")

                with self.assertRaisesRegex(gate.GateError, message):
                    gate.consume_validated_windows_evidence_handle(
                        root_final=root,
                        handle=101,
                        expected_directory=False,
                        label="fixture",
                        inspect_handle=inspect_failure,
                        get_final_path=final_failure,
                        reader=forbidden_reader,
                        boundary_validator=validate_failure,
                    )
                self.assertEqual(
                    failure_events,
                    ["inspect", "final-path", "boundary"],
                )

    def test_windows_ancestor_chain_opens_all_and_closes_reverse(self) -> None:
        root = r"C:\safe\evidence\bundle"
        paths = gate.windows_evidence_ancestor_paths(root)
        events: list[tuple[object, ...]] = []
        path_by_handle: dict[int, str] = {}

        def open_directory(path: str) -> int:
            handle = len(path_by_handle) + 1
            path_by_handle[handle] = path
            events.append(("open", path, handle))
            return handle

        def get_attributes(handle: int) -> int:
            events.append(("attributes", handle))
            return 0x10

        def get_final_path(handle: int) -> str:
            events.append(("final-path", handle))
            return gate._normalise_windows_handle_path(
                path_by_handle[handle]
            )

        def close_handle(handle: int) -> None:
            events.append(("close", handle))

        with gate.locked_windows_evidence_ancestor_chain(
            root,
            open_directory=open_directory,
            get_attributes=get_attributes,
            get_final_path=get_final_path,
            close_handle=close_handle,
        ) as chain:
            self.assertEqual(
                tuple(handle for handle, _final in chain),
                (1, 2, 3, 4),
            )
            self.assertEqual(
                tuple(final for _handle, final in chain),
                tuple(
                    gate._normalise_windows_handle_path(path)
                    for path in paths
                ),
            )
            self.assertFalse(
                any(event[0] == "close" for event in events)
            )
            events.append(("consumer",))

        expected_events: list[tuple[object, ...]] = []
        for handle, path in enumerate(paths, start=1):
            expected_events.extend(
                [
                    ("open", path, handle),
                    ("attributes", handle),
                    ("final-path", handle),
                ]
            )
        expected_events.extend(
            [
                ("consumer",),
                ("close", 4),
                ("close", 3),
                ("close", 2),
                ("close", 1),
            ]
        )
        self.assertEqual(events, expected_events)

    def test_windows_ancestor_chain_closes_reverse_on_consumer_error(
        self,
    ) -> None:
        root = r"C:\safe\evidence\bundle"
        path_by_handle: dict[int, str] = {}
        closes: list[int] = []

        def open_directory(path: str) -> int:
            handle = len(path_by_handle) + 1
            path_by_handle[handle] = path
            return handle

        with self.assertRaisesRegex(RuntimeError, "consumer failed"):
            with gate.locked_windows_evidence_ancestor_chain(
                root,
                open_directory=open_directory,
                get_attributes=lambda _handle: 0x10,
                get_final_path=lambda handle: (
                    gate._normalise_windows_handle_path(
                        path_by_handle[handle]
                    )
                ),
                close_handle=closes.append,
            ):
                raise RuntimeError("consumer failed")

        self.assertEqual(closes, [4, 3, 2, 1])

    def test_windows_capture_contract_locks_ancestors_and_handle_stability(
        self,
    ) -> None:
        self.assertEqual(
            gate.windows_evidence_ancestor_paths(
                r"C:\safe\evidence\bundle"
            ),
            (
                "C:\\",
                r"C:\safe",
                r"C:\safe\evidence",
                r"C:\safe\evidence\bundle",
            ),
        )
        directory_access, directory_share, directory_flags = (
            gate.windows_evidence_open_contract(directory=True)
        )
        file_access, file_share, file_flags = (
            gate.windows_evidence_open_contract(directory=False)
        )
        self.assertTrue(
            directory_access & gate.WINDOWS_FILE_LIST_DIRECTORY
        )
        self.assertEqual(
            directory_share,
            gate.WINDOWS_FILE_SHARE_READ
            | gate.WINDOWS_FILE_SHARE_WRITE,
        )
        self.assertEqual(file_share, gate.WINDOWS_FILE_SHARE_READ)
        self.assertFalse(file_share & 0x4)
        self.assertTrue(
            directory_flags & gate.WINDOWS_FILE_FLAG_OPEN_REPARSE_POINT
        )
        self.assertTrue(
            directory_flags & gate.WINDOWS_FILE_FLAG_BACKUP_SEMANTICS
        )
        self.assertTrue(file_access & gate.WINDOWS_GENERIC_READ)
        self.assertTrue(
            file_flags & gate.WINDOWS_FILE_FLAG_OPEN_REPARSE_POINT
        )
        before = gate.ValidatedWindowsEvidenceHandle(
            handle=1,
            attributes=0,
            file_id=(11, 22),
            size=33,
            mtime_ns=44,
        )
        for field, value in (
            ("after_attributes", 0x400),
            ("after_file_id", (11, 23)),
            ("after_size", 34),
            ("after_mtime_ns", 45),
        ):
            arguments = {
                "after_attributes": 0,
                "after_file_id": (11, 22),
                "after_size": 33,
                "after_mtime_ns": 44,
                "label": "fixture",
            }
            arguments[field] = value
            with self.subTest(drift=field):
                with self.assertRaisesRegex(gate.GateError, "changed"):
                    gate.validate_windows_evidence_handle_stability(
                        before,
                        **arguments,
                    )

    def test_evidence_consumers_use_one_snapshot_without_path_reopen(self) -> None:
        evidence = self.repo / "snapshot-evidence"
        evidence.mkdir()
        artifact = evidence / "report.xml"
        manifest = evidence / "manifest.json"
        artifact.write_text("<testsuite/>", encoding="utf-8")
        manifest.write_text("{}", encoding="utf-8")
        records = [
            {
                "path": "report.xml",
                "kind": "fixture",
                "sha256": gate.sha256_file(artifact),
                "bytes": artifact.stat().st_size,
            }
        ]
        gate.write_checksums(evidence, [artifact, manifest])
        snapshot = gate.capture_evidence_snapshot(evidence)
        with (
            patch.object(
                Path,
                "read_bytes",
                side_effect=AssertionError("path reopened"),
            ),
            patch.object(
                Path,
                "read_text",
                side_effect=AssertionError("path reopened"),
            ),
            patch.object(
                gate,
                "sha256_file",
                side_effect=AssertionError("path rehashed"),
            ),
        ):
            self.assertEqual(
                set(gate.verify_artifact_records(snapshot, records)),
                {"report.xml"},
            )
            gate.verify_checksums_file(
                snapshot,
                expected_paths={"report.xml", "manifest.json"},
            )

    def test_suite_and_support_source_provenance_is_exact(self) -> None:
        source_sha256 = "a" * 64
        suite_record = {
            "kind": "sanitised-surefire-failsafe-xml",
            "sourcePath": "module/target/surefire-reports/TEST-Suite.xml",
            "sourceSha256": source_sha256,
            "sourceRetained": False,
        }
        self.assertEqual(
            gate.require_source_provenance(
                suite_record,
                expected_source_path=suite_record["sourcePath"],
                expected_kind=suite_record["kind"],
                expected_source_sha256=source_sha256,
                source_retained=False,
                label="suite fixture",
            ),
            source_sha256,
        )

        invalid_records = (
            {**suite_record, "sourcePath": "other/report.xml"},
            {key: value for key, value in suite_record.items() if key != "sourcePath"},
            {
                key: value
                for key, value in suite_record.items()
                if key != "sourceSha256"
            },
            {**suite_record, "sourceRetained": True},
        )
        for record in invalid_records:
            with self.subTest(record=record):
                with self.assertRaisesRegex(
                    gate.GateError,
                    "source provenance is incomplete or mismatched",
                ):
                    gate.require_source_provenance(
                        record,
                        expected_source_path=suite_record["sourcePath"],
                        expected_kind=suite_record["kind"],
                        expected_source_sha256=source_sha256,
                        source_retained=False,
                        label="suite fixture",
                    )

    def test_archived_gate_spec_hash_drift_fails_provenance(self) -> None:
        with self.assertRaisesRegex(
            gate.GateError,
            "source SHA-256 does not match its authority",
        ):
            gate.require_source_provenance(
                {
                    "kind": "gate-spec",
                    "sourcePath": "scripts/phase00_ci_gate_spec.json",
                    "sourceSha256": "a" * 64,
                    "sourceRetained": True,
                },
                expected_source_path="scripts/phase00_ci_gate_spec.json",
                expected_kind="gate-spec",
                expected_source_sha256="b" * 64,
                source_retained=True,
                label="archived gate spec fixture",
            )

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

    def test_default_maven_executable_is_shell_free_and_platform_safe(self) -> None:
        resolver_calls: list[str] = []

        def resolve_windows(command: str) -> str | None:
            resolver_calls.append(command)
            return r"C:\tools\apache-maven\bin\mvn.cmd"

        self.assertEqual(
            gate.default_maven_executable(
                {"MVN": r"  C:\custom maven\mvn.cmd  "},
                "nt",
                resolve_windows,
            ),
            r"C:\custom maven\mvn.cmd",
        )
        self.assertEqual(resolver_calls, [])

        self.assertEqual(
            gate.default_maven_executable(
                {"MVN": "   "},
                "nt",
                resolve_windows,
            ),
            r"C:\tools\apache-maven\bin\mvn.cmd",
        )
        self.assertEqual(resolver_calls, ["mvn.cmd"])

        self.assertEqual(
            gate.default_maven_executable({}, "nt", lambda _command: None),
            "mvn.cmd",
        )
        self.assertEqual(
            gate.default_maven_executable(
                {},
                "posix",
                lambda _command: self.fail("POSIX must not resolve mvn.cmd"),
            ),
            "mvn",
        )

        with patch.object(
            gate,
            "default_maven_executable",
            return_value=r"C:\resolved\mvn.cmd",
        ):
            parsed = gate.build_parser().parse_args(
                ["run", "--evidence-dir", "evidence"]
            )
        self.assertEqual(parsed.maven, r"C:\resolved\mvn.cmd")

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

    def test_self_verification_failure_downgrades_private_staging_to_fail(
        self,
    ) -> None:
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
                "command": gate.formal_maven_command("mvn"),
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
            redactor=gate.SecretRedactor({}),
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

    def test_self_verification_oserror_never_leaves_staging_pass(self) -> None:
        evidence = self.repo / "self-verify-oserror"
        evidence.mkdir()
        candidate = "a" * 40
        manifest = {
            "schemaVersion": 1,
            "gateId": self.spec["gateId"],
            "status": "PASS",
            "candidateSha": candidate,
            "expectedCandidateSha": candidate,
            "artifacts": [],
            "errors": [],
            "security": {},
        }
        def verifier_fails_before_disk_publish(
            snapshot: gate.EvidenceSnapshot,
            _spec_path: Path,
            _expected_candidate: str,
        ) -> dict[str, object]:
            self.assertIn("manifest.json", snapshot.files)
            self.assertEqual(
                json.loads(
                    snapshot.files["manifest.json"].content.decode("utf-8")
                )["status"],
                "PASS",
            )
            self.assertFalse((evidence / "manifest.json").exists())
            self.assertFalse((evidence / "SHA256SUMS").exists())
            raise PermissionError("locked")

        with patch.object(
            gate,
            "_verify_evidence_snapshot",
            side_effect=verifier_fails_before_disk_publish,
        ):
            verified, error = gate.self_verify_or_downgrade(
                evidence_dir=evidence,
                spec_path=gate.DEFAULT_SPEC,
                expected_candidate=candidate,
                manifest=manifest,
                redactor=gate.SecretRedactor({}),
            )
        self.assertFalse(verified)
        self.assertIn("PermissionError", error)
        persisted = json.loads(
            (evidence / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(persisted["status"], "FAIL")
        self.assertFalse(
            any(
                snapshot.name == "manifest.json"
                and json.loads(snapshot.read_text(encoding="utf-8")).get("status")
                == "PASS"
                for snapshot in evidence.iterdir()
                if snapshot.is_file() and snapshot.name == "manifest.json"
            )
        )

    def test_failed_fail_write_never_creates_provisional_pass(self) -> None:
        evidence = self.repo / "self-verify-double-failure"
        evidence.mkdir()
        candidate = "a" * 40
        manifest = {
            "schemaVersion": 1,
            "gateId": self.spec["gateId"],
            "status": "PASS",
            "candidateSha": candidate,
            "expectedCandidateSha": candidate,
            "artifacts": [],
            "errors": [],
            "security": {},
        }
        with (
            patch.object(
                gate,
                "_verify_evidence_snapshot",
                side_effect=OSError("artifact disappeared"),
            ),
            patch.object(
                gate,
                "persist_manifest",
                side_effect=PermissionError("cannot write FAIL"),
            ),
            patch.object(
                Path,
                "unlink",
                side_effect=PermissionError("cannot unlink"),
            ),
        ):
            verified, _error = gate.self_verify_or_downgrade(
                evidence_dir=evidence,
                spec_path=gate.DEFAULT_SPEC,
                expected_candidate=candidate,
                manifest=manifest,
                redactor=gate.SecretRedactor({}),
            )
        self.assertFalse(verified)
        self.assertFalse((evidence / "manifest.json").exists())
        self.assertFalse((evidence / "SHA256SUMS").exists())

    def test_preverified_pass_writes_manifest_last(self) -> None:
        evidence = self.repo / "preverified-write-order"
        evidence.mkdir()
        prepared = gate.PreparedEvidenceManifest(
            manifest_content=b'{"status":"PASS"}\n',
            checksum_content=b"a" * 64 + b"  manifest.json\n",
            virtual_snapshot=gate.EvidenceSnapshot(evidence, {}),
            verified_manifest={"status": "PASS"},
        )
        original_atomic_write = gate.atomic_write_bytes
        writes: list[str] = []

        def fail_manifest_write(path: Path, content: bytes) -> None:
            writes.append(path.name)
            if path.name == "manifest.json":
                raise PermissionError("manifest replace denied")
            original_atomic_write(path, content)

        with patch.object(
            gate,
            "atomic_write_bytes",
            side_effect=fail_manifest_write,
        ):
            with self.assertRaises(PermissionError):
                gate.persist_preverified_pass(evidence, prepared)
        self.assertEqual(writes, ["SHA256SUMS", "manifest.json"])
        self.assertTrue((evidence / "SHA256SUMS").exists())
        self.assertFalse((evidence / "manifest.json").exists())

    def test_atomic_publish_failure_never_exposes_final_directory(self) -> None:
        staging = self.repo / "publish-ready"
        staging.mkdir()
        (staging / "manifest.json").write_text(
            '{"status":"PASS"}',
            encoding="utf-8",
        )
        final = self.repo / "publish-final"
        with patch.object(
            gate.os,
            "rename",
            side_effect=PermissionError("publish denied"),
        ):
            with self.assertRaisesRegex(gate.GateError, "atomically publish"):
                gate.publish_ready_evidence(
                    staging,
                    final,
                )
        self.assertFalse(final.exists())
        self.assertTrue(staging.exists())

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

    def test_identity_artifact_secret_scan_precedes_no_secret_assertion(self) -> None:
        evidence = self.repo / "identity-secret-scan"
        evidence.mkdir()
        identity = evidence / "target" / "runtime-identity.json"
        identity.parent.mkdir()
        identity.write_text(
            '{"instanceFingerprintSha256":"password=reflected-secret"}',
            encoding="utf-8",
        )
        manifest = {
            "status": "PASS",
            "artifacts": [
                gate.artifact_record(
                    evidence,
                    identity,
                    kind=gate.TARGET_IDENTITY_KIND,
                )
            ],
            "security": {},
        }
        with self.assertRaisesRegex(gate.GateError, "credential-shaped"):
            gate.finalize_manifest_security(
                evidence,
                manifest,
                gate.SecretRedactor({}),
            )
        self.assertNotEqual(
            manifest.get("security", {}).get("containsSecrets"),
            False,
        )
        self.assertFalse((evidence / "manifest.json").exists())

    def test_manifest_secret_scan_precedes_security_assertion(self) -> None:
        evidence = self.repo / "manifest-secret-scan"
        evidence.mkdir()
        manifest = {
            "status": "PASS",
            "artifacts": [],
            "errors": ["known-manifest-secret"],
            "security": {},
        }
        with self.assertRaisesRegex(gate.GateError, "known secret"):
            gate.finalize_manifest_security(
                evidence,
                manifest,
                gate.SecretRedactor(
                    {"PHASE00_FIXTURE_SECRET": "known-manifest-secret"}
                ),
            )
        self.assertEqual(manifest["security"], {})
        self.assertFalse((evidence / "manifest.json").exists())

    def test_spec_rejects_duplicate_declared_testcase(self) -> None:
        broken = json.loads(json.dumps(self.spec))
        broken["suites"][0]["testcases"][1] = broken["suites"][0]["testcases"][0]
        path = self.repo / "broken-spec.json"
        path.write_text(json.dumps(broken), encoding="utf-8")
        with self.assertRaisesRegex(gate.GateError, "duplicate names"):
            gate.load_spec(path)

    def test_manifest_reader_rejects_ambiguous_or_oversized_json(self) -> None:
        def envelope() -> dict[str, object]:
            return {
                "schemaVersion": 1,
                "gateId": "fixture",
                "status": "PASS",
                "candidateSha": "a" * 40,
                "expectedCandidateSha": "a" * 40,
                "gateSpec": {},
                "source": {},
                "preflight": {},
                "run": {},
                "tools": {},
                "targets": {},
                "suites": [],
                "supportingArtifacts": [],
                "artifacts": [],
                "errors": [],
                "security": {},
            }

        duplicate_dir = self.repo / "manifest-duplicate"
        duplicate_dir.mkdir()
        (duplicate_dir / "manifest.json").write_bytes(
            b'{"schemaVersion":1,"schemaVersion":1}'
        )
        with self.assertRaisesRegex(gate.GateError, "duplicate JSON field"):
            gate.read_manifest(duplicate_dir)

        oversized_dir = self.repo / "manifest-oversized"
        oversized_dir.mkdir()
        (oversized_dir / "manifest.json").write_bytes(
            b" " * (gate.MAX_MANIFEST_BYTES + 1)
        )
        with self.assertRaisesRegex(gate.GateError, "exceeds"):
            gate.read_manifest(oversized_dir)

        extra_dir = self.repo / "manifest-extra"
        extra_dir.mkdir()
        extra = envelope()
        extra["unexpected"] = True
        (extra_dir / "manifest.json").write_text(
            json.dumps(extra),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(gate.GateError, "unexpected"):
            gate.read_manifest(extra_dir)

        boolean_dir = self.repo / "manifest-boolean-schema"
        boolean_dir.mkdir()
        boolean_schema = envelope()
        boolean_schema["schemaVersion"] = True
        (boolean_dir / "manifest.json").write_text(
            json.dumps(boolean_schema),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(gate.GateError, "schemaVersion"):
            gate.read_manifest(boolean_dir)

    def test_manifest_indexes_reject_duplicate_suite_and_support_ids(self) -> None:
        for label in ("manifest suites", "manifest supportingArtifacts"):
            with self.subTest(label=label):
                with self.assertRaisesRegex(gate.GateError, "duplicate id"):
                    gate.index_unique_manifest_objects(
                        [{"id": "duplicate"}, {"id": "duplicate"}],
                        label,
                    )

    def test_artifact_records_reject_ambiguous_shapes(self) -> None:
        evidence = self.repo / "artifact-records"
        evidence.mkdir()
        artifact = evidence / "artifact.txt"
        artifact.write_text("fixture", encoding="utf-8")
        base = {
            "path": "artifact.txt",
            "kind": "fixture",
            "sha256": gate.sha256_file(artifact),
            "bytes": artifact.stat().st_size,
        }
        self.assertEqual(
            set(gate.verify_artifact_records(evidence, [base])),
            {"artifact.txt"},
        )
        for mutated, message in (
            ({**base, "unexpected": True}, "unexpected fields"),
            ({**base, "bytes": True}, "bytes must be non-negative"),
            ({**base, "sourcePath": "source.txt"}, "incomplete source"),
        ):
            with self.subTest(message=message):
                with self.assertRaisesRegex(gate.GateError, message):
                    gate.verify_artifact_records(evidence, [mutated])
        with self.assertRaisesRegex(gate.GateError, "duplicate artifact"):
            gate.verify_artifact_records(evidence, [base, dict(base)])

    def test_spec_rejects_ambiguous_schema_and_duplicate_support_id(self) -> None:
        cases: list[tuple[str, dict[str, object], str]] = []

        boolean_schema = copy.deepcopy(self.spec)
        boolean_schema["schemaVersion"] = True
        cases.append(("boolean", boolean_schema, "schemaVersion"))

        nested_extra = copy.deepcopy(self.spec)
        nested_extra["suites"][0]["unexpected"] = True
        cases.append(("nested-extra", nested_extra, "unexpected"))

        duplicate_support = copy.deepcopy(self.spec)
        second_support = copy.deepcopy(duplicate_support["supportingArtifacts"][0])
        second_support["path"] = "platform-boot/target/other-summary.xml"
        duplicate_support["supportingArtifacts"].append(second_support)
        cases.append(("duplicate-support", duplicate_support, "duplicate supporting"))

        for name, broken, message in cases:
            with self.subTest(name=name):
                path = self.repo / f"broken-spec-{name}.json"
                path.write_text(json.dumps(broken), encoding="utf-8")
                with self.assertRaisesRegex(gate.GateError, message):
                    gate.load_spec(path)

    def test_snapshot_mutation_is_rejected_and_rematerialization_restores_git(
        self,
    ) -> None:
        source_repo = self.repo / "rematerialization-source"
        source_repo.mkdir()

        def git(*arguments: str) -> str:
            completed = subprocess.run(
                ["git", *arguments],
                cwd=source_repo,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
                text=True,
            )
            self.assertEqual(
                completed.returncode,
                0,
                completed.stderr or completed.stdout,
            )
            return completed.stdout.strip()

        git("init")
        (source_repo / "pom.xml").write_text(
            "<project>candidate</project>\n",
            encoding="utf-8",
        )
        (source_repo / "verify.sh").write_text(
            "#!/bin/sh\nexit 0\n",
            encoding="utf-8",
        )
        git("add", "pom.xml", "verify.sh")
        git("update-index", "--chmod=+x", "verify.sh")
        git(
            "-c",
            "user.name=Phase00 Fixture",
            "-c",
            "user.email=phase00@example.invalid",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "-m",
            "snapshot fixture",
        )
        candidate = git("rev-parse", "HEAD")
        entries = gate.git_tree_entries(source_repo, candidate)

        with gate.immutable_candidate_snapshot(
            source_repo,
            candidate,
            self.repo,
        ) as snapshot:
            gate.verify_materialised_git_tree(
                snapshot.root,
                entries,
                "initial snapshot",
            )
            snapshot_pom = snapshot.root / "pom.xml"
            os.chmod(snapshot_pom, 0o644)
            snapshot_pom.write_text(
                "<project>mutated</project>\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(gate.GateError, "Git blob mismatch"):
                gate.verify_materialised_git_tree(
                    snapshot.root,
                    entries,
                    "mutated snapshot",
                )

            gate.rematerialise_candidate_snapshot(
                source_repo,
                snapshot,
                candidate,
                entries,
            )
            self.assertEqual(
                (snapshot.root / "pom.xml").read_text(encoding="utf-8"),
                "<project>candidate</project>\n",
            )
            gate.verify_materialised_git_tree(
                snapshot.root,
                entries,
                "restored snapshot",
            )

            if os.name != "nt":
                os.chmod(snapshot.root / "pom.xml", 0o555)
                with self.assertRaisesRegex(
                    gate.GateError,
                    "executable-mode mismatch",
                ):
                    gate.verify_materialised_git_tree(
                        snapshot.root,
                        entries,
                        "mode-drift snapshot",
                    )


if __name__ == "__main__":
    unittest.main(verbosity=2)
