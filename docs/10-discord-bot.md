# Discord

Decided 2026-10-26: **our own bot instead of DiscordSRV.** DiscordSRV is large, does a lot we don't use, and adds its own messages and commands to the server. Our bot does exactly what MostWanted needs, in the [message style](09-brand.md#messages). It's part of the [`backend`](../backend/) app with the website's API. Built so far: `/profile`.

## The server

Built 2026-10-26 by [`backend/discord-setup/build.py`](../backend/discord-setup/), on top of an old abandoned server (wiped; its ~80 members stayed). The layout lives in that script; change it there.

```
📌 INFORMATION   👋│welcome  📜│rules  📢│announcements  🛠️│updates  🔗│link      (read-only)
💬 COMMUNITY     💬│general  🤝│crews  📸│media  💡│suggestions (forum)  🤖│bot-commands
                 🎧│Lounge  🔊│Crew 1–3
🎮 GAME          🟢│server-status  🚨│wanted                                     (bot posts)
🎫 SUPPORT       🎫│support  🐛│bug-reports (forum)  📝│apply
🛡️ STAFF         💬│staff-chat  🕵️│anticheat  🔊│Staff                        (staff only)
🔒 ADMIN         🔒│admin  🧾│bot-log                                           (admins only)
```

Roles, top to bottom: **MostWanted** (the bot, `#ff9933`), **Owner** `#ff3399`, **Admin** `#ff33ff`, **Moderator** `#cc33ff`, **Helper** `#9933ff`, **Guard** `#3399ff`, **Creator** `#ffcc33`, Booster, **Linked** (gray), **Updates** (a ping role only admins can mention). All colours are from the in-game palette; red, green, yellow and aqua are left out because they mean error, success, money and info.

Settings: Community on, Medium verification, media scanned for everyone, mentions-only notifications, 2FA for moderators. AutoMod blocks spam, more than 5 mentions (10-minute timeout), invite links to other servers (staff exempt), slurs and sexual content, with alerts in 🧾│bot-log. `@everyone` can't create invites: the website links one permanent invite.

No third-party bots: the layout is code (no backup bot), support is on the website (no ticket bot), AutoMod and Discord's raid protection cover moderation, and Onboarding covers self-assigned roles. Every extra bot would be another account with high permissions.

## The app

One Discord application, **MostWanted** (client id `1564123260562440303`), is both the bot and the website's "Sign in with Discord":

- Private (only we can add it), server install only, no install link.
- Intents: Server Members on (role sync); Presence and Message Content off (Message Content only if we add the chat bridge).
- OAuth2: scope `identify` only (no email), redirects `https://api.mostwantedmc.com/auth/discord/callback` and `http://localhost:8080/auth/discord/callback` for development.
- The bot token and the OAuth client secret go only in the backend's `.env` on the VPS. Terms of Service and Privacy Policy URLs get added once those website pages exist.

## What the bot does

Support, applications and account linking live on the **website** ([accounts](08-website.md#accounts-and-the-api)); staff handle tickets, applications and punishments in the website's staff panel. Discord is one more way in, and one more place notifications arrive.

**At launch**
- **Linking:** players link Discord and Minecraft on the website; the bot gives the **Linked** role and keeps it right (unlink, leave and rejoin).
- **Rank sync:** Guard, Helper, Moderator, Admin and Creator follow the player's LuckPerms groups, both ways, within seconds (PostgreSQL `LISTEN/NOTIFY`, no polling).
- **DMs:** ticket replies and application decisions, with a link to the page. If a player's DMs are closed, they still get it on the site and in game.
- **`/support` and `/apply`:** Discord forms that create the ticket or application on the website, for people who'd rather not open a browser.
- **`/link`, `/profile`:** a link to the site; a player's stats card.
- **Status:** one message in 🟢│server-status kept up to date, and the player count as the bot's status.
- **Alerts:** robberies, riots and a new Most Wanted in 🚨│wanted, in the message style.
- **Bot log:** every role change, link and unlink in 🧾│bot-log.
- Optional: a ping in 💬│staff-chat with an "Open on website" button when a ticket or application arrives.

**Later**
- Grim alerts in 🕵️│anticheat, with a button to the player in the staff panel.
- Chat bridge between game chat and a Discord channel (off unless we want it).
- Weekly leaderboard posts, creator video/stream alerts, event announcements.
- A button for the Updates role, if Onboarding doesn't cover it.

## How it's built

**Java 25 with [JDA](https://github.com/discord-jda/JDA)**, inside `backend/` with the API (decided 2026-10-26). It shares a `common` module with `core`: the database tables, migrations (Flyway), database access (HikariCP) and message text, so a change to a table is checked against the game, the API and the bot in one build.

```
Discord ◀──▶ backend (API + bot) ──▶ PostgreSQL ◀── core ──▶ LuckPerms
                                        ▲
                     links, ranks, tickets, notifications (LISTEN/NOTIFY)
```

It runs on the VPS as its own Raptor server with a small heap (~256 MB), so game restarts don't take it down. Each feature is its own class and can be switched off in config.
