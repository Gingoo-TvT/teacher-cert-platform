#!/usr/bin/env python3
"""Phase 0 CI gate and provenance evidence generator.

The ``run`` command is the only command that invokes Maven.  It requires a
clean tracked worktree and strictly binds credential-free target markers to
the effective service environment.  Both Maven invocations run from exact
regular blobs read directly from the expected commit tree with ``git
cat-file``; every path, mode and Git object hash is verified, so concurrent
checkouts and external Git attributes cannot change the compiled source.  A
read-only target-identity preflight must pass before the formal exact-suite
``mvn clean verify`` is allowed to start.  The command then validates fresh
XML and runtime identity evidence and writes a self-verifiable evidence
directory.

The ``verify`` command is purely offline.  It verifies the candidate marker,
artifact hashes, exact suite/testcase sets, timestamps, exit code, target
bindings, preflight/runtime identity equality, and the SHA256SUMS file
in an existing evidence directory.

Raw Maven output and source XML are hashed before sanitisation.  Only the full
redacted log and sanitised XML copies enter the evidence artifact: Surefire
``<properties>`` are removed because they can contain environment secrets.
"""

from __future__ import annotations

import argparse
from contextlib import contextmanager
import datetime as dt
import hashlib
import ipaddress
import json
import ntpath
import os
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import (
    Any,
    Callable,
    Iterable,
    Iterator,
    Mapping,
    NamedTuple,
    Sequence,
    TypeVar,
)
from urllib.parse import parse_qsl, unquote_to_bytes, urlsplit


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_SPEC = SCRIPT_DIR / "phase00_ci_gate_spec.json"
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
GIT_SHA_RE = re.compile(r"^[0-9a-f]{40}$")
MAX_STRICT_JSON_BYTES = 65_536
MAX_MANIFEST_BYTES = 262_144
MAX_EVIDENCE_FILES = 512
MAX_EVIDENCE_TOTAL_BYTES = 512 * 1024 * 1024
SECRET_SCAN_POLICY = "known-env-and-credential-patterns-v1"
MINIO_IDENTITY_MODE = "provisioned-object-challenge-v2"
PASS_MANIFEST_READY_NAME = ".manifest.pass-ready"
MYSQL_UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)
REDIS_RUN_ID_RE = re.compile(r"^[0-9a-f]{40}$")
WINDOWS_RESERVED_NAMES = frozenset(
    {"CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$"}
    | {f"COM{index}" for index in range(1, 10)}
    | {f"LPT{index}" for index in range(1, 10)}
    | {f"COM{index}" for index in "¹²³"}
    | {f"LPT{index}" for index in "¹²³"}
)
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
CONFIG_ENV = {
    "mysqlUrl": "SPRING_DATASOURCE_URL",
    "redisHost": "SPRING_DATA_REDIS_HOST",
    "redisPort": "SPRING_DATA_REDIS_PORT",
    "redisDatabase": "SPRING_DATA_REDIS_DATABASE",
    "minioEndpoint": "MINIO_ENDPOINT",
    "minioPublicEndpoint": "MINIO_PUBLIC_ENDPOINT",
    "minioBucket": "MINIO_BUCKET",
}


def normalize_environment_property_name(name: str) -> str:
    """Collapse Spring's dot/hyphen/underscore and case aliases for comparison."""

    return re.sub(r"[._-]+", ".", name.strip()).strip(".").lower()


def environment_property_equivalence_key(name: str) -> str:
    """Return a separator/case-insensitive key, including camel-case spellings."""

    return re.sub(r"[._-]+", "", name.strip()).lower()


CANONICAL_TARGET_ENV_BY_PROPERTY = {
    "spring.datasource.url": "SPRING_DATASOURCE_URL",
    "spring.datasource.username": "SPRING_DATASOURCE_USERNAME",
    "spring.datasource.password": "SPRING_DATASOURCE_PASSWORD",
    "spring.data.redis.host": "SPRING_DATA_REDIS_HOST",
    "spring.data.redis.port": "SPRING_DATA_REDIS_PORT",
    "spring.data.redis.database": "SPRING_DATA_REDIS_DATABASE",
    "spring.data.redis.username": "SPRING_DATA_REDIS_USERNAME",
    "spring.data.redis.password": "SPRING_DATA_REDIS_PASSWORD",
    "minio.endpoint": "MINIO_ENDPOINT",
    "minio.access.key": "MINIO_ACCESS_KEY",
    "minio.secret.key": "MINIO_SECRET_KEY",
    "minio.bucket": "MINIO_BUCKET",
    "minio.public.endpoint": "MINIO_PUBLIC_ENDPOINT",
    "spring.profiles.active": "SPRING_PROFILES_ACTIVE",
}
CANONICAL_TARGET_ENV_BY_EQUIVALENCE = {
    environment_property_equivalence_key(property_name): environment_name
    for property_name, environment_name in CANONICAL_TARGET_ENV_BY_PROPERTY.items()
}
IDENTITY_ENV = {
    "mysqlServerUuid": "PHASE00_EXPECTED_MYSQL_SERVER_UUID",
    "redisRunId": "PHASE00_EXPECTED_REDIS_RUN_ID",
    "minioIdentityObject": "PHASE00_MINIO_IDENTITY_OBJECT",
    "minioIdentitySha256": "PHASE00_EXPECTED_MINIO_IDENTITY_SHA256",
    "minioIdentityNonce": "PHASE00_EXPECTED_MINIO_IDENTITY_NONCE",
    "minioIdentityIssuedAt": "PHASE00_EXPECTED_MINIO_IDENTITY_ISSUED_AT",
}
TARGET_IDENTITY_ARTIFACT = "target/runtime-identity.json"
TARGET_IDENTITY_KIND = "runtime-target-identity-json"
PREFLIGHT_IDENTITY_SOURCE = "platform-boot/target/phase00-target-preflight.json"
PREFLIGHT_IDENTITY_ARTIFACT = "target/preflight-identity.json"
PREFLIGHT_IDENTITY_KIND = "preflight-target-identity-json"
PREFLIGHT_PRODUCER = "cn.edu.gpnu.platform.boot.Phase00TargetPreflight"
FORBIDDEN_OVERRIDE_ENV = frozenset(
    {
        "SPRING_APPLICATION_JSON",
        "SPRING_CONFIG_LOCATION",
        "SPRING_CONFIG_ADDITIONAL_LOCATION",
        "SPRING_CONFIG_IMPORT",
        "SPRING_PROFILES_INCLUDE",
        "SPRING_PROFILES_DEFAULT",
        "JAVA_TOOL_OPTIONS",
        "JDK_JAVA_OPTIONS",
        "_JAVA_OPTIONS",
        "MAVEN_OPTS",
        "MAVEN_ARGS",
        "SPRING_DATA_REDIS_URL",
        "SPRING_REDIS_URL",
        "SPRING_DATASOURCE_JNDI_NAME",
        "SPRING_FLYWAY_URL",
        "SPRING_FLYWAY_USER",
        "SPRING_FLYWAY_PASSWORD",
        "SPRING_FLYWAY_DEFAULT_SCHEMA",
        "SPRING_FLYWAY_SCHEMAS",
        "SPRING_FLYWAY_INIT_SQLS",
        "SPRING_FLYWAY_DRIVER_CLASS_NAME",
    }
)
FORBIDDEN_OVERRIDE_PREFIXES = (
    "SPRING_DATA_REDIS_SENTINEL_",
    "SPRING_DATA_REDIS_CLUSTER_",
    "SPRING_REDIS_SENTINEL_",
    "SPRING_REDIS_CLUSTER_",
    "SPRING_DATASOURCE_HIKARI_",
    "SPRING_FLYWAY_",
    "SPRING_SQL_INIT_",
    "SPRING_PROFILES_GROUP_",
)
FORBIDDEN_NORMALIZED_PROPERTY_NAMES = frozenset(
    {
        "spring.application.json",
        "spring.data.redis.url",
        "spring.redis.url",
        "spring.datasource.jndi.name",
        "java.tool.options",
        "jdk.java.options",
        "java.options",
        "maven.opts",
        "maven.args",
    }
    | {
        normalize_environment_property_name(name)
        for name in FORBIDDEN_OVERRIDE_ENV
    }
)
FORBIDDEN_NORMALIZED_PROPERTY_PREFIXES = (
    "spring.config.",
    "spring.datasource.hikari.",
    "spring.data.redis.sentinel.",
    "spring.data.redis.cluster.",
    "spring.redis.sentinel.",
    "spring.redis.cluster.",
    "spring.flyway.",
    "spring.sql.init.",
    "spring.profiles.group.",
)
FORBIDDEN_PROPERTY_EQUIVALENCE_NAMES = frozenset(
    environment_property_equivalence_key(name)
    for name in FORBIDDEN_NORMALIZED_PROPERTY_NAMES
)
FORBIDDEN_PROPERTY_EQUIVALENCE_PREFIXES = tuple(
    environment_property_equivalence_key(prefix)
    for prefix in FORBIDDEN_NORMALIZED_PROPERTY_PREFIXES
)


class GateError(RuntimeError):
    """A deterministic gate failure."""


class SourceSnapshot(NamedTuple):
    """Immutable build input materialised from one exact Git commit."""

    root: Path
    candidate_sha: str
    tree_sha: str
    manifest_sha256: str
    file_count: int


class GitTreeEntry(NamedTuple):
    """One portable regular blob from the candidate Git tree."""

    mode: str
    object_sha: str
    path: PurePosixPath


class EvidenceFileSnapshot(NamedTuple):
    """One evidence file read exactly once from an anchored OS handle."""

    relative_path: str
    content: bytes
    sha256: str
    size: int
    mtime_ns: int
    file_id: tuple[int, int]


class EvidenceSnapshot:
    """Immutable in-memory view of an untrusted evidence directory."""

    def __init__(
        self,
        root: Path,
        files: Mapping[str, EvidenceFileSnapshot],
    ) -> None:
        self.root = root
        self.files = dict(files)

    def require(self, relative_value: str, label: str) -> EvidenceFileSnapshot:
        relative = safe_relative_path(relative_value, label).as_posix()
        snapshot = self.files.get(relative)
        if snapshot is None:
            raise GateError(f"{label} does not exist: {relative!r}")
        return snapshot


class PreparedEvidenceManifest(NamedTuple):
    """Exact PASS bytes verified before any PASS manifest reaches disk."""

    manifest_content: bytes
    checksum_content: bytes
    virtual_snapshot: EvidenceSnapshot
    verified_manifest: dict[str, Any]


class ValidatedWindowsEvidenceHandle(NamedTuple):
    """A file handle capability issued only after boundary validation."""

    handle: int
    attributes: int
    file_id: tuple[int, int]
    size: int
    mtime_ns: int


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


def require_exact_keys(
    value: Any,
    expected: set[str],
    label: str,
) -> Mapping[str, Any]:
    if not isinstance(value, dict):
        raise GateError(f"{label} must be an object")
    observed = set(value)
    if observed != expected:
        raise GateError(
            f"{label} fields are not exact: "
            f"missing={sorted(expected - observed)}, "
            f"unexpected={sorted(observed - expected)}"
        )
    return value


def require_clean_text(
    value: Any,
    label: str,
    *,
    maximum: int = 256,
) -> str:
    if not isinstance(value, str) or not value:
        raise GateError(f"{label} must be a non-empty string")
    if value != value.strip():
        raise GateError(f"{label} must not contain surrounding whitespace")
    if len(value) > maximum:
        raise GateError(f"{label} exceeds {maximum} characters")
    if any(ord(character) < 0x20 or ord(character) == 0x7F for character in value):
        raise GateError(f"{label} contains a control character")
    return value


def required_environment(
    environment: Mapping[str, str],
    name: str,
    *,
    maximum: int = 2048,
) -> str:
    return require_clean_text(environment.get(name), name, maximum=maximum)


def strict_percent_decode(
    raw: str,
    label: str,
    *,
    reject_separators: bool = True,
    allow_literal_separators: bool = False,
) -> str:
    """Decode one URI component without accepting ambiguous encodings."""

    for match in re.finditer("%", raw):
        token = raw[match.start() : match.start() + 3]
        if not re.fullmatch(r"%[0-9A-Fa-f]{2}", token):
            raise GateError(f"{label} contains malformed percent encoding")
        octet = int(token[1:], 16)
        if reject_separators and octet in {0x00, 0x2F, 0x5C}:
            raise GateError(f"{label} contains an encoded separator or NUL")
        if (
            0x30 <= octet <= 0x39
            or 0x41 <= octet <= 0x5A
            or 0x61 <= octet <= 0x7A
            or octet in {0x2D, 0x2E, 0x5F, 0x7E}
        ):
            raise GateError(f"{label} contains non-canonical percent encoding")
    try:
        decoded = unquote_to_bytes(raw).decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise GateError(f"{label} is not valid UTF-8 after percent decoding") from exc
    if any(ord(character) < 0x20 or ord(character) == 0x7F for character in decoded):
        raise GateError(f"{label} decodes to a control character")
    if (
        reject_separators
        and not allow_literal_separators
        and ("/" in decoded or "\\" in decoded or "\x00" in decoded)
    ):
        raise GateError(f"{label} decodes to a separator or NUL")
    return decoded


def single_uri_path(raw_path: str, label: str) -> str:
    if not raw_path.startswith("/") or raw_path == "/" or raw_path.count("/") != 1:
        raise GateError(f"{label} must contain exactly one non-empty path segment")
    if "\\" in raw_path:
        raise GateError(f"{label} must not contain a backslash")
    return strict_percent_decode(raw_path[1:], label)


def normalize_host(raw_host: str, label: str) -> str:
    host = require_clean_text(raw_host, label, maximum=253)
    if host.startswith("[") or host.endswith("]"):
        if not (host.startswith("[") and host.endswith("]")):
            raise GateError(f"{label} has malformed IPv6 brackets")
        host = host[1:-1]
    if "%" in host:
        raise GateError(f"{label} must not contain an IPv6 zone or percent encoding")
    try:
        return ipaddress.ip_address(host).compressed.lower()
    except ValueError:
        pass
    try:
        host.encode("ascii")
    except UnicodeEncodeError as exc:
        raise GateError(f"{label} must be an ASCII DNS name or IP address") from exc
    host = host.rstrip(".").lower()
    if not host or len(host) > 253:
        raise GateError(f"{label} is not a valid DNS name")
    if re.fullmatch(r"[0-9.]+", host):
        raise GateError(f"{label} resembles an invalid or ambiguous IPv4 address")
    labels = host.split(".")
    if any(
        not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", item)
        for item in labels
    ):
        raise GateError(f"{label} is not a canonical DNS name")
    return host


def parse_port(value: Any, label: str, default: int | None = None) -> int:
    if value is None and default is not None:
        return default
    if isinstance(value, bool):
        raise GateError(f"{label} must be an integer port")
    text = str(value)
    if not re.fullmatch(r"[0-9]+", text):
        raise GateError(f"{label} must be an integer port")
    port = int(text)
    if port < 1 or port > 65535:
        raise GateError(f"{label} is outside 1..65535")
    return port


def parse_non_negative_database(value: Any, label: str) -> int:
    if isinstance(value, bool):
        raise GateError(f"{label} must be a non-negative integer")
    text = str(value)
    if not re.fullmatch(r"0|[1-9][0-9]*", text):
        raise GateError(f"{label} must be a canonical non-negative integer")
    parsed = int(text)
    if parsed > 2_147_483_647:
        raise GateError(f"{label} exceeds the supported integer range")
    return parsed


def display_host(host: str) -> str:
    return f"[{host}]" if ":" in host else host


def validate_schema(value: str, label: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9_]{1,64}", value):
        raise GateError(f"{label} must be a 1..64 character schema name")
    return value


def validate_bucket(value: str, label: str) -> str:
    if (
        not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", value)
        or ".." in value
        or ".-" in value
        or "-." in value
        or re.fullmatch(r"[0-9]+(?:\.[0-9]+){3}", value)
    ):
        raise GateError(f"{label} is not a canonical S3 bucket name")
    return value


def split_service_uri(
    value: str,
    *,
    label: str,
    schemes: set[str],
    default_ports: Mapping[str, int],
    resource_label: str,
) -> tuple[str, str, int, str]:
    raw = require_clean_text(value, label, maximum=1024)
    if "\\" in raw:
        raise GateError(f"{label} must not contain a backslash")
    try:
        parsed = urlsplit(raw)
        port_value = parsed.port
    except ValueError as exc:
        raise GateError(f"{label} is not a valid URI: {exc}") from exc
    scheme = parsed.scheme.lower()
    if scheme not in schemes or parsed.scheme != scheme:
        raise GateError(f"{label} uses an unsupported or non-canonical scheme")
    if (
        not parsed.netloc
        or parsed.hostname is None
        or parsed.username is not None
        or parsed.password is not None
        or "@" in parsed.netloc
    ):
        raise GateError(f"{label} must contain one credential-free authority")
    if any(token in parsed.netloc for token in (",", "(", ")", ";")):
        raise GateError(f"{label} must contain exactly one service host")
    if parsed.query or parsed.fragment:
        raise GateError(f"{label} must not contain a query or fragment")
    host = normalize_host(parsed.hostname, f"{label} host")
    port = parse_port(port_value, f"{label} port", default_ports[scheme])
    resource = single_uri_path(parsed.path, f"{label} {resource_label}")
    return scheme, host, port, resource


def service_record(
    *,
    scheme: str,
    host: str,
    port: int,
    resource_name: str,
    resource: str | int,
    marker: bool,
) -> dict[str, Any]:
    record: dict[str, Any] = {
        "scheme": scheme,
        "host": host,
        "port": port,
        resource_name: resource,
    }
    if marker:
        record["marker"] = (
            f"{scheme}://{display_host(host)}:{port}/{resource}"
        )
    return record


def parse_mysql_marker(value: str) -> dict[str, Any]:
    scheme, host, port, database = split_service_uri(
        value,
        label=TARGET_ENV["mysql"],
        schemes={"mysql"},
        default_ports={"mysql": 3306},
        resource_label="database",
    )
    return service_record(
        scheme=scheme,
        host=host,
        port=port,
        resource_name="database",
        resource=validate_schema(database, "MySQL marker database"),
        marker=True,
    )


def parse_redis_marker(value: str) -> dict[str, Any]:
    scheme, host, port, database_raw = split_service_uri(
        value,
        label=TARGET_ENV["redis"],
        schemes={"redis"},
        default_ports={"redis": 6379},
        resource_label="database",
    )
    return service_record(
        scheme=scheme,
        host=host,
        port=port,
        resource_name="database",
        resource=parse_non_negative_database(database_raw, "Redis marker database"),
        marker=True,
    )


def parse_minio_marker(value: str) -> dict[str, Any]:
    scheme, host, port, bucket = split_service_uri(
        value,
        label=TARGET_ENV["minio"],
        schemes={"http", "https"},
        default_ports={"http": 80, "https": 443},
        resource_label="bucket",
    )
    return service_record(
        scheme=scheme,
        host=host,
        port=port,
        resource_name="bucket",
        resource=validate_bucket(bucket, "MinIO marker bucket"),
        marker=True,
    )


def validate_jdbc_query(query: str, label: str) -> None:
    if not query:
        return
    allowed_names = {
        "useUnicode",
        "characterEncoding",
        "serverTimezone",
        "allowPublicKeyRetrieval",
        "useSSL",
    }
    if ";" in query:
        raise GateError(f"{label} query must use unambiguous ampersand separators")
    for component in query.split("&"):
        if not component or "=" not in component:
            raise GateError(f"{label} query contains an empty or ambiguous parameter")
        raw_name, raw_value = component.split("=", 1)
        name = strict_percent_decode(
            raw_name,
            f"{label} query name",
            reject_separators=True,
        )
        strict_percent_decode(
            raw_value,
            f"{label} query value for {name!r}",
            reject_separators=True,
            allow_literal_separators=True,
        )
        if SENSITIVE_NAME_RE.search(name):
            raise GateError(f"{label} query contains a sensitive parameter name")
        if name not in allowed_names:
            raise GateError(
                f"{label} query parameter is not in the safe allowlist: {name!r}"
            )
    try:
        pairs = parse_qsl(
            query,
            keep_blank_values=True,
            strict_parsing=True,
            encoding="utf-8",
            errors="strict",
        )
    except (UnicodeDecodeError, ValueError) as exc:
        raise GateError(f"{label} query is malformed") from exc
    names = [name.lower() for name, _ in pairs]
    if len(names) != len(set(names)):
        raise GateError(f"{label} query contains duplicate parameter names")


def parse_mysql_configuration(value: str) -> dict[str, Any]:
    raw = require_clean_text(value, CONFIG_ENV["mysqlUrl"], maximum=4096)
    if not raw.startswith("jdbc:mysql://"):
        raise GateError(
            "SPRING_DATASOURCE_URL must use the single-target jdbc:mysql:// form"
        )
    uri = raw[len("jdbc:") :]
    if "\\" in uri:
        raise GateError("SPRING_DATASOURCE_URL must not contain a backslash")
    try:
        parsed = urlsplit(uri)
        port_value = parsed.port
    except ValueError as exc:
        raise GateError(f"SPRING_DATASOURCE_URL is not a valid JDBC URI: {exc}") from exc
    if (
        parsed.scheme != "mysql"
        or not parsed.netloc
        or parsed.hostname is None
        or parsed.username is not None
        or parsed.password is not None
        or "@" in parsed.netloc
    ):
        raise GateError(
            "SPRING_DATASOURCE_URL must contain one credential-free MySQL authority"
        )
    if any(token in parsed.netloc for token in (",", "(", ")", ";")):
        raise GateError("SPRING_DATASOURCE_URL must contain exactly one MySQL host")
    if parsed.fragment:
        raise GateError("SPRING_DATASOURCE_URL must not contain a fragment")
    host = normalize_host(parsed.hostname, "SPRING_DATASOURCE_URL host")
    port = parse_port(port_value, "SPRING_DATASOURCE_URL port", 3306)
    database = validate_schema(
        single_uri_path(parsed.path, "SPRING_DATASOURCE_URL database"),
        "SPRING_DATASOURCE_URL database",
    )
    validate_jdbc_query(parsed.query, "SPRING_DATASOURCE_URL")
    return service_record(
        scheme="mysql",
        host=host,
        port=port,
        resource_name="database",
        resource=database,
        marker=False,
    )


def parse_redis_configuration(environment: Mapping[str, str]) -> dict[str, Any]:
    raw_host = required_environment(environment, CONFIG_ENV["redisHost"])
    if any(token in raw_host for token in ("://", "@", "/", "\\", ",")):
        raise GateError("SPRING_DATA_REDIS_HOST must contain exactly one bare host")
    return service_record(
        scheme="redis",
        host=normalize_host(raw_host, "SPRING_DATA_REDIS_HOST"),
        port=parse_port(
            required_environment(environment, CONFIG_ENV["redisPort"]),
            "SPRING_DATA_REDIS_PORT",
        ),
        resource_name="database",
        resource=parse_non_negative_database(
            required_environment(environment, CONFIG_ENV["redisDatabase"]),
            "SPRING_DATA_REDIS_DATABASE",
        ),
        marker=False,
    )


