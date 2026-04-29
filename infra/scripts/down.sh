#!/usr/bin/env bash
# Stop and remove containers + network. The `pgdata` volume is preserved
# so the database survives. Pass `--volumes` to also drop the DB data.
source "$(dirname "$0")/_common.sh"
"${COMPOSE[@]}" down "$@"

