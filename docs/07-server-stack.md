# Server stack: everything we install

The install list for the game server. **The source of truth is [`server/manifest.json`](../server/manifest.json)**: every version pinned with its checksum, installed by [`server/install.sh`](../server/install.sh) (which the panel runs, through the MostWanted egg) and boot-tested on every change (see [server/README.md](../server/README.md)). This page explains the choices. Versions checked **2026-10-26** for Minecraft **26.3**.

Why each choice was made is in [tech](04-tech.md).

## Software

| What | Version | Where to get it | Notes |
| --- | --- | --- | --- |
| **Leaf** (server) | `leaf-26.3-13.jar` | [Leaf's download API](https://api.leafmc.one/v2/projects/leaf/versions/26.3/builds) (keeps every build) / [leafmc.one](https://www.leafmc.one/download) | Paper fork. Fallback: [Paper 26.3](https://papermc.io/downloads/paper) (build 223, stable) |
| **Java** | **25** | The egg's Java 25 image (`ghcr.io/pelican-eggs/yolks:java_25`) / [Adoptium Temurin 25](https://adoptium.net) | Paper 26.3 requires Java 25+ |
| **PostgreSQL** | 18 | A managed database in the panel ([12-launch.md](12-launch.md), step 4) | Our plugin's data (cash, bounty, stats) + LuckPerms + LibertyBans + the backend |
| **Calagopus** | latest | [calagopus.com](https://calagopus.com) | Runs the servers and the database on the VPS ([`server/panel/`](../server/panel/)) |
| **Minehut External** | | [Minehut panel](https://minehut.com) | Bedrock, DDoS, server list. Setup in [tech](04-tech.md#how-players-connect) |

**Not needed:** Velocity, Geyser, Floodgate (Minehut does it), **spark** (built into Paper/Leaf: just run `/spark`), **DiscordSRV** (our own bot does linking and role sync: [Discord bot](10-discord-bot.md)).

## Plugins

Approved 2026-10-26. The rule: **get a plugin where it's a hard, solved problem; build it into `core` where it's part of the game.** The map runs in adventure mode (players can't break or place blocks unless `core` allows it), which removes the need for several "standard" plugins.

### What we install

| Plugin | Version | Why this one |
| --- | --- | --- |
| **LuckPerms** | 5.5.71 | Ranks and permissions. The standard: fast, async, PostgreSQL storage. |
| **ViaVersion** | 5.12.2 | Newer clients can join when Minecraft updates. The only real option. |
| **GrimAC** | 2.3.74 | Anti-cheat. The strongest free one; the paid ones (Vulcan, Polar, Intave) cost a lot for little gain at our size. ⚠️ Needs the Bedrock exemption below. |
| **LibertyBans** | 1.2.0-M1 | Bans, mutes, warnings. Free, light, async, PostgreSQL. ⚠️ A milestone build, the only one for 26.3; boot-tested fine. |
| **xUtilities** | 2.0.0 | Ours: everyday commands. |
| **zChat** | 3.0.0 | Ours: chat format and filter. |
| **FancyNpcs** | 2.12.1 | NPCs (shopkeepers, armory): packet-based, skins, in-game editor. |
| **FancyHolograms** | 2.12.0 | Static floating text. Live leaderboards are `core`'s own (`/mw board`, vanilla text displays). |
| **FastAsyncWorldEdit** | 2.16.1 | Building the map. Can come off the live server once it's built. |

**Later:** **NuVotifier**, only if we list on vote sites. (No Tebex plugin: core delivers purchases itself, [store](11-store.md).)

### What `core` does instead

| Instead of | `core` | Why |
| --- | --- | --- |
| WorldGuard | **Zones**: prison boundary, safe zones, robbery spots, hideouts. Marked with a FAWE selection. | The game reacts to zones constantly. A chunk-indexed lookup in `core` is faster than asking another plugin, and adventure mode already stops griefing. |
| TAB, PlaceholderAPI | **Name tags, tab list, sidebar scoreboard** through the Paper API | Guard blue / wanted red is game state: `core` updates it the moment it changes, instead of TAB polling placeholders. Two fewer plugins. |
| CoreProtect | **Game event logs**: arrests, robberies, riots, purchases | No block griefing in adventure mode, and CoreProtect doesn't run on 26.3. |
| Plan | **Stats** in PostgreSQL, shown on the website | Plan runs its own web server and is memory-heavy; our API serves the stats. |
| Chunky | Nothing | The map is hand-built; no terrain to pre-generate. |
| VaultUnlocked, PacketEvents | Nothing | Nothing else reads our cash; Grim bundles its own PacketEvents. |
| DiscordSRV | Our **Discord bot** ([Discord bot](10-discord-bot.md)) | Linking and role sync exactly how we want them. |

## ⚠️ Bedrock and the Fancy plugins

- **FancyHolograms** uses text display entities. Bedrock has no display entities, so Geyser fakes them, and they may look different or not show. Test early; important info (robbery open/closed, bounty) also goes in chat, the action bar or the scoreboard.
- **FancyNpcs** shows NPCs as fake players, which Geyser handles well. Test skins and click actions on Bedrock.
- Both would upload usage statistics through FancyAnalytics; that's off (`server/files/plugins/FancyAnalytics/config.json`).

## ⚠️ Bedrock players and GrimAC

Grim exempts Bedrock players from checks so they don't get false flags, but it finds them **through Floodgate**, which we can't use with Minehut. Without that, Bedrock players will probably get flagged and kicked.

**Fix:** once we know how Minehut marks Bedrock players (name prefix or UUID format), `core` gives them the `grim.exempt` permission when they join. Until then, test Grim with a Bedrock player before enabling punishments.

## RAM budget (netcup RS 2000, 16 GB)

| | |
| --- | --- |
| Leaf (all plugins) | 10 GB heap (`-Xms` = `-Xmx`) + ~1–1.5 GB outside the heap |
| PostgreSQL | ~1 GB |
| OS + Calagopus | 1–1.5 GB |
| backend (API + Discord bot) | ~0.4 GB (256 MB heap) |

## Start command, configs and setup

All in [`server/`](../server/): `start.sh` (Paper's G1 flags plus Minehut's login flags), the configs under `server/files/`, and the one-time rank setup in `server/setup/luckperms.txt`. See [server/README.md](../server/README.md).
