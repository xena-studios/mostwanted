# Website and store

Decided 2026-10-25.

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

```
Leaf server (OVH VPS)                       Cloudflare
┌───────────────────────┐   every ~2 min   ┌────────────────────────────┐
│ core plugin           │   HTTPS POST     │ Worker: /api/ingest        │
│  counts stats as they │ ───────────────▶ │  checks the secret token   │
│  happen               │   (outbound)     │  writes to D1 (SQLite)     │
│        │              │                  │        │                   │
│        ▼              │                  │        ▼                   │
│ PostgreSQL            │                  │ Website reads D1:          │
│  (source of truth)    │                  │  /player/<name>            │
└───────────────────────┘                  │  /leaderboards             │
                                           └────────────────────────────┘
```

- **The game server pushes; the website never calls the game server.** The VPS only makes outbound requests (like the Tebex plugin), so its IP stays hidden behind Minehut and a busy website can't lag the game.
- **PostgreSQL on the VPS is the source of truth.** `core` writes stats there as they happen. Cloudflare **D1** holds a copy for the website.
- **Sync:** every couple of minutes, `core` sends the stats that changed since the last successful sync (and once more on shutdown). If Cloudflare is unreachable, it keeps the changes and sends them next time. Nothing is lost; the site is just a few minutes behind.
- **Auth:** the ingest endpoint only accepts requests signed with a secret shared between the server and the Worker. The secret lives in the server's config and Cloudflare's secrets, never in this repo.
- **Cost:** the site already runs on Cloudflare; Workers and D1's free tiers should cover a server our size. Check the current limits when building.
- **Live bits** (who's online, today's top bounties) push on the same channel, every minute.

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
- In-game holograms (FancyHolograms) show the same boards, read from PostgreSQL.

### Player pages

- `/player/<name>`: their head (skin), current role, rank, all stats, records, and where they place on each board.
- **Skins:** from a public skin service by UUID. Bedrock players coming through Minehut may not have a Java skin; show a default head for them.
- **Opt-out:** a `/stats private` command hides a player's page and removes them from the website boards (not from in-game ones).
- Staff in vanish, and banned players, don't appear.

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
