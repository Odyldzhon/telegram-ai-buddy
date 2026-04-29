#!/usr/bin/env bash
# Rebuild the app image from the current sources and restart ONLY the app
# container. Postgres (and its data volume) are left untouched.
#
# Use this after editing Java/resource files. For dependency or Dockerfile
# changes the same script works – `--build` rebuilds the image either way.
source "$(dirname "$0")/_common.sh"
"${COMPOSE[@]}" up -d --build app
"${COMPOSE[@]}" logs -f --tail=100 app

