# Brand

Started 2026-10-25. The server is **MostWanted** (`mostwantedmc.com`): orange, desert, Wild West wanted posters.

## Colour

**Primary: `#ff9933`.**

The rest of the palette comes from the reference logo: chunky blocky letters lit from the top, going from sand-gold to burnt brown, with a thick black outline.

| Role | Hex | Use |
| --- | --- | --- |
| **Primary** | `#ff9933` | Buttons, links, highlights, the logo's main face |
| Sand | `#eebd3b` | Logo top band, highlights, sun |
| Amber | `#e29020` | Logo second band |
| Burnt orange | `#c46002` | Logo third band, hover states |
| Rust | `#b65009` | Small accents, pixel details |
| Dark clay | `#893500` | Logo extrusion / shadow, headings on light backgrounds |
| Outline | `#000000` | Logo outline, text on orange |

**Contrast rules** (checked against WCAG AA):

- **Black text on `#ff9933`** reads well (~9.9:1). Use it for buttons.
- **`#ff9933` text on white doesn't** (~2.1:1). On light backgrounds, use `#893500` or black for text and keep orange for fills.
- On dark backgrounds, `#ff9933` text is fine.

## In-game colours

**Every colour in the game comes from this palette** (decided 2026-10-25): 24 hues, 15° apart, all at full saturation and 60% lightness (`hsl(h, 100%, 60%)`). Chat, scoreboards, tab list, name tags, menus, item names, holograms and boss bars all use these. The only extras are **white and gray** for plain text, since the palette has no neutrals.

Java shows the exact hex (MiniMessage `<#ff9933>`). **Bedrock only has Minecraft's 16 legacy colours**, so Minehut's Geyser shows the nearest one. Several hues collapse into the same Bedrock colour, so **anything players must tell apart (teams, good vs. bad) has to use hues that land on different Bedrock colours.**

| Hue | Hex | Bedrock shows (nearest) |
| --- | --- | --- |
| 0 | `#ff3333` | red `§c` |
| 15 | `#ff6633` | red `§c` |
| 30 | `#ff9933` | gold `§6` |
| 45 | `#ffcc33` | gold `§6` |
| 60 | `#ffff33` | yellow `§e` |
| 75 | `#ccff33` | yellow `§e` |
| 90 | `#99ff33` | green `§a` |
| 105 | `#66ff33` | green `§a` |
| 120 | `#33ff33` | green `§a` |
| 135 | `#33ff66` | green `§a` |
| 150 | `#33ff99` | green `§a` |
| 165 | `#33ffcc` | aqua `§b` |
| 180 | `#33ffff` | aqua `§b` |
| 195 | `#33ccff` | aqua `§b` |
| 210 | `#3399ff` | blue `§9` |
| 225 | `#3366ff` | blue `§9` |
| 240 | `#3333ff` | blue `§9` |
| 255 | `#6633ff` | blue `§9` |
| 270 | `#9933ff` | blue `§9` |
| 285 | `#cc33ff` | light purple `§d` |
| 300 | `#ff33ff` | light purple `§d` |
| 315 | `#ff33cc` | light purple `§d` |
| 330 | `#ff3399` | red `§c` |
| 345 | `#ff3366` | red `§c` |

(Nearest by RGB distance. Check on a real Bedrock client once we can join through Minehut.)

### Roles (suggested)

Picked so each one is a different colour on Bedrock too:

| Role | Hue | Hex | Bedrock |
| --- | --- | --- | --- |
| **Brand / server name / highlights** | 30 | `#ff9933` | gold |
| **Inmates** (prisoners inside) | 30 | `#ff9933` | gold |
| **Criminals** (escaped, wanted) | 0 | `#ff3333` | red |
| **Guards** | 210 | `#3399ff` | blue |
| **Cash, success, robbery complete** | 120 | `#33ff33` | green |
| **Bounty, warnings, timers** | 60 | `#ffff33` | yellow |
| **Info, tips, links** | 180 | `#33ffff` | aqua |
| **Staff, events, store cosmetics** | 300 | `#ff33ff` | light purple |
| **Errors, alarms** | 0 | `#ff3333` | red |
| Plain text | | white / gray | white / gray |

On Java, the in-between hues (15, 45, 75, …) are free for gradients and accents, e.g. a 0 → 30 → 60 fire gradient on the server name, or 210 → 240 on guard titles. Bedrock players just see the nearest colour, so never rely on a gradient to carry meaning.

### Contrast

These are bright colours, made for **dark backgrounds** (chat, scoreboards, menus). On the website's light sand sections, use them for fills and badges with black text, not as text colours.

### Scene colours

From the reference desert scene (below). For backgrounds, illustrations and the website, not for text.

| Role | Hex |
| --- | --- |
| Dusk sky | `#a46865` |
| Sunset glow | `#fddfb4` |
| Sun | `#fefef5` |
| Far mesa | `#945754` |
| Near rock | `#864231` |
| Sand | `#f2a96a` |
| Sand shadow | `#bd7046` |

## Look and feel

The reference art (2026-10-25) sets the style:

- **Pixel art.** A desert at sunset: a big pale sun low on the horizon, a dusty pink-mauve sky with streaky clouds, red-brown mesas and rock steps, a sand road leading into the distance, saguaro cacti and scrub.
- **The key art layout:** that scene, **blurred**, with the blocky orange wordmark centred on top. It works for the website hero, the Minehut listing banner, Discord, the server icon (logo only) and video thumbnails.
- **Warm and dusty, never neon.** The palette stays in sand, rust and dusk pink. Orange `#ff9933` is the only "loud" colour, and it belongs to the logo and buttons.

## Logo style

The reference is a chunky, Minecraft-style block wordmark:

- Thick, blocky letters with a **black outline** all the way around
- **Horizontal colour bands** from light at the top to dark at the bottom: sand → amber → burnt orange
- A darker **extruded bottom face** (`#893500`) so the letters look like 3D blocks
- A few **pixel notches** in a neighbouring shade, like worn sandstone

The logo is the word **MOSTWANTED** in this style. Make it as a vector (SVG) so it works from favicon to banner, and keep a flat one-colour version for small sizes and Bedrock.

## Desert theme

What "desert" can mean across the server, site and store:

- **The map:** a sun-baked penitentiary out in the desert. Sandstone and terracotta walls, a canyon or mesa around it, a dusty town with the bank and stores, an oasis, dirt roads for chases. Escaping into open desert should feel exposed.
- **Events:** sandstorms (lower visibility, a good cover for escapes), heat waves, a supply train or convoy crossing the desert.
- **Items and cosmetics:** bandanas, cowboy-hat-style cosmetics, cactus and tumbleweed trails, sand-coloured jumpsuits, "wanted" posters for top bounties.
- **Website:** the blurred pixel-art desert as the hero, the blocky logo on top, sand-coloured sections below, wanted-poster cards for the leaderboard.
- **Words:** "the Yard", "Solitary", "the Badlands", "the Pit", "Wanted", "Most Wanted".

## Naming

- Write it **MostWanted**: one word, capital M and W. Add **MC** where there's room or for handles: `mostwantedmc`.
- Server address: `play.mostwantedmc.com`. Website: `mostwantedmc.com`.
- "Most Wanted" on its own is a crowded search term (Need for Speed, the FBI list), so always pair it with Minecraft or MC in titles and listings, e.g. *MostWanted — Cops & Robbers Minecraft Server*. Don't borrow anything from Need for Speed's look.

## Still to decide

- [x] The name: **MostWanted**
- [ ] The logo: MOSTWANTED in the blocky style above
- [ ] A secondary colour, if we want one (a desert sky blue would contrast well with orange)
