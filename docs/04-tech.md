# Tech

Decided 2026-10-25. Plugin versions checked against Modrinth, PaperMC and GeyserMC the same day.

## Summary

| Layer | Choice |
| --- | --- |
| Minecraft | **26.3** |
| Game server | **Leaf 26.3** (a Paper fork, Java 25), standalone, no proxy. Paper is the fallback |
| Hosting | **netcup RS 2000** (Manassas, US East), managed with [Calagopus](https://calagopus.com) |
| Front door | **[Minehut External Server](https://support.minehut.com/hc/en-us/articles/33252734474899-How-to-Setup-a-Minehut-External-Hosted-Server)**: players join through Minehut's network, which also handles Bedrock |
| Database | **PostgreSQL**, on the same VPS |
| Game logic | **One custom Java plugin**, MostWanted (`core`), built like [xena-studios/plugins](https://github.com/xena-studios/plugins) |
| Custom items | **Our own**, with Paper's item model components. No ItemsAdder or Nexo |

## How players connect

```
Java players ─────┐
                  ├──▶ Minehut network ──────────────▶ netcup RS 2000 (US East, Calagopus)
Bedrock players ──┘    (Geyser, DDoS protection,        └─ Leaf 26.3 game server
                        server list, hides our IP)       └─ PostgreSQL
```

**What Minehut External gives us:**

- **Bedrock support:** Minehut translates Bedrock players, so we run no Geyser or Floodgate.
- **DDoS protection and a hidden IP:** players connect to Minehut, not the VPS.
- **Discovery:** Minehut's server list, lobby ads and events.
- A free `<name>.minehut.gg` address, or our own domain.

**Cost:** paid in Minehut credits. A third-party wiki says **800 credits/mo (≈ $5 with a credit subscription)**; check in the dashboard. ⚠️ One source says Minehut **paused new External purchases** except for partners. Minehut's own guide doesn't say so, but **confirm "External" can be selected before building around it.**

**Setup** (standalone Leaf, from Minehut's guide):

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
4. Minehut panel → plan **External** → External Server Settings: VPS IP, proxy type **Other**, DNS record type **Port**, port **25565**, TCPShield **Not Configured**.
5. **Firewall** port 25565 to Minehut's IP ranges, if they publish them (ask in their Discord), so nobody can go around Minehut.

**Minehut's rules for external servers:**

- All Minehut community rules apply.
- **No gambling:** no crates, loot boxes or betting cash. Robbery payouts are fixed or skill-based, never a spin.
- Don't tell players to leave Minehut and join our IP directly.
- One Minehut listing per game server.

## Hosting: netcup RS 2000

8 **dedicated** AMD EPYC cores, 16 GB RAM, 256 GB NVMe, in Manassas, Virginia. Ordered 2026-10-28 on a 12-month term. OVH's VPS-3 was the first pick (2026-10-25), but OVH had nothing above VPS-1 in stock anywhere.

**Fitting it in 16 GB:**

| | Memory |
| --- | --- |
| Leaf game server | **10 GB heap** (`-Xms` = `-Xmx`, Paper's flags) + ~1–1.5 GB outside the heap: 11.5 GB in the panel |
| PostgreSQL | 1 GB, a managed database in the panel (`shared_buffers` 256 MB) |
| OS + Calagopus (panel, node agent, database agent) | ~1–1.5 GB |
| Backend (API + Discord bot) | 512 MB in the panel (256 MB heap, ~300–400 MB used) |
| Left over | ~1.5 GB of file cache |

**Staging runs on your own PC**, not the VPS.

**What to expect:** dedicated cores, so no noisy neighbours, but EPYC clocks lower than a desktop Ryzen, and Minecraft's main thread is one core. Rough guess: **~20–40 players**. Run spark during the first playtest.

**Upgrade** when we're regularly at ~20–25 players, or MSPT stays above ~40 at peak: restore the backups on the new box and update the IP in Minehut. Players don't notice.

| Next | Server | Specs | Price |
| --- | --- | --- | --- |
| 1 | netcup RS 4000 | 12 dedicated cores, 32 GB | Check netcup |
| 2 | OVH RISE-S (dedicated) | Ryzen 7 9700X, 64 GB DDR5: much faster per core | ~$77/mo + setup |

Prices checked 2026-10-25 and 2026-10-28. A DDR5/NVMe shortage has pushed 2026 prices up a lot, so re-check before upgrading.

## Server software: Leaf

| Option | Verdict |
| --- | --- |
| **Leaf** | ✅ **Pick.** A performance fork of Paper. Runs pathfinding, mob spawning and the **entity tracker** off the main thread; the tracker is what gets expensive as players grow. Includes Purpur's options, and has a 26.3 build. |
| Purpur | 🟡 Lots of gameplay config, small speedups. Leaf already includes its options. |
| Paper | 🟡 **Fallback.** The most stable, but slower at 100 players. Leaf runs Paper plugins, so switching back is a jar swap. |
| Folia | ❌ Threads by **map region**. Our players are packed into one prison and one city, so it gains little and breaks many plugins. |
| Spigot / Bukkit | ❌ Slower, and Paper is a drop-in replacement. |

- **Updating late is fine:** Leaf can trail Paper on new versions, and ViaVersion lets newer clients join meanwhile.
- **Async features can clash** with plugins that touch entities oddly. `core` sticks to the Paper API, and every async option is a config toggle.
- **Back up before switching** (Leaf's README says so). The panel backs up nightly, and updates are tested on staging first.
- **If Leaf causes problems we can't fix,** swap the jar for Paper. Nothing else changes.

**How sure are we?** Leaf is the best drop-in choice: the most-used performance fork, already on 26.3, and Paper-compatible. (DivineMC is similar but smaller and wasn't on 26.3. Canvas and ShreddedPaper split by region like Folia.) But the fork is a small lever. What decides whether 100 players run smoothly is:

1. **Our plugin:** no heavy per-tick work, hitscan guns, caching, async database calls.
2. **Entity count:** few mobs, limited dropped items, no redstone farms.
3. **View and simulation distance**, tuned for a city.
4. **The machine's single-core speed.**

So the final call uses data: run the same 100-bot load test on **Leaf and Paper**, and keep whichever has better MSPT (milliseconds per tick; under 50 means no lag).

## Plugins

The full install list, with versions and reasons, is in **[server stack](07-server-stack.md)**. What `core` does instead of WorldGuard, TAB, PlaceholderAPI, CoreProtect, Plan, Chunky, VaultUnlocked, PacketEvents and DiscordSRV is [there too](07-server-stack.md#what-core-does-instead).

### Deliberately not using

| Not using | Because |
| --- | --- |
| EssentialsX | xUtilities covers it, and its economy, kits and homes would fight our game |
| ItemsAdder, Oraxen, Nexo | We have a known set of items and need control over how they show on Bedrock |
| Gun plugins (WeaponMechanics etc.) | Guns have to feel fair on Bedrock touch controls: ours are simple hitscan |
| Citizens | FancyNpcs covers our NPCs. Revisit only for NPCs that walk or fight |
| DecentHolograms | FancyHolograms instead |
| NeoSkript / Skript | Fine for quick tests, but the game is too big and too performance-sensitive for scripts |
| Velocity, Geyser, Floodgate | Minehut does the proxy and Bedrock |
| xLimbo | Needs a proxy to send players back |
| WorldEdit | FastAsyncWorldEdit replaces it |
| spark (plugin) | Built into Paper and Leaf |

## What we build ourselves

One plugin, **MostWanted** (`core`), in **Java 25**, built with Gradle like [xena-studios/plugins](https://github.com/xena-studios/plugins) (its `build-logic` conventions and CI boot tests). Each system is a package inside it:

| # | System | What it does |
| --- | --- | --- |
| 1 | **Player state** | Inmate, criminal or guard (on or off duty), saved in PostgreSQL |
| 1b | **Zones** | Prison boundary, safe zones, robbery spots and hideouts, marked with a FAWE selection. A chunk-indexed lookup tells the other systems when a player crosses one |
| 2 | **Combat rules** | Who can hurt who ([gameplay](02-gameplay.md#combat)), safe zones, death = arrest |
| 3 | **Arrests** | Handcuffs, taser, jail time from bounty, losing unbanked cash |
| 4 | **Bounty** | Rises from robberies and killing guards; paid to the arresting guard |
| 5 | **Economy** | Cash (unbanked vs. banked at a criminal base), guard salary |
| 6 | **Robberies** | Locations, open/close timers, announcements, alarms, payouts scaled by guards online |
| 7 | **Escapes** | Breakable walls that grow back, keycard doors, vents and sewers |
| 8 | **Riots** | Start conditions, prison PvP during a riot, alerts, end conditions, cooldown |
| 9 | **Guns** | Hitscan, ammo, reload, damage, cooldowns. Same feel on Java and Bedrock |
| 10 | **Custom items** | Every custom item (guns, food, tools, contraband), each a vanilla item underneath |
| 11 | **Shops** | Criminal base shop and guard armory, as chest GUIs |
| 12 | **Indirect criminal kills** | Traps, snitching, stealing, sabotage (once we pick which) |
| 13 | **Guard duty** | On/off duty, loadout, guards-online count. The rank itself is a LuckPerms group, synced with Discord ([Discord bot](10-discord-bot.md)) |
| 14 | **HUD** | Bounty, cash, wanted level and robbery status on the boss bar, action bar and sidebar. Name tags and tab list in role colours, updated on change ([layout](09-brand.md#scoreboard-and-tab-list)) |
| 14b | **Messages** | Every message in the [message style](09-brand.md#messages) |
| 14c | **Game log** | Arrests, robberies, riots, purchases and staff actions, in PostgreSQL |
| 14d | **Bedrock support** | Detects Bedrock players (once Minehut says how they're marked) and exempts them from GrimAC |
| 15 | **Stats and leaderboards** | Escapes, arrests, cash earned, robberies, per period. Boards in game; the website reads them through the API ([player stats](08-website.md#player-stats)) |

**Outside the plugin:**

- **Resource pack:** source art in this repo → the Java pack, built and published to **GitHub releases** by CI (Minehut only accepts packs from approved hosts). A Bedrock pack depends on Minehut ([resource pack](06-resource-pack.md)).
- **[`backend/`](../backend/):** one Java app with the website's API (`api.mostwantedmc.com`: accounts, linking, tickets, applications, the staff panel, stats) and the Discord bot ([API](08-website.md#accounts-and-the-api), [Discord](10-discord-bot.md)).
- **[`common/`](../common/):** the tables, migrations, connection pool and message style, shared by `core` and `backend`.

## The backend and `common`

Decided 2026-10-26. **Java, like `core`**, so the game, API and bot share the tables through `common`: change a column and the build fails everywhere it's used, not the live server. That's worth more than the memory a lighter language (Go) would save.

| Part | Pick | Why |
| --- | --- | --- |
| Runtime | **Java 25, virtual threads** | A thread per request costs nearly nothing, so the code stays plain |
| API | **[Javalin](https://javalin.io)** on Jetty | Small and explicit, quick to start, light on memory. Jetty serves HTTPS and checks Cloudflare's client certificate itself, so no nginx |
| Discord | **[JDA](https://github.com/discord-jda/JDA)** | The main Java Discord library |
| Database | **PostgreSQL, HikariCP, [Flyway](https://flywaydb.org), [jOOQ](https://www.jooq.org)** | Flyway migrates on start; jOOQ's classes are generated from the migrations, so queries are compiler-checked. LISTEN/NOTIFY tells `core` and `backend` when something changed |
| JSON | **Jackson** | Javalin's default |
| Sign-in | **The JDK's HTTP client** for Discord OAuth; our own sessions | Two calls to Discord. Random tokens, stored hashed, sent as `HttpOnly` cookies. No auth framework |
| Rate limits | **Bucket4j**, in memory | Per IP and per account, behind Cloudflare's |
| Logs | **SLF4J + Logback** | The panel shows the console; Logback keeps daily files |
| Tests | **JUnit + [Testcontainers](https://testcontainers.com)** | Database tests against a real PostgreSQL in CI |

- **Running it:** one jar as its own server in the panel, 256 MB heap with the Serial GC, ~300–400 MB in all. `/health` lets the bot alert 🔒│admin when the API is down.
- **`core` stays small:** it bundles `common` (our code only), and Paper's library loader downloads jOOQ, Flyway, HikariCP and the PostgreSQL driver on first start.
- **Not used:** Spring Boot (heavy and slow to start), Quarkus and Micronaut (more framework than we need), Hibernate (hides its SQL; jOOQ keeps it visible).

## How `core` stays fast

`core` runs everything for up to 100 players on one main thread, so speed is a rule. These are checked in review ([CONTRIBUTING.md](../CONTRIBUTING.md#rules-every-change-keeps)) and measured before launch.

**Do nothing until something happens**
- Event-driven: no per-tick loop over every player. The few timers (robberies, riots, jail time, walls and gates) run once a second and are cheap when idle. The scoreboard runs twice a second and only redraws what changed.
- Movement is the hottest event. Its handler returns unless the player changed block, and zone checks run only on a chunk change or zone edge.

**Keep the hot path in memory**
- Each online player's state (role, cash, bounty, zone) is loaded once on join. Gameplay never waits on the database.
- Zones are indexed by chunk, so a lookup only checks the few zones touching that chunk.
- Config, messages and items are parsed once at startup (changes need a restart).

**Database off the main thread, in batches**
- A small HikariCP pool; every query runs async.
- Changes collect in memory and save every few seconds, and on quit and shutdown. A crash loses seconds, not sessions.

**Cheap updates**
- Name tags, tab list and scoreboard update only what changed, for those who can see it.
- Guns are one ray trace per shot, not projectile entities.
- Holograms and NPCs are packet-based: no real entities to tick.

**Robust**
- Paper API only: no NMS or reflection, so it survives Leaf and Paper updates.
- Each system is its own package, wired together in `MostWanted.java`. The parts that talk to the outside (store delivery, the resource pack) can be switched off in config.
- Players are stored by UUID, Bedrock players included.

**Measured**
- Unit tests for the rules (who can hurt who, bounty maths, zone lookups); boot checks on Leaf and Paper in CI.
- spark on a full playtest, and a 100-bot load test before launch. Target: a steady 20 TPS with MSPT under 30.
- A jar size budget in CI, so a library shaded in by mistake fails the build.

## Before launch

The step-by-step is in [launch](12-launch.md).

- **Confirm Minehut External** before anything else (see the ⚠️ above).
- **Mojang's EULA and Minehut's rules:** the store is cosmetics only, and nothing in game is gambling.
- **Staff tools:** punishments (LibertyBans), anti-cheat alerts (Grim), game logs (core) and reports. Guards have power over other players, so staff must be able to review guard abuse too. Minehut staff can't moderate external servers for us.
- **Backups and a restore drill:** the panel's nightly world backups and PostgreSQL dumps, in Cloudflare R2, plus netcup snapshots. Actually test a restore.
- **Monitoring:** spark for lag spikes; the bot alerts 🔒│admin when the game, API or database goes down or lags.
- **Hardening:** SSH keys only, automatic security updates, the database and panel internals listening only inside the machine, secrets only in the panel, netcup's firewall blocking everything not listed, and the API refusing anything without Cloudflare's client certificate.
- **2FA** on Cloudflare, GitHub, netcup, Discord, Tebex and Minehut.
- `support@mostwantedmc.com` through Cloudflare Email Routing.
- A 100-bot stress test (the VPS estimate is ~20–40 players; the server has 100 slots).
- ✅ Terms and Privacy pages and a "delete my account" button (done 2026-10-27). Collect as little as possible: many players are kids.

## Build order

Steps 2–6 are built (2026-10-28). Next is the map, then steps 1 and 7 at [launch](12-launch.md).

1. **Foundation:** confirm Minehut External; set up the VPS with Calagopus, Leaf, the [server stack](07-server-stack.md) and PostgreSQL; join from Java **and** Bedrock through Minehut.
2. **Core loop:** player state, combat rules, arrests, bounty, economy, one robbery, on a test map.
3. **Custom items, guns and the resource pack** (Java first, Bedrock if Minehut allows).
4. **Escapes, riots, shops, HUD.**
5. **Website accounts and backend:** sign-in, linking, guard rank sync, tickets, applications, notifications.
6. **Polish:** stats, leaderboards, indirect kills, more robberies.
7. **Playtest at the VPS's limit**, fix what spark shows, launch, then upgrade as players grow.

## Java + Bedrock

Both editions can join, with Minehut translating Bedrock. Keep in mind:

- **No Floodgate**, so no Bedrock forms: **chest GUIs for everyone**, simple and touch-friendly.
- **Detecting Bedrock players:** find out how Minehut marks them (name prefix? UUID format?). Needed for gun balance, GrimAC and tests.
- **Resource packs:** Java packs don't work on Bedrock. **Ask Minehut whether external servers can supply a Bedrock pack and Geyser item mappings.** If not, Bedrock players see the vanilla base items ([resource pack](06-resource-pack.md)).
- **Combat feels different** on each (attack cooldown, reach, touch controls). Test Java-vs-Bedrock fights early.
- **Store players by UUID, never by name.**
- **Test on both**, phones and consoles included.
