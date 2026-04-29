#!/usr/bin/env bash
# Resolve the docker/ folder relative to this script, regardless of CWD.
# All helper scripts share this snippet via `source`.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/../docker" && pwd)"

COMPOSE=(docker compose -f "$DOCKER_DIR/docker-compose.yml")
if [[ -f "$DOCKER_DIR/.env" ]]; then
  COMPOSE+=(--env-file "$DOCKER_DIR/.env")
fi
