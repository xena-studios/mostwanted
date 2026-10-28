# Website and store

Decided 2026-10-25. **Built** in [`website/`](../website/): home (explore cards, player search, how it plays, news), news, wiki, leaderboards, player lookup, rules, staff, creators, apply, support, store and sign-in. Player search, leaderboards and sign-in show a "starts when the server opens" state until the stats feed and Discord sign-in exist.

## Stack

| Piece | Choice |
| --- | --- |
| Site | **[Astro](https://astro.build)** in [`website/`](../website/), same as [xenastudios.co](https://github.com/xena-studios/website) and the plugin docs |
| Hosting | **Cloudflare** (Workers static assets), free tier |
| Store | **[Tebex](https://tebex.io)**, through its [Headless API](https://docs.tebex.io/developers/headless-api/overview) |
| Delivery | The **Tebex plugin** on the game server runs the commands for each purchase |
| Domain | **mostwantedmc.com**: the site at the root, `play.` for the server address, `store.` if the store needs its own |

## Pages

| Page | What's on it |
| --- | --- |
| **Home** | What the server is, a trailer/screenshots, **how to join** (Java and Bedrock addresses), Discord link |
| **Store** | Packages from Tebex, styled like the rest of the site. Cart and checkout handled by Tebex |
| **Rules** | Server rules, including guard conduct |
| **Become a guard** | What guards do, requirements, and a link to the Discord application |
| **Vote** | Server list links, and what voting gives you |
| **Status** | Online or not, player count |
| **Leaderboards** | Top escapers, top guards, most wanted, richest. All-time, monthly and weekly. See [player stats](#player-stats) |
| **Player pages** | `/player/<name>`: one player's stats, records and recent activity. See [player stats](#player-stats) |

Mostly static pages with no JavaScript. Only the store's cart and the status widget need a little.

## How the store works

**Built (2026-10-28):** the store page, its catalogue and Tebex checkout. What's sold, why, and the Tebex setup steps are in [docs/11-store.md](11-store.md). The flow below is Tebex's Headless API from the browser.

```
Player on our site ──▶ Store page (our design, packages from Tebex's API)
                          │ add to cart, enter username
                          ▼
                    Tebex checkout (payment, tax, fraud, chargebacks)
                          │
                          ▼
              Tebex plugin on our server ──▶ runs commands
                                              e.g. lp user <name> parent add vip
                                                   mostwanted cosmetic give <name> <id>
```

- The Tebex plugin connects **out** to Tebex, so Minehut sitting in front of the server doesn't matter.
- Bedrock players buy with the username they play under (check how Minehut names them).
- Tebex's secret key never goes in the repo. The site only uses the public token.

## Player stats

The site shows each player's stats (escapes, arrests, robberies and so on) and leaderboards.

### How the data gets to the site

`core` writes stats to **PostgreSQL** on the VPS as they happen (in batches, off the main thread). The website reads them through the **API** at `api.mostwantedmc.com` ([below](#accounts-and-the-api)), the same database the game uses: no copy, no sync. Leaderboards and player pages are cached at Cloudflare for a minute or two, so most visits never reach the VPS. If the VPS is down, the site stays up and these pages say so.

### What we track

| Stat | Who |
| --- | --- |
| Escapes, fastest escape, escape routes used | Prisoners |
| Times arrested, longest time free | Criminals |
| Robberies (per location), cash stolen, highest bounty | Criminals |
| Arrests made, bounty collected, escapes stopped | Guards |
| Kills and deaths, split by role | Everyone |
| Riots started / joined / stopped | Inmates, guards |
| Playtime, split by role; time on duty | Everyone, guards |
| First joined, last seen | Everyone |

Each stat is a counter or a record in PostgreSQL, keyed by **UUID** (the player's latest name is stored next to it, so name changes don't lose stats).

### Leaderboards

- **All-time, monthly and weekly.** `core` keeps a counter per stat per period, so weekly boards reset cleanly without recounting.
- Boards: **Most Wanted** (highest bounty right now), **Top Escapers**, **Fastest Escapes**, **Top Guards** (arrests), **Biggest Heists**, **Richest** (cash earned), **Most Time Served**.
- On the website now: **Most Wanted**, **Top Escapers**, **Top Guards**, **Top Robbers**, **Bounty Hunters** (bounty collected) and **Most Arrested**, the boards the API serves (`backend/.../stats/Board.java`). Fastest Escapes, Biggest Heists, Richest and Most Time Served join when core records them.
- In-game holograms (FancyHolograms) show the same boards, read from PostgreSQL.

### Player pages

- `/player/<name>`: their head (skin), current role, rank, all stats, records, and where they place on each board.
- **Skins:** from a public skin service by UUID. Bedrock players coming through Minehut may not have a Java skin; show a default head for them.
- **Opt-out:** a `/stats private` command hides a player's page and removes them from the website boards (not from in-game ones).
- Staff in vanish, and banned players, don't appear.

## Accounts and the API

Decided 2026-10-26. **Players have website accounts**, and everything tied to a person lives there: support tickets, applications, purchases, linked accounts. People without Discord can get support, and people without the website can use Discord or the game.

**Signing in, no passwords:**
- **Discord:** "Sign in with Discord" (the same Discord app as the bot, scope `identify` only).
- **Minecraft:** `/website` in game shows a 6-character code to enter on the site. Works on Bedrock, where chat links can't be clicked. (Microsoft sign-in needs Mojang to approve our app; not worth the wait.)
- Signed in one way, link the other from your account page. No passwords or email means nothing to reset or leak.

**Your account page:** tickets and replies, applications and their status, purchases (from Tebex), linked accounts, stats. A "delete my account" button.

**Support and applications can start anywhere, and all land on the website:**

| From | How |
| --- | --- |
| Website | The support and apply forms |
| Discord | `/support`, `/apply`, or the buttons in 🎫│support and 📝│apply. An account is made from the Discord user if needed |
| Game | `/support <message>`. An account is made from the Minecraft player if needed |

**Notifications** (a staff reply, an application decision) arrive on the site, as a Discord DM if linked, and in game (immediately if online, otherwise on next join).

**Staff panel:** a staff-only part of the site for tickets, applications and punishments. Access comes from the player's in-game rank.

### The API

```
Browser ──▶ mostwantedmc.com (Astro on a Cloudflare Worker)
   └──────▶ api.mostwantedmc.com (proxied by Cloudflare) ──▶ VPS: backend (Java)
                                                                  │
                                         core (game) ──▶ PostgreSQL ◀── backend
```

- **One Java app, `backend`:** the API and the Discord bot together ([Discord](10-discord-bot.md)). It shares the `common` module with `core`, so the API, bot and game use the same tables. Its own Raptor server: game restarts don't touch it.
- **One database:** PostgreSQL holds accounts, links, tickets, applications, purchases, stats and game data. When staff reply, `core` and the bot hear about it instantly (`LISTEN/NOTIFY`) and tell the player in game and on Discord.
- **Reaching the VPS without exposing it:** `api.mostwantedmc.com` is a **proxied** DNS record (Cloudflare's IP shows, not ours), and:
  1. The firewall allows port 443 **only from [Cloudflare's IP ranges](https://www.cloudflare.com/ips/)**, refreshed daily by a small job. Scanners can't connect, so they can't link our certificate to our IP.
  2. A **Cloudflare Origin CA** certificate on the VPS, with SSL mode **Full (strict)**.
  3. **Authenticated Origin Pulls:** Cloudflare presents its client certificate and the VPS rejects anything without it, so other Cloudflare customers can't reach us through Cloudflare either.
- **Sessions:** cookies for `.mostwantedmc.com` that scripts can't read (`HttpOnly`, `Secure`, `SameSite=Lax`). The site calls the API cross-origin, so the API only allows `https://mostwantedmc.com`.
- **Tebex** sends purchases to `api.mostwantedmc.com/tebex`; the backend checks each one's signature.
- **Load can't lag the game:** Cloudflare rate-limits the API, Turnstile guards every form, the API rate-limits per user and IP, and public pages are cached.

Before sign-in goes live: **Terms** and **Privacy** pages on the site (Discord shows them when signing in, Tebex requires them). Collect as little as possible: many players are kids.

## Protecting the interactive parts

Decided 2026-10-26. **Bot Fight Mode is off** on mostwantedmc.com (and xenastudios.co). On the free plan it challenges every non-browser request with no way to exempt a path, which would break Tebex purchase webhooks, Discord sign-in and server-list vote callbacks. Its script was also blocked by our CSP, so it did nothing.

What protects the site instead, all on Cloudflare's free plan:

| Threat | Protection |
| --- | --- |
| DDoS | Cloudflare's DDoS protection, always on |
| Common attacks | Cloudflare's free managed WAF ruleset, on |
| Spam on forms (sign-in, apply, support) | **Turnstile** on each form, when the forms are built |
| Flooding the API | A Cloudflare **rate-limiting rule** on `api.mostwantedmc.com`, plus per-user limits in the API |
| Reaching the VPS directly | Firewall to Cloudflare's IPs only, Origin CA certificate, Authenticated Origin Pulls ([above](#the-api)) |
| Fake purchases | Tebex webhooks are **signature-checked** |
| Account abuse | **Discord sign-in** for anything tied to an account |

## What we sell

**Cosmetics only** (Mojang's EULA): no gameplay advantages.

| ✅ OK | ❌ Not OK |
| --- | --- |
| Jumpsuit / uniform colours | Guns, ammo, armor |
| Gun skins (same stats) | In-game cash |
| Trails, particles, arrest/escape effects | Shorter jail time, faster escapes |
| Chat tags, name colours | Getting the guard rank |
| Cell decorations | Anything that wins fights or robberies |
| Supporter ranks with cosmetic perks only | Loot boxes / random rewards (also against Minehut's no-gambling rule) |

## Checked

- ✅ Minehut allows an external server to run its own store (confirmed with the user, 2026-10-25).
- ✅ Tebex fees are acceptable (confirmed with the user, 2026-10-25).

## Still to do

- [x] Pick the server name: **MostWanted**
- [ ] Register **mostwantedmc.com** (Cloudflare Registrar)
- [ ] Create the Tebex store and its packages
- [ ] Build the site (separate PR)
- [ ] Add Tebex to the [server stack](07-server-stack.md) once the store exists
