# MostWanted (core)

The game plugin: every system the server runs. What it will do is in [what we build](../docs/04-tech.md#what-we-build-ourselves).

Runs on Paper 26.3 and Leaf 26.3, Java 25, with PostgreSQL.

## What's in it so far

- **Player state:** role (inmate, criminal, guard), cash, bank, bounty, jail time and stats, loaded as a player logs in and kept in memory. Changes are saved in the background every few seconds, and on quit and shutdown. If the database can't load someone, they're refused rather than playing on a blank state that would overwrite their real one.
- **Messages** from `messages.yml`, in the [message style](../docs/09-brand.md#messages): plain text there becomes small caps when it loads.
- **The scoreboard and tab list** from the [brand doc](../docs/09-brand.md#scoreboard-and-tab-list), redrawn only when something changes.
- **Zones:** boxes marked with a FAWE selection and stored in PostgreSQL, found by chunk as players move. Each has a kind:

  | Kind | What it does |
  | --- | --- |
  | `prison` | The prison's boundary. An inmate who gets outside every prison zone has escaped and becomes a criminal. |
  | `cell` | Where arrested players serve their sentence. Leaving one mid-sentence puts you back. |
  | `police` | The police station: safe for guards. |
  | `hideout` | A criminal base: safe for criminals, and where they bank cash. |
  | `robbery` | A place to rob, such as the bank. Its zone name picks its settings in `config.yml`. |
  | `safe` | No fighting at all. |
| `wall` | A weak spot. Inmates (or criminals breaking in) punch a block 20 times in a row to break it; guards are alerted; it grows back after 2 minutes. |
| `gate` | Bars or doors that open for 5 seconds when a guard, or anyone holding a keycard, right-clicks them. |
| `alarm` | The riot alarm (riots come next). |

  Zones can overlap, such as cells inside the prison. Walking into one shows its name above the hotbar. Set a spawn (`/mw zone spawn`) on the prison, the police station and each cell: that's where players are sent.
- **Combat:** guards and criminals fight; nobody else can hurt anyone (riots will change that). Nobody fights in a `safe` zone, guards are safe at the `police` station and criminals in a `hideout`, and neither can attack out of one.
- **Arrests:** guards on duty carry handcuffs. One right-click on a criminal within 3 blocks arrests them for their whole bounty; killing them pays half; a criminal who dies any other way is arrested with nobody paid. They lose their unbanked cash and serve 1 minute plus 1 per $2,500 of bounty, up to 5, in the emptiest `cell`, then go to the `prison` spawn.
- **Bounty:** +$500 for killing a guard on duty, and more for each robbery.
- **Banking:** a criminal's cash is banked the moment they walk into a `hideout`. Unbanked cash is lost on arrest.
- **Robberies:** every `robbery` zone is a place to rob. A criminal walking into an open one starts it, and the alarm goes off for guards on duty. The robbers must stay inside until the time is up (a red bar fills); if they all leave or are caught, it fails. The payout is split between the robbers and grows with the guards on duty; each robber gets bounty. Then it closes for a while and reopens with an announcement. While one is on, everyone's scoreboard has a red HEIST section. Times and payouts per spot are in `config.yml` under `robberies`.
- **Duty:** `/duty` swaps between guard and inmate, each with their own cash and bank. Guards respawn at the `police` spawn, inmates at the `prison` spawn.
- **Guns** (`guns.yml`): a pistol, shotgun, rifle and the guards' taser. Right-click to shoot. Shots are hitscan (they land along where you look, instantly), so they feel the same on Java and Bedrock. Hits go through the combat rules: safe zones hold, and kills count as arrests or bounty as usual. Headshots do 1.5×. Each gun keeps its loaded rounds on the item and reloads by itself from ammo in your inventory. The taser does no damage but slows a criminal hard for 3 seconds, so they can be cuffed; guards get it on duty, and it can't be dropped. An arrest takes a criminal's guns and ammo.
- **Shops** (`shops.yml`): chest menus, the same on Java and Bedrock. `/shop` opens the one for where you stand: the **hideout shop** for criminals in a hideout, the **armory** for guards at the police station. Put a FancyNpcs NPC in each that runs `shop` for the player when clicked. Prices come out of cash first, then the bank. Guns, ammo, armor and food; guard gear is never sold.
- **Escape routes:** breakable `wall` zones and keycard `gate` zones (above). Guards carry a keycard on duty and can't hand it over, but it drops half the time when they die; an inmate using one alerts the guards. Arrests take keycards. Broken walls are put back on shutdown, and after a crash on the next start. Vents and sewers are map building: wooden trapdoors open by hand in adventure mode. Numbers in `config.yml` under `escapes`.
- **Privacy:** `/stats private` hides a player from the website and Discord. Server log files older than 90 days and game log entries older than 12 months are deleted at startup and daily, as the [privacy page](https://mostwantedmc.com/privacy) promises (`retention` in `config.yml`).
- **Admin commands** for testing, each written to the game log.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/mostwanted info` (`/mw info`) | `mostwanted.admin` | The plugin version, the server, and whether the database is connected |
| `/mw player <name>` | `mostwanted.admin` | An online player's role, cash, bank and bounty |
| `/mw set <name> role <inmate\|criminal\|guard>` | `mostwanted.admin` | Changes their role |
| `/mw set <name> cash\|bank\|bounty <amount>` | `mostwanted.admin` | Sets an amount |
| `/stats private` · `public` | everyone | Hides your stats from the website and Discord, or shows them again. In-game boards still show you. |
| `/shop` | everyone | Opens the shop where you stand (hideout for criminals, armory for guards) |
| `/website` | everyone | A one-time code (10 minutes) to sign in on mostwantedmc.com or link this Minecraft account. Typed, not clicked, so it works on Bedrock. |
| `/duty` | `mostwanted.guard` | Goes on duty as a guard (from the prison, as an inmate) or off duty |
| `/mw give <name> gun\|ammo <id> [amount]` | `mostwanted.admin` | Gives a gun or ammo from `guns.yml`, for testing |
| `/mw zone create <name> <kind>` | `mostwanted.admin` | Makes a zone from your FAWE selection (`//wand`, then click two corners). Any shape becomes the box around it. |
| `/mw zone redefine <name>` | `mostwanted.admin` | Moves a zone to your current selection, keeping its kind and spawn |
| `/mw zone spawn <name>` | `mostwanted.admin` | Sets where players are sent in the zone (such as a cell) to where you're standing |
| `/mw zone tp <name>` | `mostwanted.admin` | Takes you to the zone's spawn, or the middle of it |
| `/mw zone list` · `here` · `delete <name>` | `mostwanted.admin` | Lists zones, shows the ones you're in, deletes one |

## Config

`plugins/MostWanted/config.yml`: the database, how often to save, the scoreboard clock's timezone, the gameplay numbers under `rules` (sentence length, kill share, guard-kill bounty, cuff range, combat tag), and each robbery spot under `robberies`. On the server it's rendered from [`server/files/plugins/MostWanted/config.yml.tmpl`](../server/files/plugins/MostWanted/config.yml.tmpl) with the password from `.env`. `messages.yml` holds every message, `guns.yml` every gun and ammo type, and `shops.yml` what each shop sells.

FAWE is optional: without it the game runs, but zones can't be marked out.

The database libraries (jOOQ, Flyway, HikariCP, the PostgreSQL driver) aren't in the jar: Paper downloads them from Maven Central's mirror on first start (`MostWantedLoader`). The jar bundles only [`common`](../common/).

## Building and running

```sh
./gradlew :core:build       # core/build/libs/core-<version>.jar
```

It needs PostgreSQL to start. For a local one:

```sh
docker run --rm -d --name mw-pg -p 5432:5432 -e POSTGRES_USER=mostwanted \
  -e POSTGRES_PASSWORD=boot-test postgres:18-alpine
./gradlew :core:runServer   # then put core/boot/config.yml in core/run/plugins/MostWanted/
```

The boot test uses the same database: `scripts/boot-test.sh --checks core/boot-checks.txt core/build/libs/core-*.jar`.
