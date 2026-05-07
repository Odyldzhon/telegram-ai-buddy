#!/usr/bin/env bash
# Stop containers but keep them (and the pgdata volume) around.
#
# Acts on a running project by name, so the docker-compose.yml is never
# parsed and unset env vars never block the command. The project name is
# taken from `-p NAME`/`--project NAME` or from COMPOSE_PROJECT_NAME.
#
#   ./stop.sh -p my_bot
source "$(dirname "$0")/_common.sh"
parse_project_arg "$@"
"${COMPOSE_LITE[@]}" stop "${SCRIPT_ARGS[@]+"${SCRIPT_ARGS[@]}"}"

