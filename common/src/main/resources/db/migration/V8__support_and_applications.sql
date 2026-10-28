-- Support tickets, applications and notifications (docs/08-website.md#accounts-and-the-api).
-- All belong to a website account and go with it when it's deleted.

-- A conversation between a player and staff.
CREATE TABLE mostwanted.tickets (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT       NOT NULL REFERENCES mostwanted.accounts ON DELETE CASCADE,
    category   VARCHAR(16)  NOT NULL CHECK (category IN ('report', 'appeal', 'bug', 'store', 'other')),
    subject    VARCHAR(100) NOT NULL,
    -- open: waiting for staff; answered: waiting for the player; closed.
    status     VARCHAR(16)  NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'answered', 'closed')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE mostwanted.ticket_messages (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  BIGINT        NOT NULL REFERENCES mostwanted.tickets ON DELETE CASCADE,
    -- Who wrote it; kept as a name too, so a reply still shows its author after they're gone.
    account_id BIGINT        REFERENCES mostwanted.accounts ON DELETE SET NULL,
    author     VARCHAR(64)   NOT NULL,
    staff      BOOLEAN       NOT NULL,
    body       VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE mostwanted.applications (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT        NOT NULL REFERENCES mostwanted.accounts ON DELETE CASCADE,
    kind       VARCHAR(16)   NOT NULL CHECK (kind IN ('guard', 'staff', 'creator')),
    -- The form's answers, by question id.
    answers    JSONB         NOT NULL,
    status     VARCHAR(16)   NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'denied')),
    -- What staff told the applicant, and who decided.
    note       VARCHAR(1000),
    decided_by VARCHAR(64),
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Something to tell a player: on the site, by Discord DM if linked, and in game.
CREATE TABLE mostwanted.notifications (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id       BIGINT       NOT NULL REFERENCES mostwanted.accounts ON DELETE CASCADE,
    text             VARCHAR(300) NOT NULL,
    -- A path on the website, like /ticket?id=12.
    link             VARCHAR(200),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at          TIMESTAMPTZ,
    shown_in_game_at TIMESTAMPTZ
);

-- Ranks for core to give in game: an accepted guard application. Only guard, ever.
CREATE TABLE mostwanted.rank_grants (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid       UUID        NOT NULL REFERENCES mostwanted.players ON DELETE CASCADE,
    rank       VARCHAR(16) NOT NULL CHECK (rank = 'guard'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    done_at    TIMESTAMPTZ
);

/* [jooq ignore start] */
CREATE INDEX tickets_account ON mostwanted.tickets (account_id);
CREATE INDEX tickets_status ON mostwanted.tickets (status, updated_at);
CREATE INDEX ticket_messages_ticket ON mostwanted.ticket_messages (ticket_id, id);
CREATE INDEX applications_account ON mostwanted.applications (account_id);
CREATE INDEX applications_status ON mostwanted.applications (status, created_at);
CREATE INDEX notifications_account ON mostwanted.notifications (account_id, id DESC);
CREATE INDEX notifications_game ON mostwanted.notifications (account_id) WHERE shown_in_game_at IS NULL;
CREATE INDEX rank_grants_pending ON mostwanted.rank_grants (id) WHERE done_at IS NULL;
/* [jooq ignore stop] */
