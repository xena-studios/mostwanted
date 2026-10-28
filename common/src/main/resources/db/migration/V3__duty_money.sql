-- Guards keep their money apart from their inmate and criminal money. Going on or
-- off duty swaps cash and bank with these, so neither side can fund the other.
ALTER TABLE mostwanted.players
    ADD COLUMN stashed_cash BIGINT NOT NULL DEFAULT 0 CHECK (stashed_cash >= 0),
    ADD COLUMN stashed_bank BIGINT NOT NULL DEFAULT 0 CHECK (stashed_bank >= 0);
