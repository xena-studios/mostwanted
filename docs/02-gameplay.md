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
| **Guards** | | Stop escapes, arrest criminals, collect bounties |

- **Everyone starts as a prisoner** (decided). There's no team-select menu.
- **Becoming a guard:** apply on the website (or with `/apply` in Discord). Staff review it, and approved players get the guard rank in-game. That makes guards a trusted, moderated role, which matters since they can arrest and shoot people.
- **On/off duty:** approved guards can probably go off duty and play as a prisoner. Decide whether off-duty guards keep their prisoner progress separate.
- **Not enough guards:** with up to 100 players and guards coming only from applications, we need enough guards online at peak times. Options: recruit lots of guards, show "guards online" so prisoners know the risk, and scale robbery payouts with how many guards are on.
- An inmate becomes a **criminal** once they cross the prison boundary.

## The loop

```
          ┌──────────────── arrested ────────────────┐
          ▼                                          │
   [Inmate in prison] ──escape──▶ [Criminal in world] ──rob──▶ cash + bounty
                                         ▲                 │
                                         └─────────────────┘
   [Guard] ──patrol / respond to alarms──▶ arrest ──▶ earns the bounty
```

### Prisoners: escaping

There should be several ways out so escapes don't get stale:

- Punch / cut through a weak wall or fence (needs a tool or a timer)
- Sewers / vents
- Grab a keycard (dropped by guards or found) to open gates
- Go over the wall with a ladder or grapple
- A friend breaks in from outside to help (opens a gate, drops a keycard)

### Criminals: robberies

Robbery spots around the map, each with its own risk and reward:

| Location | Idea |
| --- | --- |
| Bank | Big payout, long timer, alarm alerts all guards |
| Jewelry store | Grab-and-go, laser/tripwire obstacles |
| Gas station / convenience store | Small, quick, good for new criminals |
| Museum / train / cargo ship | Rotating "big heist" events |

- Each location **opens and closes** on a timer and is announced (e.g. "The bank is open!"). This spreads players around the map and gives guards something to respond to.
- Robbing raises your **bounty**. Cash is only banked once you reach a **criminal base** (safe zone).

### Guards: arresting

- **Handcuffs**: an item. Right-click a criminal at close range (maybe hold for a second) to arrest them.
- **Taser**: slows or stuns for a short time. Needs a cooldown so it isn't oppressive.
- **Tools**: spike strips, roadblocks, maybe a police helicopter later.
- **Rule:** guards can't arrest **inmates** still inside the prison who aren't escaping (or only inside certain zones). This stops spawn camping.
- Arresting a criminal pays the guard the criminal's **bounty**.

### Combat

Criminals **can fight back** (decided). Guards vs. criminals is full PvP.

- **Who can hurt who:**

  | | Guard | Criminal | Inmate |
  | --- | --- | --- | --- |
  | **Guard** | no | yes | only while escaping / in restricted zones |
  | **Criminal** | yes | no direct damage (indirect ways only) | no |
  | **Inmate** | only during a riot | no | no |

- **Weapons:** **custom guns** (decided), along with melee and vanilla gear. See [guns](#guns).
- **Criminal vs. criminal:** criminals **can't damage each other directly** (decided), but can take each other out in other ways. Ideas (decide which ones):
  - Traps (tripwires, pressure plates, lava/fall traps set in their own base area)
  - Tipping off guards (a "snitch" button that reveals another criminal's location to guards)
  - Stealing unbanked cash from a downed/arrested criminal
  - Sabotage (cutting power to a robbery so the alarm goes off for someone else)
- **Dying vs. getting arrested:** a criminal killed by a guard should count like an arrest (back to prison, guard gets the bounty, maybe a smaller share). Otherwise criminals would rather die than get cuffed.
- **Guard deaths:** the guard respawns at the police station. The criminal maybe gets extra bounty for killing a guard.
- **No spawn camping:** safe zones at the police station, criminal base, and inside the prison.

### Riots

Inmates **can start a riot** (decided).

- **How it starts:** ideas are enough inmates hitting an alarm in the yard, overwhelming a guard post, or a riot item crafted from contraband. It should need several inmates, so one person can't start one alone.
- **During a riot:** inmates and guards can fight inside the prison, and the "no arresting inmates who aren't escaping" protection is off.
- **Announced** to all guards, maybe server-wide, so guards rush back from the city. That's a great distraction for escapes and robberies happening at the same time.
- **How it ends:** a timer, guards retaking control points, or most rioters arrested or down.
- **Cooldown** so riots stay special and guards aren't stuck at the prison all day.

### Guns

- Custom guns (pistol, shotgun, rifle, guard taser) bought with cash at the criminal base / police armory.
- **Crossplay first:** guns have to feel fair between Java and Bedrock, including touch controls on phones. Simple "point and shoot" (instant hit along where you're looking) works the same on both. Fancy features like aim-down-sights scopes, recoil patterns and reload animations are where Bedrock breaks.
- Ammo as an item so guns cost money to use.
- Options: an existing gun plugin (test it on Bedrock first) or our own simple gun system. Leaning toward our own, so we control how it behaves on Bedrock.

### Getting arrested

- You go back to prison as an inmate, maybe with a jail timer that scales with your bounty.
- You lose unbanked cash.

## Economy

- **Earn:** robberies (criminals), arrests/bounties (guards), maybe a small salary while playing guard.
- **Spend:** guns and ammo, custom food, tools, armor, cosmetics, base upgrades.
- Keep all purchases permanent so progress carries over between sessions.

## Vehicles

**Not in the main game** (decided). Players get around on foot. We can add vehicles later. If we do, options are:

- Horses (vanilla, easy, slow)
- Boats on ice roads (vanilla hack, fast, weird)
- A vehicle plugin that adds cars and helicopters. **Check what Minehut allows.**
- Fake cars with resource-pack models on fast mounts

## Map

One big map with:

- **Prison** (spawn for inmates)
- **Police station** (spawn for guards)
- **City** with robbery locations
- **Criminal base(s)** (criminal safe zone, bank your cash, buy gear)
- Roads, highways, open areas for chases