def parse_minio_configuration(
    endpoint: str,
    bucket: str,
) -> dict[str, Any]:
    raw = require_clean_text(endpoint, CONFIG_ENV["minioEndpoint"], maximum=1024)
    if "\\" in raw:
        raise GateError("MINIO_ENDPOINT must not contain a backslash")
    try:
        parsed = urlsplit(raw)
        port_value = parsed.port
    except ValueError as exc:
        raise GateError(f"MINIO_ENDPOINT is not a valid URI: {exc}") from exc
    scheme = parsed.scheme.lower()
    if (
        scheme not in {"http", "https"}
        or parsed.scheme != scheme
        or not parsed.netloc
        or parsed.hostname is None
        or parsed.username is not None
        or parsed.password is not None
        or "@" in parsed.netloc
    ):
        raise GateError("MINIO_ENDPOINT must contain one credential-free HTTP authority")
    if any(token in parsed.netloc for token in (",", "(", ")", ";")):
        raise GateError("MINIO_ENDPOINT must contain exactly one host")
    if parsed.query or parsed.fragment or parsed.path not in {"", "/"}:
        raise GateError("MINIO_ENDPOINT must not contain a service path, query, or fragment")
    return service_record(
        scheme=scheme,
        host=normalize_host(parsed.hostname, "MINIO_ENDPOINT host"),
        port=parse_port(
            port_value,
            "MINIO_ENDPOINT port",
            443 if scheme == "https" else 80,
        ),
        resource_name="bucket",
        resource=validate_bucket(
            require_clean_text(bucket, CONFIG_ENV["minioBucket"]),
            "MINIO_BUCKET",
        ),
        marker=False,
    )


def comparable_service(record: Mapping[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in record.items() if key != "marker"}


def read_target_configuration(
    environment: Mapping[str, str],
) -> tuple[dict[str, Any], dict[str, Any]]:
    schema = validate_schema(
        required_environment(environment, TARGET_ENV["schema"]),
        TARGET_ENV["schema"],
    )
    run_context = required_environment(environment, TARGET_ENV["runContext"])
    if not SAFE_MARKER_RE.fullmatch(run_context) or SENSITIVE_NAME_RE.search(run_context):
        raise GateError("PHASE00_RUN_CONTEXT is not a safe non-secret marker")
    declared = {
        "schema": schema,
        "mysql": parse_mysql_marker(
            required_environment(environment, TARGET_ENV["mysql"])
        ),
        "redis": parse_redis_marker(
            required_environment(environment, TARGET_ENV["redis"])
        ),
        "minio": parse_minio_marker(
            required_environment(environment, TARGET_ENV["minio"])
        ),
        "runContext": run_context,
    }
    configured_minio = parse_minio_configuration(
        required_environment(environment, CONFIG_ENV["minioEndpoint"]),
        required_environment(environment, CONFIG_ENV["minioBucket"]),
    )
    configured_minio_public = parse_minio_configuration(
        required_environment(environment, CONFIG_ENV["minioPublicEndpoint"]),
        required_environment(environment, CONFIG_ENV["minioBucket"]),
    )
    if configured_minio_public != configured_minio:
        raise GateError(
            "MINIO_PUBLIC_ENDPOINT does not match MINIO_ENDPOINT: both must "
            "resolve to the same scheme/host/port for the exact Phase 0 gate"
        )
    configured = {
        "mysql": parse_mysql_configuration(
            required_environment(environment, CONFIG_ENV["mysqlUrl"], maximum=4096)
        ),
        "redis": parse_redis_configuration(environment),
        "minio": configured_minio,
        "minioPublic": configured_minio_public,
    }
    if declared["schema"] != configured["mysql"]["database"]:
        raise GateError("PHASE00_TARGET_SCHEMA does not match SPRING_DATASOURCE_URL")
    for service in ("mysql", "redis", "minio"):
        if comparable_service(declared[service]) != configured[service]:
            raise GateError(
                f"{TARGET_ENV[service]} does not match the configured {service} target"
            )
    return declared, configured


def validate_identity_object(value: str, label: str) -> str:
    object_name = require_clean_text(value, label, maximum=1024)
    if (
        not object_name.startswith(".phase00-target/")
        or "\\" in object_name
        or "%" in object_name
        or any(segment in {"", ".", ".."} for segment in object_name.split("/"))
        or not re.fullmatch(r"[A-Za-z0-9._/-]+", object_name)
    ):
        raise GateError(f"{label} is not a safe Phase 0 identity object path")
    return object_name


def validate_identity_issued_at(value: Any, label: str) -> str:
    issued_at = require_clean_text(value, label)
    if not re.fullmatch(
        r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z",
        issued_at,
    ):
        raise GateError(f"{label} must be canonical UTC YYYY-MM-DDTHH:MM:SSZ")
    parsed = parse_utc(issued_at, label)
    if parsed.strftime("%Y-%m-%dT%H:%M:%SZ") != issued_at:
        raise GateError(f"{label} is not a canonical UTC instant")
    return issued_at


def read_expected_identities(
    environment: Mapping[str, str],
    expected_candidate: str,
) -> dict[str, Any]:
    validate_candidate(expected_candidate, expected_candidate)
    mysql_uuid = required_environment(
        environment, IDENTITY_ENV["mysqlServerUuid"]
    ).lower()
    redis_run_id = required_environment(
        environment, IDENTITY_ENV["redisRunId"]
    ).lower()
    minio_sha = required_environment(
        environment, IDENTITY_ENV["minioIdentitySha256"]
    ).lower()
    minio_nonce = required_environment(
        environment, IDENTITY_ENV["minioIdentityNonce"]
    ).lower()
    if not MYSQL_UUID_RE.fullmatch(mysql_uuid):
        raise GateError("PHASE00_EXPECTED_MYSQL_SERVER_UUID is not a canonical UUID")
    if not REDIS_RUN_ID_RE.fullmatch(redis_run_id):
        raise GateError("PHASE00_EXPECTED_REDIS_RUN_ID must be 40 lowercase hex characters")
    if not SHA256_RE.fullmatch(minio_sha):
        raise GateError(
            "PHASE00_EXPECTED_MINIO_IDENTITY_SHA256 must be 64 lowercase hex characters"
        )
    if not SHA256_RE.fullmatch(minio_nonce):
        raise GateError(
            "PHASE00_EXPECTED_MINIO_IDENTITY_NONCE must be 64 lowercase hex characters"
        )
    return {
        "candidateSha": expected_candidate,
        "mysql": {"serverUuid": mysql_uuid},
        "redis": {"runId": redis_run_id},
        "minio": {
            "identityObject": validate_identity_object(
                required_environment(
                    environment, IDENTITY_ENV["minioIdentityObject"]
                ),
                IDENTITY_ENV["minioIdentityObject"],
            ),
            "identitySha256": minio_sha,
            "identityNonce": minio_nonce,
            "identityIssuedAt": validate_identity_issued_at(
                required_environment(
                    environment, IDENTITY_ENV["minioIdentityIssuedAt"]
                ),
                IDENTITY_ENV["minioIdentityIssuedAt"],
            ),
        },
    }


def validate_override_channels(
    environment: Mapping[str, str],
) -> dict[str, Any]:
    forbidden: list[str] = []
    for original_name, value in environment.items():
        name = original_name.upper()
        equivalence_key = environment_property_equivalence_key(original_name)
        canonical_name = CANONICAL_TARGET_ENV_BY_EQUIVALENCE.get(equivalence_key)
        is_noncanonical_target_alias = (
            canonical_name is not None and original_name != canonical_name
        )
        is_forbidden_channel = (
            name in FORBIDDEN_OVERRIDE_ENV
            or any(name.startswith(prefix) for prefix in FORBIDDEN_OVERRIDE_PREFIXES)
            or equivalence_key in FORBIDDEN_PROPERTY_EQUIVALENCE_NAMES
            or any(
                equivalence_key.startswith(prefix)
                for prefix in FORBIDDEN_PROPERTY_EQUIVALENCE_PREFIXES
            )
        )
        if is_noncanonical_target_alias or is_forbidden_channel:
            forbidden.append(original_name)
    if forbidden:
        raise GateError(
            "alternate Spring/Maven target override channels and relaxed-binding "
            "aliases must be absent: "
            f"{sorted(forbidden, key=str.upper)}"
        )
    profile_values = [
        value
        for name, value in environment.items()
        if name.upper() == "SPRING_PROFILES_ACTIVE" and value
    ]
    if len(profile_values) > 1 or any(value != "dev" for value in profile_values):
        raise GateError("SPRING_PROFILES_ACTIVE must be empty or exactly dev")
    return target_constraint_policy()


def target_constraint_policy() -> dict[str, Any]:
    return {
        "springProfilesActive": "dev",
        "relaxedBindingAliasesRejected": True,
        "canonicalTargetEnvironmentNames": sorted(
            CANONICAL_TARGET_ENV_BY_PROPERTY.values()
        ),
        "forbiddenEnvironmentNames": sorted(FORBIDDEN_OVERRIDE_ENV),
        "forbiddenEnvironmentPrefixes": list(FORBIDDEN_OVERRIDE_PREFIXES),
        "forbiddenNormalizedPropertyNames": sorted(
            FORBIDDEN_NORMALIZED_PROPERTY_NAMES
        ),
        "forbiddenNormalizedPropertyPrefixes": list(
            FORBIDDEN_NORMALIZED_PROPERTY_PREFIXES
        ),
    }


def build_maven_environment(
    environment: Mapping[str, str],
    expected_candidate: str,
) -> dict[str, str]:
    validate_override_channels(environment)
    child = {
        name: value
        for name, value in environment.items()
        if environment_property_equivalence_key(name)
        != environment_property_equivalence_key("spring.profiles.active")
    }
    child["PHASE00_EXPECTED_CANDIDATE_SHA"] = expected_candidate
    child["SPRING_PROFILES_ACTIVE"] = "dev"
    return child


def preflight_maven_command(maven_executable: str) -> list[str]:
    return [
        maven_executable,
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
    ]


def formal_maven_command(maven_executable: str) -> list[str]:
    return [
        maven_executable,
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
    ]


def strict_json_bytes(
    content: bytes,
    label: str,
    *,
    maximum: int = MAX_STRICT_JSON_BYTES,
) -> Any:
    if len(content) > maximum:
        raise GateError(f"{label} exceeds {maximum} bytes")
    try:
        text = content.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise GateError(f"{label} is not UTF-8") from exc

    def reject_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"duplicate JSON field: {key}")
            result[key] = value
        return result

    def reject_constant(value: str) -> None:
        raise ValueError(f"non-finite JSON number: {value}")

    try:
        return json.loads(
            text,
            object_pairs_hook=reject_duplicates,
            parse_constant=reject_constant,
        )
    except (json.JSONDecodeError, ValueError) as exc:
        raise GateError(f"{label} is not strict JSON: {exc}") from exc


def read_regular_json_source(
    root: Path,
    relative_path: str,
    label: str,
    *,
    fresh_after_ns: int | None,
) -> tuple[Any, bytes, str, dict[str, int]]:
    path = resolve_contained_path(
        root,
        relative_path,
        label,
        must_exist=True,
    )
    if path_is_link_or_reparse(path):
        raise GateError(f"{label} must not be a symlink/reparse point")
    try:
        before = os.lstat(path)
    except OSError as exc:
        raise GateError(f"cannot stat {label}: {exc}") from exc
    if not stat.S_ISREG(before.st_mode):
        raise GateError(f"{label} must be a regular file")
    if (
        fresh_after_ns is not None
        and before.st_mtime_ns < fresh_after_ns - 2_000_000_000
    ):
        raise GateError(f"{label} predates this gate run and is stale")
    try:
        content = path.read_bytes()
        after = os.lstat(path)
    except OSError as exc:
        raise GateError(f"cannot read {label}: {exc}") from exc
    before_identity = (
        before.st_dev,
        before.st_ino,
        before.st_size,
        before.st_mtime_ns,
    )
    after_identity = (
        after.st_dev,
        after.st_ino,
        after.st_size,
        after.st_mtime_ns,
    )
    if before_identity != after_identity or not stat.S_ISREG(after.st_mode):
        raise GateError(f"{label} changed while it was being read")
    return (
        strict_json_bytes(content, label),
        content,
        sha256_bytes(content),
        {"mtimeNs": before.st_mtime_ns, "bytes": before.st_size},
    )


def validate_service_manifest(
    value: Any,
    *,
    label: str,
    allowed_schemes: set[str],
    resource_name: str,
    marker_required: bool,
) -> dict[str, Any]:
    expected_keys = {"scheme", "host", "port", resource_name}
    if marker_required:
        expected_keys.add("marker")
    record = require_exact_keys(value, expected_keys, label)
    scheme = record["scheme"]
    if not isinstance(scheme, str) or scheme not in allowed_schemes:
        raise GateError(f"{label}.scheme is invalid")
    host = normalize_host(record["host"], f"{label}.host")
    if record["host"] != host:
        raise GateError(f"{label}.host is not canonical")
    port = parse_port(record["port"], f"{label}.port")
    if not isinstance(record["port"], int) or isinstance(record["port"], bool):
        raise GateError(f"{label}.port must be a JSON integer")
    if resource_name == "database" and scheme == "redis":
        resource: str | int = parse_non_negative_database(
            record[resource_name],
            f"{label}.{resource_name}",
        )
        if not isinstance(record[resource_name], int) or isinstance(
            record[resource_name], bool
        ):
            raise GateError(f"{label}.{resource_name} must be a JSON integer")
    elif resource_name == "database":
        resource = validate_schema(
            require_clean_text(record[resource_name], f"{label}.{resource_name}"),
            f"{label}.{resource_name}",
        )
    else:
        resource = validate_bucket(
            require_clean_text(record[resource_name], f"{label}.{resource_name}"),
            f"{label}.{resource_name}",
        )
    normalized = service_record(
        scheme=scheme,
        host=host,
        port=port,
        resource_name=resource_name,
        resource=resource,
        marker=marker_required,
    )
    if dict(record) != normalized:
        raise GateError(f"{label} is not canonical")
    return normalized


def validate_declared_and_configured_targets(
    declared_value: Any,
    configured_value: Any,
) -> tuple[dict[str, Any], dict[str, Any]]:
    declared = require_exact_keys(
        declared_value,
        {"schema", "mysql", "redis", "minio", "runContext"},
        "targets.declared",
    )
    configured = require_exact_keys(
        configured_value,
        {"mysql", "redis", "minio", "minioPublic"},
        "targets.configured",
    )
    schema = validate_schema(
        require_clean_text(declared["schema"], "targets.declared.schema"),
        "targets.declared.schema",
    )
    run_context = require_clean_text(
        declared["runContext"],
        "targets.declared.runContext",
    )
    if not SAFE_MARKER_RE.fullmatch(run_context) or SENSITIVE_NAME_RE.search(run_context):
        raise GateError("targets.declared.runContext is not a safe non-secret marker")
    normalized_declared = {
        "schema": schema,
        "mysql": validate_service_manifest(
            declared["mysql"],
            label="targets.declared.mysql",
            allowed_schemes={"mysql"},
            resource_name="database",
            marker_required=True,
        ),
        "redis": validate_service_manifest(
            declared["redis"],
            label="targets.declared.redis",
            allowed_schemes={"redis"},
            resource_name="database",
            marker_required=True,
        ),
        "minio": validate_service_manifest(
            declared["minio"],
            label="targets.declared.minio",
            allowed_schemes={"http", "https"},
            resource_name="bucket",
            marker_required=True,
        ),
        "runContext": run_context,
    }
    normalized_configured = {
        "mysql": validate_service_manifest(
            configured["mysql"],
            label="targets.configured.mysql",
            allowed_schemes={"mysql"},
            resource_name="database",
            marker_required=False,
        ),
        "redis": validate_service_manifest(
            configured["redis"],
            label="targets.configured.redis",
            allowed_schemes={"redis"},
            resource_name="database",
            marker_required=False,
        ),
        "minio": validate_service_manifest(
            configured["minio"],
            label="targets.configured.minio",
            allowed_schemes={"http", "https"},
            resource_name="bucket",
            marker_required=False,
        ),
        "minioPublic": validate_service_manifest(
            configured["minioPublic"],
            label="targets.configured.minioPublic",
            allowed_schemes={"http", "https"},
            resource_name="bucket",
            marker_required=False,
        ),
    }
    if normalized_configured["minioPublic"] != normalized_configured["minio"]:
        raise GateError(
            "targets.configured.minioPublic does not match targets.configured.minio"
        )
    if normalized_declared["schema"] != normalized_configured["mysql"]["database"]:
        raise GateError("targets.declared.schema does not match configured MySQL")
    for service in ("mysql", "redis", "minio"):
        if (
            comparable_service(normalized_declared[service])
            != normalized_configured[service]
        ):
            raise GateError(
                f"targets.declared.{service} does not match targets.configured.{service}"
            )
    return normalized_declared, normalized_configured


def validate_expected_manifest(
    value: Any,
    expected_candidate: str,
) -> dict[str, Any]:
    expected = require_exact_keys(
        value,
        {"candidateSha", "mysql", "redis", "minio"},
        "targets.expected",
    )
    candidate = require_clean_text(
        expected["candidateSha"],
        "targets.expected.candidateSha",
    ).lower()
    validate_candidate(candidate, expected_candidate)
    mysql = require_exact_keys(
        expected["mysql"],
        {"serverUuid"},
        "targets.expected.mysql",
    )
    redis = require_exact_keys(
        expected["redis"],
        {"runId"},
        "targets.expected.redis",
    )
    minio = require_exact_keys(
        expected["minio"],
        {
            "identityObject",
            "identitySha256",
            "identityNonce",
            "identityIssuedAt",
        },
        "targets.expected.minio",
    )
    mysql_uuid = require_clean_text(
        mysql["serverUuid"],
        "targets.expected.mysql.serverUuid",
    )
    redis_run_id = require_clean_text(
        redis["runId"],
        "targets.expected.redis.runId",
    )
    minio_sha = require_clean_text(
        minio["identitySha256"],
        "targets.expected.minio.identitySha256",
    )
    minio_nonce = require_clean_text(
        minio["identityNonce"],
        "targets.expected.minio.identityNonce",
    )
    if (
        mysql_uuid != mysql_uuid.lower()
        or not MYSQL_UUID_RE.fullmatch(mysql_uuid)
    ):
        raise GateError("targets.expected.mysql.serverUuid is not canonical")
    if (
        redis_run_id != redis_run_id.lower()
        or not REDIS_RUN_ID_RE.fullmatch(redis_run_id)
    ):
        raise GateError("targets.expected.redis.runId is not canonical")
    if minio_sha != minio_sha.lower() or not SHA256_RE.fullmatch(minio_sha):
        raise GateError("targets.expected.minio.identitySha256 is not canonical")
    if minio_nonce != minio_nonce.lower() or not SHA256_RE.fullmatch(minio_nonce):
        raise GateError("targets.expected.minio.identityNonce is not canonical")
    normalized = {
        "candidateSha": candidate,
        "mysql": {"serverUuid": mysql_uuid},
        "redis": {"runId": redis_run_id},
        "minio": {
            "identityObject": validate_identity_object(
                minio["identityObject"],
                "targets.expected.minio.identityObject",
            ),
            "identitySha256": minio_sha,
            "identityNonce": minio_nonce,
            "identityIssuedAt": validate_identity_issued_at(
                minio["identityIssuedAt"],
                "targets.expected.minio.identityIssuedAt",
            ),
        },
    }
    if dict(expected) != normalized:
        raise GateError("targets.expected is not canonical")
    return normalized


def validate_runtime_identity(
    value: Any,
    target_spec: Mapping[str, Any],
    declared: Mapping[str, Any],
    configured: Mapping[str, Any],
    expected: Mapping[str, Any],
    *,
    expected_producer: str | None = None,
) -> dict[str, Any]:
    identity = require_exact_keys(
        value,
        {
            "schemaVersion",
            "producerSuite",
            "candidateSha",
            "runContext",
            "mysql",
            "redis",
            "minio",
            "freshness",
        },
        "runtime target identity",
    )
    if (
        isinstance(identity["schemaVersion"], bool)
        or identity["schemaVersion"] != target_spec["schemaVersion"]
    ):
        raise GateError("runtime target identity schemaVersion mismatch")
    producer = (
        target_spec["producerSuite"]
        if expected_producer is None
        else expected_producer
    )
    if identity["producerSuite"] != producer:
        raise GateError("runtime target identity producerSuite mismatch")
    candidate = require_clean_text(
        identity["candidateSha"],
        "runtime target identity candidateSha",
    )
    if candidate != candidate.lower() or candidate != expected["candidateSha"]:
        raise GateError("runtime target identity candidateSha mismatch")
    if identity["runContext"] != declared["runContext"]:
        raise GateError("runtime target identity runContext mismatch")

    mysql = require_exact_keys(
        identity["mysql"],
        {"database", "version", "serverUuid"},
        "runtime target identity mysql",
    )
    mysql_database = require_clean_text(
        mysql["database"], "runtime target identity mysql.database"
    )
    mysql_version = require_clean_text(
        mysql["version"], "runtime target identity mysql.version"
    )
    mysql_uuid = require_clean_text(
        mysql["serverUuid"], "runtime target identity mysql.serverUuid"
    )
    if mysql_database != configured["mysql"]["database"]:
        raise GateError("runtime MySQL database does not match configured target")
    if re.match(target_spec["mysqlVersionPattern"], mysql_version) is None:
        raise GateError("runtime MySQL version does not match gate spec")
    if (
        mysql_uuid != mysql_uuid.lower()
        or not MYSQL_UUID_RE.fullmatch(mysql_uuid)
        or mysql_uuid != expected["mysql"]["serverUuid"]
    ):
        raise GateError("runtime MySQL server UUID does not match expected identity")

    redis = require_exact_keys(
        identity["redis"],
        {"version", "runId", "database"},
        "runtime target identity redis",
    )
    redis_version = require_clean_text(
        redis["version"], "runtime target identity redis.version"
    )
    redis_run_id = require_clean_text(
        redis["runId"], "runtime target identity redis.runId"
    )
    redis_database = parse_non_negative_database(
        redis["database"], "runtime target identity redis.database"
    )
    if not isinstance(redis["database"], int) or isinstance(redis["database"], bool):
        raise GateError("runtime target identity redis.database must be a JSON integer")
    if re.match(target_spec["redisVersionPattern"], redis_version) is None:
        raise GateError("runtime Redis version does not match gate spec")
    if (
        redis_run_id != redis_run_id.lower()
        or not REDIS_RUN_ID_RE.fullmatch(redis_run_id)
        or redis_run_id != expected["redis"]["runId"]
    ):
        raise GateError("runtime Redis run ID does not match expected identity")
    if redis_database != configured["redis"]["database"]:
        raise GateError("runtime Redis database does not match configured target")

    minio = require_exact_keys(
        identity["minio"],
        {
            "endpoint",
            "bucket",
            "identityObject",
            "identitySha256",
            "identityNonce",
            "identityIssuedAt",
        },
        "runtime target identity minio",
    )
    endpoint = require_clean_text(
        minio["endpoint"], "runtime target identity minio.endpoint"
    )
    bucket = validate_bucket(
        require_clean_text(
            minio["bucket"], "runtime target identity minio.bucket"
        ),
        "runtime target identity minio.bucket",
    )
    if bucket != configured["minio"]["bucket"]:
        raise GateError("runtime MinIO bucket does not match configured target")
    if parse_minio_configuration(endpoint, bucket) != configured["minio"]:
        raise GateError("runtime MinIO endpoint does not match configured target")
    identity_object = validate_identity_object(
        minio["identityObject"],
        "runtime target identity minio.identityObject",
    )
    identity_sha = require_clean_text(
        minio["identitySha256"],
        "runtime target identity minio.identitySha256",
    )
    if identity_object != expected["minio"]["identityObject"]:
        raise GateError("runtime MinIO identity object does not match expected identity")
    if (
        identity_sha != identity_sha.lower()
        or not SHA256_RE.fullmatch(identity_sha)
        or identity_sha != expected["minio"]["identitySha256"]
    ):
        raise GateError("runtime MinIO identity SHA-256 does not match expected identity")
    identity_nonce = require_clean_text(
        minio["identityNonce"],
        "runtime target identity minio.identityNonce",
    )
    if identity_nonce != identity_nonce.lower() or not SHA256_RE.fullmatch(
        identity_nonce
    ):
        raise GateError("runtime MinIO identity nonce must be 64 lowercase hex")
    if identity_nonce != expected["minio"]["identityNonce"]:
        raise GateError("runtime MinIO identity nonce does not match expected identity")
    identity_issued_at = validate_identity_issued_at(
        minio["identityIssuedAt"],
        "runtime target identity minio.identityIssuedAt",
    )
    if identity_issued_at != expected["minio"]["identityIssuedAt"]:
        raise GateError("runtime MinIO identity issuedAt does not match expected identity")
    validate_target_freshness(identity["freshness"])
    return dict(identity)


