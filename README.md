# telegram-ai-buddy

A persona-driven Telegram group-chat bot powered by **Spring Boot**, **Spring AI**. The bot lives inside a single chat, watches the
conversation, remembers it in PostgreSQL (with `pgvector` embeddings), and
chimes in only when it makes sense — when addressed by name, replied to, or
when the room has been quiet for too long.

It can also describe images, run scheduled rituals (morning joke, news
digest), and call its own database via tool-calling to look up older context.

---

## Features

- **Persona-driven replies** — every response is shaped by a configurable
  persona prompt (`BOT_ASSISTANT_PERSONA_PROMPT`).
- **Smart triggering** — reacts when:
  - the bot's name is mentioned,
  - someone replies to one of the bot's messages,
  - or the chat has been idle past `BOT_AI_TRIGGER_IDLE_PARTICIPATION_THRESHOLD`.
- **Long-term chat memory** — every message is stored in Postgres with a
  `vector(1536)` embedding (HNSW index for cosine similarity).
- **Image understanding** — incoming photos are downloaded and described by
  Gemini before being stored as text context.
- **Tool-calling** — the assistant can run safe, read-only SQL against its own
  message store via `DatabaseTools` to fetch older / specific context.
- **Scheduled proactive activity** — daily joke, news digest, and randomised
  proactive participation (each independently toggleable).
- **Quiet hours** — proactive activity is suppressed before 08:00 in the
  configured timezone.
- **Telegram-aware** — typing indicator while the AI is thinking, automatic
  splitting of replies longer than 4096 characters, forwarded-message
  attribution.

---

## Architecture

```
┌─────────────┐   updates    ┌──────────────────┐   prompt    ┌──────────────┐
│  Telegram   │─────────────►│     TelegramBot  │────────────►│ ChatClient   │
│   group     │              │  (long-polling)  │◄────────────│ (Spring AI / │
└─────────────┘              └────────┬─────────┘   reply     │   Gemini)    │
                                      │                       └──────┬───────┘
                                      │ store                        │ tool calls
                                      ▼                              ▼
                              ┌──────────────────┐         ┌──────────────────┐
                              │   MessageStore   │◄────────│  DatabaseTools   │
                              │  (JPA + vector)  │         │  (read-only SQL) │
                              └────────┬─────────┘         └──────────────────┘
                                       │
                                       ▼
                              ┌──────────────────┐
                              │   PostgreSQL +   │
                              │     pgvector     │
                              └──────────────────┘
```

Key components (`src/main/java/com/odyldzhon/bot/`):

| Package          | Highlights                                                                                  |
|------------------|---------------------------------------------------------------------------------------------|
| `telegram/`      | `TelegramBot` (long polling), `TriggerMatcher` (mention / reply / idle), `TypingIndicator`. |
| `ai/`            | `AssistantConversation`, `ImageDescriber`, `DatabaseTools` (Spring AI tool functions).      |
| `service/`       | `ScheduledAiTriggerService` — proactive replies, daily joke, news digest, quiet hours.      |
| `persistence/`   | `ChatMessageEntity`, `ChatMessageRepository`, `MessageStore` (recent + similarity search).  |
| `properties/`    | Type-safe `@ConfigurationProperties` records (`BotProperties`, `AiTriggerProperties`, …).   |
| `configuration/` | `ChatClientConfig`, `SchedulingConfig`, `BotRegistrar` (registers the long-polling bot).    |

Database schema: `src/main/resources/db/schema.sql` — single `chat_message`
table with `vector(1536)` embeddings, an HNSW cosine-similarity index, and
indexes on `created_at` / `(author, created_at)`.

---

## Tech stack

- **Java 21**, **Spring Boot 4.0.x**
- **Spring AI** + **Google Gemini** (`spring-ai-starter-model-google-genai`,
  `…-google-genai-embedding`) — chat, embeddings, image understanding,
  Google Search retrieval, tool-calling.
- **TelegramBots 6.9.7.1** — long-polling client.
- **PostgreSQL 17** + **pgvector** (via the `pgvector/pgvector:pg17` image).
- **Hibernate 7** + `hibernate-vector` for `vector(1536)` columns.
- **Docker / Docker Compose**, helper Bash scripts.
- **JUnit 5**, **Mockito**, **Testcontainers**, **AssertJ** for tests.

