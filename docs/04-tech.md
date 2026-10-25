# Tech

Decided 2026-10-25. Plugin versions checked against Modrinth / PaperMC / GeyserMC the same day.

## Summary

| Layer | Choice |
| --- | --- |
| Minecraft version | **26.3** |
| Game server | **Leaf 26.3** (Paper fork, Java 25), standalone, no proxy. Paper is the fallback |
| Hosting | **OVH US VPS-3** (US East): 6 vCores, 12 GB RAM, 100 GB NVMe. Managed with [Raptor](https://github.com/xena-studios/raptor) |
| Front door | **[Minehut External Server](https://support.minehut.com/hc/en-us/articles/33252734474899-How-to-Setup-a-Minehut-External-Hosted-Server)**: players join through Minehut's network |
| Bedrock | **Minehut** handles it (their built-in Geyser) |
| Database | **PostgreSQL** (small, on the same VPS) |
| Game logic | **One custom Java plugin** (working name `core`), built like [xena-studios/plugins](https://github.com/xena-studios/plugins) |
| Custom items | **Our own**, using Paper's item model components. No ItemsAdder / Nexo |

## How players connect

```
Java players ─────┐
                  ├──▶ Minehut network ──────────────▶ OVH VPS-3 (US East, Raptor)
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

## Hosting: OVH US VPS-3

6 vCores (shared), 12 GB RAM, 100 GB NVMe, 2 Gbps unmetered, daily backups. Ordered 2026-10-25.

**Fitting it in 12 GB:**
- Leaf game server: **~8–9 GB** (`-Xms` = `-Xmx`, Paper's recommended flags)
- PostgreSQL: ~0.5–1 GB (keep `shared_buffers` small)
- OS + Raptor: ~1–1.5 GB
- **Staging runs on your own PC**, not the VPS.

**What to expect:** shared cores, so single-core speed is the limit. Rough guess: **~15–30 players**. Fine for building, testing and launch. Run spark during the first playtest.

**Upgrade path** (when we're regularly at ~20–25 players, or MSPT stays above ~40 at peak). Restore the backups on the new box and update the IP in Minehut's External settings. Players don't notice.

| Next | Server | Specs | Price |
| --- | --- | --- | --- |
| 1 | OVH VPS-4 | 8 vCores, 24 GB | ~$27.50/mo |
| 2 | OVH KS-GAME (dedicated) | i7-7700K, 32 GB | $32.10/mo + setup |
| 3 | OVH RISE-S (dedicated) | Ryzen 7 9700X, 64 GB DDR5 | $77/mo + setup |

Prices checked 2026-10-25. A DDR5/NVMe shortage has pushed many hosts' 2026 prices up a lot, so re-check before upgrading.

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
- Leaf's own README says to back up before switching. Raptor does backups, and we test every update on **staging** first.
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
- **Citizens:** for shopkeepers and NPCs we can use vanilla mannequin / display entities from our plugin. Revisit if we need walking NPCs.
- **NeoSkript / Skript:** fine for quick tests, but the core game is too big and too performance-sensitive for scripts at 100 players.
- **Velocity, Geyser, Floodgate:** Minehut does the proxy and Bedrock for us.
- **xLimbo:** needs a proxy to send players back. Not needed without one.
- **WorldEdit:** FastAsyncWorldEdit replaces it.
- **spark (plugin):** already built into Paper/Leaf.

## What we build ourselves

One plugin, `core`, in **Java 25**, built with Gradle like [xena-studios/plugins](https://github.com/xena-studios/plugins) (reuse its `build-logic` conventions and CI boot tests). Each system is a module inside it:

| # | System | What it does |
| --- | --- | --- |
| 1 | **Player state** | Inmate / criminal / guard (on/off duty), saved in PostgreSQL. Detects crossing the prison boundary (WorldGuard region). |
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
| 13 | **Guard duty** | On/off duty, guard loadout, guards-online count. The guard rank itself comes from LuckPerms through DiscordSRV. |
| 14 | **HUD** | Bounty, cash, wanted level, robbery status: boss bar / action bar, plus placeholders for TAB. |
| 15 | **Stats & leaderboards** | Escapes, arrests, cash earned, robberies. Holograms + Discord. |

Outside the plugin:

- **Resource pack pipeline:** source art + models in this repo → build the Java pack → host it on **GitHub releases** (Minehut only accepts packs from approved hosts). Bedrock pack depends on what Minehut allows, see [resource pack](06-resource-pack.md). Automate it in CI. See [resource pack](06-resource-pack.md).
- **Guard applications:** start with a Discord form/ticket bot that already exists. Build our own bot only if it gets in the way.

## Other things we need

- **Minehut External confirmed** before anything else (see the ⚠️ above).
- **Mojang EULA + Minehut rules.** Real-money store = cosmetics only, no gameplay advantages. No gambling mechanics in-game.
- **Staff tools.** Punishments (LibertyBans), anti-cheat alerts (Grim), block logs (CoreProtect), and reports. Guards are players with power over others, so staff need to be able to review guard abuse too. Minehut staff can't moderate external servers for us.
- **Backups and a recovery drill.** OVH daily backup + Raptor's backups for world files + nightly PostgreSQL dumps, and actually test restoring one.
- **Monitoring.** spark for lag spikes, Plan for player trends, alerts to Discord when the server goes down.

## Build order

1. **Foundation:** confirm Minehut External; set up the VPS with Raptor; Leaf + the must-have plugins from [server stack](07-server-stack.md) + PostgreSQL; Minehut External settings; join from Java **and** Bedrock through Minehut.
2. **Core loop:** player state, combat rules, arrests, bounty, economy, one robbery. Playable on a test map.
3. **Custom items + guns + resource pack** (Java first, then Bedrock if Minehut supports custom Bedrock packs).
4. **Escapes, riots, shops, HUD.**
5. **Discord:** linking, guard rank sync, applications.
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
