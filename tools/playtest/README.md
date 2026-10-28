# Playtest bots

Scripted bot players that play MostWanted on a throwaway local server, and check the game does what it should: what the bots are told, where they end up, their health and inventory, the blocks around them (checked on the server), and what the database holds.

```sh
cd tools/playtest
npm install
npm run playtest                 # every scenario, about 2 minutes
npm run playtest -- cuffs jail   # only those
```

Needs Java 25, Node 24 and Docker. It builds `core`, starts PostgreSQL in Docker on port 5433 and Paper (offline mode) on port 25580 in `.cache/playtest/`, builds a small arena, marks its zones in the database, and speeds every timer up (`CONFIG` in `run.mjs`). Everything it starts, it stops. CI runs it on pull requests that touch the game (`.github/workflows/playtest.yml`).

| Scenario | Checks |
| --- | --- |
| escape | An inmate who gets out of the prison is told they escaped and becomes a criminal |
| banking | A criminal's cash goes into the bank at a hideout |
| robbery | Standing in the bank starts a robbery, alarms the guard, and pays out cash and bounty |
| combat | A criminal can hurt a guard; an inmate can't, with no riot on |
| cuffs | Handcuffs arrest a criminal: told, put in the cell, bounty cleared, jailed |
| jail | Leaving the cell puts you back; the sentence ends on time |
| guns | A pistol shot hurts a criminal |
| taser | The taser slows without hurting |
| wall | Punching a weak wall breaks it, alerts guards, and it grows back |
| gate | *Skipped:* see below |
| shop | `/shop` in a hideout opens the menu; buying takes the money and gives the item |
| riot | Inmates pressing the alarm start a riot; inmates can then hurt guards |
| duty | `/duty` swaps guard and inmate money |
| cosmetics | `/cosmetics` refuses what you don't own, saves a trail and chat colour you do; `/mw boost` reaches everyone |

## How the bots connect

Mineflayer speaks up to 26.1, and the server runs 26.3, so the test server (only the test server) has ViaVersion and ViaBackwards, pinned and checksummed in `run.mjs`. Through that translation, a few things don't work, and the harness works round them:

- **Movement:** the server misreads the bots' position packets and kicks them, so bots have no physics and never send their position. The console moves them with `/tp`; they still turn to aim.
- **Their view of the world** isn't reliable (some blocks look like air to them), so blocks are checked on the server with `execute if block`.
- **Right-clicking a block** never reaches the server, so the gate scenario is skipped (`PLAYTEST_GATES=1` runs it anyway). Gates are checked by hand until Mineflayer supports 26.3 itself.
- Lines like `Chunk size is 24 but only 20 was read ; partial packet : player_info…` are Mineflayer not knowing a field 26.x added to the tab list. Harmless.

`PLAYTEST_VERBOSE=1` prints the server's log; `PLAYTEST_DEBUG=1` also takes a thread dump of the server when a scenario fails.
