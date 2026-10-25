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

- **Server software**: Paper
- **Scripting**: [NeoSkript](https://github.com/xena-studios/neoskript) for fast iteration on game logic, if Minehut allows it. Otherwise standard Skript
- **Our plugins**: [xUtilities, zChat, xLimbo](https://github.com/xena-studios/plugins) where they fit
- **Maps**: WorldEdit / FAWE for building, a world-reset plugin for round-based play
- **Maps**: built by Zachary

## Java + Bedrock

Both editions can join (decided), through Minehut's built-in support or Geyser + Floodgate on our own server. Things to keep in mind:

- **Menus:** chest GUIs work on Bedrock but can feel clunky. Keep them simple, or use Bedrock forms where we can.
- **Custom items / resource packs:** Java packs don't automatically work on Bedrock. Stick to vanilla items with custom names/lore at first.
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
