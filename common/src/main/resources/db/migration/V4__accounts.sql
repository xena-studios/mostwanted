-- Website accounts (docs/08-website.md#accounts-and-the-api): signed in with Discord
-- or with a code from /website in game, no passwords. Either side can be linked later.
CREATE TABLE mostwanted.accounts (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Discord's user ID (a snowflake), and their name when they last signed in.
    discord_id     VARCHAR(20) UNIQUE,
    discord_name   VARCHAR(64),
    minecraft_uuid UUID        UNIQUE REFERENCES mostwanted.players ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Signed-in browsers. Only a hash of each token is kept, so a copy of this table
-- can't be used to sign in. Sessions last 30 days (the privacy page says so).
CREATE TABLE mostwanted.sessions (
    -- SHA-256 of the token, in hex.
    token_hash VARCHAR(64) PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES mostwanted.accounts ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- One-time codes from /website, to sign in or link on the site. Ten minutes each.
CREATE TABLE mostwanted.login_codes (
    code       VARCHAR(6)  PRIMARY KEY,
    uuid       UUID        NOT NULL REFERENCES mostwanted.players ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

/* [jooq ignore start] */
CREATE INDEX sessions_account ON mostwanted.sessions (account_id);
CREATE INDEX login_codes_uuid ON mostwanted.login_codes (uuid);
/* [jooq ignore stop] */
