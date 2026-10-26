# Website and store

Decided 2026-10-25.

## Stack

| Piece | Choice |
| --- | --- |
| Site | **[Astro](https://astro.build)** in [`website/`](../website/), same as [xenastudios.co](https://github.com/xena-studios/website) and the plugin docs |
| Hosting | **Cloudflare** (Workers static assets), free tier |
| Store | **[Tebex](https://tebex.io)**, through its [Headless API](https://docs.tebex.io/developers/headless-api/overview) |
| Delivery | The **Tebex plugin** on the game server runs the commands for each purchase |
| Domain | ⏳ waiting on the server name |

## Pages

| Page | What's on it |
| --- | --- |
| **Home** | What the server is, a trailer/screenshots, **how to join** (Java and Bedrock addresses), Discord link |
| **Store** | Packages from Tebex, styled like the rest of the site. Cart and checkout handled by Tebex |
| **Rules** | Server rules, including guard conduct |
| **Become a guard** | What guards do, requirements, and a link to the Discord application |
| **Vote** | Server list links, and what voting gives you |
| **Status** | Online or not, player count |
| **Leaderboards** (later) | Escapes, arrests, cash. Needs stats from the `core` plugin |

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
                                                   prisonbreak cosmetic give <name> <id>
```

- The Tebex plugin connects **out** to Tebex, so Minehut sitting in front of the server doesn't matter.
- Bedrock players buy with the username they play under (check how Minehut names them).
- Tebex's secret key never goes in the repo. The site only uses the public token.

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

- [ ] Pick the server name, then buy the domain
- [ ] Create the Tebex store and its packages
- [ ] Build the site (separate PR)
- [ ] Add Tebex to the [server stack](07-server-stack.md) once the store exists
