#!/usr/bin/env bash
# Resolve the docker/ folder relative to this script, regardless of CWD.
# All helper scripts share this snippet via `source`.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/../docker" && pwd)"

# Full compose invocation: parses docker-compose.yml + .env. Use this for any
# command that needs the service definitions (build, up, config, ...).
COMPOSE=(docker compose -f "$DOCKER_DIR/docker-compose.yml")
if [[ -f "$DOCKER_DIR/.env" ]]; then
  COMPOSE+=(--env-file "$DOCKER_DIR/.env")
fi

# Lightweight compose invocation: no `-f`, no `--env-file`, so the YAML is
# never parsed/validated. Sufficient for lifecycle commands that operate on
# already-running containers via the project label (`stop`, `down`, `logs`,
# `ps`, `restart`, ...). The project name is required and is set by
# `parse_project_arg` below (either from `-p NAME` or from
# COMPOSE_PROJECT_NAME in the shell / .env file).
COMPOSE_LITE=(docker compose)

# parse_project_arg "$@"
#
# Strips an optional `-p NAME` / `--project NAME` (or `-p=NAME`/`--project=NAME`)
# flag from the script's argv and appends `-p NAME` to BOTH the COMPOSE and
# COMPOSE_LITE commands so helper scripts can target a specific docker compose
# project. If `-p` is not provided, it falls back to the COMPOSE_PROJECT_NAME
# variable (from the current shell or the `.env` file). Remaining, unconsumed
# arguments are exposed as the SCRIPT_ARGS array, e.g.:
#
#   parse_project_arg "$@"
#   "${COMPOSE_LITE[@]}" stop "${SCRIPT_ARGS[@]+"${SCRIPT_ARGS[@]}"}"
parse_project_arg() {
  SCRIPT_ARGS=()
  local project=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -p|--project)
        if [[ -z "${2:-}" ]]; then
          echo "ERROR: $1 requires a project name" >&2
          exit 1
        fi
        project="$2"
        shift 2
        ;;
      -p=*|--project=*)
        project="${1#*=}"
        shift
        ;;
      *)
        SCRIPT_ARGS+=("$1")
        shift
        ;;
    esac
  done

  # Fallback: COMPOSE_PROJECT_NAME from the current shell, or from `.env`.
  if [[ -z "$project" ]]; then
    project="${COMPOSE_PROJECT_NAME:-}"
  fi
  if [[ -z "$project" && -f "$DOCKER_DIR/.env" ]]; then
    project="$(grep -E '^[[:space:]]*COMPOSE_PROJECT_NAME=' "$DOCKER_DIR/.env" \
                 | tail -n1 | cut -d= -f2- | tr -d '"' | tr -d "'" \
                 | xargs || true)"
  fi

  if [[ -z "$project" ]]; then
    echo "ERROR: no docker compose project name. Pass -p NAME or set COMPOSE_PROJECT_NAME." >&2
    exit 1
  fi

  COMPOSE+=(-p "$project")
  COMPOSE_LITE+=(-p "$project")
}

