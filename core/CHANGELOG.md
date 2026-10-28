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
- Banking: criminals' cash is banked when they walk into a hideout.
- Robberies: every robbery zone opens, is robbed by standing inside with the alarm going off for guards, pays out split between the robbers with a bonus per guard on duty, then closes and reopens. A red HEIST section on the scoreboard while one is on.
- `/stats private` and `/stats public`.
- Server log files older than 90 days and game log entries older than 12 months are deleted, as the privacy page promises.
- `/website`: a one-time code to sign in on the website or link accounts.
- Guns: pistol, shotgun, rifle and the guards' taser, hitscan, with magazines and auto-reload from ammo. Arrests confiscate them. `/mw give` for testing.
- Shops: `/shop` opens the hideout shop (criminals) or the armory (guards) as a chest menu; prices from cash, then bank.
- Escape routes: breakable walls that grow back, and keycard gates; guards carry a keycard that can drop when they die.
- Riots: inmates press the alarm together; guards and inmates fight in the prison until time runs out or every rioter is caught.
- The resource pack: sent on join, with vanilla-style textures for every item of ours. `pack-models` in `guns.yml` is gone (items are always tagged).
- Fixed: `config.yml` (and its server template) had lost every section but the last one added, so the shipped default couldn't start the plugin. `/duty` messages showed their keys, because YAML reads a key named `on` or `off` as true or false.
- Store ranks and cosmetics: rank tags in the tab list, trails, escape and arrest effects, gun skins, chat colours, coloured names, joins announced, `/joinmessage`, all from LuckPerms permissions. `/cosmetics` to choose them. `/mw boost <player>` for the Server Boost.