def validate_target_freshness(value: Any) -> dict[str, int]:
    freshness = require_exact_keys(
        value,
        {
            "mysqlTableCountBefore",
            "redisDatabaseSizeBefore",
            "minioObjectCountBefore",
            "minioUnexpectedObjectCountBefore",
        },
        "runtime target identity freshness",
    )
    expected = {
        "mysqlTableCountBefore": 0,
        "redisDatabaseSizeBefore": 0,
        "minioObjectCountBefore": 1,
        "minioUnexpectedObjectCountBefore": 0,
    }
    for name, expected_value in expected.items():
        actual = freshness[name]
        if (
            not isinstance(actual, int)
            or isinstance(actual, bool)
            or actual != expected_value
        ):
            raise GateError(
                f"runtime target identity freshness.{name} must be "
                f"{expected_value}, observed={actual!r}"
            )
    return expected


def identity_binding_view(identity: Mapping[str, Any]) -> dict[str, Any]:
    return {
        key: value
        for key, value in identity.items()
        if key != "producerSuite"
    }


def validate_preflight_identity_freshness(
    identity: Mapping[str, Any],
    preflight_started_ns: int,
) -> None:
    minio = identity.get("minio")
    if not isinstance(minio, Mapping):
        raise GateError("preflight identity MinIO block is missing")
    issued = parse_utc(
        str(minio.get("identityIssuedAt", "")),
        "preflight identity minio.identityIssuedAt",
    )
    started = dt.datetime.fromtimestamp(
        preflight_started_ns / 1_000_000_000,
        tz=dt.timezone.utc,
    )
    earliest = started - dt.timedelta(minutes=15)
    latest = started + dt.timedelta(minutes=2)
    if issued < earliest or issued > latest:
        raise GateError(
            "preflight MinIO identity issuedAt is outside the allowed "
            "[-15m,+2m] gate-start window"
        )


def require_matching_preflight_and_runtime(
    preflight: Mapping[str, Any],
    runtime: Mapping[str, Any],
) -> None:
    if identity_binding_view(preflight) != identity_binding_view(runtime):
        raise GateError(
            "runtime target identity does not exactly match preflight identity"
        )


def validate_manifest_target_preamble(
    value: Any,
    expected_candidate: str,
) -> tuple[
    Mapping[str, Any],
    dict[str, Any],
    dict[str, Any],
    dict[str, Any],
]:
    targets = require_exact_keys(
        value,
        {
            "declared",
            "configured",
            "expected",
            "preflight",
            "runtime",
            "evidence",
            "constraints",
        },
        "manifest targets",
    )
    constraints = require_exact_keys(
        targets["constraints"],
        {
            "springProfilesActive",
            "relaxedBindingAliasesRejected",
            "canonicalTargetEnvironmentNames",
            "forbiddenEnvironmentNames",
            "forbiddenEnvironmentPrefixes",
            "forbiddenNormalizedPropertyNames",
            "forbiddenNormalizedPropertyPrefixes",
        },
        "targets.constraints",
    )
    if dict(constraints) != target_constraint_policy():
        raise GateError("targets.constraints does not match the gate policy")
    declared, configured = validate_declared_and_configured_targets(
        targets["declared"],
        targets["configured"],
    )
    expected = validate_expected_manifest(
        targets["expected"],
        expected_candidate,
    )
    return targets, declared, configured, expected


def verify_one_target_identity_artifact(
    *,
    evidence: EvidenceSnapshot,
    manifest_identity: Any,
    evidence_value: Any,
    target_spec: Mapping[str, Any],
    declared: Mapping[str, Any],
    configured: Mapping[str, Any],
    expected: Mapping[str, Any],
    records: Mapping[str, Mapping[str, Any]],
    source_path: str,
    artifact_path: str,
    artifact_kind: str,
    producer: str,
    started_ns: int,
    label: str,
) -> dict[str, Any]:
    evidence_provenance = require_exact_keys(
        evidence_value,
        {
            "sourcePath",
            "artifactPath",
            "sourceSha256",
            "sourceMtimeNs",
            "sourceBytes",
        },
        f"{label} evidence",
    )
    if (
        evidence_provenance["sourcePath"] != source_path
        or evidence_provenance["artifactPath"] != artifact_path
        or not isinstance(evidence_provenance["sourceSha256"], str)
        or not SHA256_RE.fullmatch(evidence_provenance["sourceSha256"])
        or not isinstance(evidence_provenance["sourceMtimeNs"], int)
        or isinstance(evidence_provenance["sourceMtimeNs"], bool)
        or evidence_provenance["sourceMtimeNs"] < started_ns - 2_000_000_000
        or not isinstance(evidence_provenance["sourceBytes"], int)
        or isinstance(evidence_provenance["sourceBytes"], bool)
        or evidence_provenance["sourceBytes"] < 1
    ):
        raise GateError(f"{label} evidence provenance or freshness is invalid")
    record = records.get(artifact_path)
    if not isinstance(record, Mapping):
        raise GateError(f"archived {label} artifact is missing")
    require_source_provenance(
        record,
        expected_source_path=source_path,
        expected_kind=artifact_kind,
        expected_source_sha256=evidence_provenance["sourceSha256"],
        source_retained=True,
        label=f"{label} artifact",
    )
    if (
        record.get("sha256") != evidence_provenance["sourceSha256"]
        or record.get("bytes") != evidence_provenance["sourceBytes"]
        or sum(
            1
            for candidate in records.values()
            if candidate.get("kind") == artifact_kind
        )
        != 1
    ):
        raise GateError(f"{label} artifact hash/size is not exact")
    archived_file = evidence.require(
        artifact_path,
        f"archived {label}",
    )
    archived_identity = strict_json_bytes(
        archived_file.content,
        f"archived {label}",
    )
    if (
        archived_file.sha256 != record.get("sha256")
        or archived_file.size != record.get("bytes")
    ):
        raise GateError(f"archived {label} changed during verification")
    validated = validate_runtime_identity(
        archived_identity,
        target_spec,
        declared,
        configured,
        expected,
        expected_producer=producer,
    )
    if manifest_identity != archived_identity:
        raise GateError(f"manifest {label} differs from the archived JSON")
    return validated


def verify_runtime_target_artifact(
    *,
    evidence: EvidenceSnapshot,
    targets_value: Any,
    target_spec: Mapping[str, Any],
    expected_candidate: str,
    records: Mapping[str, Mapping[str, Any]],
    preflight_started_ns: int,
    runtime_started_ns: int,
) -> dict[str, Any]:
    targets, declared, configured, expected = validate_manifest_target_preamble(
        targets_value,
        expected_candidate,
    )
    target_evidence = require_exact_keys(
        targets["evidence"],
        {"preflight", "runtime"},
        "targets.evidence",
    )
    preflight = verify_one_target_identity_artifact(
        evidence=evidence,
        manifest_identity=targets["preflight"],
        evidence_value=target_evidence["preflight"],
        target_spec=target_spec,
        declared=declared,
        configured=configured,
        expected=expected,
        records=records,
        source_path=target_spec["preflightPath"],
        artifact_path=PREFLIGHT_IDENTITY_ARTIFACT,
        artifact_kind=PREFLIGHT_IDENTITY_KIND,
        producer=target_spec["preflightProducer"],
        started_ns=preflight_started_ns,
        label="preflight target identity",
    )
    runtime = verify_one_target_identity_artifact(
        evidence=evidence,
        manifest_identity=targets["runtime"],
        evidence_value=target_evidence["runtime"],
        target_spec=target_spec,
        declared=declared,
        configured=configured,
        expected=expected,
        records=records,
        source_path=target_spec["path"],
        artifact_path=TARGET_IDENTITY_ARTIFACT,
        artifact_kind=TARGET_IDENTITY_KIND,
        producer=target_spec["producerSuite"],
        started_ns=runtime_started_ns,
        label="runtime target identity",
    )
    validate_preflight_identity_freshness(preflight, preflight_started_ns)
    require_matching_preflight_and_runtime(preflight, runtime)
    return runtime


def safe_relative_path(value: str, label: str) -> PurePosixPath:
    if (
        not value
        or "\\" in value
        or "\x00" in value
        or ":" in value
        or any(ord(character) < 0x20 for character in value)
        or any(character in value for character in '"<>|?*')
    ):
        raise GateError(f"{label} must use non-empty forward-slash path segments")
    windows_path = PureWindowsPath(value)
    if windows_path.drive or windows_path.root:
        raise GateError(f"{label} must not use a Windows drive or UNC/root path")
    segments = value.split("/")
    if any(segment in {"", ".", ".."} for segment in segments):
        raise GateError(
            f"{label} must not contain empty, current, or parent path segments"
        )
    for segment in segments:
        if segment.endswith((" ", ".")):
            raise GateError(
                f"{label} must not contain Windows-normalised trailing characters"
            )
        basename = segment.split(".", 1)[0].rstrip(" ").upper()
        if basename in WINDOWS_RESERVED_NAMES:
            raise GateError(f"{label} contains a reserved Windows device name")
    candidate = PurePosixPath(value)
    if candidate.is_absolute() or not candidate.parts:
        raise GateError(f"{label} must be a safe repository-relative path: {value!r}")
    return candidate


def path_is_link_or_reparse(path: Path) -> bool:
    """Detect symlinks and Windows reparse points without following them."""

    try:
        metadata = os.lstat(path)
    except FileNotFoundError:
        return False
    except OSError as exc:
        raise GateError(f"cannot inspect path metadata before read: {path}: {exc}") from exc
    if stat.S_ISLNK(metadata.st_mode):
        return True
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    if getattr(metadata, "st_file_attributes", 0) & reparse_flag:
        return True
    is_junction = getattr(path, "is_junction", None)
    try:
        return bool(is_junction and is_junction())
    except OSError as exc:
        raise GateError(f"cannot inspect junction metadata before read: {path}: {exc}") from exc


def resolve_contained_path(
    root: Path,
    relative_value: str,
    label: str,
    *,
    must_exist: bool,
) -> Path:
    """Resolve an untrusted relative path without leaving or linking out of root."""

    relative = safe_relative_path(relative_value, label)
    root_absolute = root.absolute()
    if path_is_link_or_reparse(root_absolute):
        raise GateError(f"{label} root must not be a symlink/reparse point: {root}")
    if not root_absolute.is_dir():
        raise GateError(f"{label} root is not a directory: {root}")
    root_resolved = root_absolute.resolve(strict=True)

    current = root_absolute
    for segment in relative.parts:
        current = current / segment
        if path_is_link_or_reparse(current):
            raise GateError(
                f"{label} path chain contains a symlink/reparse point: {current}"
            )
    candidate = current.resolve(strict=False)
    if not candidate.is_relative_to(root_resolved):
        raise GateError(f"{label} resolves outside its allowed root: {relative_value!r}")
    if must_exist and not candidate.exists():
        raise GateError(f"{label} does not exist: {relative_value!r}")
    return candidate


def _snapshot_file_from_bytes(
    relative_path: str,
    content: bytes,
    *,
    size: int,
    mtime_ns: int,
    file_id: tuple[int, int],
) -> EvidenceFileSnapshot:
    if len(content) != size:
        raise GateError(
            f"evidence file size changed while reading: {relative_path}"
        )
    return EvidenceFileSnapshot(
        relative_path=relative_path,
        content=content,
        sha256=sha256_bytes(content),
        size=size,
        mtime_ns=mtime_ns,
        file_id=file_id,
    )


@contextmanager
def locked_posix_evidence_ancestor_chain(
    root: Path,
    *,
    directory_flags: int,
) -> Iterator[int]:
    """Open every absolute POSIX root segment without following a link."""

    root_text = os.fspath(root)
    segments = [] if root_text == "/" else root_text.split("/")[1:]
    if (
        not root_text.startswith("/")
        or root_text.startswith("//")
        or any(segment in {"", ".", ".."} for segment in segments)
    ):
        raise GateError(
            "POSIX evidence root must be an unambiguous absolute path"
        )

    handles: list[int] = []
    edges: list[tuple[int, str, tuple[int, int]]] = []
    try:
        try:
            anchor_fd = os.open("/", directory_flags)
        except OSError as exc:
            raise GateError("cannot open POSIX evidence root anchor") from exc
        handles.append(anchor_fd)
        anchor_metadata = os.fstat(anchor_fd)
        if not stat.S_ISDIR(anchor_metadata.st_mode):
            raise GateError("POSIX evidence root anchor is not a directory")

        parent_fd = anchor_fd
        for segment in segments:
            try:
                before = os.stat(
                    segment,
                    dir_fd=parent_fd,
                    follow_symlinks=False,
                )
            except OSError as exc:
                raise GateError(
                    "cannot inspect POSIX evidence ancestor before anchored open"
                ) from exc
            if not stat.S_ISDIR(before.st_mode):
                raise GateError(
                    "POSIX evidence ancestor must be a non-symlink directory"
                )
            try:
                child_fd = os.open(
                    segment,
                    directory_flags,
                    dir_fd=parent_fd,
                )
            except OSError as exc:
                raise GateError(
                    "cannot open POSIX evidence ancestor without following links"
                ) from exc
            try:
                after = os.fstat(child_fd)
                identity = (before.st_dev, before.st_ino)
                if (
                    not stat.S_ISDIR(after.st_mode)
                    or (after.st_dev, after.st_ino) != identity
                ):
                    raise GateError(
                        "POSIX evidence ancestor changed before anchored open"
                    )
            except BaseException:
                os.close(child_fd)
                raise
            handles.append(child_fd)
            edges.append((parent_fd, segment, identity))
            parent_fd = child_fd

        yield handles[-1]

        for parent_fd, segment, identity in edges:
            try:
                after = os.stat(
                    segment,
                    dir_fd=parent_fd,
                    follow_symlinks=False,
                )
            except OSError as exc:
                raise GateError(
                    "cannot re-inspect POSIX evidence ancestor"
                ) from exc
            if (
                not stat.S_ISDIR(after.st_mode)
                or (after.st_dev, after.st_ino) != identity
            ):
                raise GateError(
                    "POSIX evidence ancestor changed while evidence was captured"
                )
    finally:
        for handle in reversed(handles):
            os.close(handle)


def _capture_evidence_posix(root: Path) -> dict[str, EvidenceFileSnapshot]:
    """Capture evidence with openat/O_NOFOLLOW; no checked path is reopened."""

    nofollow = getattr(os, "O_NOFOLLOW", None)
    directory_flag = getattr(os, "O_DIRECTORY", None)
    if nofollow is None or directory_flag is None:
        raise GateError(
            "this platform cannot verify untrusted evidence with no-follow handles"
        )
    close_on_exec = getattr(os, "O_CLOEXEC", 0)
    root_flags = os.O_RDONLY | directory_flag | nofollow | close_on_exec
    file_flags = os.O_RDONLY | nofollow | close_on_exec

    files: dict[str, EvidenceFileSnapshot] = {}
    casefold_paths: dict[str, str] = {}
    total_bytes = 0

    def register(
        relative_path: str,
        content: bytes,
        before: os.stat_result,
        after: os.stat_result,
    ) -> None:
        nonlocal total_bytes
        before_identity = (
            before.st_dev,
            before.st_ino,
            before.st_size,
            before.st_mtime_ns,
            before.st_ctime_ns,
        )
        after_identity = (
            after.st_dev,
            after.st_ino,
            after.st_size,
            after.st_mtime_ns,
            after.st_ctime_ns,
        )
        if before_identity != after_identity or not stat.S_ISREG(after.st_mode):
            raise GateError(
                f"evidence file changed on its anchored handle: {relative_path}"
            )
        folded = relative_path.casefold()
        collision = casefold_paths.get(folded)
        if collision is not None and collision != relative_path:
            raise GateError(
                "evidence contains a case-insensitive path collision: "
                f"{collision!r}, {relative_path!r}"
            )
        if relative_path in files:
            raise GateError(f"duplicate evidence path: {relative_path}")
        if len(files) >= MAX_EVIDENCE_FILES:
            raise GateError(
                f"evidence exceeds {MAX_EVIDENCE_FILES} regular files"
            )
        total_bytes += len(content)
        if total_bytes > MAX_EVIDENCE_TOTAL_BYTES:
            raise GateError(
                "evidence exceeds the bounded in-memory verification size"
            )
        casefold_paths[folded] = relative_path
        files[relative_path] = _snapshot_file_from_bytes(
            relative_path,
            content,
            size=before.st_size,
            mtime_ns=before.st_mtime_ns,
            file_id=(before.st_dev, before.st_ino),
        )

    def recurse(directory_fd: int, prefix: PurePosixPath | None) -> None:
        try:
            names_before = sorted(os.listdir(directory_fd))
        except OSError as exc:
            raise GateError("cannot enumerate anchored evidence directory") from exc
        for name in names_before:
            relative = (
                PurePosixPath(name)
                if prefix is None
                else prefix / name
            )
            relative_text = safe_relative_path(
                relative.as_posix(),
                "evidence snapshot path",
            ).as_posix()
            try:
                metadata = os.stat(
                    name,
                    dir_fd=directory_fd,
                    follow_symlinks=False,
                )
            except OSError as exc:
                raise GateError(
                    f"cannot inspect anchored evidence entry: {relative_text}"
                ) from exc
            if stat.S_ISLNK(metadata.st_mode):
                raise GateError(
                    "evidence directory contains a symlink/reparse point: "
                    f"{relative_text}"
                )
            if stat.S_ISDIR(metadata.st_mode):
                try:
                    child_fd = os.open(name, root_flags, dir_fd=directory_fd)
                except OSError as exc:
                    raise GateError(
                        f"cannot open anchored evidence directory: {relative_text}"
                    ) from exc
                try:
                    child_metadata = os.fstat(child_fd)
                    if (
                        not stat.S_ISDIR(child_metadata.st_mode)
                        or (
                            child_metadata.st_dev,
                            child_metadata.st_ino,
                        )
                        != (metadata.st_dev, metadata.st_ino)
                    ):
                        raise GateError(
                            "evidence directory changed before anchored open: "
                            f"{relative_text}"
                        )
                    recurse(child_fd, relative)
                finally:
                    os.close(child_fd)
                continue
            if not stat.S_ISREG(metadata.st_mode):
                raise GateError(
                    f"evidence contains a non-regular entry: {relative_text}"
                )
            try:
                file_fd = os.open(name, file_flags, dir_fd=directory_fd)
            except OSError as exc:
                raise GateError(
                    f"cannot open anchored evidence file: {relative_text}"
                ) from exc
            try:
                before = os.fstat(file_fd)
                if (
                    not stat.S_ISREG(before.st_mode)
                    or (before.st_dev, before.st_ino)
                    != (metadata.st_dev, metadata.st_ino)
                ):
                    raise GateError(
                        "evidence file changed before anchored open: "
                        f"{relative_text}"
                    )
                if (
                    before.st_size < 0
                    or before.st_size > MAX_EVIDENCE_TOTAL_BYTES
                ):
                    raise GateError(
                        f"evidence file exceeds the bounded size: {relative_text}"
                    )
                chunks: list[bytes] = []
                remaining = before.st_size
                while remaining:
                    chunk = os.read(file_fd, min(1024 * 1024, remaining))
                    if not chunk:
                        raise GateError(
                            f"evidence file ended early: {relative_text}"
                        )
                    chunks.append(chunk)
                    remaining -= len(chunk)
                if os.read(file_fd, 1):
                    raise GateError(
                        f"evidence file grew while reading: {relative_text}"
                    )
                after = os.fstat(file_fd)
                register(relative_text, b"".join(chunks), before, after)
            finally:
                os.close(file_fd)
        try:
            names_after = sorted(os.listdir(directory_fd))
        except OSError as exc:
            raise GateError("cannot re-enumerate anchored evidence directory") from exc
        if names_after != names_before:
            raise GateError("evidence directory changed while it was captured")

    with locked_posix_evidence_ancestor_chain(
        root,
        directory_flags=root_flags,
    ) as root_fd:
        root_metadata = os.fstat(root_fd)
        if not stat.S_ISDIR(root_metadata.st_mode):
            raise GateError("evidence root must be a directory")
        recurse(root_fd, None)
    return files


def _normalise_windows_handle_path(value: str) -> str:
    if value.startswith("\\\\?\\UNC\\"):
        value = "\\\\" + value[len("\\\\?\\UNC\\") :]
    elif value.startswith("\\\\?\\"):
        value = value[len("\\\\?\\") :]
    if value.startswith("\\\\.\\") or value.startswith("\\Device\\"):
        raise GateError("evidence handle resolved to an unsupported device path")
    return ntpath.normcase(ntpath.normpath(value))


def _windows_path_is_contained(root: str, candidate: str) -> bool:
    try:
        return ntpath.commonpath([root, candidate]) == root and candidate != root
    except ValueError:
        return False


WINDOWS_GENERIC_READ = 0x80000000
WINDOWS_FILE_READ_ATTRIBUTES = 0x00000080
WINDOWS_FILE_LIST_DIRECTORY = 0x00000001
WINDOWS_FILE_SHARE_READ = 0x00000001
WINDOWS_FILE_SHARE_WRITE = 0x00000002
WINDOWS_FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000
WINDOWS_FILE_FLAG_BACKUP_SEMANTICS = 0x02000000
WINDOWS_FILE_FLAG_SEQUENTIAL_SCAN = 0x08000000


def windows_evidence_open_contract(
    *,
    directory: bool,
) -> tuple[int, int, int]:
    """Return access/share/flags for locked Windows evidence handles."""

    access = (
        WINDOWS_FILE_LIST_DIRECTORY | WINDOWS_FILE_READ_ATTRIBUTES
        if directory
        else WINDOWS_GENERIC_READ | WINDOWS_FILE_READ_ATTRIBUTES
    )
    share = (
        WINDOWS_FILE_SHARE_READ | WINDOWS_FILE_SHARE_WRITE
        if directory
        else WINDOWS_FILE_SHARE_READ
    )
    flags = WINDOWS_FILE_FLAG_OPEN_REPARSE_POINT | (
        WINDOWS_FILE_FLAG_BACKUP_SEMANTICS
        if directory
        else WINDOWS_FILE_FLAG_SEQUENTIAL_SCAN
    )
    return access, share, flags


def windows_evidence_ancestor_paths(value: str) -> tuple[str, ...]:
    """Return every lexical ancestor from volume/share root to evidence root."""

    path = PureWindowsPath(value)
    if not path.is_absolute() or not path.anchor or len(path.parts) < 1:
        raise GateError("Windows evidence root must be an absolute path")
    current = PureWindowsPath(path.parts[0])
    ancestors = [str(current)]
    for segment in path.parts[1:]:
        current /= segment
        ancestors.append(str(current))
    return tuple(ancestors)


def validate_windows_evidence_handle_boundary(
    *,
    root_final: str,
    opened_final: str,
    attributes: int,
    expected_directory: bool,
    label: str,
) -> None:
    """Validate a Windows handle before any ReadFile call is permitted."""

    directory_flag = 0x00000010
    reparse_flag = 0x00000400
    if attributes & reparse_flag:
        raise GateError(f"{label} is a reparse-point handle")
    if bool(attributes & directory_flag) is not expected_directory:
        raise GateError(f"{label} changed filesystem type before anchored open")
    if not _windows_path_is_contained(root_final, opened_final):
        raise GateError(f"{label} handle resolves outside its allowed root")


def validate_windows_evidence_handle_stability(
    before: ValidatedWindowsEvidenceHandle,
    *,
    after_attributes: int,
    after_file_id: tuple[int, int],
    after_size: int,
    after_mtime_ns: int,
    label: str,
) -> None:
    """Reject identity, size, timestamp, or type drift on one open handle."""

    if (
        after_attributes != before.attributes
        or after_file_id != before.file_id
        or after_size != before.size
        or after_mtime_ns != before.mtime_ns
    ):
        raise GateError(f"{label} changed on its anchored handle")


