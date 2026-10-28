-- What each player chose in the settings menu. All on by default; stats_private
-- (V1) is the fourth setting.
ALTER TABLE mostwanted.players
    -- The sidebar.
    ADD COLUMN show_scoreboard BOOLEAN NOT NULL DEFAULT TRUE,
    -- Chat broadcasts: robberies opening and robbed, riots, escapes, arrests. Guards' alarms aren't these.
    ADD COLUMN show_broadcasts BOOLEAN NOT NULL DEFAULT TRUE,
    -- Store ranks' join announcements and join messages.
    ADD COLUMN show_joins BOOLEAN NOT NULL DEFAULT TRUE;
