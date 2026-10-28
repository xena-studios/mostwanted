# Store

Decided 2026-10-28. The store is the server's income, so the website treats it as a main feature, not a footer link: a highlighted **Store** button in the header on every page, a store section on the home page, and a full store page at [/store](https://mostwantedmc.com/store).

## What we sell, and what we never do

**Cosmetics only.** Nothing in the store helps anyone win a fight, an escape or a robbery. That's Mojang's [commercial usage rules](https://www.minecraft.net/usage-guidelines), our [Terms](../website/src/pages/terms.astro), and the point of the game.

**Never for sale:** guns, ammo, armor, cash, shorter jail time, the guard rank, crates or anything random. (Also not sold, to stay clearly inside the rules: reserved slots, kits, boosters that affect cash or XP.)

Everything is **one-time and permanent**: no subscriptions, so no surprise charges for players who are often young.

## The catalogue

The source of truth is [`website/src/store/catalog.ts`](../website/src/store/catalog.ts): the store page, the home page and this list all read from it. Icons are in `website/public/store/`, drawn in vanilla's style like the [resource pack](../pack/README.md).

### Ranks

Each includes everything in the ones before it. The tag is a LuckPerms **suffix** (after the name), so the role tag in front (inmate, guard, staff) always shows. Colours come from the in-game palette, avoiding the colours that already mean something (red, green, yellow, aqua, role and staff colours).

| Rank | Price | Tag | Gives |
| --- | --- | --- | --- |
| VIP | $4.99 | lime `#99ff33` | Tag, Confetti escape effect, Discord role, website badge |
| VIP+ | $9.99 | teal `#33ffcc` | VIP, plus Smoke trail and chat colours |
| MVP | $19.99 | indigo `#6633ff` | VIP+, plus a coloured name, every trail, every escape and arrest effect, joins announced |
| Legend | $39.99 | rose `#ff3366` | MVP, plus every cosmetic (gun skins too) now and later, your own join message |

### Cosmetics

Each is a `mostwanted.cosmetic.<id>` permission, so they stack with ranks and work through LuckPerms.

| Cosmetic | Kind | Price |
| --- | --- | --- |
| Cash Trail | Trails | $3.99 |
| Ember Trail | Trails | $2.99 |
| Smoke Trail | Trails | $2.99 |
| Siren Trail | Trails | $3.99 |
| Fireworks Escape | Escape effects | $2.99 |
| Confetti Escape | Escape effects | $2.99 |
| Thunder Escape | Escape effects | $3.99 |
| Spotlight Arrest | Arrest effects | $2.99 |
| Siren Arrest | Arrest effects | $2.99 |
| Gilded Pistol | Gun skins | $4.99 |
| Copper Shotgun | Gun skins | $4.99 |
| Midnight Rifle | Gun skins | $4.99 |
| Diamond Taser | Gun skins | $3.99 |
| Chat Colours | Chat | $2.99 |

### Support the server

| Package | Price | What happens |
| --- | --- | --- |
| Server Boost | $4.99 | Fireworks over the map and a thank-you message from the server |
| Tip Jar | $1.99 | Nothing in game: a thank-you |

Prices are starting points: change them in Tebex (the site shows Tebex's prices once connected) and in `catalog.ts` (shown before then).

## How the website store works

- The page shows the catalogue from `catalog.ts`. Once the Tebex **public token** is set, it loads the live store from Tebex's [Headless API](https://docs.tebex.io/developers/headless-api/introduction) and **matches packages by name**: prices come from Tebex, and anything not found in Tebex shows "Unavailable".
- The cart lives in the browser. Checkout asks for the Minecraft username (Bedrock players use their name as it shows in game; anyone can buy for a friend) and an optional **creator code** (Tebex's creator codes, for the [creator program](../website/src/pages/creators.astro)).
- Checkout makes a Tebex basket, adds the packages, sends the player through Tebex's sign-in if the store wants one, then to Tebex's payment page. They come back to `/store?thanks=1`.
- Without the token, everything works except payment: the button says checkout opens at launch.
- The site only ever uses the **public** token, which Tebex makes for browsers. The private key never goes near the website or this repo.

## Setting up Tebex (what's left for you)

1. **Create the webstore** at [creator.tebex.io](https://creator.tebex.io). Choose **Minecraft (Offline/Geyser)** as the game type, so Bedrock players coming through Minehut can buy with the name they play under.
2. **Create the packages** with exactly these names (they're matched by name), the prices above, and these commands. Set each command to run **without the player needing to be online**.

   | Package name | Command |
   | --- | --- |
   | VIP | `lp user {username} parent add vip` |
   | VIP+ | `lp user {username} parent add vip_plus` |
   | MVP | `lp user {username} parent add mvp` |
   | Legend | `lp user {username} parent add legend` |
   | Each cosmetic (e.g. Cash Trail) | `lp user {username} permission set mostwanted.cosmetic.<id> true` (ids in `catalog.ts`: `trail-cash`, `escape-fireworks`, …) |
   | Server Boost | `mostwanted boost {username}` |
   | Tip Jar | none |

   Categories are up to you (Ranks, Cosmetics, Support): only the package names matter to the site.
3. **Add the rank groups** to LuckPerms: the "Store ranks" block in [`server/setup/luckperms.txt`](../server/setup/luckperms.txt).
4. **Connect the website:** copy the **Public Token** from Tebex (Developers → API keys) into the repo's **Settings → Secrets and variables → Actions → Variables** as `TEBEX_PUBLIC_TOKEN`, then run the website workflow (or push anything). It's a variable, not a secret, because it's public by design.
5. **Delivery:** Tebex runs the commands through its **Tebex plugin** on the game server, which needs the store's **secret key** in its config, kept in the server's `.env`, never in this repo. The plugin isn't on the approved list yet; it needs your OK (alternative: the backend receives Tebex's webhooks and core runs the commands, more work).

## Not built in game yet

The store can take orders now, but these need building in `core` before launch so every purchase does what the page says:

- **Cosmetics:** trails, escape and arrest effects, gun skins (pack textures for the four skins are drawn: the site's icons), chat colours, the coloured name, joins announced and the join message, all reading the permissions above.
- **`mostwanted boost <player>`** for the Server Boost.
- **Rank tags in the tab list:** core draws the tab list itself, and doesn't show LuckPerms suffixes yet. Chat shows them if zChat's formats include the suffix.
- **The supporter badge** on website player pages, and **Discord roles** per rank (the bot can sync them like the Linked role).
