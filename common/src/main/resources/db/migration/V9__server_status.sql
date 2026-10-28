-- The game server's player count, written by core every 30 seconds, for the Discord
-- bot's status. One row; a stale updated_at means the server is down.
CREATE TABLE mostwanted.server_status (
    id          SMALLINT    PRIMARY KEY CHECK (id = 1),
    online      INTEGER     NOT NULL,
    max_players INTEGER     NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
