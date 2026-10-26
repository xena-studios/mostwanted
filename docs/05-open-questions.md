# Open questions

Decisions we need to make. When one gets answered, write the answer and move it to "Decided."

## Open

1. **Minehut External:** can we still buy the External plan (one source says it's partner-only now)? Can external servers supply a Bedrock resource pack + custom item mappings? Does Minehut publish IP ranges for the firewall? How are Bedrock players marked? (needed so GrimAC doesn't false-flag them)
2. **Indirect criminal-vs-criminal kills:** which ones? Traps, snitching, stealing, sabotage? See [combat](02-gameplay.md#combat).
3. **Riot rules:** how one starts, how it ends, cooldown. See [riots](02-gameplay.md#riots).
4. **Guards:** how many do we need, can they go off duty, and what's the application process?
5. **Name?** See [names](../ideas/names.md).

## Decided

- **Genre:** escape game, like Roblox Jailbreak. Not classic mine-and-rank-up prison. (2026-10-25)
- **Teams:** two teams, Guards and Prisoners. Prisoners become Criminals after escaping. (2026-10-25)
- **Guards are by application:** everyone starts as a prisoner. Guard rank comes from applying in the Discord. (2026-10-25)
- **Format:** one persistent world, no rounds. (2026-10-25)
- **Vehicles:** not in the main game. Maybe later. (2026-10-25)
- **Editions:** Java and Bedrock both, with Bedrock handled by Minehut. (2026-10-25)
- **Map:** Zachary is building it. (2026-10-25)
- **Combat:** criminals can fight back. Guards vs. criminals is PvP. (2026-10-25)
- **Criminal vs. criminal:** no direct damage, only indirect ways. (2026-10-25)
- **Riots:** inmates can start them. (2026-10-25)
- **Custom items:** guns, food, tools, and more through the resource pack. (2026-10-25)
- **Version:** Minecraft 26.3. (2026-10-25)
- **Size:** up to 100 players at once. (2026-10-25)
- **Tech stack:** Leaf 26.3 standalone on an OVH US VPS-3 (Raptor), connected through Minehut External Server (Bedrock, DDoS, server list). PostgreSQL, one custom Java plugin. Full list in [tech](04-tech.md). (2026-10-25)
- **Guns:** our own hitscan guns, no gun plugin. (2026-10-25)
- **Custom items:** our own plugin + pack pipeline, no ItemsAdder/Nexo. (2026-10-25)
- **Website and store:** Astro on Cloudflare, store through Tebex Headless, cosmetics only. See [website](08-website.md). (2026-10-25)
- **Brand:** orange `#ff9933`, desert theme, blocky Minecraft-style logo. In-game colours only from the 24-hue `hsl(h, 100%, 60%)` palette. See [brand](09-brand.md). (2026-10-25)
- **Repo layout:** `core/`, `website/`, `resource-pack/`, `server/` alongside the docs. (2026-10-25)
