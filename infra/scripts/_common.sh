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

# parse_project_arg "$@"
#
# Strips an optional `-p NAME` / `--project NAME` (or `-p=NAME`/`--project=NAME`)
# flag from the script's argv and appends `-p NAME` to the COMPOSE command, so
# helper scripts can target a specific docker compose project. Remaining,
# unconsumed arguments are exposed as the SCRIPT_ARGS array, e.g.:
#
#   parse_project_arg "$@"
#   "${COMPOSE[@]}" stop "${SCRIPT_ARGS[@]+"${SCRIPT_ARGS[@]}"}"
parse_project_arg() {
  SCRIPT_ARGS=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -p|--project)
        if [[ -z "${2:-}" ]]; then
          echo "ERROR: $1 requires a project name" >&2
          exit 1
        fi
        COMPOSE+=(-p "$2")
        shift 2
        ;;
      -p=*|--project=*)
        COMPOSE+=(-p "${1#*=}")
        shift
        ;;
      *)
        SCRIPT_ARGS+=("$1")
        shift
        ;;
    esac
  done
}

