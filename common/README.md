# Common

What `core` (the game) and `backend` (the API and Discord bot) share, so they can't disagree: the database tables, the connection pool, LISTEN/NOTIFY, and the message style. A library, not a plugin: `core` and `backend` bundle it.

```
src/main/resources/db/migration/   the tables, as Flyway migrations (V1__..., V2__...)
Database            the HikariCP pool; brings the schema up to date on start
DatabaseConfig      where PostgreSQL is; never prints the password
Notifications       LISTEN/NOTIFY: tells core and backend something changed, instantly
db/ (generated)     a jOOQ class per table, generated from the migrations
style/              the palette, message kinds (✔ ✖ ★ ⚑ ⓘ ✉), small caps and money format ($1,200)
```

## The database

All our tables live in the `mostwanted` schema of the shared PostgreSQL database, next to LuckPerms' and LibertyBans' tables. `Database.start` runs any new migrations before anything else touches the database, whichever of `core` or `backend` starts first. Both can start at once: Flyway locks the schema while it migrates.

```java
Database database = Database.start(DatabaseConfig.fromEnv(System.getenv(), 4), "backend");

database.sql().update(PLAYERS)
        .set(PLAYERS.CASH, PLAYERS.CASH.plus(payout))
        .where(PLAYERS.UUID.eq(uuid))
        .execute();
```

Queries are [jOOQ](https://www.jooq.org): SQL, checked by the compiler. The `PLAYERS` class is generated from the migrations at build time, so renaming or dropping a column breaks the build everywhere it's used, not the live server. Every query blocks: `core` runs them off the main thread.

### Changing the tables

1. Add the next migration: `src/main/resources/db/migration/V2__what_it_does.sql`. **Never edit a migration once it's merged**: servers that already ran it would refuse to start.
2. `./gradlew :common:build` regenerates the classes and runs the database tests.
3. A table arrives with the code that uses it, not before.

jOOQ reads the SQL by simulating it in H2, which can't parse every PostgreSQL index. Indexes don't change the generated classes, so wrap them in `/* [jooq ignore start] */` … `/* [jooq ignore stop] */`. Use `VARCHAR(n)` rather than `TEXT` for columns in a key or index (H2 can't index `TEXT`; in PostgreSQL they cost the same).

## Notifications

A notification is only a nudge: what happened is always in a table too. Publish in the same transaction as the change, so it's only sent once the change is committed:

```java
database.sql().transaction(tx -> {
    // ...insert the staff reply...
    Notifications.publish(tx.dsl(), "ticket_reply", String.valueOf(ticketId));
});
```

A listener reconnects by itself if PostgreSQL restarts. Notifications sent while it was away are lost, so the `onConnect` callback, which runs after every (re)connect, catches up from the tables.

## Tests

`./gradlew :common:test`. The database tests run against a real PostgreSQL 18 in Docker ([Testcontainers](https://testcontainers.com)); without Docker they're skipped, and CI always runs them.
