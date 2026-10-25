# Server stack: everything we install

The install list for the game server. Versions checked **2026-10-25** for Minecraft **26.3**. Re-check before installing; newer builds come out often.

Why each choice was made is in [tech](04-tech.md).

## Software

| What | Version | Where to get it | Notes |
| --- | --- | --- | --- |
| **Leaf** (server) | `leaf-26.3-12.jar` | [GitHub releases](https://github.com/Winds-Studio/Leaf/releases) / [leafmc.one](https://www.leafmc.one/download) | Paper fork. Fallback: [Paper 26.3](https://papermc.io/downloads/paper) (build 223, stable) |
| **Java** | **25** | Raptor's Java 25 image / [Adoptium Temurin 25](https://adoptium.net) | Paper 26.3 requires Java 25+ |
| **PostgreSQL** | latest 17 or 18 | Raptor / distro package | Our plugin's data (cash, bounty, stats) + LuckPerms |
| **Raptor** | latest | [raptorpanel.net](https://raptorpanel.net) | Runs the server on the VPS |
| **Minehut External** | | [Minehut panel](https://minehut.com) | Bedrock, DDoS, server list. Setup in [tech](04-tech.md#how-players-connect) |

**Not needed:** Velocity, Geyser, Floodgate (Minehut does it), **spark** (built into Paper/Leaf: just run `/spark`).

## Plugins

### Must-have (install first)

| Plugin | Version | Get it | What it's for |
| --- | --- | --- | --- |
| **LuckPerms** | 5.5.71 | [Modrinth](https://modrinth.com/plugin/luckperms) | Ranks: prisoner, guard, staff. Store in PostgreSQL |
| **WorldGuard** | 7.0.20 | [Modrinth](https://modrinth.com/plugin/worldguard) | Regions: prison, safe zones, robbery spots, criminal base |
| **FastAsyncWorldEdit** | 2.16.1 | [Modrinth](https://modrinth.com/plugin/fastasyncworldedit) | Building the map. **Replaces** WorldEdit; don't install both |
| **ViaVersion** | 5.12.2-SNAPSHOT | [Modrinth](https://modrinth.com/plugin/viaversion) | Newer clients can join when Minecraft updates. ⚠️ snapshot, swap for a release when out |
| **GrimAC** | 2.3.75 | [Modrinth](https://modrinth.com/plugin/grimac) | Anti-cheat. ⚠️ See the Bedrock note below |
| **CoreProtect** | 24.1 | [Modrinth](https://modrinth.com/plugin/coreprotect) | Block logs + rollbacks. ⚠️ tagged 26.2; test on 26.3 |
| **LibertyBans** | 1.2.0-M1 | [Modrinth](https://modrinth.com/plugin/libertybans) | Bans, mutes, warnings. ⚠️ milestone build |
| **xUtilities** | 2.0.0 | [Modrinth](https://modrinth.com/plugin/xutilities) | Ours: everyday commands (spawn, tp, vanish, etc.) |
| **zChat** | 3.0.0 | [Modrinth](https://modrinth.com/plugin/zchat) | Ours: chat format + filter |

### Game support

| Plugin | Version | Get it | What it's for |
| --- | --- | --- | --- |
| **PlaceholderAPI** | 2.12.3 | [Modrinth](https://modrinth.com/plugin/placeholderapi) | Our plugin exposes `%core_cash%`, `%core_bounty%`, etc. to other plugins |
| **TAB** | 6.2.1 | [Modrinth](https://modrinth.com/plugin/tab-was-taken) | Tab list, name tags (team colors), sidebar scoreboard |
| **FancyHolograms** | 2.12.0 | [Modrinth](https://modrinth.com/plugin/fancyholograms) | Leaderboards, robbery signs, floating text. Uses display entities. ⚠️ check how they look on Bedrock |
| **FancyNpcs** | 2.12.1 | [Modrinth](https://modrinth.com/plugin/fancynpcs) | Shopkeepers, guard armory, warden, quest/info NPCs. Packet-based, so cheap on the server |
| **DiscordSRV** | 1.30.5 | [Modrinth](https://modrinth.com/plugin/discordsrv) | Account linking, Discord guard role → LuckPerms group, chat bridge |
| **Chunky** | 1.5.3 | [Modrinth](https://modrinth.com/plugin/chunky) | Pre-generate the map once, then can be removed |

### Later / only if needed

| Plugin | Version | Get it | When |
| --- | --- | --- | --- |
| **Plan** | 5.9 build 3705 (beta) | [Modrinth](https://modrinth.com/plugin/plan) | Player analytics. Uses RAM, so add once things are stable |
| **VaultUnlocked** | 2.20.3 | [Modrinth](https://modrinth.com/plugin/vaultunlocked) | Only if another plugin needs to read our cash |
| **PacketEvents** | 2.14.0 | [Modrinth](https://modrinth.com/plugin/packetevents) | Only if our `core` plugin needs packet tricks |

### Ours (to build)

| Plugin | What |
| --- | --- |
| **core** | The whole game: teams, arrests, bounty, cash, robberies, riots, guns, custom items, shops, HUD, stats. See [what we build](04-tech.md#what-we-build-ourselves) |

## ⚠️ Bedrock and the Fancy plugins

- **FancyHolograms** uses text display entities. Bedrock has no display entities, so Geyser has to fake them, and that may look different or not show. Test a hologram with a Bedrock player early. If it's bad, FancyHolograms can also be set to the older armor stand style for key holograms (check its config).
- **FancyNpcs** shows NPCs as fake players, which Geyser handles well. Test skins and click actions on Bedrock.

## ⚠️ Bedrock players and GrimAC

Grim exempts Bedrock players from checks so they don't get false flags, but it finds them **through Floodgate**, which we can't use with Minehut. Without that, Bedrock players will probably get flagged and kicked.

**Fix:** once we know how Minehut marks Bedrock players (name prefix or UUID format), `core` gives them the `grim.exempt` permission when they join. Until then, test Grim with a Bedrock player before enabling punishments.

## Load order / dependencies

```
LuckPerms ─┐
FAWE ──────┼─▶ WorldGuard ─┐
PlaceholderAPI ────────────┼─▶ core (our plugin) ─▶ TAB / FancyHolograms / FancyNpcs read its placeholders
                           └─ DiscordSRV ─▶ LuckPerms (role sync)
```

`core`'s `paper-plugin.yml` should depend on LuckPerms, WorldGuard and PlaceholderAPI, and soft-depend on FancyNpcs (to open shops when an NPC is clicked) and FancyHolograms (to update leaderboards).

## RAM budget (VPS-3, 12 GB)

| | |
| --- | --- |
| Leaf (all plugins) | 8–9 GB (`-Xms` = `-Xmx`) |
| PostgreSQL | 0.5–1 GB |
| OS + Raptor | 1–1.5 GB |

## Start command

Paper's recommended flags (from PaperMC) plus Minehut's login flags. Replace `9G` with the heap size.

```
java -Xms9G -Xmx9G \
  -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem \
  -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 \
  -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 \
  -XX:SurvivorRatio=32 \
  -Dminecraft.api.services.host=https://api.minecraftservices.com/ \
  -Dminecraft.api.auth.host=https://authserver.mojang.com/ \
  -Dminecraft.api.account.host=https://api.mojang.com/ \
  -Dminecraft.api.profiles.host=https://api.mojang.com/ \
  -Dminecraft.api.session.host=https://api.minehut.com/mitm/proxy \
  -jar leaf-26.3-12.jar --nogui
```

## Config checklist

- [ ] `config/paper-global.yml`: `proxies.proxy-protocol: true` (Minehut)
- [ ] `server.properties`: `enforce-secure-profile=false` (Minehut), `view-distance` ~8, `simulation-distance` ~6 (tune with spark)
- [ ] `server.properties`: `resource-pack=` GitHub release URL, `require-resource-pack=true`
- [ ] LuckPerms + LibertyBans + CoreProtect → PostgreSQL (or their default file storage at first)
- [ ] WorldGuard: global region `pvp deny`; `core` decides who can hurt who
- [ ] Leaf config: start with defaults, turn async options on one at a time and test
- [ ] Firewall: only Minehut's IPs on 25565 (once we know them), SSH key-only
