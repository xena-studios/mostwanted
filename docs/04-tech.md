# Tech

## Hosting: Minehut

Things to check and write down here (don't trust memory, verify on Minehut's current docs/plans):

- [ ] Which plan / tier we need (RAM, player slots)
- [ ] Free servers sleep when empty. How does that affect us?
- [ ] Can we upload our own plugin jars, or only use the Minehut plugin marketplace?
- [x] Bedrock support: Minehut has it built in
- [ ] Server software available (Paper, etc.) and Minecraft version
- [ ] Custom domain / server address
- [ ] Monetization rules (what we're allowed to sell)

## Stack (ideas)

- **Server software**: Paper (or whatever Minehut supports best)
- **Scripting**: [NeoSkript](https://github.com/xena-studios/neoskript) for fast iteration on game logic, if Minehut allows it. Otherwise standard Skript
- **Our plugins**: [xUtilities, zChat, xLimbo](https://github.com/xena-studios/plugins) where they fit
- **Maps**: WorldEdit / FAWE for building, a world-reset plugin for round-based play
- **Maps**: built by Zachary

## Java + Bedrock

Both editions can join (decided), through Minehut's built-in Bedrock support. Things to keep in mind:

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
