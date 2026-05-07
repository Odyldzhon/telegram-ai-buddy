#!/usr/bin/env bash
# Stop containers but keep them (and the pgdata volume) around.
#
# Optionally target a specific docker compose project:
#   ./stop.sh -p my_bot
source "$(dirname "$0")/_common.sh"
parse_project_arg "$@"
"${COMPOSE[@]}" stop "${SCRIPT_ARGS[@]+"${SCRIPT_ARGS[@]}"}"

