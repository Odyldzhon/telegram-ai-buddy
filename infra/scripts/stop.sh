#!/usr/bin/env bash
# Stop containers but keep them (and the pgdata volume) around.
source "$(dirname "$0")/_common.sh"
"${COMPOSE[@]}" stop "$@"

