#!/usr/bin/env bash
# Tail logs of all services (or a specific one: ./logs.sh app).
source "$(dirname "$0")/_common.sh"
"${COMPOSE[@]}" logs -f --tail=200 "$@"

