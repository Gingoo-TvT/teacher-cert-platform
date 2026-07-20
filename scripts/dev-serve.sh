#!/usr/bin/env bash
set -euo pipefail

# Do not invoke this script from Codex/headless exec. It starts a persistent
# service and is intended for an external terminal or review harness only.

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <name> <health-url> <command> [args...]" >&2
  exit 2
fi

name="$1"
health_url="$2"
shift 2

runtime_dir="${TMPDIR:-/tmp}/teacher-cert-platform"
mkdir -p "$runtime_dir"

pid_file="$runtime_dir/${name}.pid"
log_file="$runtime_dir/${name}.log"

if [ -f "$pid_file" ]; then
  old_pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
    echo "$name already running: pid=$old_pid log=$log_file"
    exit 0
  fi
  rm -f "$pid_file"
fi

if [ "$name" = "backend" ]; then
  export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
fi

nohup "$@" >"$log_file" 2>&1 &
pid="$!"
echo "$pid" >"$pid_file"
echo "$name starting: pid=$pid log=$log_file"

for _ in $(seq 1 60); do
  if curl -fsS "$health_url" >/dev/null 2>&1; then
    echo "$name UP"
    exit 0
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "$name exited before health check passed, see $log_file" >&2
    exit 1
  fi
  sleep 2
done

echo "$name health check timed out, see $log_file" >&2
exit 1