---

## Quick start (Docker — recommended)

Prereqs: Docker + Docker Compose, a Telegram bot token (from
[@BotFather](https://t.me/BotFather)), and a Google Gemini API key.

```bash
git clone https://github.com/Odyldzhon/telegram-ai-buddy.git
cd telegram-ai-buddy

# 1. Configure
cp infra/docker/.env.example infra/docker/.env
# edit infra/docker/.env — at minimum:
#   BOT_USERNAME, BOT_TOKEN, BOT_NAME, BOT_LANGUAGE,
#   GEMINI_API_KEY, BOT_AI_TRIGGER_CHAT_ID, DB_PASSWORD

# 2. Build images and start the stack in the background
./infra/scripts/up.sh

# 3. Tail the app logs
./infra/scripts/logs.sh app
```

That's it. The bot will start long-polling Telegram and only react in the
chat whose id matches `BOT_AI_TRIGGER_CHAT_ID`.

> **Tip:** to discover a chat id, temporarily leave `BOT_AI_TRIGGER_CHAT_ID`
> blank and watch the logs for the `Ignoring message from unrelated chat …`
> warning when you write into the target chat.

### Helper scripts (`infra/scripts/`)

| Script              | What it does                                                                                |
|---------------------|---------------------------------------------------------------------------------------------|
| `up.sh`             | Build images and start the stack (validates required env vars first).                       |
| `stop.sh`           | Stop containers, keep volumes. Acts on a running project; YAML is **not** parsed.           |
| `down.sh`           | Remove containers + network. Pass `--volumes` to also wipe the DB.                          |
| `logs.sh [svc]`     | Tail logs (all services or a specific one, e.g. `logs.sh app`).                             |
| `rebuild-app.sh`    | Rebuild the `app` image from current sources and restart only `app`. DB is left untouched.  |

All lifecycle scripts (`stop`, `down`, `logs`) accept an optional
`-p NAME` / `--project NAME` to target a specific compose project, and
otherwise fall back to `COMPOSE_PROJECT_NAME` from your shell or `.env`:

```bash
./infra/scripts/stop.sh -p second
./infra/scripts/down.sh --project second --volumes
./infra/scripts/logs.sh -p second app
```

See [`infra/README.md`](infra/README.md) for more on operations,
host provisioning (`infra/host/host_init_config.sh`), and conventions.

---

## Running locally without Docker

Requires JDK 21, Maven, and a running PostgreSQL with the `pgvector`
extension and the schema from `src/main/resources/db/schema.sql` applied.

```bash
# Export everything from infra/docker/.env.example into your shell, then:
mvn spring-boot:run
```

---

## Configuration reference

All knobs are environment variables; `application.yml` only wires them into
type-safe records under `bot.*` and `spring.ai.*`.

### Telegram

| Variable        | Purpose                                              |
|-----------------|------------------------------------------------------|
| `BOT_USERNAME`  | The bot's `@username` (used to detect reply-to-bot). |
| `BOT_TOKEN`     | BotFather token.                                     |
| `BOT_NAME`      | Display name; used as the trigger keyword.           |
| `BOT_LANGUAGE`  | Hint for the persona's preferred language.           |

### AI trigger / proactive behaviour

| Variable                                      | Purpose                                                                            |
|-----------------------------------------------|------------------------------------------------------------------------------------|
| `BOT_AI_TRIGGER_ENABLED`                      | Master switch for the proactive scheduler (`start()`).                             |
| `BOT_AI_TRIGGER_SEND_DAILY_JOKE_ENABLED`      | Toggle the 08:00 daily joke job.                                                   |
| `BOT_AI_TRIGGER_SEND_NEWS_ENABLED`            | Toggle the 08:05 daily news digest job.                                            |
| `BOT_AI_TRIGGER_CHAT_ID`                      | The single chat id the bot is allowed to listen to and post in.                    |
| `BOT_AI_TRIGGER_HISTORY_WINDOW`               | How far back proactive replies look (e.g. `3h`).                                   |
| `BOT_AI_TRIGGER_MIN_DELAY` / `…_MAX_DELAY`    | Random delay range between proactive reactions (e.g. `2h` / `4h`).                 |
| `BOT_AI_TRIGGER_MAX_HISTORY_MESSAGES`         | Cap on messages pulled into the proactive prompt.                                  |
| `BOT_AI_TRIGGER_TIME_ZONE`                    | Timezone used for quiet-hours (`Europe/Kyiv`, …).                                  |
| `BOT_AI_TRIGGER_IDLE_PARTICIPATION_THRESHOLD` | After this much silence from the bot, plain messages also trigger a reply.         |

### Assistant

| Variable                       | Purpose                                                          |
|--------------------------------|------------------------------------------------------------------|
| `BOT_ASSISTANT_RETRY_BACKOFF`  | Backoff between retries on assistant failures (e.g. `1s`).       |
| `BOT_ASSISTANT_PERSONA_PROMPT` | The system persona injected into every AI request.               |

### Gemini

| Variable          | Purpose                                                 |
|-------------------|---------------------------------------------------------|
| `GEMINI_API_KEY`  | Used for both chat and embedding clients.               |

The chat model and options (`max-output-tokens`, Google Search retrieval,
tool invocations) are pinned in `application.yml`; the embedding model is
`gemini-embedding-2` with 1536 dimensions to match the DB column.

### Database / infra

| Variable          | Purpose                                                                |
|-------------------|------------------------------------------------------------------------|
| `DB_HOST`         | DB hostname (also used as the Postgres container name in compose).     |
| `DB_PORT`         | DB port (internal only — never published to the host).                 |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Standard Postgres credentials.                                         |
| `DB_VOLUME`       | Name of the named volume holding `pgdata`.                             |
| `DOCKER_NETWORK`  | Name of the bridge network shared by `postgres` and `app`.             |
| `COMPOSE_PROJECT_NAME` | Compose project name; lets you run multiple isolated stacks.       |
| `JAVA_OPTS`       | Extra JVM flags for the app container (defaults to `-Xms256m -Xmx512m`). |

### Output limits (besides `max-output-tokens`)

- **Telegram:** `TelegramBot.MAX_MESSAGE_SIZE = 4096` — replies longer than
  this are split into multiple in-order messages.
- **Database tools:** `MAX_ROWS = 100` and `MAX_CHARS = 10_000` cap rows and
  textual size of any tool-call result fed back to the model.

---

## Tests

```bash
mvn -DskipITs test       # fast unit tests
mvn verify               # includes Testcontainers-backed integration tests
```

Coverage spans the AI plumbing (`AssistantConversation`, `DatabaseTools`,
`ImageDescriber`), persistence (`MessageStore`, JPA entity, integration
test against a real Postgres via Testcontainers), Telegram glue
(`TelegramBot`, `TriggerMatcher`, `TypingIndicator`, message-author /
links / trigger utilities), the proactive scheduler, and Spring property
binding.

---

## Project layout

```
telegram-ai-buddy/
├── pom.xml
├── infra/
│   ├── docker/        Dockerfile, docker-compose.yml, .env.example
│   ├── scripts/       up / stop / down / logs / rebuild-app  (+ shared _common.sh)
│   ├── host/          one-off VM bootstrap (UFW, docker, …)
│   └── README.md
└── src/
    ├── main/
    │   ├── java/com/odyldzhon/bot/
    │   │   ├── ai/             AssistantConversation, ImageDescriber, DatabaseTools
    │   │   ├── configuration/  ChatClientConfig, SchedulingConfig, BotRegistrar
    │   │   ├── persistence/    ChatMessageEntity, repository, MessageStore
    │   │   ├── properties/     @ConfigurationProperties records
    │   │   ├── service/        ScheduledAiTriggerService
    │   │   ├── telegram/       TelegramBot, TriggerMatcher, TypingIndicator, util/
    │   │   └── Main.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/schema.sql
    └── test/                  JUnit 5 + Mockito + Testcontainers + AssertJ
```

---

## License

[MIT](LICENSE) © Odyldzhon.