@contextmanager
def locked_windows_evidence_ancestor_chain(
    root_value: str,
    *,
    open_directory: Callable[[str], int],
    get_attributes: Callable[[int], int],
    get_final_path: Callable[[int], str],
    close_handle: Callable[[int], Any],
) -> Iterator[tuple[tuple[int, str], ...]]:
    """Hold every non-reparse ancestor handle and always close in reverse."""

    directory_flag = 0x00000010
    reparse_flag = 0x00000400
    ancestor_handles: list[tuple[int, str]] = []
    try:
        previous_final: str | None = None
        for ancestor_path in windows_evidence_ancestor_paths(root_value):
            handle = open_directory(ancestor_path)
            try:
                attributes = get_attributes(handle)
                if (
                    not attributes & directory_flag
                    or attributes & reparse_flag
                ):
                    raise GateError(
                        "Windows evidence ancestor must be a regular "
                        f"non-reparse directory: {ancestor_path}"
                    )
                ancestor_final = get_final_path(handle)
                if (
                    previous_final is not None
                    and not _windows_path_is_contained(
                        previous_final,
                        ancestor_final,
                    )
                ):
                    raise GateError(
                        "Windows evidence ancestor chain changed while opening"
                    )
                ancestor_handles.append((handle, ancestor_final))
                previous_final = ancestor_final
            except BaseException:
                close_handle(handle)
                raise
        if not ancestor_handles:
            raise GateError("Windows evidence ancestor chain is empty")
        yield tuple(ancestor_handles)
    finally:
        for handle, _final in reversed(ancestor_handles):
            close_handle(handle)


_WindowsEvidenceReaderResult = TypeVar("_WindowsEvidenceReaderResult")


def consume_validated_windows_evidence_handle(
    *,
    root_final: str,
    handle: int,
    expected_directory: bool,
    label: str,
    inspect_handle: Callable[
        [int],
        tuple[int, tuple[int, int], int, int],
    ],
    get_final_path: Callable[[int], str],
    reader: Callable[
        [ValidatedWindowsEvidenceHandle, str],
        _WindowsEvidenceReaderResult,
    ],
    boundary_validator: Callable[..., None] = (
        validate_windows_evidence_handle_boundary
    ),
) -> _WindowsEvidenceReaderResult:
    """Issue a handle capability to ``reader`` only after boundary checks."""

    attributes, file_id, size, mtime_ns = inspect_handle(handle)
    opened_final = get_final_path(handle)
    boundary_validator(
        root_final=root_final,
        opened_final=opened_final,
        attributes=attributes,
        expected_directory=expected_directory,
        label=label,
    )
    validated = ValidatedWindowsEvidenceHandle(
        handle=handle,
        attributes=attributes,
        file_id=file_id,
        size=size,
        mtime_ns=mtime_ns,
    )
    return reader(validated, opened_final)


def _capture_evidence_windows(root: Path) -> dict[str, EvidenceFileSnapshot]:
    """Capture evidence through locked Windows handles before any content read."""

    import ctypes
    from ctypes import wintypes

    open_existing = 3
    file_attribute_directory = 0x00000010
    file_attribute_reparse_point = 0x00000400
    file_type_disk = 0x0001
    invalid_handle_value = ctypes.c_void_p(-1).value

    class FileTime(ctypes.Structure):
        _fields_ = [
            ("low", wintypes.DWORD),
            ("high", wintypes.DWORD),
        ]

    class ByHandleFileInformation(ctypes.Structure):
        _fields_ = [
            ("attributes", wintypes.DWORD),
            ("creation_time", FileTime),
            ("last_access_time", FileTime),
            ("last_write_time", FileTime),
            ("volume_serial", wintypes.DWORD),
            ("size_high", wintypes.DWORD),
            ("size_low", wintypes.DWORD),
            ("number_of_links", wintypes.DWORD),
            ("file_index_high", wintypes.DWORD),
            ("file_index_low", wintypes.DWORD),
        ]

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    create_file = kernel32.CreateFileW
    create_file.argtypes = [
        wintypes.LPCWSTR,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.LPVOID,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.HANDLE,
    ]
    create_file.restype = wintypes.HANDLE
    get_information = kernel32.GetFileInformationByHandle
    get_information.argtypes = [
        wintypes.HANDLE,
        ctypes.POINTER(ByHandleFileInformation),
    ]
    get_information.restype = wintypes.BOOL
    get_final_path = kernel32.GetFinalPathNameByHandleW
    get_final_path.argtypes = [
        wintypes.HANDLE,
        wintypes.LPWSTR,
        wintypes.DWORD,
        wintypes.DWORD,
    ]
    get_final_path.restype = wintypes.DWORD
    get_file_type = kernel32.GetFileType
    get_file_type.argtypes = [wintypes.HANDLE]
    get_file_type.restype = wintypes.DWORD
    read_file = kernel32.ReadFile
    read_file.argtypes = [
        wintypes.HANDLE,
        wintypes.LPVOID,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        wintypes.LPVOID,
    ]
    read_file.restype = wintypes.BOOL
    close_handle = kernel32.CloseHandle
    close_handle.argtypes = [wintypes.HANDLE]
    close_handle.restype = wintypes.BOOL

    def windows_error(label: str) -> GateError:
        return GateError(f"{label}: Windows error {ctypes.get_last_error()}")

    def open_handle(path: Path, *, directory: bool) -> int:
        access, share, flags = windows_evidence_open_contract(
            directory=directory,
        )
        handle = create_file(
            str(path),
            access,
            share,
            None,
            open_existing,
            flags,
            None,
        )
        if handle in (None, 0, invalid_handle_value):
            raise windows_error("cannot open anchored evidence handle")
        return int(handle)

    def information(handle: int) -> tuple[
        ByHandleFileInformation,
        tuple[int, int],
        int,
        int,
    ]:
        if get_file_type(handle) != file_type_disk:
            raise GateError("evidence handle is not a disk file")
        value = ByHandleFileInformation()
        if not get_information(handle, ctypes.byref(value)):
            raise windows_error("cannot inspect anchored evidence handle")
        file_id = (
            int(value.volume_serial),
            (int(value.file_index_high) << 32) | int(value.file_index_low),
        )
        size = (int(value.size_high) << 32) | int(value.size_low)
        mtime_ns = (
            (int(value.last_write_time.high) << 32)
            | int(value.last_write_time.low)
        ) * 100
        return value, file_id, size, mtime_ns

    def inspect_handle(
        handle: int,
    ) -> tuple[int, tuple[int, int], int, int]:
        value, file_id, size, mtime_ns = information(handle)
        return int(value.attributes), file_id, size, mtime_ns

    def final_path(handle: int) -> str:
        buffer = ctypes.create_unicode_buffer(32_768)
        length = get_final_path(handle, buffer, len(buffer), 0)
        if length == 0 or length >= len(buffer):
            raise windows_error("cannot resolve anchored evidence handle path")
        return _normalise_windows_handle_path(buffer.value)

    def read_locked_file(
        validated: ValidatedWindowsEvidenceHandle,
        relative_path: str,
    ) -> EvidenceFileSnapshot:
        handle = validated.handle
        before_attributes = validated.attributes
        before_id = validated.file_id
        before_size = validated.size
        before_mtime = validated.mtime_ns
        if (
            before_attributes & file_attribute_directory
            or before_attributes & file_attribute_reparse_point
        ):
            raise GateError(
                f"evidence file handle is not regular: {relative_path}"
            )
        if before_size > MAX_EVIDENCE_TOTAL_BYTES:
            raise GateError(
                f"evidence file exceeds the bounded size: {relative_path}"
            )
        chunks: list[bytes] = []
        remaining = before_size
        buffer = ctypes.create_string_buffer(1024 * 1024)
        while remaining:
            requested = min(len(buffer), remaining)
            completed = wintypes.DWORD()
            if not read_file(
                handle,
                buffer,
                requested,
                ctypes.byref(completed),
                None,
            ):
                raise windows_error("cannot read anchored evidence file")
            if completed.value == 0:
                raise GateError(
                    f"evidence file ended early: {relative_path}"
                )
            chunks.append(buffer.raw[: completed.value])
            remaining -= completed.value
        completed = wintypes.DWORD()
        if not read_file(
            handle,
            buffer,
            1,
            ctypes.byref(completed),
            None,
        ):
            raise windows_error("cannot finish anchored evidence read")
        if completed.value:
            raise GateError(f"evidence file grew while reading: {relative_path}")
        (
            after_attributes,
            after_id,
            after_size,
            after_mtime,
        ) = inspect_handle(handle)
        validate_windows_evidence_handle_stability(
            validated,
            after_attributes=after_attributes,
            after_file_id=after_id,
            after_size=after_size,
            after_mtime_ns=after_mtime,
            label=f"evidence file {relative_path}",
        )
        return _snapshot_file_from_bytes(
            relative_path,
            b"".join(chunks),
            size=before_size,
            mtime_ns=before_mtime,
            file_id=before_id,
        )

    files: dict[str, EvidenceFileSnapshot] = {}
    casefold_paths: dict[str, str] = {}
    total_bytes = 0
    with locked_windows_evidence_ancestor_chain(
        str(root),
        open_directory=lambda path: open_handle(
            Path(path),
            directory=True,
        ),
        get_attributes=lambda handle: inspect_handle(handle)[0],
        get_final_path=final_path,
        close_handle=close_handle,
    ) as ancestor_handles:
        root_handle, root_final = ancestor_handles[-1]

        def recurse(
            directory_handle: int,
            expected_final: str,
            prefix: PurePosixPath | None,
        ) -> None:
            nonlocal total_bytes
            directory_final = final_path(directory_handle)
            if directory_final != expected_final:
                raise GateError(
                    "evidence directory handle path changed during capture"
                )
            if (
                directory_handle != root_handle
                and not _windows_path_is_contained(root_final, directory_final)
            ):
                raise GateError(
                    "evidence directory handle resolves outside its root"
                )
            directory_path = Path(directory_final)
            try:
                entries_before = sorted(
                    list(os.scandir(directory_path)),
                    key=lambda entry: entry.name.casefold(),
                )
            except OSError as exc:
                raise GateError(
                    "cannot enumerate locked evidence directory"
                ) from exc
            names_before = [entry.name for entry in entries_before]
            for entry in entries_before:
                relative = (
                    PurePosixPath(entry.name)
                    if prefix is None
                    else prefix / entry.name
                )
                relative_text = safe_relative_path(
                    relative.as_posix(),
                    "evidence snapshot path",
                ).as_posix()
                try:
                    metadata = entry.stat(follow_symlinks=False)
                except OSError as exc:
                    raise GateError(
                        f"cannot inspect locked evidence entry: {relative_text}"
                    ) from exc
                attributes = getattr(metadata, "st_file_attributes", 0)
                if attributes & file_attribute_reparse_point:
                    raise GateError(
                        "evidence directory contains a symlink/reparse point: "
                        f"{relative_text}"
                    )
                is_directory = bool(attributes & file_attribute_directory)
                handle = open_handle(Path(entry.path), directory=is_directory)
                try:
                    if is_directory:
                        consume_validated_windows_evidence_handle(
                            root_final=root_final,
                            handle=handle,
                            expected_directory=True,
                            label=f"evidence entry {relative_text}",
                            inspect_handle=inspect_handle,
                            get_final_path=final_path,
                            reader=lambda validated, opened_final: recurse(
                                validated.handle,
                                opened_final,
                                relative,
                            ),
                        )
                        continue
                    snapshot = consume_validated_windows_evidence_handle(
                        root_final=root_final,
                        handle=handle,
                        expected_directory=False,
                        label=f"evidence entry {relative_text}",
                        inspect_handle=inspect_handle,
                        get_final_path=final_path,
                        reader=lambda validated, _opened_final: (
                            read_locked_file(
                                validated,
                                relative_text,
                            )
                        ),
                    )
                    folded = relative_text.casefold()
                    collision = casefold_paths.get(folded)
                    if collision is not None and collision != relative_text:
                        raise GateError(
                            "evidence contains a case-insensitive path collision: "
                            f"{collision!r}, {relative_text!r}"
                        )
                    if relative_text in files:
                        raise GateError(
                            f"duplicate evidence path: {relative_text}"
                        )
                    if len(files) >= MAX_EVIDENCE_FILES:
                        raise GateError(
                            f"evidence exceeds {MAX_EVIDENCE_FILES} regular files"
                        )
                    total_bytes += snapshot.size
                    if total_bytes > MAX_EVIDENCE_TOTAL_BYTES:
                        raise GateError(
                            "evidence exceeds the bounded in-memory verification size"
                        )
                    casefold_paths[folded] = relative_text
                    files[relative_text] = snapshot
                finally:
                    close_handle(handle)
            try:
                names_after = sorted(
                    entry.name for entry in os.scandir(directory_path)
                )
            except OSError as exc:
                raise GateError(
                    "cannot re-enumerate locked evidence directory"
                ) from exc
            if sorted(names_before) != names_after:
                raise GateError(
                    "evidence directory changed while it was captured"
                )

        recurse(root_handle, root_final, None)
    return files


def capture_evidence_snapshot(evidence_dir: Path) -> EvidenceSnapshot:
    """Read an untrusted evidence tree once through anchored, no-follow handles."""

    root = evidence_dir.absolute()
    if os.name == "nt":
        files = _capture_evidence_windows(root)
    else:
        files = _capture_evidence_posix(root)
    return EvidenceSnapshot(root, files)


def resolve_repo_file(
    repo_root: Path,
    candidate: Path,
    label: str,
) -> tuple[Path, str]:
    """Require a regular, non-reparse file lexically and physically under repo."""

    root_absolute = repo_root.absolute()
    candidate_absolute = candidate.absolute()
    try:
        relative_text = candidate_absolute.relative_to(root_absolute).as_posix()
    except ValueError as exc:
        raise GateError(f"{label} must be located inside the repository") from exc
    resolved = resolve_contained_path(
        root_absolute,
        relative_text,
        label,
        must_exist=True,
    )
    if not resolved.is_file():
        raise GateError(f"{label} must be a regular file: {relative_text}")
    return resolved, relative_text


def load_spec_bytes(content: bytes, label: str) -> dict[str, Any]:
    """Parse and validate the exact authoritative gate-spec bytes."""

    spec = strict_json_bytes(content, label)

    spec = require_exact_keys(
        spec,
        {
            "schemaVersion",
            "gateId",
            "suites",
            "supportingArtifacts",
            "targetEvidence",
        },
        "gate spec",
    )
    if (
        isinstance(spec.get("schemaVersion"), bool)
        or spec.get("schemaVersion") != 1
    ):
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
        suite = require_exact_keys(
            suite,
            {"id", "runner", "report", "suite", "testcases"},
            label,
        )
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
    supporting_ids: set[str] = set()
    supporting_paths: set[str] = set()
    for index, artifact in enumerate(supporting):
        label = f"supportingArtifacts[{index}]"
        artifact = require_exact_keys(
            artifact,
            {"id", "kind", "path", "required"},
            label,
        )
        if not isinstance(artifact.get("id"), str) or not artifact["id"]:
            raise GateError(f"{label}.id must be non-empty")
        if not isinstance(artifact.get("path"), str):
            raise GateError(f"{label}.path must be a string")
        safe_relative_path(artifact["path"], f"{label}.path")
        if not isinstance(artifact.get("kind"), str) or not artifact["kind"]:
            raise GateError(f"{label}.kind must be non-empty")
        if not isinstance(artifact.get("required", False), bool):
            raise GateError(f"{label}.required must be boolean")
        if artifact["id"] in supporting_ids:
            raise GateError(
                f"duplicate supporting artifact id in spec: {artifact['id']}"
            )
        if artifact["path"] in supporting_paths or artifact["path"] in seen_reports:
            raise GateError(f"duplicate report/supporting path: {artifact['path']}")
        supporting_ids.add(artifact["id"])
        supporting_paths.add(artifact["path"])

    target_evidence = require_exact_keys(
        spec.get("targetEvidence"),
        {
            "path",
            "producerSuite",
            "preflightPath",
            "preflightProducer",
            "schemaVersion",
            "mysqlVersionPattern",
            "redisVersionPattern",
            "minioIdentityMode",
            "required",
        },
        "targetEvidence",
    )
    target_path = target_evidence["path"]
    if not isinstance(target_path, str):
        raise GateError("targetEvidence.path must be a string")
    safe_relative_path(target_path, "targetEvidence.path")
    if target_path in seen_reports or target_path in supporting_paths:
        raise GateError("targetEvidence.path must be unique")
    if (
        not isinstance(target_evidence["producerSuite"], str)
        or target_evidence["producerSuite"] not in seen_suites
    ):
        raise GateError("targetEvidence.producerSuite must name a declared suite")
    if target_evidence["preflightPath"] != PREFLIGHT_IDENTITY_SOURCE:
        raise GateError(
            "targetEvidence.preflightPath must name the fixed preflight JSON"
        )
    safe_relative_path(
        target_evidence["preflightPath"],
        "targetEvidence.preflightPath",
    )
    if (
        target_evidence["preflightPath"] == target_path
        or target_evidence["preflightPath"] in seen_reports
        or target_evidence["preflightPath"] in supporting_paths
    ):
        raise GateError("targetEvidence.preflightPath must be unique")
    if target_evidence["preflightProducer"] != PREFLIGHT_PRODUCER:
        raise GateError("targetEvidence.preflightProducer is not the fixed producer")
    if (
        isinstance(target_evidence["schemaVersion"], bool)
        or target_evidence["schemaVersion"] != 5
    ):
        raise GateError("targetEvidence.schemaVersion must be integer 5")
    for key in ("mysqlVersionPattern", "redisVersionPattern"):
        pattern = target_evidence[key]
        if (
            not isinstance(pattern, str)
            or not pattern.startswith("^")
            or not pattern.endswith("$")
        ):
            raise GateError(f"targetEvidence.{key} must be a fully anchored regex")
        try:
            re.compile(pattern)
        except re.error as exc:
            raise GateError(f"targetEvidence.{key} is invalid: {exc}") from exc
    if target_evidence["minioIdentityMode"] != MINIO_IDENTITY_MODE:
        raise GateError(
            "targetEvidence.minioIdentityMode must lock the object-challenge-v2 contract"
        )
    if target_evidence["required"] is not True:
        raise GateError("targetEvidence.required must be true")
    return spec


def load_spec(path: Path) -> dict[str, Any]:
    try:
        content = path.read_bytes()
    except OSError as exc:
        raise GateError(f"cannot read gate spec {path}: {exc}") from exc
    return load_spec_bytes(content, f"gate spec {path}")


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


def validate_candidate_window(start_head: str, end_head: str, expected: str) -> None:
    """Require one clean commit identity for the complete Maven/evidence window."""

    validate_candidate(start_head, expected)
    end_normalized = end_head.strip().lower()
    if not GIT_SHA_RE.fullmatch(end_normalized):
        raise GateError(
            f"end candidate SHA is not a full 40-character SHA: {end_head!r}"
        )
    if start_head.strip().lower() != end_normalized:
        raise GateError(
            "candidate HEAD drifted during Maven execution: "
            f"start={start_head.strip().lower()}, end={end_normalized}"
        )
    validate_candidate(end_normalized, expected)


def verify_candidate_attestation(
    manifest: Mapping[str, Any],
    expected: str,
) -> None:
    source = manifest.get("source")
    if not isinstance(source, dict):
        raise GateError("manifest source attestation is missing")
    start_head = source.get("startHead")
    after_preflight_head = source.get("afterPreflightHead")
    end_head = source.get("endHead")
    if (
        not isinstance(start_head, str)
        or not isinstance(after_preflight_head, str)
        or not isinstance(end_head, str)
    ):
        raise GateError(
            "manifest start/preflight/end candidate HEAD markers are missing"
        )
    validate_candidate_window(start_head, after_preflight_head, expected)
    validate_candidate_window(start_head, end_head, expected)
    validate_candidate(str(manifest.get("candidateSha", "")), expected)
    if manifest.get("candidateSha") != start_head:
        raise GateError("manifest candidateSha does not equal source.startHead")
    if manifest.get("expectedCandidateSha") != expected:
        raise GateError("manifest expectedCandidateSha does not match verifier input")


def verify_source_snapshot_attestation(
    source: Mapping[str, Any],
    repo_root: Path,
    expected_candidate: str,
    expected_spec_sha256: str,
) -> dict[str, Any]:
    """Rebind archived evidence to the expected commit tree and gate spec."""

    snapshot_candidate = source.get("snapshotCandidateSha")
    snapshot_tree = source.get("snapshotTreeSha")
    snapshot_manifest = source.get("snapshotManifestSha256")
    snapshot_file_count = source.get("snapshotFileCount")
    snapshot_spec = source.get("snapshotSpecSha256")
    if source.get("snapshotMode") != "git-object-tree":
        raise GateError("evidence source snapshot mode must be git-object-tree")
    if source.get("snapshotContainsGitMetadata") is not False:
        raise GateError("evidence source snapshot must exclude Git metadata")
    if not isinstance(snapshot_candidate, str):
        raise GateError("evidence source snapshot candidate SHA is missing")
    validate_candidate(snapshot_candidate, expected_candidate)
    expected_tree = git_tree_sha(repo_root, expected_candidate)
    if snapshot_tree != expected_tree:
        raise GateError(
            "evidence source snapshot tree does not match the candidate tree"
        )
    entries = git_tree_entries(repo_root, expected_candidate)
    expected_manifest = git_tree_manifest_sha256(entries)
    if not isinstance(snapshot_manifest, str) or not SHA256_RE.fullmatch(
        snapshot_manifest
    ):
        raise GateError("evidence source snapshot manifest SHA-256 is invalid")
    if snapshot_manifest != expected_manifest:
        raise GateError(
            "evidence source snapshot manifest does not match the candidate tree"
        )
    if (
        not isinstance(snapshot_file_count, int)
        or isinstance(snapshot_file_count, bool)
        or snapshot_file_count != len(entries)
    ):
        raise GateError("evidence source snapshot file count is invalid")
    if snapshot_spec != expected_spec_sha256:
        raise GateError(
            "evidence source snapshot gate spec differs from its authority"
        )
    return {
        "mode": "git-object-tree",
        "candidateSha": expected_candidate,
        "treeSha": expected_tree,
        "manifestSha256": expected_manifest,
        "fileCount": len(entries),
    }


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


class PendingPassEvidence(NamedTuple):
    """PASS candidate retained in memory until source cleanup has succeeded."""

    manifest: dict[str, Any]
    redactor: SecretRedactor


def assert_secret_free_bytes(
    content: bytes,
    label: str,
    redactor: SecretRedactor,
) -> None:
    """Fail closed if a publishable text artifact still contains a credential."""

    try:
        text = content.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise GateError(f"{label} is not UTF-8 and cannot be secret-scanned") from exc
    if redactor.redact(text) != text:
        raise GateError(
            f"{label} contains a known secret or credential-shaped value"
        )


