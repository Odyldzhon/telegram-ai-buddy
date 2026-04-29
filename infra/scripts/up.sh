#!/usr/bin/env bash
# Build images (if needed) and start the stack in the background.
source "$(dirname "$0")/_common.sh"
"${COMPOSE[@]}" up -d --build "$@"
"${COMPOSE[@]}" ps

