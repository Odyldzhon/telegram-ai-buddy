# infra/

Operations assets for the bot, organised by concern:

```
infra/
├── docker/    Dockerfile, docker-compose.yml, .env.example
├── scripts/   thin Bash wrappers around `docker compose`
└── host/      one-off provisioning scripts for the VM (UFW, updates, …)
```

## First-time setup

```bash
cp infra/docker/.env.example infra/docker/.env
# edit infra/docker/.env – fill in BOT_TOKEN, GEMINI_API_KEY, DB_PASSWORD …
```

## Helper scripts

Bash – works on Linux, macOS, and Windows via **Git Bash**. Run them from
anywhere; each script resolves its paths relative to its own location.

| Script                          | What it does                                                       |
|---------------------------------|--------------------------------------------------------------------|
| `infra/scripts/up.sh`           | Build images (if needed) and start the stack in background.        |
| `infra/scripts/stop.sh`         | Stop containers, keep them and the `pgdata` volume.                |
| `infra/scripts/down.sh`         | Remove containers + network. Add `--volumes` to wipe the DB.       |
| `infra/scripts/logs.sh [svc]`   | Tail logs (all services, or e.g. `logs.sh app`).                   |
| `infra/scripts/rebuild-app.sh`  | Rebuild the app image from current sources and restart only `app`. |

On Linux/macOS, make them executable once:

```bash
chmod +x infra/scripts/*.sh
```

(Not needed under Git Bash on Windows.)

## Host provisioning

`infra/host/host_init_config.sh` is the one-off bootstrap for a fresh VM
(updates the system, installs/configures UFW, opens only port 22). Run it
once after provisioning the server, before deploying the app.

## Notes

* Postgres is **not** published to the host – it is only reachable from the
  `app` container over the internal `botnet` network.
* DB data lives in the named `pgdata` volume; it survives `down` unless
  you pass `--volumes`.
