# MostWanted changelog

## Unreleased

- The plugin skeleton: `/mostwanted info` (alias `/mw`), permission `mostwanted.admin`.
- Player state in PostgreSQL through `common`: role, cash, bank, bounty, jail time and stats, loaded on login and saved in the background.
- The scoreboard and tab list, and messages from `messages.yml` in small caps.
- `/mw player` and `/mw set` for testing, written to the game log.
- Zones marked with a FAWE selection (`/mw zone`), stored in PostgreSQL: prison, cells, police station, hideouts, robbery spots and safe zones. Walking into one shows its name.
- Escaping: an inmate who leaves the prison becomes a criminal, and everyone is told.
- Fixed: new messages added to an existing section of `messages.yml` showed their key instead of the text.
- Combat rules: guards and criminals fight, nobody else; safe zones, and each side safe at its own base.
- Arrests: handcuffs (one right-click), half the bounty for a kill, unbanked cash lost, a sentence in a cell of 1 minute plus 1 per $2,500 of bounty, up to 5.
- `/duty` for guards (`mostwanted.guard`), with guard money kept apart. +$500 bounty for killing a guard.
- Gameplay numbers in `config.yml` under `rules`.
