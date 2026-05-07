#!/usr/bin/env bash
# Tail logs of all services (or a specific one: ./logs.sh app).
# Acts on a running project by name; docker-compose.yml is never parsed.
#
#   ./logs.sh -p my_bot app
source "$(dirname "$0")/_common.sh"
parse_project_arg "$@"
"${COMPOSE_LITE[@]}" logs -f --tail=200 "${SCRIPT_ARGS[@]+"${SCRIPT_ARGS[@]}"}"

