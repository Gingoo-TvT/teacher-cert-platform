#!/usr/bin/env bash
set -euo pipefail

# Simulate a healthy `docker run` followed by an inspect failure. The identity
# step must stop before creating an artifact that could later be checksummed.
docker() {
  if [[ "$1" == "run" ]]; then
    printf '10001\n'
    return 0
  fi
  if [[ "$1" == "image" && "$2" == "inspect" ]]; then
    return 17
  fi
  return 99
}

probe_dir="$(mktemp -d)"
probe_file="$probe_dir/image-identities.txt"
trap 'rm -f "$probe_file"; rmdir "$probe_dir"' EXIT

set +e
(
  set -euo pipefail
  BACKEND_IMAGE=backend:test
  FRONTEND_IMAGE=frontend:test
  GITHUB_SHA=0123456789012345678901234567890123456789

  backend_uid="$(docker run --rm --entrypoint id "$BACKEND_IMAGE" -u)"
  frontend_uid="$(docker run --rm --entrypoint id "$FRONTEND_IMAGE" -u)"
  [[ "$backend_uid" =~ ^[0-9]+$ && "$backend_uid" -ne 0 ]]
  [[ "$frontend_uid" =~ ^[0-9]+$ && "$frontend_uid" -ne 0 ]]

  backend_image_id="$(docker image inspect --format '{{.Id}}' "$BACKEND_IMAGE")"
  frontend_image_id="$(docker image inspect --format '{{.Id}}' "$FRONTEND_IMAGE")"
  [[ "$backend_image_id" =~ ^sha256:[0-9a-f]{64}$ ]]
  [[ "$frontend_image_id" =~ ^sha256:[0-9a-f]{64}$ ]]

  printf 'source.revision=%s\n' "$GITHUB_SHA" > "$probe_file"
)
probe_status=$?
set -e

[[ "$probe_status" -ne 0 ]]
[[ ! -e "$probe_file" ]]
printf 'WS-7 image inspect failure propagation: PASS (exit=%s)\n' "$probe_status"