def serialize_manifest_bytes(manifest: Mapping[str, Any]) -> bytes:
    serialized = (
        json.dumps(manifest, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    ).encode("utf-8")
    if len(serialized) > MAX_MANIFEST_BYTES:
        raise GateError(
            f"evidence manifest exceeds {MAX_MANIFEST_BYTES} bytes"
        )
    return serialized


def finalize_manifest_security(
    evidence: EvidenceSnapshot | Path,
    manifest: dict[str, Any],
    redactor: SecretRedactor,
) -> None:
    """Scan every publishable artifact before asserting containsSecrets=false."""

    snapshot = (
        evidence
        if isinstance(evidence, EvidenceSnapshot)
        else capture_evidence_snapshot(evidence)
    )
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise GateError("manifest artifacts must be a list before secret scan")
    scanned_paths: set[str] = set()
    for index, record in enumerate(artifacts):
        if not isinstance(record, Mapping):
            raise GateError(f"manifest artifacts[{index}] is not an object")
        path_value = record.get("path")
        if not isinstance(path_value, str):
            raise GateError(f"manifest artifacts[{index}] has no path")
        relative = safe_relative_path(
            path_value,
            f"manifest artifacts[{index}].path",
        ).as_posix()
        if relative in scanned_paths:
            raise GateError(f"duplicate artifact path before secret scan: {relative}")
        artifact = snapshot.require(
            relative,
            f"secret scan artifact {index}",
        )
        assert_secret_free_bytes(
            artifact.content,
            f"artifact {relative}",
            redactor,
        )
        scanned_paths.add(relative)
    candidate_security = {
        "containsSecrets": False,
        "rawSourcesRetained": False,
        "xmlPropertiesRemoved": True,
        "logRedacted": True,
        "secretScan": {
            "status": "PASS",
            "policy": SECRET_SCAN_POLICY,
            "artifactCount": len(scanned_paths),
        },
    }
    candidate_manifest = dict(manifest)
    candidate_manifest["security"] = candidate_security
    serialized = serialize_manifest_bytes(candidate_manifest)
    assert_secret_free_bytes(serialized, "evidence manifest", redactor)
    manifest["security"] = candidate_security


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
            env=isolated_git_environment(),
        )
    except OSError as exc:
        raise GateError(f"cannot execute git: {exc}") from exc
    if completed.returncode != 0:
        raise GateError(f"cannot resolve candidate SHA: {completed.stderr.strip()}")
    return completed.stdout.strip().lower()


def git_tree_sha(repo_root: Path, candidate: str) -> str:
    """Resolve the exact tree object for a full candidate commit SHA."""

    validate_candidate(candidate, candidate)
    try:
        completed = subprocess.run(
            ["git", "rev-parse", f"{candidate}^{{tree}}"],
            cwd=repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            text=True,
            env=isolated_git_environment(),
        )
    except OSError as exc:
        raise GateError(f"cannot resolve candidate tree: {exc}") from exc
    if completed.returncode != 0:
        raise GateError(
            "cannot resolve candidate tree: "
            f"{completed.stderr.strip() or completed.stdout.strip()}"
        )
    tree_sha = completed.stdout.strip().lower()
    if not GIT_SHA_RE.fullmatch(tree_sha):
        raise GateError(f"candidate tree is not a full Git SHA: {tree_sha!r}")
    return tree_sha


def isolated_git_environment() -> dict[str, str]:
    """Remove environment redirects and disable replace refs for source reads."""

    child = {
        name: value
        for name, value in os.environ.items()
        if not name.upper().startswith("GIT_")
    }
    child["GIT_NO_REPLACE_OBJECTS"] = "1"
    return child


def portable_windows_key(path: PurePosixPath) -> str:
    """Use an OS-independent key for Windows case-insensitive collision checks."""

    return str(PureWindowsPath(*path.parts)).casefold()


def parse_git_tree_entries(content: bytes) -> list[GitTreeEntry]:
    """Parse one NUL-delimited ``git ls-tree -r`` result fail-closed."""

    if not content.endswith(b"\0"):
        raise GateError("candidate Git tree listing is not NUL terminated")
    entries: list[GitTreeEntry] = []
    file_keys: set[str] = set()
    directory_paths: dict[str, str] = {}
    for index, raw_entry in enumerate(content[:-1].split(b"\0")):
        if not raw_entry:
            raise GateError("candidate Git tree listing contains an empty entry")
        try:
            metadata, raw_path = raw_entry.split(b"\t", 1)
            mode_raw, type_raw, object_raw = metadata.split(b" ")
            path_text = raw_path.decode("utf-8", errors="strict")
            mode = mode_raw.decode("ascii", errors="strict")
            object_type = type_raw.decode("ascii", errors="strict")
            object_sha = object_raw.decode("ascii", errors="strict").lower()
        except (UnicodeDecodeError, ValueError) as exc:
            raise GateError(
                f"candidate Git tree entry {index} is malformed"
            ) from exc
        if object_type != "blob" or mode not in {"100644", "100755"}:
            raise GateError(
                "candidate Git tree contains a symlink, gitlink, or "
                f"non-regular entry: {path_text!r}"
            )
        if not GIT_SHA_RE.fullmatch(object_sha):
            raise GateError(
                f"candidate Git tree entry has an invalid blob SHA: {path_text!r}"
            )
        path = safe_relative_path(path_text, "candidate Git tree path")
        if any(part.casefold() == ".git" for part in path.parts):
            raise GateError("candidate Git tree must not contain Git metadata paths")
        key = portable_windows_key(path)
        parents = [
            PurePosixPath(*path.parts[:depth])
            for depth in range(1, len(path.parts))
        ]
        parent_keys = {portable_windows_key(parent) for parent in parents}
        if (
            key in file_keys
            or key in directory_paths
            or any(parent in file_keys for parent in parent_keys)
        ):
            raise GateError(
                "candidate Git tree contains a duplicate, prefix, or "
                f"Windows case-colliding path: {path_text!r}"
            )
        for parent in parents:
            parent_key = portable_windows_key(parent)
            existing = directory_paths.get(parent_key)
            if existing is not None and existing != parent.as_posix():
                raise GateError(
                    "candidate Git tree contains Windows case-colliding "
                    f"directories: {path_text!r}"
                )
            directory_paths[parent_key] = parent.as_posix()
        file_keys.add(key)
        entries.append(GitTreeEntry(mode, object_sha, path))
    if not entries:
        raise GateError("candidate Git tree must contain at least one regular file")
    return entries


def git_tree_entries(repo_root: Path, candidate: str) -> list[GitTreeEntry]:
    """Read exact candidate paths, modes and blob IDs without attributes."""

    validate_candidate(candidate, candidate)
    try:
        completed = subprocess.run(
            ["git", "ls-tree", "-r", "-z", "--full-tree", candidate],
            cwd=repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            env=isolated_git_environment(),
        )
    except OSError as exc:
        raise GateError(f"cannot list candidate Git tree: {exc}") from exc
    if completed.returncode != 0:
        detail = completed.stderr.decode("utf-8", errors="replace").strip()
        raise GateError(f"cannot list candidate Git tree: {detail}")
    return parse_git_tree_entries(completed.stdout)


def read_candidate_git_blob(
    repo_root: Path,
    candidate: str,
    relative_path: str,
    label: str,
    *,
    maximum: int = MAX_STRICT_JSON_BYTES,
) -> bytes:
    """Read one bounded file directly from the candidate's immutable Git blob."""

    candidate_path = safe_relative_path(relative_path, label)
    matching_entries = [
        entry
        for entry in git_tree_entries(repo_root, candidate)
        if entry.path == candidate_path
    ]
    if len(matching_entries) != 1:
        raise GateError(
            f"{label} must exist exactly once in the candidate Git tree: "
            f"{candidate_path.as_posix()!r}"
        )
    object_sha = matching_entries[0].object_sha
    environment = isolated_git_environment()
    try:
        size_result = subprocess.run(
            ["git", "cat-file", "-s", object_sha],
            cwd=repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            env=environment,
        )
    except OSError as exc:
        raise GateError(f"cannot inspect {label} Git blob: {exc}") from exc
    if size_result.returncode != 0:
        detail = size_result.stderr.decode(
            "utf-8",
            errors="replace",
        ).strip()
        raise GateError(f"cannot inspect {label} Git blob: {detail}")
    try:
        size_text = size_result.stdout.decode("ascii", errors="strict").strip()
    except UnicodeDecodeError as exc:
        raise GateError(f"{label} Git blob size is malformed") from exc
    if not re.fullmatch(r"[0-9]+", size_text):
        raise GateError(f"{label} Git blob size is malformed: {size_text!r}")
    size = int(size_text)
    if size > maximum:
        raise GateError(f"{label} Git blob exceeds {maximum} bytes")

    try:
        content_result = subprocess.run(
            ["git", "cat-file", "blob", object_sha],
            cwd=repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            env=environment,
        )
    except OSError as exc:
        raise GateError(f"cannot read {label} Git blob: {exc}") from exc
    if content_result.returncode != 0:
        detail = content_result.stderr.decode(
            "utf-8",
            errors="replace",
        ).strip()
        raise GateError(f"cannot read {label} Git blob: {detail}")
    content = content_result.stdout
    if len(content) != size:
        raise GateError(
            f"{label} Git blob size mismatch: expected={size}, actual={len(content)}"
        )
    try:
        digest = hashlib.sha1(usedforsecurity=False)
    except TypeError:
        digest = hashlib.sha1()
    digest.update(f"blob {size}\0".encode("ascii"))
    digest.update(content)
    if digest.hexdigest() != object_sha:
        raise GateError(f"{label} Git blob content hash mismatch")
    return content


def git_tree_manifest_sha256(entries: Sequence[GitTreeEntry]) -> str:
    """Hash a canonical path/mode/blob manifest for offline source rebinding."""

    digest = hashlib.sha256()
    for entry in sorted(entries, key=lambda item: item.path.as_posix()):
        digest.update(
            (
                f"{entry.mode} {entry.object_sha}\t"
                f"{entry.path.as_posix()}\0"
            ).encode("utf-8")
        )
    return digest.hexdigest()


def materialise_git_blobs(
    repo_root: Path,
    snapshot_root: Path,
    entries: Sequence[GitTreeEntry],
) -> None:
    """Write exact candidate blobs and verify every Git object hash."""

    try:
        process = subprocess.Popen(
            ["git", "cat-file", "--batch"],
            cwd=repo_root,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=isolated_git_environment(),
        )
    except OSError as exc:
        raise GateError(f"cannot read candidate Git blobs: {exc}") from exc
    assert process.stdin is not None
    assert process.stdout is not None
    assert process.stderr is not None
    try:
        for entry in entries:
            process.stdin.write((entry.object_sha + "\n").encode("ascii"))
            process.stdin.flush()
            header = process.stdout.readline()
            try:
                observed_sha, object_type, size_raw = (
                    header.rstrip(b"\n").split(b" ")
                )
                size = int(size_raw)
            except (ValueError, TypeError) as exc:
                raise GateError(
                    f"Git blob header is malformed for {entry.path.as_posix()!r}"
                ) from exc
            if (
                observed_sha.decode("ascii", errors="replace").lower()
                != entry.object_sha
                or object_type != b"blob"
                or size < 0
            ):
                raise GateError(
                    f"Git blob identity mismatch for {entry.path.as_posix()!r}"
                )
            destination = snapshot_root.joinpath(*entry.path.parts)
            destination.parent.mkdir(parents=True, exist_ok=True)
            try:
                blob_digest = hashlib.sha1(usedforsecurity=False)
            except TypeError:
                blob_digest = hashlib.sha1()
            blob_digest.update(f"blob {size}\0".encode("ascii"))
            remaining = size
            with destination.open("xb") as output:
                while remaining:
                    chunk = process.stdout.read(min(1024 * 1024, remaining))
                    if not chunk:
                        raise GateError(
                            f"Git blob ended early for {entry.path.as_posix()!r}"
                        )
                    output.write(chunk)
                    blob_digest.update(chunk)
                    remaining -= len(chunk)
            if process.stdout.read(1) != b"\n":
                raise GateError(
                    f"Git blob delimiter is missing for {entry.path.as_posix()!r}"
                )
            if blob_digest.hexdigest() != entry.object_sha:
                raise GateError(
                    f"Git blob content hash mismatch for {entry.path.as_posix()!r}"
                )
            # Maven只应在 target/ 下产出；候选输入本身设为只读，降低同用户并发工具
            # 在两次 hash attestation 之间意外改写源码的机会。
            os.chmod(destination, 0o555 if entry.mode == "100755" else 0o444)
        process.stdin.close()
        error_content = process.stderr.read()
        return_code = process.wait()
        if return_code != 0:
            detail = error_content.decode("utf-8", errors="replace").strip()
            raise GateError(f"cannot read candidate Git blobs: {detail}")
    except BaseException:
        if process.poll() is None:
            process.kill()
        process.wait()
        raise
    finally:
        if not process.stdin.closed:
            process.stdin.close()
        process.stdout.close()
        process.stderr.close()


def git_blob_sha_from_file(path: Path, label: str) -> str:
    """Recompute one regular file's canonical Git blob SHA-1 without links."""

    if path_is_link_or_reparse(path):
        raise GateError(f"{label} must not be a symlink/reparse point")
    try:
        before = os.lstat(path)
    except OSError as exc:
        raise GateError(f"cannot stat {label}: {exc}") from exc
    if not stat.S_ISREG(before.st_mode):
        raise GateError(f"{label} must be a regular file")
    try:
        digest = hashlib.sha1(usedforsecurity=False)
    except TypeError:
        digest = hashlib.sha1()
    digest.update(f"blob {before.st_size}\0".encode("ascii"))
    try:
        with path.open("rb") as stream:
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                digest.update(chunk)
        after = os.lstat(path)
    except OSError as exc:
        raise GateError(f"cannot read {label}: {exc}") from exc
    before_identity = (
        before.st_dev,
        before.st_ino,
        before.st_size,
        before.st_mtime_ns,
    )
    after_identity = (
        after.st_dev,
        after.st_ino,
        after.st_size,
        after.st_mtime_ns,
    )
    if before_identity != after_identity or not stat.S_ISREG(after.st_mode):
        raise GateError(f"{label} changed while its Git blob hash was computed")
    return digest.hexdigest()


def is_maven_output_path(path: PurePosixPath) -> bool:
    """Allow only Maven's root/module target trees as untracked snapshot output."""

    parts = path.parts
    return bool(
        parts
        and (
            parts[0] == "target"
            or (
                len(parts) >= 2
                and parts[0].startswith("platform-")
                and parts[1] == "target"
            )
        )
    )


def verify_materialised_git_tree(
    snapshot_root: Path,
    entries: Sequence[GitTreeEntry],
    label: str,
) -> None:
    """Prove candidate paths still equal Git and no extra build input appeared."""

    root = snapshot_root.absolute()
    if path_is_link_or_reparse(root) or not root.is_dir():
        raise GateError(f"{label} root must be a regular non-reparse directory")
    expected_files = {entry.path.as_posix(): entry for entry in entries}
    expected_directories = {
        PurePosixPath(*entry.path.parts[:depth]).as_posix()
        for entry in entries
        for depth in range(1, len(entry.path.parts))
    }
    observed_files: set[str] = set()
    pending: list[tuple[Path, PurePosixPath | None]] = [(root, None)]
    while pending:
        directory, relative_directory = pending.pop()
        try:
            children = list(os.scandir(directory))
        except OSError as exc:
            raise GateError(f"cannot enumerate {label}: {directory}: {exc}") from exc
        for child in children:
            child_path = Path(child.path)
            relative = (
                PurePosixPath(child.name)
                if relative_directory is None
                else relative_directory / child.name
            )
            relative_text = relative.as_posix()
            if path_is_link_or_reparse(child_path):
                raise GateError(
                    f"{label} contains a symlink/reparse point: {relative_text}"
                )
            if child.is_dir(follow_symlinks=False):
                if (
                    relative_text not in expected_directories
                    and is_maven_output_path(relative)
                ):
                    continue
                if relative_text not in expected_directories:
                    raise GateError(
                        f"{label} contains an undeclared input directory: "
                        f"{relative_text}"
                    )
                pending.append((child_path, relative))
                continue
            if not child.is_file(follow_symlinks=False):
                raise GateError(
                    f"{label} contains a non-regular entry: {relative_text}"
                )
            expected = expected_files.get(relative_text)
            if expected is None:
                if is_maven_output_path(relative):
                    continue
                raise GateError(
                    f"{label} contains an undeclared input file: {relative_text}"
                )
            actual_sha = git_blob_sha_from_file(
                child_path, f"{label} file {relative_text!r}"
            )
            if actual_sha != expected.object_sha:
                raise GateError(
                    f"{label} Git blob mismatch for {relative_text!r}: "
                    f"actual={actual_sha}, expected={expected.object_sha}"
                )
            if os.name != "nt":
                observed_mode = os.lstat(child_path).st_mode
                observed_executable = bool(
                    observed_mode
                    & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
                )
                expected_executable = expected.mode == "100755"
                if observed_executable is not expected_executable:
                    raise GateError(
                        f"{label} executable-mode mismatch for "
                        f"{relative_text!r}: actual={observed_executable}, "
                        f"expected={expected_executable}"
                    )
            observed_files.add(relative_text)
    if observed_files != set(expected_files):
        missing = sorted(set(expected_files) - observed_files)
        raise GateError(f"{label} is missing candidate files: {missing}")


def rematerialise_candidate_snapshot(
    repo_root: Path,
    snapshot: SourceSnapshot,
    expected_candidate: str,
    expected_entries: Sequence[GitTreeEntry],
) -> None:
    """Replace the preflight tree with a second exact formal-build tree."""

    current_tree = git_tree_sha(repo_root, expected_candidate)
    current_entries = git_tree_entries(repo_root, expected_candidate)
    if (
        current_tree != snapshot.tree_sha
        or current_entries != list(expected_entries)
        or git_tree_manifest_sha256(current_entries) != snapshot.manifest_sha256
    ):
        raise GateError(
            "candidate Git objects changed before formal snapshot materialisation"
        )
    remove_directory_tree(snapshot.root)
    snapshot.root.mkdir()
    materialise_git_blobs(repo_root, snapshot.root, current_entries)
    verify_materialised_git_tree(
        snapshot.root,
        current_entries,
        "formal immutable source snapshot before Maven",
    )


def remove_directory_tree(root: Path) -> None:
    """Remove a private tree without following or chmod-ing links/reparse points."""

    if not os.path.lexists(root):
        return
    if path_is_link_or_reparse(root) or not root.is_dir():
        raise GateError("temporary source root changed into a link or non-directory")
    for directory, directory_names, file_names in os.walk(
        root,
        topdown=True,
        followlinks=False,
    ):
        directory_path = Path(directory)
        for directory_name in list(directory_names):
            child = directory_path / directory_name
            if path_is_link_or_reparse(child):
                directory_names.remove(directory_name)
                continue
            try:
                os.chmod(child, stat.S_IWRITE | stat.S_IREAD | stat.S_IEXEC)
            except OSError:
                pass
        for file_name in file_names:
            file_path = directory_path / file_name
            if path_is_link_or_reparse(file_path):
                continue
            try:
                os.chmod(file_path, stat.S_IWRITE | stat.S_IREAD)
            except OSError:
                pass
    try:
        os.chmod(root, stat.S_IWRITE | stat.S_IREAD | stat.S_IEXEC)
    except OSError:
        pass
    shutil.rmtree(root, ignore_errors=False)


@contextmanager
def private_temporary_directory(
    parent: Path,
    prefix: str,
) -> Iterator[Path]:
    """Create a normal-permission temp directory for MSYS/Windows portability."""

    parent_input = parent.absolute()
    parent_input.mkdir(parents=True, exist_ok=True)
    if path_is_link_or_reparse(parent_input) or not parent_input.is_dir():
        raise GateError("temporary source parent must be a regular directory")
    parent_resolved = parent_input.resolve(strict=True)
    root: Path | None = None
    for _ in range(16):
        candidate = parent_resolved / f"{prefix}{secrets.token_hex(16)}"
        try:
            candidate.mkdir()
        except FileExistsError:
            continue
        root = candidate
        break
    if root is None:
        raise GateError("cannot allocate a unique temporary source directory")
    try:
        yield root
    finally:
        remove_directory_tree(root)


@contextmanager
def immutable_candidate_snapshot(
    repo_root: Path,
    candidate: str,
    temporary_parent: Path | None = None,
) -> Iterator[SourceSnapshot]:
    """Yield a disposable source tree whose bytes come only from ``candidate``."""

    candidate_normalized = candidate.strip().lower()
    validate_candidate(candidate_normalized, candidate_normalized)
    tree_sha = git_tree_sha(repo_root, candidate_normalized)
    entries = git_tree_entries(repo_root, candidate_normalized)
    manifest_sha256 = git_tree_manifest_sha256(entries)
    parent = repo_root if temporary_parent is None else temporary_parent
    with private_temporary_directory(parent, ".phase00-source-") as temporary_root:
        if path_is_link_or_reparse(temporary_root):
            raise GateError("temporary source root must not be a reparse point")
        snapshot_root = temporary_root / "source"
        snapshot_root.mkdir()
        try:
            materialise_git_blobs(repo_root, snapshot_root, entries)
        except OSError as exc:
            raise GateError(f"cannot materialise candidate Git tree: {exc}") from exc
        if (snapshot_root / ".git").exists():
            raise GateError("immutable source snapshot unexpectedly contains .git")
        yield SourceSnapshot(
            root=snapshot_root,
            candidate_sha=candidate_normalized,
            tree_sha=tree_sha,
            manifest_sha256=manifest_sha256,
            file_count=len(entries),
        )


def bind_end_candidate(
    repo_root: Path,
    start_head: str,
    expected: str,
) -> str:
    """Re-read HEAD after Maven and bind the end of the evidence window."""

    end_head = git_head(repo_root)
    validate_candidate_window(start_head, end_head, expected)
    return end_head


def require_clean_tracked_worktree(repo_root: Path) -> None:
    """Bind generated evidence to HEAD, not to uncommitted tracked source."""

    try:
        completed = subprocess.run(
            ["git", "status", "--porcelain=v1", "--untracked-files=no"],
            cwd=repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            env=isolated_git_environment(),
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
            env=isolated_git_environment(),
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
        report = resolve_contained_path(
            repo_root,
            suite["report"],
            f"{suite['id']} report cleanup path",
            must_exist=False,
        )
        candidates.append(report)
        text_report = report_text_path(report)
        if text_report is not None:
            candidates.append(text_report)
    for artifact in spec.get("supportingArtifacts", []):
        candidates.append(
            resolve_contained_path(
                repo_root,
                artifact["path"],
                f"{artifact['id']} support cleanup path",
                must_exist=False,
            )
        )
    target_evidence = spec["targetEvidence"]
    candidates.append(
        resolve_contained_path(
            repo_root,
            target_evidence["path"],
            "target identity cleanup path",
            must_exist=False,
        )
    )
    candidates.append(
        resolve_contained_path(
            repo_root,
            target_evidence["preflightPath"],
            "preflight target identity cleanup path",
            must_exist=False,
        )
    )

    removed: list[str] = []
    for path in candidates:
        if path_is_link_or_reparse(path):
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
    if not report_path.exists():
        raise GateError(f"required XML is missing: {suite_spec['report']}")
    if path_is_link_or_reparse(report_path) or not report_path.is_file():
        raise GateError(f"required XML must be a regular non-symlink file: {report_path}")
    metadata = report_path.stat()
    if (
        fresh_after_ns is not None
        and metadata.st_mtime_ns < fresh_after_ns - 2_000_000_000
    ):
        raise GateError(
            f"required XML predates this run and is stale: {suite_spec['report']}"
        )
    try:
        content = report_path.read_bytes()
    except OSError as exc:
        raise GateError(
            f"cannot read required XML {suite_spec['report']}: {exc}"
        ) from exc
    return inspect_suite_report_bytes(
        content,
        metadata.st_mtime_ns,
        suite_spec,
    )


def inspect_suite_report_bytes(
    content: bytes,
    mtime_ns: int,
    suite_spec: Mapping[str, Any],
) -> dict[str, Any]:
    """Parse a suite from the same immutable bytes used for its hash."""

    class_name = suite_spec["suite"]
    expected_names = list(suite_spec["testcases"])
    try:
        root = ET.fromstring(content)
    except ET.ParseError as exc:
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
        "sourceMtimeNs": mtime_ns,
    }


