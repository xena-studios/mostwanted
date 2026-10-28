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

## Messages

Decided 2026-10-26: **symbols for everyday messages, banners for big moments** (options B + D from the mock-ups). It reads like a police radio: alarms, bounties and arrests at a glance. Updated 2026-10-27: **small caps for system text**, to match the [scoreboard and tab list](#scoreboard-and-tab-list).

### The rule everything follows

- **Labels and system text: small caps, grey.** `ʏᴏᴜʀ ʙᴏᴜɴᴛʏ ɪѕ ɴᴏᴡ`
- **Names, places and values: normal case, white.** `Alex`, `Cell Block B`, `2:14`
- **Money and bounties: always yellow `$1,200`**, everywhere (chat, scoreboard, holograms).
- **Section headers: bold capitals**, orange, red while an alarm is on. `PROFILE`, `HEIST`
- **Roles in their colour:** guards' names blue, wanted criminals' red, in chat, name tags and the tab list.

Message files keep plain text. `core` converts it to small caps once when messages load (`SmallCaps` in [`common`](../common/)), so writing a message stays easy. Keep system messages short: small caps are slower to read in long sentences.

### Everyday: one line, a coloured symbol, grey small caps

No server tag on system messages. The symbol says what kind of message it is, so it still reads on Bedrock where some colours merge:

| Symbol | Colour | Means | Example |
| --- | --- | --- | --- |
| ✔ | green `#33ff33` | Done, turned on | ✔ ᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ᴛᴏ **Spawn**. |
| ✖ | red `#ff3333` | Refused, error | ✖ ʏᴏᴜ ᴄᴀɴ'ᴛ ᴅᴏ ᴛʜᴀᴛ ᴡʜɪʟᴇ ᴄᴜꜰꜰᴇᴅ. |
| ★ | yellow `#ffff33` | Money, bounty | ★ ʏᴏᴜʀ ʙᴏᴜɴᴛʏ ɪѕ ɴᴏᴡ **$2,500**. |
| ⚑ | red `#ff3333` / blue `#3399ff` | Alarm, arrest (red for criminals' side, blue for guards') | ⚑ ᴛʜᴇ **Bank** ɪѕ ᴏᴘᴇɴ. ɢᴜᴀʀᴅѕ ʜᴀᴠᴇ ʙᴇᴇɴ ᴀʟᴇʀᴛᴇᴅ. |
| ⓘ | aqua `#33ffff` | Info, tips, links | ⓘ ʙᴀɴᴋ ʏᴏᴜʀ ᴄᴀѕʜ ᴀᴛ ᴀ ʜɪᴅᴇᴏᴜᴛ ʙᴇꜰᴏʀᴇ ᴀ ɢᴜᴀʀᴅ ꜰɪɴᴅѕ ʏᴏᴜ. |
| ✉ | aqua `#33ffff` | Private messages | ✉ ʏᴏᴜ → **Alex**: meet at the gas station |

### Player chat: a rank tag, no brackets

`ɪɴᴍᴀᴛᴇ Alex: never` · `ɢᴜᴀʀᴅ Steve: freeze!` · `ᴍᴏᴅ Sam: please keep it clean`

The tag in small caps: inmate orange (hue 30), guard blue (hue 210), staff light purple (hue 300). The name is white (blue for guards); the message stays exactly as typed, in white.

### Big moments: a banner, a title and a sound

Only for robberies opening, riots, a new Most Wanted, and server events. Nothing routine gets one, so they stay special.

```
▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
        ⚑ RIOT IN CELL BLOCK B ⚑
   ɪɴᴍᴀᴛᴇѕ ʜᴀᴠᴇ ᴛᴀᴋᴇɴ ᴛʜᴇ ʏᴀʀᴅ. ɢᴜᴀʀᴅѕ, ʀᴇѕᴘᴏɴᴅ!
          ᴇɴᴅѕ ɪɴ 2:00 · /riot info
▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
```

The bars and headline in red (or the event's colour), bold. Plus a screen title (**RIOT!** / Cell Block B), a sound, and the action bar for whoever it affects (`⚑ ʀɪᴏᴛ | ᴘʀɪѕᴏɴ ᴘᴠᴘ ɪѕ ᴏɴ | 1:52`).

## Scoreboard and tab list

Decided 2026-10-27, modelled on AZEscape's. Two-tone bold title everywhere: **Most** white + **Wanted** orange.

### Scoreboard (sidebar)

```
        MostWanted                  title, bold
◷ 23:24 ET · 10/27/26               clock, grey
                                    
PROFILE                             header, bold orange
★ ʀᴏʟᴇ: Inmate                      role in its colour
⛃ ᴄᴀѕʜ: $443                        money yellow
⌂ ʙᴀɴᴋ: $1,200
                                    
⧗ ѕᴇɴᴛᴇɴᴄᴇ: 2:14                    what the role is doing
⚔ ᴇѕᴄᴀᴘᴇѕ: 3
☠ ᴀʀʀᴇѕᴛᴇᴅ: 4
                                    
ᴍᴏѕᴛᴡᴀɴᴛᴇᴅᴍᴄ.ᴄᴏᴍ                    footer, orange
```

- **The clock stays** (date and time, Eastern, the server's timezone, labelled `ET`): screenshots then show when something happened, for reports, appeals and staff checking the logs.
- **The middle follows the role:** inmates see their sentence, escapes and arrests; criminals their bounty and wanted level (`★★★★★`, red stars filled); guards their duty, arrests and the current alarm.
- **Live events add a red section** while they're on: `HEIST` (the robbery, its timer, guards online), `RIOT` (where, time left, guards online).
- Each line has a grey icon; no red score numbers down the side (hidden with Paper's number format).
- Updated only when a value changes, never on a timer, apart from the clock once a minute.

### Tab list

```
MostWanted
[head] MrZCookies
ʏᴏᴜ ᴀʀᴇ ᴘʟᴀʏɪɴɢ ᴏɴ ᴍᴏѕᴛᴡᴀɴᴛᴇᴅ
ᴡᴇ ʜᴀᴠᴇ 24 ᴘʟᴀʏᴇʀѕ ᴏɴʟɪɴᴇ              the number orange

[players]

ɢᴜᴀʀᴅѕ 6 · ᴄʀɪᴍɪɴᴀʟѕ 6 · ɪɴᴍᴀᴛᴇѕ 12
ᴍᴏѕᴛᴡᴀɴᴛᴇᴅᴍᴄ.ᴄᴏᴍ
```

- Players sorted staff, guards, criminals (highest bounty first), then inmates.
- Each entry: the role tag in small caps and colour, then the name (blue for guards, red for criminals, white otherwise).
- Your own head next to your name in the header (a player-head text component; Java only).

### Bedrock

Bedrock players get the same layouts, with three differences to check on a real client once we can join through Minehut:
- If its font lacks the small-caps letters or an icon, `core` sends Bedrock players plain capitals and icons it can draw (`core` knows who's on Bedrock).
- Only 16 colours, so hues merge as in the [palette table](#in-game-colours).
- It shows the sidebar's score numbers, and no heads in the tab list.

### Where it's set

- **xUtilities** and **zChat**: their full configs in [`server/files/plugins/`](../server/files/plugins/) use these symbols, colours and small caps.
- **LuckPerms** rank prefixes ([`server/setup/luckperms.txt`](../server/setup/luckperms.txt)): name tags.
- **`core`**: one message module sends everything else, and draws the scoreboard and tab list, so new messages follow the style by default.
- Check every symbol on a Bedrock client: ⚑, ⓘ and the scoreboard icons are the ones most likely to be missing from its font. Swap any that don't render.

## Menus

Decided 2026-10-28: **menus are how players do things**, so nobody has to know a command (Bedrock players on a phone least of all). Everything is a chest menu, which Bedrock shows the same way (no Bedrock forms: there's no Floodgate on our side). `core/.../menu/Menus.java` draws them all the same way:

- **The way in:** a nether star, **Menu**, always in the last hotbar slot (it can't be dropped, moved, stored or lost), and `/menu`. The main menu opens your profile, settings, the shop, cosmetics, the store, leaderboards, your account (a sign-in code) and the wiki.
- **Title:** bold, dark grey, normal case (`MostWanted`, `Settings`). Menus have a light background, so palette colours don't read there.
- **Background:** black glass panes with no tooltip, in every empty slot.
- **Items:** the name in white, normal case (`Your profile`); rank names in their rank's colour. Lore in grey small caps, values in white, money in yellow, like messages. Lore lines stay short (about 30 characters), with a blank line between parts.
- **The last lore line says what a click does:** `▸ ᴄʟɪᴄᴋ ᴛᴏ ᴏᴘᴇɴ`, the arrow in aqua. A state goes just above it: `✔ ᴏɴ` in green, `✖ ᴏꜰꜰ` in red. Something in use glints.
- **The bottom row:** **Back** (an arrow) bottom left, **Close** (a barrier) in the middle, and one extra action bottom right (Settings on your profile, the website on the store).
- **On/off settings:** the setting's icon, with a lime or red pane under it; clicking either switches it.
- **Feedback:** a quiet click sound on every button. A menu redraws in place after a change, rather than sending a chat message.
- **Messages** for every menu are in `messages.yml` (`menu`, `profile`, `settings`, `leaderboards`, `store-menu`).

| Menu | Command | What's in it |
| --- | --- | --- |
| Main menu | `/menu`, the menu item | Everything below, plus a sign-in code and the wiki |
| Profile | `/profile [name]` | Head, role, bounty and wanted stars, every stat all time / this month / this week; your own money (only you see it). Private profiles are only shown to their owner (and staff). |
| Settings | `/settings` | Public stats, scoreboard, server news (broadcasts) and join announcements |
| Shop | `/shop`, or the main menu where you stand | The hideout shop or the armory |
| Cosmetics | `/cosmetics` | Trails, effects, gun skins, chat colour |
| Store | `/store`, `/buy` | The website's store, with links to its cart ([11-store.md](11-store.md#in-game)) |
| Leaderboards | `/leaderboards`, `/lb` | Every board, then its top 10 as heads, all time, this month or this week; a head opens that profile |

## Look and feel

**The files** (logos, key art, icons) are in [`brand/`](../brand/).


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

The logo is the word **MOSTWANTED** in this style, plus an **MW** monogram for square and small spaces (server icon, avatars, favicon). Both are in [`brand/source/`](../brand/source/). Make it as a vector (SVG) so it works from favicon to banner, and keep a flat one-colour version for small sizes and Bedrock.

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
- [x] The logo: MOSTWANTED wordmark and MW monogram, in [`brand/`](../brand/)
- [ ] A secondary colour, if we want one (a desert sky blue would contrast well with orange)
