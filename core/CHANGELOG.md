# MostWanted changelog

## Unreleased

- The plugin skeleton: `/mostwanted info` (alias `/mw`), permission `mostwanted.admin`.
- Player state in PostgreSQL through `common`: role, cash, bank, bounty, jail time and stats, loaded on login and saved in the background.
- The scoreboard and tab list, and messages from `messages.yml` in small caps.
- `/mw player` and `/mw set` for testing, written to the game log.
- Zones marked with a FAWE selection (`/mw zone`), stored in PostgreSQL: prison, cells, police station, hideouts, robbery spots and safe zones. Walking into one shows its name.
- Escaping: an inmate who leaves the prison becomes a criminal, and everyone is told.
- Fixed: new messages added to an existing section of `messages.yml` showed their key instead of the text.
