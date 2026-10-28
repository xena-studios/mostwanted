# Open questions

Decisions we need to make. When one gets answered, write the answer and move it to "Decided."

## Open

1. **Minehut External:** can we still buy the External plan (one source says it's partner-only now)? Can external servers supply a Bedrock resource pack + custom item mappings? Does Minehut publish IP ranges for the firewall? How are Bedrock players marked? (needed so GrimAC doesn't false-flag them)
2. **Indirect criminal-vs-criminal kills:** which ones? Traps, snitching, stealing, sabotage? See [combat](02-gameplay.md#combat).
3. **Guards:** how many do we need at launch?

## Decided

- **Riots:** 3 inmates press the alarm within 30 seconds, with at least 1 guard on duty; it lasts 5 minutes or until every rioter is caught; 30 minutes before the next. All in `config.yml` under `riots`. (2026-10-27)
- **Guards:** a LuckPerms guard rank (`mostwanted.guard`), applied for on the website (`/apply`); `/duty` switches on and off duty, and off duty they play as an inmate with their own money. (2026-10-27)

- **Name:** **MostWanted**, domain **mostwantedmc.com**. Repo renamed to `xena-studios/mostwanted`, plugin to MostWanted. (2026-10-25)
- **Genre:** escape game, like Roblox Jailbreak. Not classic mine-and-rank-up prison. (2026-10-25)
- **Teams:** two teams, Guards and Prisoners. Prisoners become Criminals after escaping. (2026-10-25)
- **Guards are by application:** everyone starts as a prisoner. Guard rank comes from applying (on the website, or `/apply` in Discord). (2026-10-25, updated 2026-10-26)
- **Format:** one persistent world, no rounds. (2026-10-25)
- **Vehicles:** not in the main game. Maybe later. (2026-10-25)
- **Editions:** Java and Bedrock both, with Bedrock handled by Minehut. (2026-10-25)
- **Map:** Zachary is building it. (2026-10-25)
- **Combat:** criminals can fight back. Guards vs. criminals is PvP. (2026-10-25)
- **Criminal vs. criminal:** no direct damage, only indirect ways. (2026-10-25)
- **Riots:** inmates can start them. (2026-10-25)
- **Custom items:** guns, food, tools, and more through the resource pack. (2026-10-25)
- **Version:** Minecraft 26.3. (2026-10-25)
- **Size:** up to 100 players at once. (2026-10-25)
- **Tech stack:** Leaf 26.3 standalone on a VPS managed with Raptor (a netcup RS 2000 in Manassas since 2026-10-28; OVH was out of stock), connected through Minehut External Server (Bedrock, DDoS, server list). PostgreSQL, one custom Java plugin. Full list in [tech](04-tech.md). (2026-10-25)
- **Guns:** our own hitscan guns, no gun plugin. (2026-10-25)
- **Custom items:** our own plugin + pack pipeline, no ItemsAdder/Nexo. (2026-10-25)
- **Website and store:** Astro on Cloudflare, store through Tebex Headless, cosmetics only. See [website](08-website.md). (2026-10-25)
- **Brand:** orange `#ff9933`, desert theme, blocky Minecraft-style logo. In-game colours only from the 24-hue `hsl(h, 100%, 60%)` palette. See [brand](09-brand.md). (2026-10-25)
- **Player stats on the website:** served by our API from PostgreSQL, cached at Cloudflare. Replaces the earlier Worker + D1 copy. (2026-10-26)
- **Repo layout:** `core/`, `website/`, `server/` alongside the docs. (2026-10-25) The resource pack is `pack/` (2026-10-27).
- **Server plugins (approved):** LuckPerms, ViaVersion, GrimAC, LibertyBans, xUtilities, zChat, FancyNpcs, FancyHolograms, FastAsyncWorldEdit. `core` handles zones, name tags/tab/scoreboard, game logs and stats instead of WorldGuard, TAB, PlaceholderAPI, CoreProtect and Plan. See [server stack](07-server-stack.md). (2026-10-26)
- **Discord:** our own bot, not DiscordSRV. See [Discord bot](10-discord-bot.md). (2026-10-26)
- **Messages:** coloured symbols for everyday messages, banners for big moments. See [brand](09-brand.md#messages). (2026-10-26)
- **Server setup:** pinned in `server/manifest.json`, installed by `server/install.sh`, boot-tested in CI. (2026-10-26)
- **Website accounts:** sign in with Discord or a Minecraft code, no passwords. Tickets, applications and purchases live on the website, with a staff panel; notifications on the site, Discord and in game. See [accounts and the API](08-website.md#accounts-and-the-api). (2026-10-26)
- **Backend:** one Java app (`backend/`) with the API and the Discord bot (JDA), sharing `common` with `core`; one PostgreSQL database. Served at `api.mostwantedmc.com`, proxied by Cloudflare, firewalled to Cloudflare's IPs with an Origin CA certificate and Authenticated Origin Pulls. (2026-10-26)
- **Discord server:** built from `backend/discord-setup/build.py` on a reused, wiped server; one Discord app for the bot and website sign-in; no third-party bots. See [Discord](10-discord-bot.md). (2026-10-26)
- **Backend and `common` stack:** Java 25 on virtual threads; Javalin (Jetty) for the API, JDA for the bot; PostgreSQL through HikariCP, with Flyway migrations and jOOQ queries generated from them, in `common`. No Spring or Hibernate. See [tech](04-tech.md#the-backend-and-common). (2026-10-26)
- **Memory:** 10 GB heap for the game server, leaving room for PostgreSQL, Raptor and the backend on 16 GB (8 GB of 12 on the OVH plan first chosen). Backups through Raptor's backup system. (2026-10-26)
