# Tech

## Hosting: Minehut

Things to check and write down here (don't trust memory, verify on Minehut's current docs/plans):

- [ ] Which plan / tier we need (RAM, player slots)
- [ ] Free servers sleep when empty. How does that affect us?
- [ ] Can we upload our own plugin jars, or only use the Minehut plugin marketplace?
- [ ] Server software available (Paper, etc.) and Minecraft version
- [ ] Custom domain / server address
- [ ] Monetization rules (what we're allowed to sell)

## Stack (ideas)

- **Server software**: Paper (or whatever Minehut supports best)
- **Scripting**: [NeoSkript](https://github.com/xena-studios/neoskript) for fast iteration on game logic, if Minehut allows it. Otherwise standard Skript
- **Our plugins**: [xUtilities, zChat, xLimbo](https://github.com/xena-studios/plugins) where they fit
- **Maps**: WorldEdit / FAWE for building, a world-reset plugin for round-based play
- **Vehicles**: vehicle plugin TBD (check Minehut support)

## Build vs. buy

| Piece | Existing plugin? | Build ourselves? | Notes |
| --- | --- | --- | --- |
| Teams, bounty, arrests | | | Probably custom |
| Vehicles | | | |
| Robbery locations + timers | | | |
| Shop / gear | | | |
| Economy | | | |
