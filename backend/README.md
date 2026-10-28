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

**Accounts** (no passwords: Discord, or a code from `/website` in game):

| Route | What it does |
| --- | --- |
| `POST /v1/auth/code` `{"code": "ABC234"}` | Signs in with a `/website` code (10 minutes, one use). Makes the account the first time. |
| `GET /v1/auth/discord` | Sends the browser to Discord (scope `identify` only). Comes back to `/v1/auth/discord/callback`, then to the site's `/account`. Signed in already? It links Discord instead. |
| `GET /v1/me` | The signed-in account: its Discord and Minecraft sides, and whether they're linked. 401 if not signed in. |
| `POST /v1/me/minecraft` `{"code": "..."}` | Links the Minecraft account behind a code. |
| `POST /v1/auth/logout` | Signs out this browser. |
| `DELETE /v1/me` | Deletes the account, its sessions and its links. Game data stays (the privacy page says to email for that). |

- The session is a 30-day cookie scripts can't read (`HttpOnly`, `SameSite=Lax`, `Secure`, shared by `COOKIE_DOMAIN`). Only its SHA-256 is stored.
- Anything that changes something must come from an `API_ORIGINS` page (its `Origin` header), so other sites can't act for a signed-in visitor.
- Code attempts are limited to 10 a minute per visitor.
- Linking an account that already exists on its own (signed in with just the code, or just Discord) folds it in. One that's linked to someone else can't be taken.
- When an account has both sides, the bot gives the **Linked** role; deleting the account takes it back.
- Expired sessions and codes are deleted daily.

Errors are `{"error": "..."}` with a reason. Each visitor gets 120 requests a minute (429 after that). Browsers can only call it from `API_ORIGINS`.

**The bot:** `/profile <player>`, a stats card linking to the player's page. Commands are registered on the server the bot is in when it starts.

## Settings

Environment variables, from `.env` (copy [`.env.example`](.env.example)). Secrets (the database password, the bot's token, the OAuth client secret) never go in this repo.

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
