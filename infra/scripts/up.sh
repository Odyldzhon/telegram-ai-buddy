#!/usr/bin/env bash
# Build images (if needed) and start the stack in the background.
#
# Configuration can come from EITHER (or both):
#   • environment variables in the current shell, e.g.
#       BOT_USERNAME=LeboAiBot BOT_TOKEN=xxx ./up.sh
#   • a `docker/.env` file next to docker-compose.yml.
# Shell vars take precedence over values in `.env`.

source "$(dirname "$0")/_common.sh"

REQUIRED_VARS=(
  COMPOSE_PROJECT_NAME
  BOT_USERNAME BOT_TOKEN BOT_NAME BOT_LANGUAGE
  GEMINI_API_KEY
  BOT_AI_TRIGGER_ENABLED
  BOT_AI_TRIGGER_SEND_DAILY_JOKE_ENABLED BOT_AI_TRIGGER_SEND_NEWS_ENABLED
  BOT_AI_TRIGGER_REPLY_TO_ANY
  BOT_AI_TRIGGER_CHAT_ID
  BOT_AI_TRIGGER_HISTORY_WINDOW BOT_AI_TRIGGER_MIN_DELAY
  BOT_AI_TRIGGER_MAX_DELAY BOT_AI_TRIGGER_MAX_HISTORY_MESSAGES
  BOT_AI_TRIGGER_TIME_ZONE BOT_AI_TRIGGER_IDLE_PARTICIPATION_THRESHOLD
  BOT_ASSISTANT_RETRY_BACKOFF BOT_ASSISTANT_PERSONA_PROMPT
  DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD DB_VOLUME
  DOCKER_NETWORK
  JAVA_OPTS
)

# Build the union of keys defined in `.env` (if present) and the current shell,
# so we don't false-alarm on vars that compose will resolve from the env file.
declare -A AVAILABLE=()
if [[ -f "$DOCKER_DIR/.env" ]]; then
  while IFS= read -r line; do
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    key="${line%%=*}"
    key="${key#"${key%%[![:space:]]*}"}"   # ltrim
    key="${key%"${key##*[![:space:]]}"}"   # rtrim
    [[ -n "$key" ]] && AVAILABLE["$key"]=1
  done < "$DOCKER_DIR/.env"
fi

missing=()
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" && -z "${AVAILABLE[$var]:-}" ]]; then
    missing+=("$var")
  fi
done

if (( ${#missing[@]} > 0 )); then
  echo "ERROR: missing required variable(s):" >&2
  printf '  - %s\n' "${missing[@]}" >&2
  echo "Set them in the shell (export VAR=...) or add them to $DOCKER_DIR/.env" >&2
  exit 1
fi

"${COMPOSE[@]}" up -d --build "$@"
"${COMPOSE[@]}" ps
