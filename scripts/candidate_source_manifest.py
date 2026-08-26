#!/usr/bin/env python3
"""Capture and verify the complete source identity of a dirty Git candidate.

The manifest binds:

* the current HEAD commit;
* the raw binary diff for every tracked change; and
* every non-ignored untracked file by repository-relative path and SHA-256.

The manifest must live outside the repository so it cannot include itself.
This tool is deliberately offline and uses only Python's standard library.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import stat
import subprocess
import sys
import tempfile
from typing import Any, Iterable


SCHEMA_VERSION = 1
GIT_CONFIG_ARGS = (
    "-c",
    "core.quotePath=true",
    "-c",
    "diff.noprefix=false",
    "-c",
    "diff.mnemonicPrefix=false",
)
TRACKED_DIFF_ARGS = (
    "diff",
    "--binary",
    "--full-index",
    "--no-ext-diff",
    "--no-textconv",
    "--no-renames",
    "--diff-algorithm=myers",
    "--no-indent-heuristic",
    "--no-color",
    "--src-prefix=a/",
    "--dst-prefix=b/",
    "--unified=3",
    "HEAD",
    "--",
)
TRACKED_NAMES_ARGS = (
    "diff",
    "--name-only",
    "-z",
    "--no-ext-diff",
    "--no-textconv",
    "--no-renames",
    "--diff-algorithm=myers",
    "--no-color",
    "HEAD",
    "--",
)
UNTRACKED_ARGS = ("ls-files", "--others", "--exclude-standard", "-z")


class ManifestError(RuntimeError):
    """Raised when a candidate cannot be captured or verified safely."""


def _git(repo: Path, args: Iterable[str]) -> bytes:
    env = os.environ.copy()
    env["GIT_OPTIONAL_LOCKS"] = "0"
    env["LC_ALL"] = "C"
    command = ("git", *GIT_CONFIG_ARGS, *tuple(args))
    try:
        result = subprocess.run(
            command,
            cwd=repo,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        raise ManifestError(f"cannot execute Git: {exc}") from exc
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise ManifestError(
            f"Git command failed ({result.returncode}): {' '.join(command)}"
            + (f": {detail}" if detail else "")
        )
    return result.stdout


def resolve_repo(repo_argument: str | os.PathLike[str]) -> Path:
    requested = Path(repo_argument).resolve()
    raw_root = _git(requested, ("rev-parse", "--show-toplevel"))
    try:
        root_text = raw_root.decode("utf-8").strip()
    except UnicodeDecodeError as exc:
        raise ManifestError("Git repository root is not valid UTF-8") from exc
    if not root_text:
        raise ManifestError("Git returned an empty repository root")
    return Path(root_text).resolve(strict=True)


def _split_nul_paths(raw: bytes, label: str) -> list[tuple[bytes, str]]:
    if raw and not raw.endswith(b"\0"):
        raise ManifestError(f"{label} output is not NUL terminated")
    encoded_paths = raw[:-1].split(b"\0") if raw else []
    decoded: list[tuple[bytes, str]] = []
    seen: set[bytes] = set()
    for encoded in encoded_paths:
        if not encoded or encoded in seen:
            raise ManifestError(f"{label} contains an empty or duplicate path")
        seen.add(encoded)
        try:
            path = encoded.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise ManifestError(f"{label} contains a non-UTF-8 path") from exc
        pure = PurePosixPath(path)
        if pure.is_absolute() or not pure.parts or any(
            part in ("", ".", "..") for part in pure.parts
        ):
            raise ManifestError(f"{label} contains an unsafe path: {path!r}")
        decoded.append((encoded, path))
    decoded.sort(key=lambda item: item[0])
    return decoded


def _sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _hash_regular_file(repo: Path, relative_path: str) -> dict[str, Any]:
    pure = PurePosixPath(relative_path)
    candidate = repo.joinpath(*pure.parts)
    try:
        file_stat = candidate.lstat()
    except OSError as exc:
        raise ManifestError(
            f"cannot inspect untracked file {relative_path!r}: {exc}"
        ) from exc
    if stat.S_ISLNK(file_stat.st_mode) or not stat.S_ISREG(file_stat.st_mode):
        raise ManifestError(
            f"untracked path must be a regular file, observed {relative_path!r}"
        )

    digest = hashlib.sha256()
    byte_count = 0
    try:
        with candidate.open("rb") as source:
            while True:
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                digest.update(chunk)
                byte_count += len(chunk)
    except OSError as exc:
        raise ManifestError(
            f"cannot hash untracked file {relative_path!r}: {exc}"
        ) from exc
    return {
        "path": relative_path,
        "byteCount": byte_count,
        "sha256": digest.hexdigest(),
    }


def _head(repo: Path) -> str:
    raw = _git(repo, ("rev-parse", "--verify", "HEAD^{commit}"))
    try:
        value = raw.decode("ascii").strip()
    except UnicodeDecodeError as exc:
        raise ManifestError("HEAD is not ASCII") from exc
    if len(value) != 40 or any(character not in "0123456789abcdef" for character in value):
        raise ManifestError(f"HEAD is not a canonical 40-character SHA-1: {value!r}")
    return value


def _untracked_entries(
    repo: Path, encoded_paths: list[tuple[bytes, str]]
) -> list[dict[str, Any]]:
    return [_hash_regular_file(repo, path) for _, path in encoded_paths]


def build_candidate(repo: Path) -> dict[str, Any]:
    """Build one stable candidate identity, failing if inputs move mid-capture."""

    head_before = _head(repo)
    diff_before = _git(repo, TRACKED_DIFF_ARGS)
    tracked_names_before = _split_nul_paths(
        _git(repo, TRACKED_NAMES_ARGS), "tracked changed-file list"
    )
    untracked_paths_before = _split_nul_paths(
        _git(repo, UNTRACKED_ARGS), "untracked file list"
    )
    untracked_before = _untracked_entries(repo, untracked_paths_before)

    head_after = _head(repo)
    diff_after = _git(repo, TRACKED_DIFF_ARGS)
    tracked_names_after = _split_nul_paths(
        _git(repo, TRACKED_NAMES_ARGS), "tracked changed-file list"
    )
    untracked_paths_after = _split_nul_paths(
        _git(repo, UNTRACKED_ARGS), "untracked file list"
    )
    untracked_after = _untracked_entries(repo, untracked_paths_after)

    if head_before != head_after:
        raise ManifestError("HEAD changed while the candidate manifest was captured")
    if diff_before != diff_after or tracked_names_before != tracked_names_after:
        raise ManifestError(
            "tracked candidate bytes changed while the manifest was captured"
        )
    if (
        untracked_paths_before != untracked_paths_after
        or untracked_before != untracked_after
    ):
        raise ManifestError(
            "untracked candidate bytes changed while the manifest was captured"
        )

    return {
        "schemaVersion": SCHEMA_VERSION,
        "definition": {
            "fileHash": "sha256(raw file bytes)",
            "pathEncoding": "repository-relative UTF-8 Git paths",
            "pathOrder": "ascending raw UTF-8 bytes",
            "trackedDiffCommand": "git "
            + " ".join((*GIT_CONFIG_ARGS, *TRACKED_DIFF_ARGS)),
            "untrackedCommand": "git "
            + " ".join((*GIT_CONFIG_ARGS, *UNTRACKED_ARGS)),
        },
        "head": head_before,
        "trackedDiff": {
            "byteCount": len(diff_before),
            "changedFileCount": len(tracked_names_before),
            "sha256": _sha256_bytes(diff_before),
        },
        "untracked": {
            "fileCount": len(untracked_before),
            "files": untracked_before,
        },
    }


def _canonical_json(value: Any) -> bytes:
    return (
        json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        + "\n"
    ).encode("utf-8")


def envelope(candidate: dict[str, Any]) -> dict[str, Any]:
    return {
        "candidate": candidate,
        "candidateFingerprintSha256": _sha256_bytes(_canonical_json(candidate)),
    }


def _is_within(path: Path, root: Path) -> bool:
    try:
        common = os.path.commonpath(
            (os.path.normcase(str(path)), os.path.normcase(str(root)))
        )
    except ValueError:
        return False
    return common == os.path.normcase(str(root))


def external_manifest_path(path_argument: str | os.PathLike[str], repo: Path) -> Path:
    path = Path(path_argument).resolve(strict=False)
    if _is_within(path, repo):
        raise ManifestError(
            "manifest path must be outside the Git repository to avoid self-inclusion"
        )
    return path


def write_manifest(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = _canonical_json(value)
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            prefix=f".{path.name}.",
            suffix=".tmp",
            dir=path.parent,
            delete=False,
        ) as target:
            temporary_name = target.name
            target.write(payload)
            target.flush()
            os.fsync(target.fileno())
        os.replace(temporary_name, path)
        temporary_name = None
    except OSError as exc:
        raise ManifestError(f"cannot write manifest {path}: {exc}") from exc
    finally:
        if temporary_name is not None:
            try:
                Path(temporary_name).unlink()
            except OSError:
                pass


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ManifestError(f"manifest contains duplicate JSON key: {key!r}")
        result[key] = value
    return result


def read_manifest(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
    except OSError as exc:
        raise ManifestError(f"cannot read manifest {path}: {exc}") from exc
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=_reject_duplicate_keys)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ManifestError(f"manifest is not canonical UTF-8 JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise ManifestError("manifest root must be a JSON object")
    if raw != _canonical_json(value):
        raise ManifestError("manifest bytes are not in canonical JSON form")
    return value


def verify_manifest(repo: Path, path: Path) -> dict[str, Any]:
    expected = read_manifest(path)
    if set(expected) != {"candidate", "candidateFingerprintSha256"}:
        raise ManifestError("manifest envelope fields are invalid")
    expected_candidate = expected.get("candidate")
    expected_fingerprint = expected.get("candidateFingerprintSha256")
    if not isinstance(expected_candidate, dict) or not isinstance(
        expected_fingerprint, str
    ):
        raise ManifestError("manifest candidate or fingerprint has an invalid type")
    recorded_fingerprint = _sha256_bytes(_canonical_json(expected_candidate))
    if recorded_fingerprint != expected_fingerprint:
        raise ManifestError(
            "manifest fingerprint does not match its recorded candidate payload"
        )

    observed_candidate = build_candidate(repo)
    observed_fingerprint = _sha256_bytes(_canonical_json(observed_candidate))
    if observed_candidate != expected_candidate:
        expected_untracked = expected_candidate.get("untracked", {})
        observed_untracked = observed_candidate.get("untracked", {})
        raise ManifestError(
            "candidate source identity changed: "
            f"expected fingerprint={expected_fingerprint}, "
            f"observed fingerprint={observed_fingerprint}, "
            f"expected tracked={expected_candidate.get('trackedDiff', {}).get('changedFileCount')}, "
            f"observed tracked={observed_candidate.get('trackedDiff', {}).get('changedFileCount')}, "
            f"expected untracked={expected_untracked.get('fileCount')}, "
            f"observed untracked={observed_untracked.get('fileCount')}"
        )
    return expected


def _print_summary(value: dict[str, Any], action: str) -> None:
    candidate = value["candidate"]
    print(f"PASS: candidate source manifest {action}")
    print(f"fingerprint={value['candidateFingerprintSha256']}")
    print(f"head={candidate['head']}")
    print(f"trackedChangedFiles={candidate['trackedDiff']['changedFileCount']}")
    print(f"untrackedFiles={candidate['untracked']['fileCount']}")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    capture = subparsers.add_parser("capture", help="capture a candidate manifest")
    capture.add_argument("--repo", default=".", help="Git working tree (default: .)")
    capture.add_argument("--output", required=True, help="manifest path outside repo")

    verify = subparsers.add_parser("verify", help="verify a candidate manifest")
    verify.add_argument("--repo", default=".", help="Git working tree (default: .)")
    verify.add_argument("--manifest", required=True, help="manifest path outside repo")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    arguments = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        repo = resolve_repo(arguments.repo)
        if arguments.command == "capture":
            path = external_manifest_path(arguments.output, repo)
            value = envelope(build_candidate(repo))
            write_manifest(path, value)
            _print_summary(value, "captured")
        else:
            path = external_manifest_path(arguments.manifest, repo)
            value = verify_manifest(repo, path)
            _print_summary(value, "verified")
        return 0
    except ManifestError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
