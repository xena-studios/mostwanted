# Gameplay

Think **Roblox Jailbreak, in Minecraft**: one persistent open world, two sides, and a loop that never resets.

## Format

**One persistent world** (decided). No rounds or match timer. Players drop in and out at any time, and the world keeps running.

## Teams

There are two teams. The prisoner team has two states.

| Team | State | Goal |
| --- | --- | --- |
| **Prisoners** | **Inmate**: inside the prison | Escape |
| | **Criminal**: escaped, out in the world | Rob places, stack cash, stay free |
| **Cops** | | Stop escapes, arrest criminals, collect bounties |

- You pick a team from a menu at spawn and can switch later, maybe with a cooldown.
- **Team balance:** cap cops at a share of online players (e.g. at most 1 cop for every 2–3 prisoners) so there's always someone to chase.
- An inmate becomes a **criminal** once they cross the prison boundary.

## The loop

```
          ┌──────────────── arrested ────────────────┐
          ▼                                          │
   [Inmate in prison] ──escape──▶ [Criminal in world] ──rob──▶ cash + bounty
                                         ▲                 │
                                         └─────────────────┘
   [Cop] ──patrol / respond to alarms──▶ arrest ──▶ earns the bounty
```

### Prisoners: escaping

There should be several ways out so escapes don't get stale:

- Punch / cut through a weak wall or fence (needs a tool or a timer)
- Sewers / vents
- Grab a keycard (dropped by cops or found) to open gates
- Go over the wall with a ladder or grapple
- Hop into a getaway vehicle a friend parked outside

### Criminals: robberies

Robbery spots around the map, each with its own risk and reward:

| Location | Idea |
| --- | --- |
| Bank | Big payout, long timer, alarm alerts all cops |
| Jewelry store | Grab-and-go, laser/tripwire obstacles |
| Gas station / convenience store | Small, quick, good for new criminals |
| Museum / train / cargo ship | Rotating "big heist" events |

- Each location **opens and closes** on a timer and is announced (e.g. "The bank is open!"). This spreads players around the map and gives cops something to respond to.
- Robbing raises your **bounty**. Cash is only banked once you reach a **criminal base** (safe zone).

### Cops: arresting

- **Handcuffs**: an item. Right-click a criminal at close range (maybe hold for a second) to arrest them.
- **Taser**: slows or stuns for a short time. Needs a cooldown so it isn't oppressive.
- **Tools**: spike strips, roadblocks, maybe a police helicopter later.
- **Rule:** cops can't arrest **inmates** still inside the prison who aren't escaping (or only inside certain zones). This stops spawn camping.
- Arresting a criminal pays the cop the criminal's **bounty**.

### Getting arrested

- You go back to prison as an inmate, maybe with a jail timer that scales with your bounty.
- You lose unbanked cash.

## Economy

- **Earn:** robberies (criminals), arrests/bounties (cops), maybe a small salary while playing cop.
- **Spend:** vehicles, weapons/gear, cosmetics, a garage, house/base upgrades.
- Keep all purchases permanent so progress carries over between sessions.

## Vehicles

Vehicles are a huge part of Jailbreak, and vanilla Minecraft doesn't really have cars. Options:

- Horses (vanilla, easy, slow)
- Boats on ice roads (vanilla hack, fast, weird)
- A vehicle plugin that adds cars and helicopters. **Check what Minehut allows.**
- Fake cars with resource-pack models on fast mounts

## Map

One big map with:

- **Prison** (spawn for inmates)
- **Police station** (spawn for cops)
- **City** with robbery locations
- **Criminal base(s)** (criminal safe zone, bank your cash, buy gear)
- Roads, highways, open areas for chases
