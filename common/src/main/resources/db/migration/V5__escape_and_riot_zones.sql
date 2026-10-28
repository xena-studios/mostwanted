-- Three more zone kinds: weak walls inmates can break, gates a keycard opens, and
-- the riot alarm inmates press together. Only the CHECK changes, so jOOQ's classes
-- don't (and its simulator can't drop a constraint by PostgreSQL's name for it).
/* [jooq ignore start] */
ALTER TABLE mostwanted.zones DROP CONSTRAINT zones_kind_check;
ALTER TABLE mostwanted.zones ADD CONSTRAINT zones_kind_check
    CHECK (kind IN ('prison', 'cell', 'police', 'hideout', 'robbery', 'safe', 'wall', 'gate', 'alarm'));
/* [jooq ignore stop] */
