-- Areas of the map the game reacts to: the prison boundary, cells, safe zones,
-- hideouts and robbery spots. Staff mark each one with a FAWE selection (/mw zone).
-- Each is a box; overlapping is fine, such as cells inside the prison.
CREATE TABLE mostwanted.zones (
    -- Lowercase, used in commands: "prison", "bank", "cell-1".
    name        VARCHAR(32) PRIMARY KEY,
    kind        VARCHAR(16) NOT NULL
                CHECK (kind IN ('prison', 'cell', 'police', 'hideout', 'robbery', 'safe')),
    world       VARCHAR(64) NOT NULL,
    -- Block coordinates, inclusive.
    min_x       INTEGER     NOT NULL,
    min_y       INTEGER     NOT NULL,
    min_z       INTEGER     NOT NULL,
    max_x       INTEGER     NOT NULL,
    max_y       INTEGER     NOT NULL,
    max_z       INTEGER     NOT NULL,
    -- Where players are sent in this zone, such as a cell for an arrest. Optional.
    spawn_x     DOUBLE PRECISION,
    spawn_y     DOUBLE PRECISION,
    spawn_z     DOUBLE PRECISION,
    spawn_yaw   REAL,
    spawn_pitch REAL,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (min_x <= max_x AND min_y <= max_y AND min_z <= max_z),
    CHECK ((spawn_x IS NULL) = (spawn_y IS NULL) AND (spawn_y IS NULL) = (spawn_z IS NULL)
        AND (spawn_z IS NULL) = (spawn_yaw IS NULL) AND (spawn_yaw IS NULL) = (spawn_pitch IS NULL))
);
