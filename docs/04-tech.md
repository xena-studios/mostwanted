# Tech

Decided 2026-10-25. Plugin versions checked against Modrinth / PaperMC / GeyserMC the same day.

## Summary

| Layer | Choice |
| --- | --- |
| Minecraft version | **26.3** |
| Game server | **Leaf 26.3** (Paper fork, Java 25), standalone, no proxy. Paper is the fallback |
| Hosting | **netcup RS 2000** (Manassas, US East): 8 dedicated cores, 16 GB RAM, 256 GB NVMe. Managed with [Calagopus](https://calagopus.com) |
| Front door | **[Minehut External Server](https://support.minehut.com/hc/en-us/articles/33252734474899-How-to-Setup-a-Minehut-External-Hosted-Server)**: players join through Minehut's network |
| Bedrock | **Minehut** handles it (their built-in Geyser) |
| Database | **PostgreSQL** (small, on the same VPS) |
| Game logic | **One custom Java plugin**, MostWanted (the `core` module), built like [xena-studios/plugins](https://github.com/xena-studios/plugins) |
| Custom items | **Our own**, using Paper's item model components. No ItemsAdder / Nexo |

## How players connect

```
Java players ─────┐
                  ├──▶ Minehut network ──────────────▶ netcup RS 2000 (US East, Calagopus)
Bedrock players ──┘    (Geyser, DDoS protection,        └─ Leaf 26.3 game server
                        server list, hides our IP)       └─ PostgreSQL
```

**Minehut External Server gives us:**
- **Bedrock support.** Minehut translates Bedrock players for us, so no Geyser/Floodgate to run.
- **DDoS protection** and a **hidden server IP.** Players connect to Minehut, not to our VPS.
- **Minehut's server list, lobby ads and events.** This solves "how do people find us".
- A free `<name>.minehut.gg` address, or our own domain.

**Cost:** the External plan is paid in Minehut credits. A third-party wiki says **800 credits/mo (≈ $5 with a credit subscription)**. Check in the dashboard. ⚠️ One source says Minehut **paused new External plan purchases** except for partners. Minehut's own guide doesn't mention that, but **confirm we can actually select "External" before building around it.**

**Setup (standalone Leaf, from Minehut's guide):**
1. `config/paper-global.yml` → `proxies` → `proxy-protocol: true`
2. `server.properties` → `enforce-secure-profile=false`
3. Start flags:
   ```
   -Dminecraft.api.services.host=https://api.minecraftservices.com/
   -Dminecraft.api.auth.host=https://authserver.mojang.com/
   -Dminecraft.api.account.host=https://api.mojang.com/
   -Dminecraft.api.profiles.host=https://api.mojang.com/
   -Dminecraft.api.session.host=https://api.minehut.com/mitm/proxy
   ```
4. Minehut panel → server plan **External** → External Server Settings: VPS IP, proxy type **Other**, DNS record type **Port**, port **25565**, TCPShield **Not Configured**.
5. **Firewall** the VPS so port 25565 only accepts connections from Minehut, if Minehut publishes its IP ranges (ask in their Discord). Then nobody can attack or bypass Minehut by hitting the VPS directly.

**Minehut rules we must follow (external servers):**
- All Minehut community rules apply.
- **No server-made gambling mechanics.** No crates/loot boxes or betting cash. Robbery payouts must be fixed or skill-based, not a spin.
- Don't tell players to leave Minehut and join our IP directly.
- One Minehut listing per game server.

## Hosting: netcup RS 2000

8 **dedicated** AMD EPYC cores, 16 GB RAM, 256 GB NVMe, in Manassas, Virginia (US East). Ordered 2026-10-28, on a 12-month term. OVH's VPS-3 was the first pick (2026-10-25), but OVH had nothing above VPS-1 in stock in any region.

**Fitting it in 16 GB:**
- Leaf game server: **10 GB heap** (`-Xms` = `-Xmx`, Paper's recommended flags), plus ~1–1.5 GB Java uses outside the heap: 11.5 GB in the panel
- PostgreSQL: 1 GB, a managed database in the panel (`shared_buffers` 256 MB)
- OS + Calagopus (panel, node agent, database agent): ~1–1.5 GB
- backend (website API + Discord bot): 512 MB in the panel (256 MB heap, ~300–400 MB used)
- What's left (~1.5 GB) is the system's file cache. **Staging runs on your own PC**, not the VPS.

**What to expect:** dedicated cores, so no neighbour slows us down, but EPYC cores clock lower than a desktop Ryzen, and Minecraft's main thread is one core. Rough guess: **~20–40 players**. Run spark during the first playtest.

**Upgrade path** (when we're regularly at ~20–25 players, or MSPT stays above ~40 at peak). Restore the backups on the new box and update the IP in Minehut's External settings. Players don't notice.

| Next | Server | Specs | Price |
| --- | --- | --- | --- |
| 1 | netcup RS 4000 | 12 dedicated cores, 32 GB | check netcup |
| 2 | OVH RISE-S (dedicated) | Ryzen 7 9700X, 64 GB DDR5: much faster per core | ~$77/mo + setup |

Prices checked 2026-10-25 and 2026-10-28. A DDR5/NVMe shortage has pushed many hosts' 2026 prices up a lot, so re-check before upgrading.

## Server software: Leaf

| Option | Verdict |
| --- | --- |
| **Leaf** | ✅ **Pick.** Paper fork built for performance. Runs pathfinding, mob spawning and the **entity tracker** off the main thread. The entity tracker gets expensive fast as player count grows, so this is exactly the work that hurts at 100 players. It also includes Purpur's config options. Has a 26.3 build. |
| Purpur | 🟡 Good, lots of gameplay config, but its speedups are small. Leaf already includes Purpur's options. |
| Paper | 🟡 **Fallback.** The most stable, but leaves performance on the table at 100 players. Leaf runs Paper plugins, so switching back is a jar swap. |
| Folia | ❌ Multi-threads by **map region**. Our players are packed into one prison and one city, which is mostly the same region, so Folia gains little and breaks many plugins (WorldGuard, for one). |
| Spigot / Bukkit | ❌ Slower, and Paper-based servers are drop-in replacements. |

**Updating late is fine.** Leaf/Purpur can trail Paper on new Minecraft versions, but **ViaVersion** lets newer clients join until the server catches up. 

**Risks and how we handle them:**
- Async features can clash with plugins that touch entities in unusual ways. Our `core` plugin sticks to the normal Paper API. Every async option is a config toggle, so we turn off whatever causes trouble.
- Leaf's own README says to back up before switching. The panel backs up nightly, and we test every update on **staging** first.
- If Leaf ever causes problems we can't fix, swap the jar for Paper. Nothing else changes.

**How sure are we?** Leaf is the best **drop-in** choice right now: the most-used performance fork, already on 26.3, and it runs Paper plugins. (DivineMC is similar but smaller and wasn't on 26.3 yet. Multithreaded forks like Canvas and ShreddedPaper split by region like Folia, so they have the same problem with a crowded city.) But the fork is a **small lever**. What decides whether 100 players run smoothly is mostly:

1. **Our own plugin.** No heavy work every tick, hitscan guns, caching, database calls off the main thread.
2. **Entity count.** Few mobs, limited dropped items, no redstone farms.
3. **View and simulation distance** tuned for a city.
4. **The machine's single-core speed.**

So the final call is made with data: in the load test, run the same 100-bot test on **Leaf and Paper** and keep whichever has better MSPT (milliseconds per tick; under 50 = no lag).

## Plugins we use

The full install list, with versions, links, start command and config checklist, is in **[server stack](07-server-stack.md)**.

### Deliberately not using

- **EssentialsX:** xUtilities covers it, and Essentials' economy/kits/homes would fight our game.
- **ItemsAdder / Oraxen / Nexo:** we only need a known set of items, and we need control over how they show on Bedrock. Paper's item model components + our own pack build are enough.
- **Gun plugins (WeaponMechanics etc.):** we need guns that feel fair on Bedrock touch controls. Ours will be simple hitscan.
- **Citizens:** FancyNpcs covers our NPCs (shopkeepers, armory, info). Revisit only if we need NPCs that walk around or fight.
- **DecentHolograms:** FancyHolograms instead.
- **NeoSkript / Skript:** fine for quick tests, but the core game is too big and too performance-sensitive for scripts at 100 players.
- **Velocity, Geyser, Floodgate:** Minehut does the proxy and Bedrock for us.
- **xLimbo:** needs a proxy to send players back. Not needed without one.
- **WorldEdit:** FastAsyncWorldEdit replaces it.
- **spark (plugin):** already built into Paper/Leaf.
- **WorldGuard, TAB, PlaceholderAPI, CoreProtect, Plan, Chunky, VaultUnlocked, PacketEvents, DiscordSRV:** `core` or our bot covers each, or it isn't needed. Why, one by one: [server stack](07-server-stack.md#what-core-does-instead).

## What we build ourselves

One plugin, **MostWanted** (the `core` module), in **Java 25**, built with Gradle like [xena-studios/plugins](https://github.com/xena-studios/plugins) (reuse its `build-logic` conventions and CI boot tests). Each system is a module inside it:

| # | System | What it does |
| --- | --- | --- |
| 1 | **Player state** | Inmate / criminal / guard (on/off duty), saved in PostgreSQL. |
| 1b | **Zones** | The prison boundary, safe zones, robbery spots and hideouts, marked with a FAWE selection and stored in PostgreSQL. A chunk-indexed lookup tells the other systems when a player crosses one (instead of WorldGuard). |
| 2 | **Combat rules** | Who can damage who (the table in [gameplay](02-gameplay.md#combat)), safe zones, death = arrest. |
| 3 | **Arrests** | Handcuffs, taser, jail time based on bounty, losing unbanked cash. |
| 4 | **Bounty** | Goes up from robberies and killing guards. Paid to the guard who makes the arrest. |
| 5 | **Economy** | Cash (unbanked vs. banked at a criminal base), guard salary, all stored in PostgreSQL. |
| 6 | **Robberies** | Locations, open/close timers, announcements, alarms, payouts scaled by guards online. |
| 7 | **Escapes** | Breakable walls that regenerate, keycard doors, vents/sewers. |
| 8 | **Riots** | Start conditions, prison PvP on during a riot, alerts, end conditions, cooldown. |
| 9 | **Guns** | Hitscan guns, ammo, reload, damage, cooldowns. Same feel on Java and Bedrock. |
| 10 | **Custom items** | Registry of every custom item (guns, food, tools, contraband) using item model components. Each is a vanilla item underneath. |
| 11 | **Shops** | Criminal base shop, guard armory. Chest GUIs for everyone (no Floodgate forms, see [Java + Bedrock](#java--bedrock)). |
| 12 | **Indirect criminal kills** | Traps, snitching, stealing, sabotage (once we pick which). |
| 13 | **Guard duty** | On/off duty, guard loadout, guards-online count. The guard rank itself comes from LuckPerms, synced from Discord by our bot ([Discord bot](10-discord-bot.md)). |
| 14 | **HUD** | Bounty, cash, wanted level, robbery status: boss bar, action bar and sidebar scoreboard. Name tags and the tab list in each role's colour (guards blue, wanted criminals red), updated when the state changes (instead of TAB). Layout: [scoreboard and tab list](09-brand.md#scoreboard-and-tab-list). |
| 14b | **Messages** | One place that sends every message in the [message style](09-brand.md#messages): ✔ ✖ ★ ⚑ ⓘ one-liners, and banners with a title and sound for big moments. |
| 14c | **Game log** | Arrests, robberies, riots, purchases and staff actions, written to PostgreSQL for staff and the website (instead of CoreProtect). |
| 14d | **Bedrock support** | Detects Bedrock players (once Minehut tells us how they're marked) and exempts them from GrimAC's checks. |
| 15 | **Stats & leaderboards** | Escapes, arrests, cash earned, robberies, per period. Holograms in game; the website reads them from PostgreSQL through the API (see [player stats](08-website.md#player-stats)). |

Outside the plugin:

- **Resource pack pipeline:** source art + models in this repo → build the Java pack → host it on **GitHub releases** (Minehut only accepts packs from approved hosts). Bedrock pack depends on what Minehut allows, see [resource pack](06-resource-pack.md). Automate it in CI. See [resource pack](06-resource-pack.md).
- **The backend** in [`backend/`](../backend/): one Java app with the website's API (`api.mostwantedmc.com`: accounts, linking, tickets, applications, the staff panel, stats) and the Discord bot (rank sync, DMs, alerts). See [accounts and the API](08-website.md#accounts-and-the-api) and [Discord](10-discord-bot.md).
- **`common`:** the database tables, migrations, connection pool and message style shared by `core` and `backend` ([below](#the-backend-and-common)).

## The backend and `common`

Decided 2026-10-26. **Java, like `core`**, so the game, the API and the bot share one module, [`common`](../common/), for the tables. Change a column and the build fails everywhere it's used, instead of the live server. That's worth more than the memory a lighter language (Go) would save.

| Part | Pick | Why |
| --- | --- | --- |
| Runtime | **Java 25, virtual threads** | A thread per request costs nearly nothing, so the code stays plain. |
| API | **[Javalin](https://javalin.io)** on Jetty | Small and explicit (a route is a function), quick to start, light on memory. Jetty serves HTTPS with the Origin CA certificate and checks Cloudflare's client certificate itself, so no nginx. |
| Discord | **[JDA](https://github.com/discord-jda/JDA)** | The main Java Discord library. |
| Database | **PostgreSQL, HikariCP, [Flyway](https://flywaydb.org), [jOOQ](https://www.jooq.org)** (in `common`) | Flyway brings the tables up to date on start; jOOQ's classes are generated from the migrations, so queries are checked by the compiler. LISTEN/NOTIFY tells `core` and `backend` when something changed. |
| JSON | **Jackson** | Javalin's default. |
| Sign-in | **The JDK's HTTP client** for Discord OAuth; our own sessions | Two calls to Discord. Session tokens are random, stored hashed, sent as `HttpOnly` cookies. No auth framework to trust. |
| Rate limits | **Bucket4j**, in memory | Per IP and per account, behind Cloudflare's. |
| Logs | **SLF4J + Logback** | The panel shows the console; Logback keeps daily files. |
| Tests | **JUnit + [Testcontainers](https://testcontainers.com)** | Database tests run against a real PostgreSQL in CI. |

**Running it:** one jar as its own server in the panel, 256 MB heap with the Serial GC (the lightest for a small heap), ~300–400 MB in all. `/health` lets the bot alert 🔒│admin when the API is down.

**`core` stays small:** it bundles `common` (our code only) and has Paper download jOOQ, Flyway, HikariCP and the PostgreSQL driver on first start (Paper's library loader), rather than shading them into the plugin jar.

**Not used:** Spring Boot (heavy and slow to start for a few dozen routes), Quarkus and Micronaut (more framework than we need), Hibernate (hides the SQL it runs; jOOQ keeps it visible).

## How `core` stays fast

`core` runs everything for up to 100 players on the main thread, one CPU core, so speed is a rule, not a hope. These are checked in review ([CONTRIBUTING.md](../CONTRIBUTING.md#what-a-change-has-to-keep-true)) and measured before launch.

**Do nothing until something happens**
- Event-driven: no loop that runs every tick over every player. The few timers (robberies, riots, jail time, walls and gates) run once a second, not every tick, and are cheap when nothing is going on; the scoreboard runs twice a second and only redraws what changed.
- Movement is the hottest event. Its handler returns immediately unless the player changed block, and zone checks only run when they changed chunk or crossed a zone edge.

**Keep the hot path in memory**
- Each online player's state (role, cash, bounty, zone) lives in memory, loaded once on join. Gameplay never waits on the database.
- Zones are indexed by chunk: finding "which zone am I in" looks at the few zones touching that chunk, not all of them.
- Config, messages and item definitions are parsed once at startup, never per event (changing them takes a restart).

**Database off the main thread, in batches**
- PostgreSQL through a small HikariCP pool (a handful of connections). Every query runs async.
- Changes are written behind: stats and cash changes collect in memory and save in batches every few seconds, and on quit and shutdown. Nothing is lost on a clean stop; a crash loses seconds, not sessions.

**Cheap updates for 100 players**
- Name tags, tab list and scoreboard update only what changed, for the players who can see it. Nothing redraws every second.
- Guns are hitscan (one ray trace per shot through the Paper API), not projectile entities.
- Holograms and NPCs are packet-based (FancyHolograms, FancyNpcs): no real entities to tick.

**Robust**
- Paper API only: no NMS or reflection into server internals, so it keeps running on Leaf and through Paper updates.
- Each system is its own package, wired together in `MostWanted.java`; the parts that talk to the outside (store delivery, the resource pack) can be switched off in config.
- Players are stored by UUID. Bedrock players (through Minehut) included.

**Measured**
- Unit tests for the rules (who can hurt who, bounty maths, zone lookups); boot checks on Leaf and Paper in CI.
- spark profiles on a full playtest, and a 100-bot load test before launch: the target is a steady 20 TPS with MSPT under 30.
- A jar size budget in CI, like the plugins repo, so a library shaded in by mistake fails the build.

## Other things we need

- **Minehut External confirmed** before anything else (see the ⚠️ above).
- **Mojang EULA + Minehut rules.** Real-money store = cosmetics only, no gameplay advantages. No gambling mechanics in-game.
- **Staff tools.** Punishments (LibertyBans), anti-cheat alerts (Grim), game logs (core's own), and reports. Guards are players with power over others, so staff need to be able to review guard abuse too. Minehut staff can't moderate external servers for us.
- **Backups and a recovery drill.** The panel's nightly backups: the world files (saved first) and a dump of the managed PostgreSQL, in Cloudflare R2, plus netcup snapshots. Actually test restoring one.
- **Monitoring.** spark for lag spikes; the bot alerts 🔒│admin when the game, API or database goes down or the server lags.
- **Before launch** (the step-by-step is [launch](12-launch.md)):
  - ~~Terms and Privacy pages, and a "delete my account" button~~ (done 2026-10-27; collect as little as possible: many players are kids).
  - VPS hardening: SSH keys only, automatic security updates, the database and the panel's internals listening only inside the machine, secrets only in the panel, netcup's firewall blocking everything not listed, and the API refusing anything without Cloudflare's client certificate ([12-launch.md](12-launch.md)).
  - 2FA on Cloudflare, GitHub, netcup, Discord, Tebex and Minehut.
  - `support@mostwantedmc.com` through Cloudflare Email Routing.
  - A 100-bot stress test (the VPS estimate is ~20–40 players; the server has 100 slots).

## Build order

Steps 2 to 6 are built (2026-10-28); next is the map, then step 1 and step 7 for launch ([12-launch.md](12-launch.md)).

1. **Foundation:** confirm Minehut External; set up the VPS with Calagopus; Leaf + the must-have plugins from [server stack](07-server-stack.md) + PostgreSQL; Minehut External settings; join from Java **and** Bedrock through Minehut.
2. **Core loop:** player state, combat rules, arrests, bounty, economy, one robbery. Playable on a test map.
3. **Custom items + guns + resource pack** (Java first, then Bedrock if Minehut supports custom Bedrock packs).
4. **Escapes, riots, shops, HUD.**
5. **Website accounts and backend:** sign-in, linking, guard rank sync, tickets, applications, notifications. (The Discord server itself is built.)
6. **Polish:** stats, leaderboards, indirect kills, more robberies.
7. **Playtest at the VPS's limit**, fix what spark shows, launch, then upgrade the box as players grow.

## Java + Bedrock

Both editions can join (decided). Minehut translates Bedrock players. Things to keep in mind:

- **No Floodgate on our server**, so no native Bedrock forms. Use **chest GUIs for everyone**, kept simple and big-button friendly for touch.
- **Detecting Bedrock players:** find out how Minehut marks them (name prefix? UUID format?). We need this for gun balance and tests.
- **Custom items / resource packs:** Java packs don't work on Bedrock. **Ask Minehut whether external servers can supply a Bedrock pack + Geyser custom item mappings.** If not, Bedrock players see the vanilla base item (that's why every custom item is a renamed vanilla item). See [resource pack](06-resource-pack.md).
- **Combat:** Java and Bedrock PvP feel different (attack cooldown, reach, touch controls). Test fights between a Java player and a Bedrock player early.
- **Store players by UUID, never by name.**
- **Test everything on both**, phone and console included, not just PC.
