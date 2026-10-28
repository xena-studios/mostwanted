# MostWanted (core)

The game plugin: every system the server runs. What it will do is in [what we build](../docs/04-tech.md#what-we-build-ourselves).

Runs on Paper 26.3 and Leaf 26.3, Java 25, with PostgreSQL.

## What's in it so far

- **Player state:** role (inmate, criminal, guard), cash, bank, bounty, jail time and stats, loaded as a player logs in and kept in memory. Changes are saved in the background every few seconds, and on quit and shutdown. If the database can't load someone, they're refused rather than playing on a blank state that would overwrite their real one.
- **Messages** from `messages.yml`, in the [message style](../docs/09-brand.md#messages): plain text there becomes small caps when it loads.
- **The scoreboard and tab list** from the [brand doc](../docs/09-brand.md#scoreboard-and-tab-list), redrawn only when something changes.
- **Admin commands** for testing, each written to the game log.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/mostwanted info` (`/mw info`) | `mostwanted.admin` | The plugin version, the server, and whether the database is connected |
| `/mw player <name>` | `mostwanted.admin` | An online player's role, cash, bank and bounty |
| `/mw set <name> role <inmate\|criminal\|guard>` | `mostwanted.admin` | Changes their role |
| `/mw set <name> cash\|bank\|bounty <amount>` | `mostwanted.admin` | Sets an amount |

## Config

`plugins/MostWanted/config.yml`: the database, how often to save, and the scoreboard clock's timezone. On the server it's rendered from [`server/files/plugins/MostWanted/config.yml.tmpl`](../server/files/plugins/MostWanted/config.yml.tmpl) with the password from `.env`. `messages.yml` holds every message.

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
