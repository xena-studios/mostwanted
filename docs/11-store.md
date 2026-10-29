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

## Built to sell, honestly

The page is laid out to make buying easy and to show real value, without the tricks that work on kids:

- **Easy to get around:** a bar under the header with Ranks, Cosmetics, Support and Questions, highlighting the section you're in. The cart sits at the bottom right, with its count and total, once something is in it.
- **Value you can check:** each rank shows what its included cosmetics cost on their own (Legend: every cosmetic, worth more than its price), and each cosmetic says which rank includes it.
- **A cart that saves you money:** if a rank covers what's in the cart for less, it offers a one-click swap ("MVP includes all of these and costs $5.94 less"); if a rank costs only a little more (up to $5), it says so. It flags anything already included in a rank in the cart, and lower ranks a higher one covers, so nobody pays twice.
- **Fewer mistakes at checkout:** the username is remembered and shows that player's head, so you can see it's the right account. The creator code is tucked away until you need it.
- **Adding** shows a short confirmation with a way straight to checkout.
- **Not used, on purpose:** countdown timers, "only N left", fake "X people bought this", pre-ticked extras, subscriptions. Tebex's community goals (a "help pay for the server" bar) need the private key, so they'd go through the backend later rather than the browser.

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
5. **Delivery:** core delivers purchases itself, no Tebex plugin needed. In Tebex, add a game server (Integrations → Game servers, type **Offline/Geyser**) and copy its **secret key** into the game server's **Startup** tab in the panel as **Tebex secret key** (never this repo), then restart. Core asks Tebex's [command queue](https://docs.tebex.io/plugin/endpoints/command-queue) as often as Tebex says (about every 2 minutes), runs each command from the console and marks it done. It runs a command only if it matches `store.allowed-commands` in `config.yml`: exactly the commands above. Anything else stays undelivered in Tebex and shows in the server log, so a leaked key can't run other commands. A new kind of package needs a new pattern there.

## In game

Core reads the permissions above, so a purchase works the moment LuckPerms has it (ranks are checked again every 30 seconds, so nobody has to rejoin).

- **`/store`** (or `/buy`, or Store in the main menu) is the store as a menu: ranks, cosmetics and support, with the website's descriptions and prices and what you already have. It reads the website's own catalogue (`/store.json`, built from `catalog.ts`), so the two never disagree; core fetches it every 30 minutes and keeps a copy in `store-catalog.json`. Nothing is paid in game: clicking gives a link, **`mostwantedmc.com/buy/<id>`**, that opens the website's store with it in the cart and the cart open (short enough to type on Bedrock, where chat links can't be clicked). The prices shown are the catalogue's, so keep them the same as Tebex's.
- **`/cosmetics`** opens a menu: one row each for trails, escape effects, arrest effects, gun skins and chat colour. Click one you own to use it, again to take it off; one you don't own opens the store menu. Choices are saved in the database (`cosmetic_choices`).
- **Trails** puff behind you as you walk. **Escape effects** go off where you break out. **Arrest effects** are the guard's, shown on the criminal they arrest. Fireworks from cosmetics never hurt anyone.
- **Gun skins** swap the look of that gun in your inventory, with the resource pack (`pack/README.md`). Same gun, same stats; someone who picks it up sees their own.
- **Chat colours:** click to go through the palette's colours (right-click goes back), then off.
- **Rank tags** come after the name in the tab list. Chat shows LuckPerms' suffix if zChat's format includes it.
- **Coloured name** (MVP and Legend): the player's display name, in their rank's colour, which chat shows if zChat's format uses the display name. The tab list keeps role colours, which mean something in the game.
- **Joins announced** (MVP and Legend), and **`/joinmessage set <message>`** (Legend): one line, up to 48 characters, always shown as plain text.
- **`mostwanted boost <player>`**: a title and fireworks for everyone online, and a thank-you in chat.

Ops aren't given a rank tag: only a LuckPerms group really set counts. They do own every cosmetic (ops have every permission), which helps testing.

- **Supporter badge:** a player's page on the website shows their rank ("★ MVP supporter", in its colour, linking to the store). The backend reads it from LuckPerms' own table, which shares our database (`Stats.supporter`). Players with private stats have no page, so no badge either.

- **Discord roles:** linked players get their rank's role on Discord ([10-discord-bot.md](10-discord-bot.md)) once you've made roles named VIP, VIP+, MVP and Legend.
