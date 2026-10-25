# Tech

## Hosting

Not decided yet. The preference is our own server, with Minehut as the fallback if it turns out to be the better option.

| | Minehut | Our own server (VPS / dedicated box + [Raptor](https://github.com/xena-studios/raptor)) |
| --- | --- | --- |
| **Cost** | Free tier, paid plans for more RAM/slots | Monthly rent for the machine. Check current prices |
| **Getting players** | **Built-in server list.** New players find you without advertising | No built-in discovery. Server lists, Discord, TikTok/YouTube are on us |
| **Bedrock** | Built in | Geyser + Floodgate plugins (standard, well documented) |
| **Plugins** | Limited to what Minehut allows (verify) | Anything, including our own jars |
| **Always on** | Free servers sleep when empty | Always on |
| **Performance** | Shared, capped by plan | All the machine's CPU/RAM is ours |
| **Address** | `name.minehut.gg` (custom domain on paid plans?) | Our own domain, e.g. `play.<name>.com` |
| **Control** | Their panel, their rules | Full control: backups, files, versions, multiple servers later |

**Leaning:** our own server running Raptor. A persistent Jailbreak-style world with custom logic needs to be always on and able to run any plugin, and we'd be testing Raptor on a real server at the same time. The big thing we give up is Minehut's player discovery, so we'd need a plan for getting players.

Caveat: Raptor is pre-launch with no uptime promise yet. That's fine while we build and test. Revisit before opening to the public.

### Minehut things to check (if we go that way)

- [x] Bedrock support: Minehut has it built in
- [ ] Which plan / tier we'd need (RAM, player slots)
- [ ] Can we upload our own plugin jars?
- [ ] Monetization rules (what we're allowed to sell)

## Stack (ideas)

- **Minecraft version**: **26.3**, the latest version everything supports (decided, checked 2026-10-25). Paper 26.3 has stable builds (needs Java 25), and Geyser supports Java 26.3 and current Bedrock versions. Re-check plugins as we pick them.
- **Server software**: Paper
- **Scripting**: [NeoSkript](https://github.com/xena-studios/neoskript) for fast iteration on game logic, if Minehut allows it. Otherwise standard Skript
- **Our plugins**: [xUtilities, zChat, xLimbo](https://github.com/xena-studios/plugins) where they fit
- **Maps**: WorldEdit / FAWE for building, a world-reset plugin for round-based play
- **Maps**: built by Zachary

## Handling 100 players

Target: **up to 100 players online at once** (decided), all in one persistent world.

- **One world means one game server.** We can't split one map across servers, so it all runs on a single Paper server. That's the bottleneck. Minecraft does most of its work on one CPU core, so **single-core speed matters more than core count**.
- **Setup:**
  ```
  Java players ───────┐
                      ├──▶ Velocity proxy (+ Geyser + Floodgate) ──▶ Paper 26.3 game server
  Bedrock players ────┘
  ```
  Running Geyser on the proxy keeps Bedrock translation work off the game server and leaves room for a lobby or second server later.
- **Machine:** a dedicated server (not a shared VPS) with a recent high-clock CPU, NVMe storage, and enough RAM for ~12–16 GB for the game server plus the proxy. Rough numbers. Confirm with load tests.
- **Things that eat performance at 100 players:** lots of entities, redstone, too many bullets as real projectiles (another reason for instant-hit guns), big view distance. Tune `view-distance` / `simulation-distance` and Paper's entity limits.
- **Load test before launch.** Use bots (and a real playtest night) to check 100 players running around the city with guns.
- **Folia** (multi-threaded Paper) could handle more players in one world, but many plugins don't work on it. NeoSkript does. Keep it as a backup plan if Paper can't hold 100.

## Discord

- Guard applications happen in Discord (form, ticket or bot). Approved players get the guard rank.
- **Account linking** between Discord and Minecraft (e.g. DiscordSRV-style linking) so the Discord role and in-game rank stay in sync. It has to work for Bedrock players too (Floodgate accounts).
- Raptor can host Discord bots, so a custom bot could live on the same machine.

## Java + Bedrock

Both editions can join (decided), through Minehut's built-in support or Geyser + Floodgate on our own server. Things to keep in mind:

- **Menus:** chest GUIs work on Bedrock but can feel clunky. Keep them simple, or use Bedrock forms where we can.
- **Custom items / resource packs:** Java packs don't work on Bedrock. We need a separate Bedrock pack sent through Geyser. See [resource pack](06-resource-pack.md).
- **Combat:** Java and Bedrock PvP feel different (attack cooldown, reach, touch controls). Test fights between a Java player and a Bedrock player early.
- **Usernames:** Bedrock players usually get a prefix (like `.Name`). Make sure our scripts/plugins handle that.
- **Test everything on both**, phone and console included, not just PC.

## Build vs. buy

| Piece | Existing plugin? | Build ourselves? | Notes |
| --- | --- | --- | --- |
| Teams, bounty, arrests | | | Probably custom |
| Robbery locations + timers | | | |
| Shop / gear | | | |
| PvP rules / safe zones | | | |
| Economy | | | |
