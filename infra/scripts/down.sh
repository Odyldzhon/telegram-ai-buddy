#!/usr/bin/env bash
# Stop and remove containers + network. The `pgdata` volume is preserved
# so the database survives. Pass `--volumes` to also drop the DB data.
#
# Acts on a running project by name, so the docker-compose.yml is never
# parsed and unset env vars never block the command. The project name is
# taken from `-p NAME`/`--project NAME` or from COMPOSE_PROJECT_NAME.
#
#   ./down.sh -p my_bot
#   ./down.sh --project my_bot --volumes
source "$(dirname "$0")/_common.sh"
parse_project_arg "$@"
"${COMPOSE_LITE[@]}" down "${SCRIPT_ARGS[@]+"${SCRIPT_ARGS[@]}"}"

