# Website

Decided 2026-10-25, built in [`website/`](../website/). Sign-in, accounts, player pages, leaderboards, Support and Apply are open since 2026-10-28, ahead of the game.

## Stack

| Piece | Choice |
| --- | --- |
| Site | **[Astro](https://astro.build)**, like [xenastudios.co](https://github.com/xena-studios/website) and the plugin docs |
| Hosting | **Cloudflare** (a Worker with static assets), free tier |
| Store | **[Tebex](https://tebex.io)**, through its [Headless API](https://docs.tebex.io/developers/headless-api/overview) |
| Delivery | `core` reads Tebex's command queue and runs each purchase's commands (no Tebex plugin; [store](11-store.md)) |
| Domain | **mostwantedmc.com**: the site at the root, `play.` for the server, `api.` for the backend |

## Pages

| Page | What's on it |
| --- | --- |
| **Home** | What the server is, explore cards, player search, how it plays, news |
| **News**, **Wiki** | Markdown in `website/src/content/` |
| **Store** | Ranks, cosmetics and support packages, with a cart and Tebex checkout ([store](11-store.md)) |
| **Leaderboards**, **Player pages** | Stats from the API ([player stats](#player-stats)) |
| **Rules**, **Staff**, **Creators** | Server rules (including guard conduct), the team, the creator program |
| **Apply**, **Support**, **Account**, **Ticket** | Signed-in forms and pages ([accounts](#accounts-and-the-api)) |
| **Panel** | Staff only: tickets and applications |
| **Login**, **Terms**, **Privacy** | Sign-in with Discord or a game code; the legal pages |

The header shows live whether the server is up and how many are playing (`/v1/status`) once `gameOpen` is set in `website/src/site.ts`. JavaScript is small and per page: the address copy button, player lookup, sign-in, the account pages and the store's cart. **Later:** a vote page, if we list on vote sites.

## How the store works

Built 2026-10-28. What's sold, why, and setting up Tebex are in [store](11-store.md).

```
Player on our site ──▶ Store page (our design, packages from Tebex's API)
                          │ add to cart, enter username
                          ▼
                    Tebex checkout (payment, tax, fraud, chargebacks)
                          │
                          ▼
              core on our server (asks Tebex) ──▶ runs commands
                                              e.g. lp user <name> parent add vip
                                                   mostwanted boost <name>
```

- `core` connects **out** to Tebex, so Minehut in front of the server doesn't matter.
- Bedrock players buy with the name they play under (check how Minehut names them).
- The site only uses Tebex's public token. The secret key never goes in the repo.

## Player stats

### How the data gets to the site

`core` writes stats to **PostgreSQL** as they happen (in batches, off the main thread). The site reads them through the **API** at `api.mostwantedmc.com` from the same database: no copy, no sync. Leaderboards and player pages are cached at Cloudflare for a minute or two, so most visits never reach the VPS. If the VPS is down, the site stays up and says so.

**In game too:** staff put boards up around the map with `/mw board add <board> [all|month|week]` (floating text, refreshed every minute, kept in `boards.yml`). Board ids match the website's: `most-wanted`, `escapes`, `guards`, `robberies`, `bounty-earned`, `most-arrested`. Private players are left off.

### What we track

| Stat | Who |
| --- | --- |
| Escapes, fastest escape, escape routes used | Prisoners |
| Times arrested, longest time free | Criminals |
| Robberies (per location), cash stolen, highest bounty | Criminals |
| Arrests made, bounty collected, escapes stopped | Guards |
| Kills and deaths, by role | Everyone |
| Riots started, joined or stopped | Inmates, guards |
| Playtime by role; time on duty | Everyone, guards |
| First joined, last seen | Everyone |

Each stat is a counter or record keyed by **UUID**, with the latest name stored next to it, so name changes keep stats.

### Leaderboards

- **All-time, monthly and weekly.** `core` keeps a counter per stat per period, so weekly boards reset cleanly.
- **Live now:** Most Wanted (bounty right now), Top Escapers, Top Guards (arrests), Top Robbers, Bounty Hunters (bounty collected) and Most Arrested. These are the API's boards (`common/.../Board.java`), shared with the game.
- **Later,** once `core` records them: Fastest Escapes, Biggest Heists, Richest (cash earned), Most Time Served.
- In game, the same boards show as floating text (`/mw board`) and in the `/leaderboards` menu.

### Player pages

- `/player?name=…`: their head, role, rank, stats, records, and where they place on each board.
- **Skins** come from a public skin service by UUID. Bedrock players may have no Java skin, so they get a default head.
- **Opt-out:** `/stats private` hides a player's page and removes them from every board, on the site and in game.
- Staff in vanish and banned players don't appear.

## Accounts and the API

Decided 2026-10-26. **Players have website accounts**, and everything tied to a person lives there: tickets, applications, purchases and linked accounts. Players without Discord can still get support, and players who never open the site can use Discord or the game.

**Signing in, no passwords:**

- **Discord:** "Sign in with Discord" (the bot's own Discord app, scope `identify` only).
- **Minecraft:** `/website` in game shows a 6-character code to type on the site. Works on Bedrock, where chat links can't be clicked. (Microsoft sign-in needs Mojang to approve our app; not worth the wait.)
- Signed in one way, link the other from your account page. No passwords or email means nothing to reset or leak.

**Support and applications all land on the website:**

| From | How | Built |
| --- | --- | --- |
| Website | The Support and Apply forms | ✅ |
| Discord | `/support`, `/apply`, or the buttons in 🎫│support and 📝│apply; an account is made from the Discord user if needed | Not yet |
| Game | `/support <message>`; an account is made from the player if needed | Not yet |

**What's built** (2026-10-28):

- **Support** (`/support`): pick what it's about (report, appeal, bug, purchase, other), a subject and what happened. Proof is links (YouTube, Imgur, Medal), no uploads. Up to 3 new tickets an hour. Each ticket has a page (`/ticket?id=…`) with the conversation, a reply box and Close.
- **Apply** (`/apply`): guard, staff (helper, moderator or builder) and creator forms. The questions come from the backend (`backend/.../support/Forms.java`, at `/v1/forms`). Needs Minecraft linked; one waiting per kind, and a week after a "no".
- **Account page:** notifications, tickets, applications (with staff's note), linked accounts, a link to the panel for staff, and "delete my account". Purchases from Tebex are planned.
- **Staff panel** (`/panel`): helpers and up (LuckPerms `helper`, `moderator`, `admin`, `owner` on the linked account) answer tickets; moderators and up accept or deny applications, with a note. Accepting a **guard** gives the rank in game at once (`core` runs `lp user <uuid> parent add guard`, from `rank_grants`, guard only). Staff and creator ranks are given by hand.
- **Notifications** (a staff reply, a closed ticket, a decision) go on the account page, as a Discord DM if linked, and in game (`✉` in chat, at once or on next join).
- **Not yet:** punishments in the panel.

### The API

```
Browser ──▶ mostwantedmc.com (Astro on a Cloudflare Worker)
   └──────▶ api.mostwantedmc.com (proxied by Cloudflare) ──▶ VPS: backend (Java)
                                                                  │
                                         core (game) ──▶ PostgreSQL ◀── backend
```

- **One Java app, `backend`,** holds the API and the Discord bot ([Discord](10-discord-bot.md)), sharing `common` with `core`. It's its own server in the panel, so game restarts don't touch it.
- **One database:** accounts, links, tickets, applications, purchases, stats and game data. When staff reply, `core` and the bot hear at once (`LISTEN/NOTIFY`) and tell the player.
- **Sessions:** cookies for `.mostwantedmc.com` that scripts can't read (`HttpOnly`, `Secure`, `SameSite=Lax`). The API only accepts calls from `https://mostwantedmc.com`.
- **Load can't lag the game:** Cloudflare rate-limits the API, the API rate-limits per user and IP, and public pages are cached.

**Reaching the VPS without exposing it.** `api.mostwantedmc.com` is a **proxied** record (Cloudflare's IP shows, not ours), and:

1. **A Cloudflare Origin CA certificate** on the VPS, with SSL mode **Full (strict)**.
2. **Authenticated Origin Pulls:** the API only accepts TLS connections that present Cloudflare's client certificate, refused in the handshake before any request is read ([backend/README.md](../backend/README.md#https-and-authenticated-origin-pulls)). Cloudflare's certificate is the same for every customer, so it proves a request came through Cloudflare, not through *our* zone. Uploading our own client certificate to the zone closes that; it's a follow-up after launch ([launch](12-launch.md#6-cloudflare-for-the-api-10-min)).
3. **Port 8443,** because 443 is the panel's. A Cloudflare Origin Rule sends `api` traffic there, so visitors still use 443.

A netcup firewall rule limiting 8443 to [Cloudflare's IP ranges](https://www.cloudflare.com/ips/) would hide it from scanners too, once it's worth the upkeep.

## Protecting the interactive parts

Decided 2026-10-26. **Bot Fight Mode is off** on mostwantedmc.com (and xenastudios.co). On the free plan it challenges every non-browser request with no way to exempt a path, which breaks Tebex webhooks, Discord sign-in and vote callbacks. Our CSP blocked its script anyway.

What protects the site instead, all on Cloudflare's free plan:

| Threat | Protection |
| --- | --- |
| DDoS | Cloudflare's DDoS protection, always on |
| Common attacks | Cloudflare's free managed WAF ruleset |
| Form spam (sign-in, apply, support) | **Turnstile** on each form (not added yet) |
| Flooding the API | A Cloudflare **rate-limiting rule** on `api.mostwantedmc.com`, plus per-user limits in the API |
| Reaching the VPS directly | Origin CA certificate and Authenticated Origin Pulls ([above](#the-api)) |
| Fake purchases | Tebex webhooks are **signature-checked** |
| Account abuse | **Discord sign-in** or a game code for anything tied to an account |

## What we sell

**Cosmetics only** (Mojang's EULA). The full catalogue is in [store](11-store.md).

| ✅ OK | ❌ Not OK |
| --- | --- |
| Jumpsuit and uniform colours | Guns, ammo, armor |
| Gun skins (same stats) | In-game cash |
| Trails, particles, arrest and escape effects | Shorter jail time, faster escapes |
| Chat tags, name colours | The guard rank |
| Cell decorations | Anything that wins fights or robberies |
| Supporter ranks with cosmetic perks only | Loot boxes or random rewards (also against Minehut's rules) |

Checked 2026-10-25: Minehut allows an external server to run its own store, and Tebex's fees are acceptable.

## Still to do

- [ ] Create the Tebex store and its packages ([launch, step 11](12-launch.md#11-tebex))
- [ ] Turnstile on the forms
- [ ] Support and applications from Discord and the game
- [ ] Punishments in the staff panel
