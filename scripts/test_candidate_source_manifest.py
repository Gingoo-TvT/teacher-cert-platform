#!/usr/bin/env python3
"""Pure-offline tests for candidate_source_manifest.py."""

from __future__ import annotations

import importlib.util
import os
from pathlib import Path
import shutil
import stat
import subprocess
import unittest
import uuid


SCRIPT = Path(__file__).with_name("candidate_source_manifest.py")
SPEC = importlib.util.spec_from_file_location("candidate_source_manifest", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
candidate_source_manifest = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(candidate_source_manifest)


def _remove_readonly(function, path: str, _error) -> None:
    os.chmod(path, stat.S_IWRITE)
    function(path)


def _cleanup_tree(path: Path) -> None:
    shutil.rmtree(path, onerror=_remove_readonly)


class CandidateSourceManifestTest(unittest.TestCase):

    def setUp(self) -> None:
        test_root = SCRIPT.parent.parent / "target" / "candidate-source-manifest-tests"
        test_root.mkdir(parents=True, exist_ok=True)
        self.base = test_root / f"run-{uuid.uuid4().hex}"
        self.base.mkdir()
        self.addCleanup(_cleanup_tree, self.base)
        self.repo = self.base / "repo"
        self.repo.mkdir()
        self._git("init")
        self._git("config", "user.email", "manifest-test@example.invalid")
        self._git("config", "user.name", "Manifest Test")
        (self.repo / "tracked.txt").write_bytes(b"baseline\n")
        self._git("add", "tracked.txt")
        self._git("commit", "-m", "baseline")
        (self.repo / "tracked.txt").write_bytes(b"candidate\n")
        (self.repo / "src").mkdir()
        (self.repo / "src" / "Untracked.java").write_bytes(b"class Untracked {}\n")
        self.manifest = self.base / "candidate-source-manifest.json"

    def _git(self, *args: str) -> subprocess.CompletedProcess[bytes]:
        result = subprocess.run(
            ("git", *args),
            cwd=self.repo,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if result.returncode != 0:
            self.fail(result.stderr.decode("utf-8", errors="replace"))
        return result

    def _capture(self) -> dict:
        repo = candidate_source_manifest.resolve_repo(self.repo)
        value = candidate_source_manifest.envelope(
            candidate_source_manifest.build_candidate(repo)
        )
        candidate_source_manifest.write_manifest(self.manifest, value)
        return value

    def test_capture_binds_tracked_diff_and_exact_untracked_file(self) -> None:
        value = self._capture()

        candidate = value["candidate"]
        self.assertEqual(1, candidate["trackedDiff"]["changedFileCount"])
        self.assertEqual(1, candidate["untracked"]["fileCount"])
        self.assertEqual(
            "src/Untracked.java",
            candidate["untracked"]["files"][0]["path"],
        )
        self._git("config", "diff.noprefix", "true")
        self._git("config", "diff.context", "9")
        self._git("config", "diff.indentHeuristic", "true")
        verified = candidate_source_manifest.verify_manifest(
            candidate_source_manifest.resolve_repo(self.repo),
            self.manifest,
        )
        self.assertEqual(value, verified)

    def test_verify_rejects_untracked_content_change(self) -> None:
        self._capture()
        (self.repo / "src" / "Untracked.java").write_bytes(b"class Changed {}\n")

        with self.assertRaisesRegex(
            candidate_source_manifest.ManifestError,
            "candidate source identity changed",
        ):
            candidate_source_manifest.verify_manifest(
                candidate_source_manifest.resolve_repo(self.repo),
                self.manifest,
            )

    def test_verify_rejects_tracked_content_change(self) -> None:
        self._capture()
        (self.repo / "tracked.txt").write_bytes(b"changed again\n")

        with self.assertRaisesRegex(
            candidate_source_manifest.ManifestError,
            "candidate source identity changed",
        ):
            candidate_source_manifest.verify_manifest(
                candidate_source_manifest.resolve_repo(self.repo),
                self.manifest,
            )

    def test_verify_rejects_untracked_path_set_change(self) -> None:
        self._capture()
        (self.repo / "src" / "Added.java").write_bytes(b"class Added {}\n")

        with self.assertRaisesRegex(
            candidate_source_manifest.ManifestError,
            "candidate source identity changed",
        ):
            candidate_source_manifest.verify_manifest(
                candidate_source_manifest.resolve_repo(self.repo),
                self.manifest,
            )

    def test_verify_rejects_head_change(self) -> None:
        self._capture()
        self._git("add", ".")
        self._git("commit", "-m", "different candidate")

        with self.assertRaisesRegex(
            candidate_source_manifest.ManifestError,
            "candidate source identity changed",
        ):
            candidate_source_manifest.verify_manifest(
                candidate_source_manifest.resolve_repo(self.repo),
                self.manifest,
            )

    def test_manifest_inside_repository_is_rejected(self) -> None:
        repo = candidate_source_manifest.resolve_repo(self.repo)
        with self.assertRaisesRegex(
            candidate_source_manifest.ManifestError,
            "outside the Git repository",
        ):
            candidate_source_manifest.external_manifest_path(
                self.repo / "candidate.json",
                repo,
            )


if __name__ == "__main__":
    unittest.main()
