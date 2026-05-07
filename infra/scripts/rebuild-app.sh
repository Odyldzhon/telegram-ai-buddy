#!/usr/bin/env bash
# Rebuild the app image from the current sources and restart ONLY the app
# container. Postgres (and its data volume) are left untouched.
#
# Use this after editing Java/resource files. For dependency or Dockerfile
# changes the same script works – `--build` rebuilds the image either way.
#
# Optionally target a specific docker compose project:
#   ./rebuild-app.sh -p my_bot
#   ./rebuild-app.sh --project my_bot -- extra-compose-args
source "$(dirname "$0")/_common.sh"
parse_project_arg "$@"
"${COMPOSE[@]}" up -d --build app "${SCRIPT_ARGS[@]+"${SCRIPT_ARGS[@]}"}"
"${COMPOSE[@]}" logs -f --tail=100 app

