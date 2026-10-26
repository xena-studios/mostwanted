# Brand

Started 2026-10-25. The name is still open, but the look isn't: **orange, desert**.

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

**In game:** Java chat, scoreboards and menus can use `#ff9933` directly (MiniMessage `<#ff9933>`). Bedrock only has the 16 legacy colours, so Geyser shows it as the nearest one, **gold** (`§6`). Pick text that still reads when it's gold.

## Logo style

The reference is a chunky, Minecraft-style block wordmark:

- Thick, blocky letters with a **black outline** all the way around
- **Horizontal colour bands** from light at the top to dark at the bottom: sand → amber → burnt orange
- A darker **extruded bottom face** (`#893500`) so the letters look like 3D blocks
- A few **pixel notches** in a neighbouring shade, like worn sandstone

The real logo waits for the name. Make it as a vector (SVG) so it works from favicon to banner, and keep a flat one-colour version for small sizes and Bedrock.

## Desert theme

What "desert" can mean across the server, site and store:

- **The map:** a sun-baked penitentiary out in the desert. Sandstone and terracotta walls, a canyon or mesa around it, a dusty town with the bank and stores, an oasis, dirt roads for chases. Escaping into open desert should feel exposed.
- **Events:** sandstorms (lower visibility, a good cover for escapes), heat waves, a supply train or convoy crossing the desert.
- **Items and cosmetics:** bandanas, cowboy-hat-style cosmetics, cactus and tumbleweed trails, sand-coloured jumpsuits, "wanted" posters for top bounties.
- **Website:** warm sand background, sunset-orange gradients, the blocky logo, wanted-poster cards for the leaderboard.
- **Words:** "the Yard", "Solitary", "the Badlands", "the Pit", "Wanted", "Most Wanted".

## Still to decide

- [ ] The name (see [names](../ideas/names.md))
- [ ] The logo, once there's a name
- [ ] A secondary colour, if we want one (a desert sky blue would contrast well with orange)
