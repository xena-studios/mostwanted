-- The core loop's tables: who played, their game state, their stats, and what happened.
-- Everything lives in the mostwanted schema, apart from LuckPerms' and LibertyBans' tables.
-- Never edit a merged migration: add the next V<n>__ file instead.
-- jOOQ reads these files to generate the table classes. It can't parse every index,
-- and indexes don't change the classes, so they sit between [jooq ignore] markers.

CREATE SCHEMA IF NOT EXISTS mostwanted;

-- One row per player who ever joined, keyed by UUID (Bedrock players through Minehut too).
-- Gameplay state is loaded on join and written back in batches, off the main thread.
CREATE TABLE mostwanted.players (
    uuid          UUID        PRIMARY KEY,
    -- Their latest name, so a name change keeps their stats.
    name          VARCHAR(64) NOT NULL,
    bedrock       BOOLEAN     NOT NULL DEFAULT FALSE,
    role          VARCHAR(16) NOT NULL DEFAULT 'inmate'
                  CHECK (role IN ('inmate', 'criminal', 'guard')),
    -- Money is whole dollars. Unbanked cash is lost on arrest; banked cash is safe.
    cash          BIGINT      NOT NULL DEFAULT 0 CHECK (cash >= 0),
    bank          BIGINT      NOT NULL DEFAULT 0 CHECK (bank >= 0),
    bounty        BIGINT      NOT NULL DEFAULT 0 CHECK (bounty >= 0),
    -- Set while serving jail time; NULL when free.
    jailed_until  TIMESTAMPTZ,
    -- /stats private: hidden from the website, not from in-game boards.
    stats_private BOOLEAN     NOT NULL DEFAULT FALSE,
    first_joined  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Player pages look players up by name.
/* [jooq ignore start] */
CREATE INDEX players_name ON mostwanted.players (lower(name));
/* [jooq ignore stop] */
-- The Most Wanted board.
/* [jooq ignore start] */
CREATE INDEX players_bounty ON mostwanted.players (bounty DESC) WHERE bounty > 0;
/* [jooq ignore stop] */

-- One counter (or record, such as the fastest escape) per player, stat and period.
-- period is 'all', a month ('2026-10') or an ISO week ('2026-W44'), so weekly and
-- monthly boards start fresh without recounting anything.
CREATE TABLE mostwanted.stats (
    uuid   UUID        NOT NULL REFERENCES mostwanted.players ON DELETE CASCADE,
    stat   VARCHAR(64) NOT NULL,
    period VARCHAR(8)  NOT NULL,
    value  BIGINT      NOT NULL,
    PRIMARY KEY (uuid, stat, period)
);

-- Leaderboards: the top values of one stat in one period.
/* [jooq ignore start] */
CREATE INDEX stats_board ON mostwanted.stats (stat, period, value DESC);
/* [jooq ignore stop] */

-- Arrests, robberies, riots, purchases and staff actions, for staff and the website.
-- details holds what's specific to each kind, such as the location robbed or the payout.
CREATE TABLE mostwanted.game_log (
    id      BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    kind    VARCHAR(64) NOT NULL,
    -- Who did it and who it happened to, when there was someone.
    actor   UUID,
    target  UUID,
    details JSONB       NOT NULL DEFAULT '{}'
);

/* [jooq ignore start] */
CREATE INDEX game_log_at ON mostwanted.game_log (at DESC);
CREATE INDEX game_log_actor ON mostwanted.game_log (actor, at DESC) WHERE actor IS NOT NULL;
CREATE INDEX game_log_target ON mostwanted.game_log (target, at DESC) WHERE target IS NOT NULL;
/* [jooq ignore stop] */
