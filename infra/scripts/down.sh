#!/usr/bin/env bash
# Stop and remove containers + network. The `pgdata` volume is preserved
# so the database survives. Pass `--volumes` to also drop the DB data.
#
# Optionally target a specific docker compose project:
#   ./down.sh -p my_bot
#   ./down.sh --project my_bot --volumes
source "$(dirname "$0")/_common.sh"
parse_project_arg "$@"
"${COMPOSE[@]}" down "${SCRIPT_ARGS[@]+"${SCRIPT_ARGS[@]}"}"

