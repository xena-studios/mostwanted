# Backend

The services around the game, in one Java app: the website's API (`api.mostwantedmc.com`) and the Discord bot. The plan is in [docs/08-website.md](../docs/08-website.md#accounts-and-the-api) and [docs/10-discord-bot.md](../docs/10-discord-bot.md); the stack in [docs/04-tech.md](../docs/04-tech.md#the-backend-and-common).

```
src/             the app: Javalin for the API, JDA for the bot, common for the tables
discord-setup/   builds the Discord server (roles, channels, posts, AutoMod) from a layout in code
```

## What's in it so far

**The API**, JSON only. Stats responses can be cached by Cloudflare for two minutes. Players who chose `/stats private` aren't found.

| Route | What it returns |
| --- | --- |
| `GET /health` | Whether the database and the bot are up. 503 if the database is down. |
| `GET /v1/players/{name}` | A player's role, bounty, first and last seen, and every stat all-time, this month and this week. Case doesn't matter. |
| `GET /v1/leaderboards/{board}?period=all\|month\|week&limit=1-50` | The top players on a board: `most-wanted` (bounty right now), `escapes`, `guards` (arrests), `robberies`, `bounty-earned`, `most-arrested`. |

Errors are `{"error": "..."}` with a reason. Each visitor gets 120 requests a minute (429 after that). Browsers can only call it from `API_ORIGINS`.

**The bot:** `/profile <player>`, a stats card linking to the player's page. Commands are registered on the server the bot is in when it starts.

## Settings

Environment variables, from `.env` (copy [`.env.example`](.env.example)). Secrets (the database password, the bot's token) never go in this repo.

## Building and running

```sh
./gradlew :backend:build     # backend/build/libs/backend.jar, everything included
```

Locally, against the dev server's database (core's README has the `docker run` line):

```sh
DB_PASSWORD=boot-test API_PORT=8081 java -jar backend/build/libs/backend.jar
curl localhost:8081/v1/leaderboards/escapes
```

Add `DISCORD_TOKEN` to run the bot too. On the VPS it's its own Raptor server: `backend.jar`, `start.sh` and `.env` in one folder, started with `./start.sh`.
