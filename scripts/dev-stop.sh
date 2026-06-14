#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <name>" >&2
  exit 2
fi

name="$1"
runtime_dir="${TMPDIR:-/tmp}/teacher-cert-platform"
pid_file="$runtime_dir/${name}.pid"

if [ ! -f "$pid_file" ]; then
  echo "$name not running"
  exit 0
fi

pid="$(cat "$pid_file" 2>/dev/null || true)"
rm -f "$pid_file"

if [ -z "$pid" ]; then
  echo "$name pid file was empty"
  exit 0
fi

if command -v taskkill >/dev/null 2>&1; then
  taskkill //F //PID "$pid" >/dev/null 2>&1 || true
else
  kill "$pid" >/dev/null 2>&1 || true
fi

echo "$name stopped: pid=$pid"