def validate_suite_reports(
    repo_root: Path,
    spec: Mapping[str, Any],
    fresh_after_ns: int | None,
) -> list[dict[str, Any]]:
    results = []
    for suite in spec["suites"]:
        report_path = resolve_contained_path(
            repo_root,
            suite["report"],
            f"{suite['id']} source report",
            must_exist=False,
        )
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
    if path_is_link_or_reparse(path) or not path.is_file():
        raise GateError(f"Failsafe summary must be a regular non-symlink file: {path}")
    if (
        fresh_after_ns is not None
        and path.stat().st_mtime_ns < fresh_after_ns - 2_000_000_000
    ):
        raise GateError(f"Failsafe summary predates this run and is stale: {path}")
    try:
        content = path.read_bytes()
    except OSError as exc:
        raise GateError(f"cannot read Failsafe summary {path}: {exc}") from exc
    return inspect_failsafe_summary_bytes(
        content,
        minimum_completed=minimum_completed,
    )


def inspect_failsafe_summary_bytes(
    content: bytes,
    *,
    minimum_completed: int,
) -> dict[str, int]:
    """Parse a Failsafe summary from immutable snapshot bytes."""

    try:
        root = ET.fromstring(content)
    except ET.ParseError as exc:
        raise GateError(f"cannot parse Failsafe summary XML: {exc}") from exc
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
    environment: Mapping[str, str],
) -> tuple[int, str]:
    raw_digest = hashlib.sha256()
    try:
        process = subprocess.Popen(
            list(command),
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            env=dict(environment),
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


def capture_maven_run(
    command: Sequence[str],
    cwd: Path,
    redactor: SecretRedactor,
    environment: Mapping[str, str],
) -> tuple[dict[str, Any], bytes, str]:
    safe_log_fd, safe_log_name = tempfile.mkstemp(
        prefix="phase00-maven-", suffix=".log"
    )
    os.close(safe_log_fd)
    safe_log_temp = Path(safe_log_name)
    started_at = utc_now()
    started_ns = time.time_ns()
    try:
        exit_code, raw_log_sha256 = stream_maven(
            command,
            cwd,
            redactor,
            safe_log_temp,
            environment,
        )
        ended_ns = time.time_ns()
        ended_at = utc_now()
        return (
            {
                "command": list(command),
                "workingDirectory": ".",
                "startedAtUtc": started_at,
                "endedAtUtc": ended_at,
                "startedEpochNs": started_ns,
                "endedEpochNs": ended_ns,
                "exitCode": exit_code,
                "cleanLifecycleRequired": True,
            },
            safe_log_temp.read_bytes(),
            raw_log_sha256,
        )
    finally:
        safe_log_temp.unlink(missing_ok=True)


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


def validate_failed_evidence_bundle(
    evidence_dir: Path,
    expected_candidate: str,
) -> None:
    """Validate a publishable FAIL bundle without accepting it as PASS."""

    evidence = capture_evidence_snapshot(evidence_dir)
    manifest = read_manifest(evidence)
    validate_candidate(
        str(manifest.get("candidateSha", "")),
        expected_candidate,
    )
    if manifest.get("expectedCandidateSha") != expected_candidate:
        raise GateError("FAIL manifest expected candidate is not exact")
    if manifest.get("status") != "FAIL":
        raise GateError("non-successful gate cannot publish a PASS manifest")
    records = verify_artifact_records(evidence, manifest.get("artifacts"))
    verify_checksums_file(
        evidence,
        expected_paths=set(records) | {"manifest.json"},
    )
    security = require_exact_keys(
        manifest.get("security"),
        {
            "containsSecrets",
            "rawSourcesRetained",
            "xmlPropertiesRemoved",
            "logRedacted",
            "secretScan",
        },
        "FAIL manifest security",
    )
    secret_scan = require_exact_keys(
        security.get("secretScan"),
        {"status", "policy", "artifactCount"},
        "FAIL manifest security.secretScan",
    )
    if (
        security.get("containsSecrets") is not False
        or secret_scan.get("status") != "PASS"
        or secret_scan.get("policy") != SECRET_SCAN_POLICY
        or secret_scan.get("artifactCount") != len(records)
    ):
        raise GateError("FAIL evidence did not complete its secret scan")
    verification_redactor = SecretRedactor(dict(os.environ))
    for artifact_path in records:
        assert_secret_free_bytes(
            evidence.require(
                artifact_path,
                f"FAIL secret-scanned artifact {artifact_path}",
            ).content,
            f"FAIL secret-scanned artifact {artifact_path}",
            verification_redactor,
        )
    assert_secret_free_bytes(
        evidence.require("manifest.json", "FAIL secret-scanned manifest").content,
        "FAIL secret-scanned manifest",
        verification_redactor,
    )


def publish_ready_evidence(
    ready_dir: Path,
    final_dir: Path,
) -> None:
    """Atomically publish as the final fallible state transition."""

    if os.path.lexists(final_dir):
        raise GateError(
            f"evidence directory already exists; stale artifacts are rejected: {final_dir}"
        )
    try:
        os.rename(ready_dir, final_dir)
    except OSError as exc:
        raise GateError(
            "cannot atomically publish the verified evidence directory"
        ) from exc


def allocate_ready_evidence_path(parent: Path, final_name: str) -> Path:
    """Choose an uncreated same-filesystem path for the ready evidence bundle."""

    if not final_name or final_name in {".", ".."}:
        raise GateError("evidence directory must have a concrete final name")
    for _ in range(16):
        candidate = parent / (
            f".{final_name}.ready-{secrets.token_hex(16)}"
        )
        if not os.path.lexists(candidate):
            return candidate
    raise GateError("cannot allocate a unique ready evidence path")


def artifact_record(
    evidence_dir: Path,
    path: Path,
    *,
    kind: str,
    source_path: str | None = None,
    source_sha256: str | None = None,
    source_retained: bool = False,
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
        record["sourceRetained"] = source_retained
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


def atomic_write_bytes(path: Path, content: bytes) -> None:
    """Replace one staging file only after its complete bytes are durable."""

    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.",
        suffix=".tmp",
        dir=path.parent,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except BaseException:
        try:
            os.close(descriptor)
        except OSError:
            pass
        temporary.unlink(missing_ok=True)
        raise


def checksum_bytes_for_snapshot(
    evidence: EvidenceSnapshot,
    manifest: Mapping[str, Any],
    manifest_content: bytes,
) -> bytes:
    """Build the exact checksum file from one already captured artifact view."""

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise GateError("manifest artifacts must be a list before checksumming")
    digests: dict[str, str] = {
        "manifest.json": sha256_bytes(manifest_content),
    }
    for index, record in enumerate(artifacts):
        path_value = record.get("path") if isinstance(record, Mapping) else None
        if not isinstance(path_value, str):
            raise GateError(f"manifest artifacts[{index}] has no path")
        relative = safe_relative_path(
            path_value,
            f"manifest artifacts[{index}].path",
        ).as_posix()
        if relative in digests:
            raise GateError(f"duplicate artifact path before checksumming: {relative}")
        digests[relative] = evidence.require(
            relative,
            f"checksummed artifact {index}",
        ).sha256
    return "".join(
        f"{digests[path]}  {path}\n"
        for path in sorted(digests)
    ).encode("utf-8")


def virtual_manifest_snapshot(
    evidence: EvidenceSnapshot,
    manifest_content: bytes,
    checksum_content: bytes,
) -> EvidenceSnapshot:
    """Overlay exact manifest/checksum bytes without exposing them on disk."""

    if "manifest.json" in evidence.files or "SHA256SUMS" in evidence.files:
        raise GateError(
            "private staging must not contain a manifest or checksum before verification"
        )
    now_ns = time.time_ns()
    files = dict(evidence.files)
    for ordinal, (relative, content) in enumerate(
        (
            ("manifest.json", manifest_content),
            ("SHA256SUMS", checksum_content),
        ),
        start=1,
    ):
        files[relative] = _snapshot_file_from_bytes(
            relative,
            content,
            size=len(content),
            mtime_ns=now_ns,
            file_id=(-1, ordinal),
        )
    return EvidenceSnapshot(evidence.root, files)


def verify_prepared_pass(
    *,
    evidence_dir: Path,
    spec_path: Path,
    expected_candidate: str,
    manifest: dict[str, Any],
    redactor: SecretRedactor,
) -> PreparedEvidenceManifest:
    """Verify the exact future PASS bytes entirely in memory."""

    artifact_snapshot = capture_evidence_snapshot(evidence_dir)
    finalize_manifest_security(artifact_snapshot, manifest, redactor)
    manifest_content = serialize_manifest_bytes(manifest)
    checksum_content = checksum_bytes_for_snapshot(
        artifact_snapshot,
        manifest,
        manifest_content,
    )
    virtual_snapshot = virtual_manifest_snapshot(
        artifact_snapshot,
        manifest_content,
        checksum_content,
    )
    verified_manifest = _verify_evidence_snapshot(
        virtual_snapshot,
        spec_path,
        expected_candidate,
    )
    return PreparedEvidenceManifest(
        manifest_content=manifest_content,
        checksum_content=checksum_content,
        virtual_snapshot=virtual_snapshot,
        verified_manifest=verified_manifest,
    )


def require_snapshot_payload_match(
    actual: EvidenceSnapshot,
    expected: EvidenceSnapshot,
    label: str,
) -> None:
    """Require the same paths and bytes while ignoring filesystem metadata."""

    if set(actual.files) != set(expected.files):
        raise GateError(f"{label} file set differs from the verified snapshot")
    for relative_path in sorted(expected.files):
        actual_file = actual.files[relative_path]
        expected_file = expected.files[relative_path]
        if (
            actual_file.content != expected_file.content
            or actual_file.sha256 != expected_file.sha256
            or actual_file.size != expected_file.size
        ):
            raise GateError(
                f"{label} changed after verification: {relative_path}"
            )


def snapshot_with_withheld_pass_manifest(
    snapshot: EvidenceSnapshot,
) -> EvidenceSnapshot:
    """Represent a publish tree whose canonical PASS marker is still hidden."""

    if PASS_MANIFEST_READY_NAME in snapshot.files:
        raise GateError("verified PASS snapshot contains its reserved marker path")
    manifest = snapshot.require("manifest.json", "verified PASS manifest")
    files = dict(snapshot.files)
    del files["manifest.json"]
    files[PASS_MANIFEST_READY_NAME] = _snapshot_file_from_bytes(
        PASS_MANIFEST_READY_NAME,
        manifest.content,
        size=manifest.size,
        mtime_ns=manifest.mtime_ns,
        file_id=manifest.file_id,
    )
    return EvidenceSnapshot(snapshot.root, files)


def materialize_preverified_pass(
    publish_dir: Path,
    prepared: PreparedEvidenceManifest,
) -> None:
    """Build a new private bundle solely from already verified snapshot bytes."""

    if any(publish_dir.iterdir()):
        raise GateError(
            "private PASS publish directory must start empty"
        )
    expected = prepared.virtual_snapshot
    if (
        expected.require(
            "manifest.json",
            "prepared PASS manifest",
        ).content
        != prepared.manifest_content
        or expected.require(
            "SHA256SUMS",
            "prepared PASS checksum",
        ).content
        != prepared.checksum_content
    ):
        raise GateError("prepared PASS bytes differ from their verified snapshot")

    for relative_path in sorted(
        set(expected.files) - {"SHA256SUMS", "manifest.json"}
    ):
        relative = safe_relative_path(
            relative_path,
            "prepared PASS artifact path",
        )
        atomic_write_bytes(
            publish_dir.joinpath(*relative.parts),
            expected.files[relative_path].content,
        )
    atomic_write_bytes(
        publish_dir / "SHA256SUMS",
        prepared.checksum_content,
    )
    # The PASS marker is the final file made visible inside the private bundle.
    atomic_write_bytes(
        publish_dir / "manifest.json",
        prepared.manifest_content,
    )


def publish_preverified_pass(
    *,
    staging_dir: Path,
    final_dir: Path,
    spec_path: Path,
    expected_candidate: str,
    prepared: PreparedEvidenceManifest,
) -> None:
    """Persist, reverify, and atomically publish one exact private PASS bundle."""

    publish_dir = allocate_ready_evidence_path(
        final_dir.parent,
        f"{final_dir.name}.publish",
    )
    try:
        publish_dir.mkdir()
    except OSError as exc:
        raise GateError("cannot create the private PASS publish directory") from exc
    directory_published = False
    try:
        materialize_preverified_pass(publish_dir, prepared)
        persisted_snapshot = capture_evidence_snapshot(publish_dir)
        require_snapshot_payload_match(
            persisted_snapshot,
            prepared.virtual_snapshot,
            "persisted private PASS bundle",
        )
        persisted_manifest = _verify_evidence_snapshot(
            persisted_snapshot,
            spec_path,
            expected_candidate,
        )
        if persisted_manifest != prepared.verified_manifest:
            raise GateError(
                "persisted private PASS manifest differs after verification"
            )

        withheld_snapshot = snapshot_with_withheld_pass_manifest(
            prepared.virtual_snapshot
        )
        try:
            os.rename(
                publish_dir / "manifest.json",
                publish_dir / PASS_MANIFEST_READY_NAME,
            )
        except OSError as exc:
            raise GateError(
                "cannot withhold the private PASS manifest before publication"
            ) from exc
        private_withheld_snapshot = capture_evidence_snapshot(publish_dir)
        require_snapshot_payload_match(
            private_withheld_snapshot,
            withheld_snapshot,
            "private bundle with withheld PASS manifest",
        )

        if os.path.lexists(final_dir):
            raise GateError(
                "evidence directory already exists; stale artifacts are rejected: "
                f"{final_dir}"
            )
        # The collection tree is never the published PASS and must not be left
        # behind after success. Failure to remove it prevents publication.
        remove_directory_tree(staging_dir)

        # Publish without a canonical PASS marker, then validate the exact
        # bytes at their final path. Any failure leaves an unverifiable bundle.
        try:
            os.rename(publish_dir, final_dir)
        except OSError as exc:
            raise GateError(
                "cannot publish the evidence directory with PASS withheld"
            ) from exc
        directory_published = True
        published_withheld_snapshot = capture_evidence_snapshot(final_dir)
        require_snapshot_payload_match(
            published_withheld_snapshot,
            withheld_snapshot,
            "published bundle with withheld PASS manifest",
        )

        # This atomic marker rename is the final fallible success transition.
        # No verification or cleanup step may turn a completed PASS into FAIL.
        try:
            os.rename(
                final_dir / PASS_MANIFEST_READY_NAME,
                final_dir / "manifest.json",
            )
        except OSError as exc:
            raise GateError(
                "cannot atomically expose the verified PASS manifest"
            ) from exc
    except Exception:
        if not directory_published and os.path.lexists(publish_dir):
            remove_directory_tree(publish_dir)
        raise


def persist_manifest(
    evidence_dir: Path,
    manifest: Mapping[str, Any],
) -> Path:
    manifest_path = evidence_dir / "manifest.json"
    serialized = serialize_manifest_bytes(manifest)
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
    checksum_lines = []
    for path in sorted(artifact_files, key=lambda item: item.as_posix()):
        relative = path.relative_to(evidence_dir).as_posix()
        digest = (
            sha256_bytes(serialized)
            if path == manifest_path
            else sha256_file(path)
        )
        checksum_lines.append(f"{digest}  {relative}\n")
    checksum_content = "".join(checksum_lines).encode("utf-8")
    atomic_write_bytes(evidence_dir / "SHA256SUMS", checksum_content)
    atomic_write_bytes(manifest_path, serialized)
    return manifest_path


def self_verify_or_downgrade(
    *,
    evidence_dir: Path,
    final_dir: Path,
    spec_path: Path,
    expected_candidate: str,
    manifest: dict[str, Any],
    redactor: SecretRedactor,
) -> tuple[bool, str | None]:
    """Publish only a rebuilt, disk-reverified PASS; otherwise persist FAIL."""

    try:
        prepared = verify_prepared_pass(
            evidence_dir=evidence_dir,
            spec_path=spec_path,
            expected_candidate=expected_candidate,
            manifest=manifest,
            redactor=redactor,
        )
        publish_preverified_pass(
            staging_dir=evidence_dir,
            final_dir=final_dir,
            spec_path=spec_path,
            expected_candidate=expected_candidate,
            prepared=prepared,
        )
    except Exception as exc:
        safe_detail = redactor.redact(str(exc))
        error = (
            "evidence PASS finalization failed "
            f"({type(exc).__name__}): {safe_detail}"
        )
        manifest["status"] = "FAIL"
        errors = manifest.setdefault("errors", [])
        if not isinstance(errors, list):
            errors = []
            manifest["errors"] = errors
        errors.append(error)
        try:
            finalize_manifest_security(evidence_dir, manifest, redactor)
            persist_manifest(evidence_dir, manifest)
        except Exception:
            for path in (
                evidence_dir / "manifest.json",
                evidence_dir / "SHA256SUMS",
            ):
                try:
                    path.unlink(missing_ok=True)
                except OSError:
                    pass
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


def default_maven_executable(
    environment: Mapping[str, str] | None = None,
    platform_name: str | None = None,
    executable_resolver: Callable[[str], str | None] = shutil.which,
) -> str:
    """Return a shell-free Maven executable suitable for the host platform."""

    source = os.environ if environment is None else environment
    override = source.get("MVN", "").strip()
    if override:
        return override

    effective_platform = os.name if platform_name is None else platform_name
    if effective_platform == "nt":
        # Python subprocess uses shell=False throughout this gate.  A bare
        # ``mvn`` therefore cannot rely on cmd.exe's PATHEXT expansion.
        return executable_resolver("mvn.cmd") or "mvn.cmd"
    return "mvn"


def expected_candidate_from_args(
    value: str | None,
    environment: Mapping[str, str] | None = None,
) -> str:
    source = os.environ if environment is None else environment
    expected = (value or source.get("PHASE00_EXPECTED_CANDIDATE_SHA", "")).strip()
    if not expected:
        raise GateError(
            "expected candidate SHA is required via --expected-candidate-sha "
            "or PHASE00_EXPECTED_CANDIDATE_SHA"
        )
    return expected.lower()


def run_gate(args: argparse.Namespace) -> int:
    """Run the gate from exact immutable blobs of the requested candidate."""

    repo_root_input = args.repo_root.absolute()
    if path_is_link_or_reparse(repo_root_input) or not repo_root_input.is_dir():
        raise GateError("repository root must be a regular non-reparse directory")
    repo_root = repo_root_input.resolve(strict=True)
    expected_candidate = expected_candidate_from_args(
        args.expected_candidate_sha,
        dict(os.environ),
    )
    requested_evidence_dir = args.evidence_dir.absolute()
    try:
        requested_evidence_dir.parent.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        raise GateError("cannot create the evidence parent directory") from exc
    evidence_parent_input = requested_evidence_dir.parent
    if (
        path_is_link_or_reparse(evidence_parent_input)
        or not evidence_parent_input.is_dir()
    ):
        raise GateError("evidence parent must be a regular non-reparse directory")
    evidence_parent = evidence_parent_input.resolve(strict=True)
    final_evidence_dir = evidence_parent / requested_evidence_dir.name
    if os.path.lexists(final_evidence_dir):
        raise GateError(
            "evidence directory already exists; stale artifacts are rejected: "
            f"{final_evidence_dir}"
        )
    ready_dir = allocate_ready_evidence_path(
        evidence_parent,
        final_evidence_dir.name,
    )
    published = False
    try:
        with immutable_candidate_snapshot(
            repo_root,
            expected_candidate,
            evidence_parent,
        ) as source_snapshot:
            result, pending_pass = _run_gate_from_snapshot(
                args,
                source_snapshot,
                ready_dir,
            )
        # No PASS bytes exist before source-snapshot cleanup completes.
        spec_path = (
            args.spec
            if args.spec.is_absolute()
            else Path.cwd() / args.spec
        ).absolute()
        suite_count = 0
        testcase_count = 0
        if result == 0:
            if pending_pass is None:
                raise GateError("successful gate did not retain a PASS candidate")
            suites = pending_pass.manifest.get("suites")
            if not isinstance(suites, list):
                raise GateError("PASS candidate manifest suites are invalid")
            suite_count = len(suites)
            testcase_count = sum(
                item.get("observedTests", 0)
                for item in suites
                if isinstance(item, Mapping)
            )
            verified, verification_error = self_verify_or_downgrade(
                evidence_dir=ready_dir,
                final_dir=final_evidence_dir,
                spec_path=spec_path,
                expected_candidate=expected_candidate,
                manifest=pending_pass.manifest,
                redactor=pending_pass.redactor,
            )
            if not verified:
                result = 1
                try:
                    print(
                        f"[phase00-ci-gate] FAIL: {verification_error}",
                        file=sys.stderr,
                    )
                except Exception:
                    pass
            else:
                published = True
        if result != 0:
            validate_failed_evidence_bundle(ready_dir, expected_candidate)
            publish_ready_evidence(ready_dir, final_evidence_dir)
            published = True
    finally:
        if not published and os.path.lexists(ready_dir):
            if path_is_link_or_reparse(ready_dir):
                raise GateError(
                    "ready evidence path became a reparse point during cleanup"
                )
            remove_directory_tree(ready_dir)
    if result == 0:
        try:
            print(
                "[phase00-ci-gate] PASS: "
                f"{suite_count} exact suites, "
                f"{testcase_count} testcases, "
                f"candidate={expected_candidate}"
            )
        except Exception:
            # Publication already succeeded; a broken output stream must not
            # turn an exact published PASS into a failure exit status.
            pass
    return result


def _run_gate_from_snapshot(
    args: argparse.Namespace,
    source_snapshot: SourceSnapshot,
    evidence_dir: Path,
) -> tuple[int, PendingPassEvidence | None]:
    repo_root_input = args.repo_root.absolute()
    if path_is_link_or_reparse(repo_root_input) or not repo_root_input.is_dir():
        raise GateError("repository root must be a regular non-reparse directory")
    repo_root = repo_root_input.resolve(strict=True)
    spec_input = args.spec if args.spec.is_absolute() else Path.cwd() / args.spec
    spec_path, spec_source_path = resolve_repo_file(
        repo_root,
        spec_input,
        "gate spec",
    )
    spec_source_sha256_at_start = sha256_file(spec_path)
    start_environment = dict(os.environ)
    expected_candidate = expected_candidate_from_args(
        args.expected_candidate_sha,
        start_environment,
    )
    start_head = git_head(repo_root)
    validate_candidate(start_head, expected_candidate)
    validate_candidate(source_snapshot.candidate_sha, expected_candidate)
    expected_tree_sha = git_tree_sha(repo_root, expected_candidate)
    if source_snapshot.tree_sha != expected_tree_sha:
        raise GateError(
            "immutable source snapshot tree differs from the candidate tree"
        )
    expected_tree_entries = git_tree_entries(repo_root, expected_candidate)
    expected_manifest_sha256 = git_tree_manifest_sha256(expected_tree_entries)
    if source_snapshot.manifest_sha256 != expected_manifest_sha256:
        raise GateError(
            "immutable source snapshot manifest differs from the candidate tree"
        )
    if source_snapshot.file_count != len(expected_tree_entries):
        raise GateError(
            "immutable source snapshot file count differs from the candidate tree"
        )
    build_root = source_snapshot.root
    if (
        path_is_link_or_reparse(build_root)
        or not build_root.is_dir()
        or (build_root / ".git").exists()
    ):
        raise GateError(
            "immutable source snapshot must be a regular directory without .git"
        )
    snapshot_spec_path = resolve_contained_path(
        build_root,
        spec_source_path,
        "immutable snapshot gate spec",
        must_exist=True,
    )
    if not snapshot_spec_path.is_file():
        raise GateError("immutable snapshot gate spec must be a regular file")
    snapshot_spec_sha256 = sha256_file(snapshot_spec_path)
    if snapshot_spec_sha256 != spec_source_sha256_at_start:
        raise GateError(
            "live gate spec differs from the immutable candidate snapshot: "
            f"live={spec_source_sha256_at_start}, "
            f"snapshot={snapshot_spec_sha256}"
        )
    spec = load_spec(snapshot_spec_path)
    require_clean_tracked_worktree(repo_root)
    ignored_untracked_at_start = require_no_build_relevant_untracked(repo_root)
    declared_targets, configured_targets = read_target_configuration(
        start_environment
    )
    expected_identities = read_expected_identities(
        start_environment,
        expected_candidate,
    )
    target_constraints = validate_override_channels(start_environment)
    maven_environment = build_maven_environment(
        start_environment,
        expected_candidate,
    )
    prepare_evidence_dir(evidence_dir)

    redactor = SecretRedactor(maven_environment)
    tool_versions = build_tool_versions(build_root, args.maven, redactor)
    preflight_removed_reports = remove_declared_reports(build_root, spec)
    errors: list[str] = []
    suite_results: list[dict[str, Any]] = []
    artifacts: list[dict[str, Any]] = []
    snapshot_verified_after_preflight = False
    snapshot_rematerialized_before_formal = False
    snapshot_verified_after_formal = False
    formal_removed_reports: list[str] = []

    preflight_command = preflight_maven_command(args.maven)
    (
        preflight_run,
        preflight_log_content,
        preflight_raw_log_sha256,
    ) = capture_maven_run(
        preflight_command,
        build_root,
        redactor,
        maven_environment,
    )
    preflight_run["oldDeclaredReportsRemoved"] = preflight_removed_reports
    preflight_run["formalSuiteContract"] = False
    preflight_run["sourceSnapshot"] = {
        "mode": "git-object-tree",
        "candidateSha": source_snapshot.candidate_sha,
        "treeSha": source_snapshot.tree_sha,
        "manifestSha256": source_snapshot.manifest_sha256,
        "fileCount": source_snapshot.file_count,
    }
    if preflight_run["exitCode"] != 0:
        errors.append(
            f"Phase 0 target preflight exited with {preflight_run['exitCode']}"
        )
    try:
        verify_materialised_git_tree(
            build_root,
            expected_tree_entries,
            "preflight immutable source snapshot after Maven",
        )
        snapshot_verified_after_preflight = True
    except GateError as exc:
        errors.append(f"post-preflight source snapshot verification failed: {exc}")

    after_preflight_head: str | None = None
    try:
        after_preflight_head = bind_end_candidate(
            repo_root,
            start_head,
            expected_candidate,
        )
    except GateError as exc:
        errors.append(f"post-preflight candidate binding failed: {exc}")

    source_clean_after_preflight = True
    ignored_untracked_after_preflight = ignored_untracked_at_start
    try:
        require_clean_tracked_worktree(repo_root)
        ignored_untracked_after_preflight = require_no_build_relevant_untracked(
            repo_root
        )
    except GateError as exc:
        source_clean_after_preflight = False
        errors.append(f"post-preflight source cleanliness failed: {exc}")

    spec_source_sha256_after_preflight = sha256_file(spec_path)
    if spec_source_sha256_after_preflight != spec_source_sha256_at_start:
        errors.append(
            "gate spec changed during target preflight: "
            f"start={spec_source_sha256_at_start}, "
            f"end={spec_source_sha256_after_preflight}"
        )

    spec_dir = evidence_dir / "spec"
    spec_dir.mkdir(parents=True, exist_ok=True)
    evidence_spec = spec_dir / "phase00_ci_gate_spec.json"
    shutil.copyfile(snapshot_spec_path, evidence_spec)
    spec_record = artifact_record(
        evidence_dir,
        evidence_spec,
        kind="gate-spec",
        source_path=spec_source_path,
        source_sha256=snapshot_spec_sha256,
        source_retained=True,
    )
    if spec_record["sha256"] != snapshot_spec_sha256:
        errors.append("archived gate spec hash does not match its source")
    artifacts.append(spec_record)

    logs_dir = evidence_dir / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)
    preflight_log = logs_dir / "maven-target-preflight.log"
    preflight_log.write_bytes(preflight_log_content)
    artifacts.append(
        artifact_record(
            evidence_dir,
            preflight_log,
            kind="complete-redacted-preflight-maven-log",
            source_sha256=preflight_raw_log_sha256,
            redactions=(
                "known-sensitive-environment-values",
                "credential-assignments",
                "uri-userinfo",
                "jwt-shaped-values",
            ),
        )
    )

    preflight_identity: dict[str, Any] | None = None
    preflight_evidence_manifest: dict[str, Any] = {
        "sourcePath": spec["targetEvidence"]["preflightPath"],
        "artifactPath": PREFLIGHT_IDENTITY_ARTIFACT,
        "sourceSha256": None,
        "sourceMtimeNs": None,
        "sourceBytes": None,
    }
    try:
        (
            observed_preflight,
            preflight_content,
            preflight_source_sha256,
            preflight_metadata,
        ) = read_regular_json_source(
            build_root,
            spec["targetEvidence"]["preflightPath"],
            "preflight target identity source",
            fresh_after_ns=preflight_run["startedEpochNs"],
        )
        preflight_identity = validate_runtime_identity(
            observed_preflight,
            spec["targetEvidence"],
            declared_targets,
            configured_targets,
            expected_identities,
            expected_producer=spec["targetEvidence"]["preflightProducer"],
        )
        validate_preflight_identity_freshness(
            preflight_identity,
            preflight_run["startedEpochNs"],
        )
        assert_secret_free_bytes(
            preflight_content,
            "preflight target identity",
            redactor,
        )
        preflight_destination = evidence_dir / PREFLIGHT_IDENTITY_ARTIFACT
        preflight_destination.parent.mkdir(parents=True, exist_ok=True)
        preflight_destination.write_bytes(preflight_content)
        preflight_record = artifact_record(
            evidence_dir,
            preflight_destination,
            kind=PREFLIGHT_IDENTITY_KIND,
            source_path=spec["targetEvidence"]["preflightPath"],
            source_sha256=preflight_source_sha256,
            source_retained=True,
        )
        if preflight_record["sha256"] != preflight_source_sha256:
            raise GateError(
                "archived preflight target identity differs from its source bytes"
            )
        artifacts.append(preflight_record)
        preflight_evidence_manifest = {
            "sourcePath": spec["targetEvidence"]["preflightPath"],
            "artifactPath": preflight_record["path"],
            "sourceSha256": preflight_source_sha256,
            "sourceMtimeNs": preflight_metadata["mtimeNs"],
            "sourceBytes": preflight_metadata["bytes"],
        }
    except (GateError, OSError) as exc:
        errors.append(f"preflight target identity validation failed: {exc}")

    runtime_source = resolve_contained_path(
        build_root,
        spec["targetEvidence"]["path"],
        "preflight runtime-identity absence check",
        must_exist=False,
    )
    if runtime_source.exists():
        errors.append(
            "target preflight unexpectedly produced the formal runtime identity"
        )

    if not errors:
        try:
            rematerialise_candidate_snapshot(
                repo_root,
                source_snapshot,
                expected_candidate,
                expected_tree_entries,
            )
            rematerialized_spec_sha256 = sha256_file(snapshot_spec_path)
            if rematerialized_spec_sha256 != snapshot_spec_sha256:
                raise GateError(
                    "rematerialized formal gate spec hash differs from the "
                    "candidate snapshot"
                )
            if load_spec(snapshot_spec_path) != spec:
                raise GateError(
                    "rematerialized formal gate spec content differs from preflight"
                )
            formal_removed_reports = remove_declared_reports(build_root, spec)
            snapshot_rematerialized_before_formal = True
        except (GateError, OSError) as exc:
            errors.append(f"formal source rematerialization failed: {exc}")

    if errors:
        fail_manifest: dict[str, Any] = {
            "schemaVersion": 1,
            "gateId": spec["gateId"],
            "status": "FAIL",
            "candidateSha": start_head,
            "expectedCandidateSha": expected_candidate,
            "gateSpec": {
                "sourcePath": spec_source_path,
                "sourceSha256": snapshot_spec_sha256,
                "artifactPath": spec_record["path"],
                "artifactSha256": spec_record["sha256"],
            },
            "source": {
                "startHead": start_head,
                "afterPreflightHead": after_preflight_head,
                "endHead": after_preflight_head,
                "snapshotMode": "git-object-tree",
                "snapshotCandidateSha": source_snapshot.candidate_sha,
                "snapshotTreeSha": source_snapshot.tree_sha,
                "snapshotManifestSha256": source_snapshot.manifest_sha256,
                "snapshotFileCount": source_snapshot.file_count,
                "snapshotSpecSha256": snapshot_spec_sha256,
                "snapshotContainsGitMetadata": False,
                "snapshotVerifiedAfterPreflight": (
                    snapshot_verified_after_preflight
                ),
                "snapshotRematerializedBeforeFormal": (
                    snapshot_rematerialized_before_formal
                ),
                "snapshotVerifiedAfterFormal": snapshot_verified_after_formal,
                "trackedWorktreeCleanAtStart": True,
                "trackedWorktreeCleanAfterPreflight": source_clean_after_preflight,
                "trackedWorktreeCleanAtEnd": source_clean_after_preflight,
                "buildRelevantUntrackedAbsentAtStart": True,
                "buildRelevantUntrackedAbsentAfterPreflight": (
                    source_clean_after_preflight
                ),
                "buildRelevantUntrackedAbsentAtEnd": source_clean_after_preflight,
                "ignoredNonBuildUntrackedAtStart": ignored_untracked_at_start,
                "ignoredNonBuildUntrackedAfterPreflight": (
                    ignored_untracked_after_preflight
                ),
                "ignoredNonBuildUntrackedAtEnd": ignored_untracked_after_preflight,
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
            "preflight": preflight_run,
            "run": {"executed": False},
            "tools": tool_versions,
            "targets": {
                "declared": declared_targets,
                "configured": configured_targets,
                "expected": expected_identities,
                "preflight": preflight_identity,
                "runtime": None,
                "evidence": {
                    "preflight": preflight_evidence_manifest,
                    "runtime": None,
                },
                "constraints": target_constraints,
            },
            "suites": [],
            "supportingArtifacts": [],
            "artifacts": artifacts,
            "errors": errors,
            "security": {},
        }
        finalize_manifest_security(evidence_dir, fail_manifest, redactor)
        persist_manifest(evidence_dir, fail_manifest)
        print("[phase00-ci-gate] PRECHECK FAIL:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1, None

    maven_command = formal_maven_command(args.maven)
    started_at = utc_now()
    started_ns = time.time_ns()
    safe_log_fd, safe_log_name = tempfile.mkstemp(
        prefix="phase00-maven-", suffix=".log"
    )
    os.close(safe_log_fd)
    safe_log_temp = Path(safe_log_name)
    try:
        maven_exit_code, raw_log_sha256 = stream_maven(
            maven_command,
            build_root,
            redactor,
            safe_log_temp,
            maven_environment,
        )
        ended_ns = time.time_ns()
        ended_at = utc_now()
        if maven_exit_code != 0:
            errors.append(f"Maven clean verify exited with {maven_exit_code}")
        try:
            verify_materialised_git_tree(
                build_root,
                expected_tree_entries,
                "formal immutable source snapshot after Maven",
            )
            snapshot_verified_after_formal = True
        except GateError as exc:
            errors.append(f"post-formal source snapshot verification failed: {exc}")
        end_head: str | None = None
        try:
            end_head = bind_end_candidate(
                repo_root,
                start_head,
                expected_candidate,
            )
        except GateError as exc:
            errors.append(f"post-run candidate binding failed: {exc}")
        spec_source_sha256_at_end = sha256_file(spec_path)
        if spec_source_sha256_at_end != spec_source_sha256_at_start:
            errors.append(
                "gate spec changed during Maven execution: "
                f"start={spec_source_sha256_at_start}, "
                f"end={spec_source_sha256_at_end}"
            )
        source_clean_at_end = True
        ignored_untracked_at_end = ignored_untracked_at_start
        try:
            require_clean_tracked_worktree(repo_root)
            ignored_untracked_at_end = require_no_build_relevant_untracked(repo_root)
        except GateError as exc:
            source_clean_at_end = False
            errors.append(f"post-run source cleanliness failed: {exc}")
        try:
            suite_results = validate_suite_reports(build_root, spec, started_ns)
        except GateError as exc:
            errors.append(str(exc))

        runtime_identity: dict[str, Any] | None = None
        target_evidence_manifest: dict[str, Any] = {
            "sourcePath": spec["targetEvidence"]["path"],
            "artifactPath": TARGET_IDENTITY_ARTIFACT,
            "sourceSha256": None,
            "sourceMtimeNs": None,
            "sourceBytes": None,
        }
        try:
            (
                observed_identity,
                identity_content,
                identity_source_sha256,
                identity_metadata,
            ) = read_regular_json_source(
                build_root,
                spec["targetEvidence"]["path"],
                "runtime target identity source",
                fresh_after_ns=started_ns,
            )
            runtime_identity = validate_runtime_identity(
                observed_identity,
                spec["targetEvidence"],
                declared_targets,
                configured_targets,
                expected_identities,
            )
            if preflight_identity is None:
                raise GateError("validated preflight target identity is unavailable")
            require_matching_preflight_and_runtime(
                preflight_identity,
                runtime_identity,
            )
            assert_secret_free_bytes(
                identity_content,
                "runtime target identity",
                redactor,
            )
            identity_destination = evidence_dir / TARGET_IDENTITY_ARTIFACT
            identity_destination.parent.mkdir(parents=True, exist_ok=True)
            identity_destination.write_bytes(identity_content)
            identity_record = artifact_record(
                evidence_dir,
                identity_destination,
                kind=TARGET_IDENTITY_KIND,
                source_path=spec["targetEvidence"]["path"],
                source_sha256=identity_source_sha256,
                source_retained=True,
            )
            if identity_record["sha256"] != identity_source_sha256:
                raise GateError(
                    "archived runtime target identity differs from its source bytes"
                )
            artifacts.append(identity_record)
            target_evidence_manifest = {
                "sourcePath": spec["targetEvidence"]["path"],
                "artifactPath": identity_record["path"],
                "sourceSha256": identity_source_sha256,
                "sourceMtimeNs": identity_metadata["mtimeNs"],
                "sourceBytes": identity_metadata["bytes"],
            }
        except (GateError, OSError) as exc:
            errors.append(f"runtime target identity validation failed: {exc}")

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
        suite_source_hash_by_id: dict[str, str] = {}
        for suite in spec["suites"]:
            try:
                source = resolve_contained_path(
                    build_root,
                    suite["report"],
                    f"{suite['id']} source report",
                    must_exist=False,
                )
            except GateError as exc:
                errors.append(str(exc))
                continue
            if not source.exists() or not source.is_file():
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
                suite_source_hash_by_id[suite["id"]] = source_hash
            except GateError as exc:
                errors.append(str(exc))

        supporting_results: list[dict[str, Any]] = []
        minimum_failsafe_completed = sum(
            len(suite["testcases"])
            for suite in spec["suites"]
            if suite["runner"] == "failsafe"
        )
        for support in spec.get("supportingArtifacts", []):
            try:
                source = resolve_contained_path(
                    build_root,
                    support["path"],
                    f"{support['id']} supporting source",
                    must_exist=False,
                )
            except GateError as exc:
                errors.append(str(exc))
                supporting_results.append(
                    {
                        "id": support["id"],
                        "kind": support["kind"],
                        "sourcePath": support["path"],
                        "required": support.get("required", False),
                        "present": False,
                    }
                )
                continue
            result = {
                "id": support["id"],
                "kind": support["kind"],
                "sourcePath": support["path"],
                "required": support.get("required", False),
                "present": source.exists() and source.is_file(),
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
                result["sourceSha256"] = suite_source_hash_by_id[result["id"]]

        status = "PASS" if maven_exit_code == 0 and not errors else "FAIL"
        manifest: dict[str, Any] = {
            "schemaVersion": 1,
            "gateId": spec["gateId"],
            "status": status,
            "candidateSha": start_head,
            "expectedCandidateSha": expected_candidate,
            "gateSpec": {
                "sourcePath": spec_source_path,
                "sourceSha256": snapshot_spec_sha256,
                "artifactPath": spec_record["path"],
                "artifactSha256": spec_record["sha256"],
            },
            "source": {
                "startHead": start_head,
                "afterPreflightHead": after_preflight_head,
                "endHead": end_head,
                "snapshotMode": "git-object-tree",
                "snapshotCandidateSha": source_snapshot.candidate_sha,
                "snapshotTreeSha": source_snapshot.tree_sha,
                "snapshotManifestSha256": source_snapshot.manifest_sha256,
                "snapshotFileCount": source_snapshot.file_count,
                "snapshotSpecSha256": snapshot_spec_sha256,
                "snapshotContainsGitMetadata": False,
                "snapshotVerifiedAfterPreflight": (
                    snapshot_verified_after_preflight
                ),
                "snapshotRematerializedBeforeFormal": (
                    snapshot_rematerialized_before_formal
                ),
                "snapshotVerifiedAfterFormal": snapshot_verified_after_formal,
                "trackedWorktreeCleanAtStart": True,
                "trackedWorktreeCleanAfterPreflight": (
                    source_clean_after_preflight
                ),
                "trackedWorktreeCleanAtEnd": source_clean_at_end,
                "buildRelevantUntrackedAbsentAtStart": True,
                "buildRelevantUntrackedAbsentAfterPreflight": (
                    source_clean_after_preflight
                ),
                "buildRelevantUntrackedAbsentAtEnd": source_clean_at_end,
                "ignoredNonBuildUntrackedAtStart": ignored_untracked_at_start,
                "ignoredNonBuildUntrackedAfterPreflight": (
                    ignored_untracked_after_preflight
                ),
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
            "preflight": preflight_run,
            "run": {
                "command": maven_command,
                "workingDirectory": ".",
                "startedAtUtc": started_at,
                "endedAtUtc": ended_at,
                "startedEpochNs": started_ns,
                "endedEpochNs": ended_ns,
                "exitCode": maven_exit_code,
                "oldDeclaredReportsRemoved": formal_removed_reports,
                "cleanLifecycleRequired": True,
                "sourceSnapshot": {
                    "mode": "git-object-tree",
                    "candidateSha": source_snapshot.candidate_sha,
                    "treeSha": source_snapshot.tree_sha,
                    "manifestSha256": source_snapshot.manifest_sha256,
                    "fileCount": source_snapshot.file_count,
                },
            },
            "tools": tool_versions,
            "targets": {
                "declared": declared_targets,
                "configured": configured_targets,
                "expected": expected_identities,
                "preflight": preflight_identity,
                "runtime": runtime_identity,
                "evidence": {
                    "preflight": preflight_evidence_manifest,
                    "runtime": target_evidence_manifest,
                },
                "constraints": target_constraints,
            },
            "suites": suite_results,
            "supportingArtifacts": supporting_results,
            "artifacts": artifacts,
            "errors": errors,
            "security": {},
        }
        if status == "PASS":
            return 0, PendingPassEvidence(
                manifest=manifest,
                redactor=redactor,
            )
        finalize_manifest_security(evidence_dir, manifest, redactor)
        persist_manifest(evidence_dir, manifest)
        print("[phase00-ci-gate] FAIL:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1, None
    finally:
        safe_log_temp.unlink(missing_ok=True)


def read_manifest(
    evidence: EvidenceSnapshot | Path,
) -> dict[str, Any]:
    snapshot = (
        evidence
        if isinstance(evidence, EvidenceSnapshot)
        else capture_evidence_snapshot(evidence)
    )
    content = snapshot.require(
        "manifest.json",
        "evidence manifest",
    ).content
    manifest = require_exact_keys(
        strict_json_bytes(
            content,
            "evidence manifest",
            maximum=MAX_MANIFEST_BYTES,
        ),
        {
            "schemaVersion",
            "gateId",
            "status",
            "candidateSha",
            "expectedCandidateSha",
            "gateSpec",
            "source",
            "preflight",
            "run",
            "tools",
            "targets",
            "suites",
            "supportingArtifacts",
            "artifacts",
            "errors",
            "security",
        },
        "evidence manifest",
    )
    if (
        isinstance(manifest.get("schemaVersion"), bool)
        or manifest.get("schemaVersion") != 1
    ):
        raise GateError("evidence manifest schemaVersion must be 1")
    return dict(manifest)


def index_unique_manifest_objects(
    value: Any,
    label: str,
) -> dict[str, Mapping[str, Any]]:
    """Index an exact manifest array without last-value-wins ambiguity."""

    if not isinstance(value, list):
        raise GateError(f"{label} must be a list")
    indexed: dict[str, Mapping[str, Any]] = {}
    for index, item in enumerate(value):
        if not isinstance(item, dict):
            raise GateError(f"{label}[{index}] must be an object")
        item_id = item.get("id")
        if not isinstance(item_id, str) or not item_id:
            raise GateError(f"{label}[{index}].id must be non-empty")
        if item_id in indexed:
            raise GateError(f"{label} contains duplicate id: {item_id}")
        indexed[item_id] = item
    return indexed


def list_evidence_files(evidence: EvidenceSnapshot | Path) -> set[str]:
    """Return the exact file set from one immutable evidence snapshot."""

    snapshot = (
        evidence
        if isinstance(evidence, EvidenceSnapshot)
        else capture_evidence_snapshot(evidence)
    )
    return set(snapshot.files)


def verify_checksums_file(
    evidence: EvidenceSnapshot | Path,
    expected_paths: set[str],
) -> None:
    snapshot = (
        evidence
        if isinstance(evidence, EvidenceSnapshot)
        else capture_evidence_snapshot(evidence)
    )
    checksum_file = snapshot.require("SHA256SUMS", "SHA256SUMS")
    try:
        lines = checksum_file.content.decode(
            "utf-8",
            errors="strict",
        ).splitlines()
    except UnicodeDecodeError as exc:
        raise GateError("SHA256SUMS is not UTF-8") from exc
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
        artifact = snapshot.require(
            relative.as_posix(),
            "SHA256SUMS artifact",
        )
        actual = artifact.sha256
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
    actual_files = list_evidence_files(snapshot)
    expected_files = expected_paths | {"SHA256SUMS"}
    if actual_files != expected_files:
        missing = sorted(expected_files - actual_files)
        unexpected = sorted(actual_files - expected_files)
        raise GateError(
            "evidence directory contains missing/unlisted files: "
            f"missing={missing}, unexpected={unexpected}"
        )


def verify_artifact_records(
    evidence: EvidenceSnapshot | Path,
    artifacts: Any,
) -> dict[str, dict[str, Any]]:
    snapshot = (
        evidence
        if isinstance(evidence, EvidenceSnapshot)
        else capture_evidence_snapshot(evidence)
    )
    if not isinstance(artifacts, list) or not artifacts:
        raise GateError("manifest artifacts must be a non-empty list")
    records: dict[str, dict[str, Any]] = {}
    for index, record in enumerate(artifacts):
        if not isinstance(record, dict):
            raise GateError(f"artifacts[{index}] must be an object")
        allowed_fields = {
            "path",
            "kind",
            "sha256",
            "bytes",
            "sourcePath",
            "sourceSha256",
            "sourceRetained",
            "redactions",
        }
        unexpected_fields = set(record) - allowed_fields
        if unexpected_fields:
            raise GateError(
                f"artifacts[{index}] has unexpected fields: "
                f"{sorted(unexpected_fields)}"
            )
        required_fields = {"path", "kind", "sha256", "bytes"}
        missing_fields = required_fields - set(record)
        if missing_fields:
            raise GateError(
                f"artifacts[{index}] is missing fields: {sorted(missing_fields)}"
            )
        path_text = record.get("path")
        kind = record.get("kind")
        digest = record.get("sha256")
        byte_count = record.get("bytes")
        source_digest = record.get("sourceSha256")
        if not isinstance(path_text, str):
            raise GateError(f"artifacts[{index}].path must be a string")
        if not isinstance(kind, str) or not kind:
            raise GateError(f"artifacts[{index}].kind must be non-empty")
        if (
            not isinstance(byte_count, int)
            or isinstance(byte_count, bool)
            or byte_count < 0
        ):
            raise GateError(f"artifacts[{index}].bytes must be non-negative")
        relative = safe_relative_path(path_text, f"artifacts[{index}].path")
        if path_text in records:
            raise GateError(f"duplicate artifact record: {path_text}")
        if not isinstance(digest, str) or not SHA256_RE.fullmatch(digest):
            raise GateError(f"invalid artifact SHA-256 for {path_text}")
        if source_digest is not None and (
            not isinstance(source_digest, str) or not SHA256_RE.fullmatch(source_digest)
        ):
            raise GateError(f"invalid source SHA-256 for {path_text}")
        provenance_fields = {
            field
            for field in ("sourcePath", "sourceSha256", "sourceRetained")
            if field in record
        }
        if provenance_fields:
            if "sourceSha256" not in record or "sourceRetained" not in record:
                raise GateError(
                    f"incomplete source provenance for artifact {path_text}"
                )
            if not isinstance(record.get("sourceRetained"), bool):
                raise GateError(
                    f"artifact sourceRetained must be boolean for {path_text}"
                )
            if "sourcePath" in record:
                source_path = record.get("sourcePath")
                if not isinstance(source_path, str):
                    raise GateError(
                        f"artifact sourcePath must be a string for {path_text}"
                    )
                safe_relative_path(
                    source_path,
                    f"artifact sourcePath for {path_text}",
                )
        redactions = record.get("redactions")
        if redactions is not None and (
            not isinstance(redactions, list)
            or any(not isinstance(value, str) or not value for value in redactions)
            or len(redactions) != len(set(redactions))
        ):
            raise GateError(f"artifact redactions are invalid for {path_text}")
        file_snapshot = snapshot.require(
            relative.as_posix(),
            f"artifact {index}",
        )
        actual = file_snapshot.sha256
        if actual != digest:
            raise GateError(
                f"artifact SHA-256 mismatch for {path_text}: "
                f"actual={actual}, expected={digest}"
            )
        if file_snapshot.size != byte_count:
            raise GateError(f"artifact byte count mismatch for {path_text}")
        records[path_text] = record
    return records


def require_source_provenance(
    record: Mapping[str, Any],
    *,
    expected_source_path: str,
    expected_kind: str,
    expected_source_sha256: str | None,
    source_retained: bool,
    label: str,
) -> str:
    source_sha256 = record.get("sourceSha256")
    if (
        record.get("kind") != expected_kind
        or record.get("sourcePath") != expected_source_path
        or not isinstance(source_sha256, str)
        or not SHA256_RE.fullmatch(source_sha256)
        or record.get("sourceRetained") is not source_retained
    ):
        raise GateError(f"{label} source provenance is incomplete or mismatched")
    if expected_source_sha256 is not None and source_sha256 != expected_source_sha256:
        raise GateError(f"{label} source SHA-256 does not match its authority")
    return source_sha256


def require_artifact_record_shape(
    records: Mapping[str, Mapping[str, Any]],
    path: str,
    fields: set[str],
    label: str,
) -> Mapping[str, Any]:
    record = records.get(path)
    if record is None:
        raise GateError(f"{label} artifact record is missing")
    return require_exact_keys(record, fields, f"{label} artifact record")


def verify_evidence(
    evidence_dir: Path,
    spec_path: Path,
    expected_candidate: str,
) -> dict[str, Any]:
    evidence = capture_evidence_snapshot(evidence_dir)
    return _verify_evidence_snapshot(
        evidence,
        spec_path,
        expected_candidate,
    )


def _verify_evidence_snapshot(
    evidence: EvidenceSnapshot,
    spec_path: Path,
    expected_candidate: str,
) -> dict[str, Any]:
    repo_root = SCRIPT_DIR.parent.resolve(strict=True)
    _spec_resolved, spec_source_path = resolve_repo_file(
        repo_root,
        spec_path,
        "verification gate spec",
    )
    candidate_spec_content = read_candidate_git_blob(
        repo_root,
        expected_candidate,
        spec_source_path,
        "verification gate spec",
    )
    (
        _live_spec,
        live_spec_content,
        _live_spec_sha256,
        _live_spec_metadata,
    ) = read_regular_json_source(
        repo_root,
        spec_source_path,
        "verification gate spec",
        fresh_after_ns=None,
    )
    if live_spec_content != candidate_spec_content:
        raise GateError(
            "verification gate spec differs from the expected candidate Git blob"
        )
    spec_source_sha256 = sha256_bytes(candidate_spec_content)
    spec = load_spec_bytes(
        candidate_spec_content,
        f"candidate gate spec {spec_source_path}",
    )
    manifest = read_manifest(evidence)
    verify_candidate_attestation(manifest, expected_candidate)
    if manifest.get("gateId") != spec["gateId"]:
        raise GateError("manifest gateId does not match gate spec")
    if manifest.get("status") != "PASS":
        raise GateError(f"evidence gate status is not PASS: {manifest.get('status')!r}")
    source = require_exact_keys(
        manifest.get("source"),
        {
            "startHead",
            "afterPreflightHead",
            "endHead",
            "snapshotMode",
            "snapshotCandidateSha",
            "snapshotTreeSha",
            "snapshotManifestSha256",
            "snapshotFileCount",
            "snapshotSpecSha256",
            "snapshotContainsGitMetadata",
            "snapshotVerifiedAfterPreflight",
            "snapshotRematerializedBeforeFormal",
            "snapshotVerifiedAfterFormal",
            "trackedWorktreeCleanAtStart",
            "trackedWorktreeCleanAfterPreflight",
            "trackedWorktreeCleanAtEnd",
            "buildRelevantUntrackedAbsentAtStart",
            "buildRelevantUntrackedAbsentAfterPreflight",
            "buildRelevantUntrackedAbsentAtEnd",
            "ignoredNonBuildUntrackedAtStart",
            "ignoredNonBuildUntrackedAfterPreflight",
            "ignoredNonBuildUntrackedAtEnd",
            "statusCommand",
            "untrackedCommand",
        },
        "manifest source",
    )
    if (
        source.get("snapshotVerifiedAfterPreflight") is not True
        or source.get("snapshotRematerializedBeforeFormal") is not True
        or source.get("snapshotVerifiedAfterFormal") is not True
        or source.get("trackedWorktreeCleanAtStart") is not True
        or source.get("trackedWorktreeCleanAfterPreflight") is not True
        or source.get("trackedWorktreeCleanAtEnd") is not True
        or source.get("buildRelevantUntrackedAbsentAtStart") is not True
        or source.get("buildRelevantUntrackedAbsentAfterPreflight") is not True
        or source.get("buildRelevantUntrackedAbsentAtEnd") is not True
        or not isinstance(source.get("ignoredNonBuildUntrackedAtStart"), int)
        or isinstance(source.get("ignoredNonBuildUntrackedAtStart"), bool)
        or source.get("ignoredNonBuildUntrackedAtStart") < 0
        or not isinstance(
            source.get("ignoredNonBuildUntrackedAfterPreflight"), int
        )
        or isinstance(source.get("ignoredNonBuildUntrackedAfterPreflight"), bool)
        or source.get("ignoredNonBuildUntrackedAfterPreflight") < 0
        or not isinstance(source.get("ignoredNonBuildUntrackedAtEnd"), int)
        or isinstance(source.get("ignoredNonBuildUntrackedAtEnd"), bool)
        or source.get("ignoredNonBuildUntrackedAtEnd") < 0
        or source.get("statusCommand")
        != ["git", "status", "--porcelain=v1", "--untracked-files=no"]
        or source.get("untrackedCommand")
        != ["git", "ls-files", "--others", "--exclude-standard", "-z"]
    ):
        raise GateError("evidence does not prove candidate source cleanliness")
    expected_snapshot_binding = verify_source_snapshot_attestation(
        source,
        repo_root,
        expected_candidate,
        spec_source_sha256,
    )
    preflight = require_exact_keys(
        manifest.get("preflight"),
        {
            "command",
            "workingDirectory",
            "startedAtUtc",
            "endedAtUtc",
            "startedEpochNs",
            "endedEpochNs",
            "exitCode",
            "cleanLifecycleRequired",
            "oldDeclaredReportsRemoved",
            "formalSuiteContract",
            "sourceSnapshot",
        },
        "manifest preflight",
    )
    if preflight.get("exitCode") != 0:
        raise GateError("evidence target preflight exitCode must be 0")
    preflight_command = preflight.get("command")
    expected_preflight_tail = preflight_maven_command("mvn")[1:]
    if (
        not isinstance(preflight_command, list)
        or preflight_command[1:] != expected_preflight_tail
        or Path(str(preflight_command[0])).name.lower()
        not in {"mvn", "mvn.cmd", "mvnw", "mvnw.cmd"}
        or preflight.get("cleanLifecycleRequired") is not True
        or preflight.get("formalSuiteContract") is not False
        or preflight.get("sourceSnapshot") != expected_snapshot_binding
        or preflight.get("workingDirectory") != "."
        or preflight.get("oldDeclaredReportsRemoved") != []
    ):
        raise GateError(f"unexpected target preflight command: {preflight_command!r}")
    preflight_started = parse_utc(
        preflight.get("startedAtUtc"),
        "preflight.startedAtUtc",
    )
    preflight_ended = parse_utc(
        preflight.get("endedAtUtc"),
        "preflight.endedAtUtc",
    )
    preflight_started_ns = preflight.get("startedEpochNs")
    preflight_ended_ns = preflight.get("endedEpochNs")
    if (
        preflight_ended < preflight_started
        or not isinstance(preflight_started_ns, int)
        or isinstance(preflight_started_ns, bool)
        or not isinstance(preflight_ended_ns, int)
        or isinstance(preflight_ended_ns, bool)
        or preflight_ended_ns < preflight_started_ns
    ):
        raise GateError("target preflight timestamps are missing or reversed")
    run = require_exact_keys(
        manifest.get("run"),
        {
            "command",
            "workingDirectory",
            "startedAtUtc",
            "endedAtUtc",
            "startedEpochNs",
            "endedEpochNs",
            "exitCode",
            "oldDeclaredReportsRemoved",
            "cleanLifecycleRequired",
            "sourceSnapshot",
        },
        "manifest run",
    )
    if run.get("exitCode") != 0:
        raise GateError("evidence Maven exitCode must be 0")
    command = run.get("command")
    expected_formal_command = formal_maven_command("mvn")
    if (
        not isinstance(command, list)
        or len(command) != len(expected_formal_command)
        or command[1:] != expected_formal_command[1:]
        or command[0] != preflight_command[0]
        or Path(str(command[0])).name.lower()
        not in {"mvn", "mvn.cmd", "mvnw", "mvnw.cmd"}
        or run.get("sourceSnapshot") != expected_snapshot_binding
        or run.get("workingDirectory") != "."
        or run.get("oldDeclaredReportsRemoved") != []
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
        or isinstance(started_ns, bool)
        or not isinstance(ended_ns, int)
        or isinstance(ended_ns, bool)
        or ended_ns < started_ns
    ):
        raise GateError("run epoch nanoseconds are missing or reversed")
    if preflight_ended > started or preflight_ended_ns > started_ns:
        raise GateError("formal Maven verify started before target preflight ended")

    validate_manifest_target_preamble(
        manifest.get("targets"),
        expected_candidate,
    )
    tools = manifest.get("tools")
    if (
        not isinstance(tools, dict)
        or set(tools) != {"git", "java", "maven", "python"}
        or any(not isinstance(value, str) or not value for value in tools.values())
        or any(value.lower().startswith("unavailable:") for value in tools.values())
    ):
        raise GateError("manifest tool versions are incomplete")
    security = require_exact_keys(
        manifest.get("security"),
        {
            "containsSecrets",
            "rawSourcesRetained",
            "xmlPropertiesRemoved",
            "logRedacted",
            "secretScan",
        },
        "manifest security",
    )
    secret_scan = require_exact_keys(
        security.get("secretScan"),
        {"status", "policy", "artifactCount"},
        "manifest security.secretScan",
    )
    if (
        security.get("containsSecrets") is not False
        or security.get("rawSourcesRetained") is not False
        or security.get("xmlPropertiesRemoved") is not True
        or security.get("logRedacted") is not True
        or secret_scan.get("status") != "PASS"
        or secret_scan.get("policy") != SECRET_SCAN_POLICY
        or not isinstance(secret_scan.get("artifactCount"), int)
        or isinstance(secret_scan.get("artifactCount"), bool)
        or secret_scan.get("artifactCount") < 0
    ):
        raise GateError("manifest does not assert a secret-free artifact")

    records = verify_artifact_records(evidence, manifest.get("artifacts"))
    if secret_scan.get("artifactCount") != len(records):
        raise GateError("manifest secret scan artifact count is not exact")
    verification_redactor = SecretRedactor(dict(os.environ))
    for artifact_path in records:
        assert_secret_free_bytes(
            evidence.require(
                artifact_path,
                f"secret-scanned artifact {artifact_path}",
            ).content,
            f"secret-scanned artifact {artifact_path}",
            verification_redactor,
        )
    assert_secret_free_bytes(
        evidence.require("manifest.json", "secret-scanned manifest").content,
        "secret-scanned manifest",
        verification_redactor,
    )
    verify_checksums_file(
        evidence,
        expected_paths=set(records) | {"manifest.json"},
    )
    base_artifact_fields = {"path", "kind", "sha256", "bytes"}
    source_artifact_fields = base_artifact_fields | {
        "sourcePath",
        "sourceSha256",
        "sourceRetained",
    }
    redacted_source_artifact_fields = source_artifact_fields | {"redactions"}
    redacted_log_artifact_fields = base_artifact_fields | {
        "sourceSha256",
        "sourceRetained",
        "redactions",
    }
    expected_record_paths = {
        "spec/phase00_ci_gate_spec.json",
        "logs/maven-target-preflight.log",
        "logs/maven-clean-verify.log",
        PREFLIGHT_IDENTITY_ARTIFACT,
        TARGET_IDENTITY_ARTIFACT,
    }
    require_artifact_record_shape(
        records,
        "spec/phase00_ci_gate_spec.json",
        source_artifact_fields,
        "gate spec",
    )
    for log_path, expected_kind in (
        (
            "logs/maven-target-preflight.log",
            "complete-redacted-preflight-maven-log",
        ),
        ("logs/maven-clean-verify.log", "complete-redacted-maven-log"),
    ):
        log_record = require_artifact_record_shape(
            records,
            log_path,
            redacted_log_artifact_fields,
            log_path,
        )
        if (
            log_record.get("kind") != expected_kind
            or log_record.get("sourceRetained") is not False
        ):
            raise GateError(f"{log_path} artifact provenance is invalid")
    for identity_path, identity_kind in (
        (PREFLIGHT_IDENTITY_ARTIFACT, PREFLIGHT_IDENTITY_KIND),
        (TARGET_IDENTITY_ARTIFACT, TARGET_IDENTITY_KIND),
    ):
        identity_record = require_artifact_record_shape(
            records,
            identity_path,
            source_artifact_fields,
            identity_path,
        )
        if identity_record.get("kind") != identity_kind:
            raise GateError(f"{identity_path} artifact kind is invalid")
    verify_runtime_target_artifact(
        evidence=evidence,
        targets_value=manifest.get("targets"),
        target_spec=spec["targetEvidence"],
        expected_candidate=expected_candidate,
        records=records,
        preflight_started_ns=preflight_started_ns,
        runtime_started_ns=started_ns,
    )
    gate_spec = require_exact_keys(
        manifest.get("gateSpec"),
        {
            "sourcePath",
            "sourceSha256",
            "artifactPath",
            "artifactSha256",
        },
        "manifest gateSpec",
    )
    expected_spec_artifact = "spec/phase00_ci_gate_spec.json"
    if (
        gate_spec.get("sourcePath") != spec_source_path
        or gate_spec.get("sourceSha256") != spec_source_sha256
        or gate_spec.get("artifactPath") != expected_spec_artifact
        or gate_spec.get("artifactSha256") != spec_source_sha256
    ):
        raise GateError("manifest gate spec provenance does not match the verifier spec")
    spec_record = records.get(expected_spec_artifact)
    if (
        sum(
            1 for record in records.values() if record.get("kind") == "gate-spec"
        )
        != 1
        or not isinstance(spec_record, dict)
    ):
        raise GateError("archived gate spec artifact provenance is incomplete")
    require_source_provenance(
        spec_record,
        expected_source_path=spec_source_path,
        expected_kind="gate-spec",
        expected_source_sha256=spec_source_sha256,
        source_retained=True,
        label="archived gate spec",
    )
    if spec_record.get("sha256") != spec_source_sha256:
        raise GateError("archived gate spec hash does not match the verifier spec")
    archived_spec = evidence.require(
        expected_spec_artifact,
        "archived gate spec",
    )
    if archived_spec.content != candidate_spec_content:
        raise GateError(
            "archived gate spec bytes differ from the candidate Git blob"
        )
    log_records = [
        record
        for record in records.values()
        if record.get("kind") == "complete-redacted-maven-log"
    ]
    if len(log_records) != 1:
        raise GateError("evidence must contain exactly one complete Maven log")
    preflight_log_records = [
        record
        for record in records.values()
        if record.get("kind") == "complete-redacted-preflight-maven-log"
    ]
    if len(preflight_log_records) != 1:
        raise GateError(
            "evidence must contain exactly one complete target preflight Maven log"
        )

    by_id = index_unique_manifest_objects(
        manifest.get("suites"),
        "manifest suites",
    )
    if set(by_id) != {suite["id"] for suite in spec["suites"]}:
        raise GateError("manifest exact suite ID set does not match gate spec")
    for suite in spec["suites"]:
        result = require_exact_keys(
            by_id[suite["id"]],
            {
                "id",
                "runner",
                "suite",
                "expectedTests",
                "observedTests",
                "failures",
                "errors",
                "skipped",
                "testcases",
                "sourceReport",
                "sourceMtimeNs",
                "evidenceArtifact",
                "sourceSha256",
            },
            f"manifest suite {suite['id']}",
        )
        if result.get("runner") != suite["runner"]:
            raise GateError(f"manifest suite runner mismatch for {suite['id']}")
        numeric_result_fields = (
            "expectedTests",
            "observedTests",
            "failures",
            "errors",
            "skipped",
            "sourceMtimeNs",
        )
        if any(
            not isinstance(result.get(field), int)
            or isinstance(result.get(field), bool)
            or result.get(field) < 0
            for field in numeric_result_fields
        ):
            raise GateError(f"manifest numeric result is invalid for {suite['id']}")
        if result.get("expectedTests") != len(suite["testcases"]):
            raise GateError(f"manifest expected test count mismatch for {suite['id']}")
        if result.get("suite") != suite["suite"]:
            raise GateError(f"manifest suite class mismatch for {suite['id']}")
        if result.get("testcases") != suite["testcases"]:
            raise GateError(f"manifest testcase set mismatch for {suite['id']}")
        if any(result.get(key) != 0 for key in ("failures", "errors", "skipped")):
            raise GateError(f"manifest non-green suite result for {suite['id']}")
        if result.get("observedTests") != len(suite["testcases"]):
            raise GateError(f"manifest test count mismatch for {suite['id']}")
        evidence_path = result.get("evidenceArtifact")
        expected_evidence_path = f"xml/{suite['id']}.xml"
        if evidence_path != expected_evidence_path or evidence_path not in records:
            raise GateError(f"manifest suite artifact missing for {suite['id']}")
        expected_record_paths.add(expected_evidence_path)
        suite_record = require_artifact_record_shape(
            records,
            evidence_path,
            redacted_source_artifact_fields,
            f"manifest suite {suite['id']}",
        )
        source_sha256 = result.get("sourceSha256")
        if (
            result.get("sourceReport") != suite["report"]
            or not isinstance(source_sha256, str)
            or not SHA256_RE.fullmatch(source_sha256)
        ):
            raise GateError(f"manifest suite source provenance mismatch for {suite['id']}")
        require_source_provenance(
            suite_record,
            expected_source_path=suite["report"],
            expected_kind="sanitised-surefire-failsafe-xml",
            expected_source_sha256=source_sha256,
            source_retained=False,
            label=f"manifest suite {suite['id']}",
        )
        suite_snapshot = evidence.require(
            evidence_path,
            "suite evidence path",
        )
        inspect_suite_report_bytes(
            suite_snapshot.content,
            suite_snapshot.mtime_ns,
            suite,
        )

    support_by_id = index_unique_manifest_objects(
        manifest.get("supportingArtifacts"),
        "manifest supportingArtifacts",
    )
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
        base_support_fields = {
            "id",
            "kind",
            "sourcePath",
            "required",
            "present",
        }
        if result.get("present") is True:
            expected_support_fields = base_support_fields | {
                "artifactPath",
                "sourceSha256",
            }
            if support_spec["kind"] == "maven-summary-xml":
                expected_support_fields.add("counts")
        else:
            expected_support_fields = base_support_fields
        result = require_exact_keys(
            result,
            expected_support_fields,
            f"manifest supporting artifact {support_id}",
        )
        if (
            result.get("kind") != support_spec["kind"]
            or result.get("required") is not support_spec.get("required", False)
            or not isinstance(result.get("present"), bool)
        ):
            raise GateError(
                f"supporting artifact declaration mismatch: {support_id}"
            )
        if result.get("sourcePath") != support_spec["path"]:
            raise GateError(
                f"supporting source path does not match spec: {support_id}"
            )
        if support_spec.get("required", False) and result.get("present") is not True:
            raise GateError(f"required supporting artifact is absent: {support_id}")
        if result.get("present") is not True:
            continue
        artifact_path = result.get("artifactPath")
        expected_artifact_path = f"xml/support-{support_id}.xml"
        if artifact_path != expected_artifact_path or artifact_path not in records:
            raise GateError(f"supporting artifact evidence is missing: {support_id}")
        expected_record_paths.add(expected_artifact_path)
        support_record = require_artifact_record_shape(
            records,
            artifact_path,
            redacted_source_artifact_fields,
            f"supporting artifact {support_id}",
        )
        source_sha256 = result.get("sourceSha256")
        if (
            not isinstance(source_sha256, str)
            or not SHA256_RE.fullmatch(source_sha256)
        ):
            raise GateError(
                f"supporting source provenance does not match spec: {support_id}"
            )
        require_source_provenance(
            support_record,
            expected_source_path=support_spec["path"],
            expected_kind=support_spec["kind"],
            expected_source_sha256=source_sha256,
            source_retained=False,
            label=f"supporting artifact {support_id}",
        )
        if support_spec["kind"] == "maven-summary-xml":
            support_snapshot = evidence.require(
                artifact_path,
                "supporting evidence path",
            )
            parsed_counts = inspect_failsafe_summary_bytes(
                support_snapshot.content,
                minimum_completed=minimum_failsafe_completed,
            )
            if result.get("counts") != parsed_counts:
                raise GateError(
                    f"supporting summary count drift in manifest: {support_id}"
                )
            if (
                result.get("sourceSha256")
                != support_record.get("sourceSha256")
            ):
                raise GateError(
                    f"supporting summary source hash drift in manifest: {support_id}"
                )

    if set(records) != expected_record_paths:
        raise GateError(
            "manifest artifact path set is not exact: "
            f"missing={sorted(expected_record_paths - set(records))}, "
            f"unexpected={sorted(set(records) - expected_record_paths)}"
        )
    if manifest.get("errors") != []:
        raise GateError("PASS manifest must contain an empty errors list")
    return manifest


def verify_gate(args: argparse.Namespace) -> int:
    expected = expected_candidate_from_args(args.expected_candidate_sha)
    verify_evidence(
        evidence_dir=args.evidence_dir.absolute(),
        spec_path=args.spec.absolute(),
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
    run_parser.add_argument(
        "--maven",
        default=default_maven_executable(),
        help=(
            "Maven executable (default: non-empty MVN, otherwise mvn.cmd "
            "resolved from PATH on Windows or mvn on POSIX)"
        ),
    )
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
    except OSError as exc:
        print(
            "[phase00-ci-gate] FAIL: "
            f"filesystem operation failed ({type(exc).__name__})",
            file=sys.stderr,
        )
        return 1
    except Exception as exc:
        print(
            "[phase00-ci-gate] FAIL: "
            f"unexpected gate failure ({type(exc).__name__})",
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
