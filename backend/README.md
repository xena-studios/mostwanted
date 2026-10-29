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
| `DELETE /v1/me` | Deletes the account, its sessions and its links, tickets, applications and notifications. Game data stays (the privacy page says to email for that). |
| `GET /v1/forms` | The support categories and each application's questions, for the site's forms. |
| `GET`, `POST /v1/me/tickets` `{"category", "subject", "body"}` | Your tickets; opens one (3 an hour at most). |
| `GET /v1/tickets/{id}` | A ticket and its messages: yours, or any if you're staff. |
| `POST /v1/tickets/{id}/messages` `{"body"}`, `POST /v1/tickets/{id}/close` | Replies (staff replies notify the player), or closes it. |
| `GET`, `POST /v1/me/applications` `{"kind", "answers"}` | Your applications; sends one (Minecraft linked, one waiting per kind, a week after a "no"). |
| `GET /v1/me/notifications`, `POST /v1/me/notifications/read` | Your notifications; marks them read. |
| `GET /v1/staff/tickets?status=`, `GET /v1/staff/applications?status=` | Staff only (helper and up, from LuckPerms): the queues. |
| `POST /v1/staff/applications/{id}` `{"decision": "accept"\|"deny", "note"}` | Moderators and up. Accepting a guard gives the guard rank in game. |

- The session is a 30-day cookie scripts can't read (`HttpOnly`, `SameSite=Lax`, `Secure`, shared by `COOKIE_DOMAIN`). Only its SHA-256 is stored.
- Anything that changes something must come from an `API_ORIGINS` page (its `Origin` header), so other sites can't act for a signed-in visitor.
- Code attempts are limited to 10 a minute per visitor.
- Linking an account that already exists on its own (signed in with just the code, or just Discord) folds it in. One that's linked to someone else can't be taken.
- When an account has both sides, the bot gives the **Linked** role; deleting the account takes it back.
- Every 10 minutes, linked players get the Discord role of their store rank (VIP, VIP+, MVP, Legend), read from LuckPerms ([docs/10-discord-bot.md](../docs/10-discord-bot.md)).
- Expired sessions and codes are deleted daily.

Errors are `{"error": "..."}` with a reason. Each visitor gets 120 requests a minute (429 after that). Browsers can only call it from `API_ORIGINS`.

**The bot:** `/profile <player>`, a stats card linking to the player's page. Commands are registered on the server the bot is in when it starts.

## Settings

Environment variables, from `.env` (copy [`.env.example`](.env.example)). Secrets (the database password, the bot's token, the OAuth client secret, the TLS key) never go in this repo.

### HTTPS and Authenticated Origin Pulls

On the VPS the API serves HTTPS itself (Jetty, no nginx), and only to Cloudflare ([docs/08-website.md](../docs/08-website.md#the-api)):

| Setting | What it is |
| --- | --- |
| `TLS_CERT` | The Cloudflare **Origin CA** certificate, PEM (Cloudflare → SSL/TLS → Origin Server). Cloudflare's SSL mode is **Full (strict)**, so it checks this. |
| `TLS_KEY` | Its private key, PEM (`BEGIN PRIVATE KEY`, as Cloudflare gives it). `chmod 600`. An old `BEGIN RSA PRIVATE KEY` file is refused with the `openssl` line that converts it. |
| `TLS_CLIENT_CA` | Cloudflare's **Authenticated Origin Pulls** CA, PEM. With it, a connection without a client certificate signed by it is refused during the TLS handshake, before any request is read: scanners and anyone going around Cloudflare get nothing. Cloudflare's certificate is the same for every customer, though, so another Cloudflare zone pointed at the VPS gets through; our own client certificate on the zone closes that ([docs/08-website.md](../docs/08-website.md#the-api)). |

Paths are relative to the folder the backend runs in (`tls/origin.pem`). `TLS_CERT` and `TLS_KEY` switch HTTPS on, on `API_PORT`, instead of HTTP: there's no plain HTTP port alongside. Both or neither; `TLS_CLIENT_CA` only with them. Without `TLS_CLIENT_CA` anyone may connect (fine for a first test, not for launch). Leave all three out locally for plain HTTP. The files are read and checked at start (the key must match the certificate), so a mistake stops the start with the reason. `OriginTlsTest` checks the handshake with throwaway certificates made by `keytool`: Cloudflare's gets through; none, or one from another CA, is refused.

Setting it up step by step, with the Cloudflare clicks: [docs/12-launch.md](../docs/12-launch.md).

## Building and running

```sh
./gradlew :backend:build     # backend/build/libs/backend.jar, everything included
```

Locally, against the dev server's database (core's README has the `docker run` line):

```sh
DB_PASSWORD=boot-test API_PORT=8081 java -jar backend/build/libs/backend.jar
curl localhost:8081/v1/leaderboards/escapes
```

Add `DISCORD_TOKEN` to run the bot too. On the VPS it's its own server in the panel: `backend.jar`, `start.sh` and `tls/` in one folder, its settings on the Startup tab, started with `./start.sh` ([docs/12-launch.md](../docs/12-launch.md)).
