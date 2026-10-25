# Tech

Decided 2026-10-25. Plugin versions checked against Modrinth / PaperMC / GeyserMC the same day.

## Summary

| Layer | Choice |
| --- | --- |
| Minecraft version | **26.3** |
| Game server | **Leaf 26.3** (Paper fork, Java 25). Paper is the fallback |
| Proxy | **Velocity** |
| Bedrock | **Geyser + Floodgate** on Velocity |
| Hosting | **Our own dedicated server, managed with [Raptor](https://github.com/xena-studios/raptor)** |
| Database | **PostgreSQL** (player data, cash, stats, LuckPerms) |
| Game logic | **One custom Java plugin** (working name `core`), built like [xena-studios/plugins](https://github.com/xena-studios/plugins) |
| Custom items | **Our own**, using Paper's item model components. No ItemsAdder / Nexo |

## Server software: Leaf

| Option | Verdict |
| --- | --- |
| **Leaf** | ✅ **Pick.** Paper fork built for performance. Runs pathfinding, mob spawning and the **entity tracker** off the main thread. The entity tracker gets expensive fast as player count grows, so this is exactly the work that hurts at 100 players. It also includes Purpur's config options. Has a 26.3 build. |
| Purpur | 🟡 Good, lots of gameplay config, but its speedups are small. Leaf already includes Purpur's options. |
| Paper | 🟡 **Fallback.** The most stable, but leaves performance on the table at 100 players. Leaf runs Paper plugins, so switching back is a jar swap. |
| Folia | ❌ Multi-threads by **map region**. Our players are packed into one prison and one city, which is mostly the same region, so Folia gains little and breaks many plugins (WorldGuard, for one). |
| Spigot / Bukkit | ❌ Slower, and Paper-based servers are drop-in replacements. |

**Updating late is fine.** Leaf/Purpur can trail Paper on new Minecraft versions, but **ViaVersion** lets newer clients join until the server catches up. It also covers Geyser, which always speaks the newest Java version.

**Risks and how we handle them:**
- Async features can clash with plugins that touch entities in unusual ways. Our `core` plugin sticks to the normal Paper API. Every async option is a config toggle, so we turn off whatever causes trouble.
- Leaf's own README says to back up before switching. Raptor does backups, and we test every update on **staging** first.
- If Leaf ever causes problems we can't fix, swap the jar for Paper. Nothing else changes.

## Layout

```
                    ┌──────────────── one dedicated machine (Raptor) ────────────────┐
Java players ──────▶│ Velocity proxy ──▶ Leaf 26.3 "game" (the world)                │
Bedrock players ───▶│  + Geyser          ──▶ Paper "limbo" (xLimbo: holds players     │
                    │  + Floodgate             during restarts/crashes)               │
                    │  + LuckPerms       PostgreSQL       Discord bot (later)         │
                    │                    Leaf "staging" (test server, not public)     │
                    └─────────────────────────────────────────────────────────────────┘
```

## Hosting: our own server

**Decided:** a dedicated machine we rent, managed with Raptor. At 100 players in one world, we need an always-on server with a fast CPU and full control over plugins, which Minehut can't really give us. What we give up is Minehut's server list, so we need a plan for getting players (server lists, Discord, TikTok/YouTube).

**Machine:**
- A recent **high-clock CPU**. Single-core speed matters most, because the world runs mostly on one thread.
- **64 GB RAM**: ~16 GB for the game server, plus proxy, limbo, staging, PostgreSQL, and room to grow.
- **NVMe** storage and off-machine **backups** (Raptor does encrypted backups).
- A data center close to where most players are (likely North America).

**Raptor caveat:** it's pre-launch with no uptime promise yet. Fine while we build. Revisit before public launch.

## Plugins we use

Checked 2026-10-25. ✅ = has a 26.3 build. ⚠️ = verify before installing.

### Proxy (Velocity)

| Plugin | Why | 26.3 |
| --- | --- | --- |
| Geyser | Lets Bedrock players join | ✅ |
| Floodgate | Bedrock players join without a Java account | ✅ |
| LuckPerms | Ranks and permissions, synced across servers through PostgreSQL | ✅ |

### Game server (Leaf)

| Plugin | Why | 26.3 |
| --- | --- | --- |
| Floodgate | Our plugin uses its API to detect Bedrock players and show them forms | ✅ |
| ViaVersion | Lets newer Java clients (and Geyser) join while the server is still on an older version | ✅ |
| LuckPerms | Ranks: prisoner, guard, staff | ✅ |
| WorldGuard + WorldEdit | Regions (prison, safe zones, robbery spots) that our plugin reads. Building tools for the map | ✅ |
| FastAsyncWorldEdit | Faster edits for big builds. Building only; can be left off live | ✅ |
| PacketEvents | Packet library our plugin and Grim use | ✅ |
| GrimAC | Anti-cheat with proper Bedrock (Geyser) handling | ✅ |
| CoreProtect | Logs and rollback of block changes (griefing, staff investigations) | ⚠️ latest build tagged 26.2 |
| spark | Performance profiling | ✅ |
| PlaceholderAPI | Shares our stats (cash, bounty) with TAB and other plugins | ✅ |
| TAB | Tab list, name tags, scoreboard. Works on Bedrock | ✅ |
| DiscordSRV | Discord ↔ Minecraft account linking, Discord role → LuckPerms group sync, chat bridge | ✅ |
| DecentHolograms | Floating text (leaderboards, robbery signs) | ✅ |
| Chunky | Pre-generate the map so it doesn't lag on first load | ✅ |
| VaultUnlocked | Economy bridge, only if another plugin needs our cash | ✅ |
| **xUtilities** | Our own: everyday commands (instead of EssentialsX) | ✅ |
| **zChat** | Our own: chat format + filter | ✅ |

### Limbo server

| Plugin | Why | 26.3 |
| --- | --- | --- |
| **xLimbo** | Our own: catches players when the game server restarts or crashes, then sends them back | ✅ |

### Deliberately not using

- **EssentialsX:** xUtilities covers it, and Essentials' economy/kits/homes would fight our game.
- **ItemsAdder / Oraxen / Nexo:** we only need a known set of items, and we need control over how they show on Bedrock. Paper's item model components + our own pack build are enough.
- **Gun plugins (WeaponMechanics etc.):** we need guns that feel fair on Bedrock touch controls. Ours will be simple hitscan.
- **Citizens:** for shopkeepers and NPCs we can use vanilla mannequin / display entities from our plugin. Revisit if we need walking NPCs.
- **NeoSkript / Skript:** fine for quick tests, but the core game is too big and too performance-sensitive for scripts at 100 players.

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
| 11 | **Shops** | Criminal base shop, guard armory. Chest GUI on Java, Floodgate forms on Bedrock. |
| 12 | **Indirect criminal kills** | Traps, snitching, stealing, sabotage (once we pick which). |
| 13 | **Guard duty** | On/off duty, guard loadout, guards-online count. The guard rank itself comes from LuckPerms through DiscordSRV. |
| 14 | **HUD** | Bounty, cash, wanted level, robbery status: boss bar / action bar, plus placeholders for TAB. |
| 15 | **Stats & leaderboards** | Escapes, arrests, cash earned, robberies. Holograms + Discord. |

Outside the plugin:

- **Resource pack pipeline:** source art + models in this repo → build the Java pack → convert to a Bedrock pack + Geyser mappings → upload. Automate it in CI. See [resource pack](06-resource-pack.md).
- **Guard applications:** start with a Discord form/ticket bot that already exists. Build our own bot only if it gets in the way.

## Build order

1. **Foundation:** set up the machine with Raptor; Velocity + Geyser + Floodgate + Leaf + ViaVersion + LuckPerms + PostgreSQL; join from Java and Bedrock.
2. **Core loop:** player state, combat rules, arrests, bounty, economy, one robbery. Playable on a test map.
3. **Custom items + guns + resource pack** (Java and Bedrock).
4. **Escapes, riots, shops, HUD.**
5. **Discord:** linking, guard rank sync, applications.
6. **Polish:** stats, leaderboards, indirect kills, more robberies.
7. **Load test 100 players**, fix what spark shows, then launch.

## Java + Bedrock

Both editions can join (decided), through Geyser + Floodgate on the proxy. Things to keep in mind:

- **Menus:** chest GUIs work on Bedrock but can feel clunky. Our plugin shows Bedrock players native forms (Floodgate's Forms API) and Java players chest GUIs.
- **Custom items / resource packs:** Java packs don't work on Bedrock. We need a separate Bedrock pack sent through Geyser. See [resource pack](06-resource-pack.md).
- **Combat:** Java and Bedrock PvP feel different (attack cooldown, reach, touch controls). Test fights between a Java player and a Bedrock player early.
- **Usernames:** Bedrock players usually get a prefix (like `.Name`). Store players by UUID, never by name.
- **Test everything on both**, phone and console included, not just PC.

