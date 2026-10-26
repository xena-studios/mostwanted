# Discord bot

Decided 2026-10-26: **our own bot instead of DiscordSRV.** DiscordSRV is large, does a lot we don't use, and adds its own messages and commands to the server. Our bot does exactly what MostWanted needs, in the [message style](09-brand.md#messages). It lives in [`bot/`](../bot/). Not built yet.

## What it does

| Feature | How |
| --- | --- |
| **Account linking** | `/link` in game shows a short code; `/link <code>` to the bot (or a button) connects the Discord and Minecraft accounts. Works for Bedrock players too. |
| **Guard role sync** | Give someone the **Guard** role in Discord and they're a guard in game within seconds; take it away and they aren't. Same for staff roles. |
| **Applications** | Guard, staff and creator forms (Discord modals), posted to a staff channel with accept/deny buttons. Accepting a guard gives the role, which syncs the rank. |
| **Tickets** | Reports, bug reports and appeals, each in a private thread with staff. |
| **Live status** | Bot status and a pinned message: online or not, player count. |
| **Alerts** | Robberies, riots and a new Most Wanted posted to an alerts channel, in the message style. |
| **Chat bridge** (optional) | Game chat to a Discord channel and back. Off unless we want it. |

## How it talks to the game server

```
Discord ◀──▶ bot ──▶ PostgreSQL ◀── core (MostWanted plugin) ──▶ LuckPerms
                         ▲
                 links, roles, alerts
```

- The bot and `core` share the server's **PostgreSQL**: no web endpoint, nothing exposed to the internet.
- **Linking:** `core` writes a one-time code for the player; the bot redeems it and stores the Discord ↔ Minecraft link.
- **Roles:** the bot writes the linked player's Discord roles; `core` notices (PostgreSQL `LISTEN/NOTIFY`, no polling) and sets the matching LuckPerms group.
- **Alerts and status:** `core` writes events; the bot posts them.

## Where it runs

On the same VPS, as its own Raptor server (Raptor runs Discord bots). It needs little memory (about 100 MB).

## Still to decide

- The language and library (we'll pick when building it; a mainstream, well-maintained Discord library either way).
- Which channels and roles the Discord server has (needs the Discord to exist).
