<!--
  CurseForge page body for Chaosaholic. Paste into the project description (Markdown editor).
  Numbers marked "default" are the designer's reasonable defaults; the docs writer syncs them with the implementation.
-->

<p align="center"><img src="https://raw.githubusercontent.com/nezo32/chaosaholic/main/docs/branding/curseforge_logo.png" alt="Chaosaholic logo" width="256"></p>

<p align="center"><em>Level up. Unleash chaos. Survive it.</em></p>

**Chaosaholic** turns every level-up into a dice roll. Each time you gain an experience level, a **random chaos
event** starts. Sometimes it's a gift (double ores, Speed III, a chest falling from the sky). Sometimes it's a
disaster (TNT rain, an anvil storm, a swarm of angry bees). And sometimes it just makes no sense (every mob nearby
turns into a chicken). Farm XP at your own risk.

A **Fabric mod for Minecraft Java 26.2–26.3**.

## Features

- 🎲 **Every level is a roll:** gain a level, get a random event. 30 of them, in three flavours.
- 🍀 **Good, 😈 Bad, 🌀 Weird:** 9 blessings, 10 disasters and 11 things that make no sense at all.
- ⏱️ **Always readable:** a coloured boss bar for each active event shows the time left, and a title and a sound tell
  you what just happened.
- ⚠️ **Fair warning:** TNT Rain, Anvil Rain and Lightning Storm give you 3 seconds to run, and every hazard has a
  safety cap.
- 🔁 **Everything goes back:** chickens turn back into the mobs they were, tiny things grow back, the sun comes back,
  and anvils, bees and party sheep are cleaned up when the event ends.
- ⚙️ **Toggle anywhere:** an ON/OFF button at world creation, plus operator commands to turn the mode on or off,
  switch single events off, or trigger one on purpose.
- 🔕 **Your call on noise:** turn the event sound, the messages or the screen effects (camera shake) off, through
  Mod Menu or `/chaosaholic-notify`. Danger warnings always come through.
- 🌍 **English and Russian** out of the box.

## What it does

When Chaosaholic Mode is on, each experience level a player gains does the following:

1. **Skipped cases:** players in Creative or Spectator.
2. **Roll:** one random event from the events that are switched on in this world. Good, Bad and Weird are all in
   the same pool.
3. **Warning (dangerous events only):** TNT Rain, Anvil Rain and Lightning Storm show a ⚠ warning on the actionbar
   and play a rising ping for 3 seconds before anything falls.
4. **Start:** the event name appears as a title (green for Good, red for Bad, purple for Weird) with a short
   subtitle, and a sound plays.
5. **Countdown:** a boss bar in the category colour shows the event and the time left. Several events can run at
   once, each with its own bar. Instant events (Sky Chest, Swap, Random Teleport) just happen, with no bar.
6. **Cleanup:** when the time is up, the event undoes itself: effects end, spawned mobs, bees, anvils and party
   sheep are removed, chickens turn back, the time of day is restored.

Levelling up **during** an event rolls another one. Chaos stacks. Difficulty and hardcore settings are never changed,
and events respect them (for example, Mob Surprise spawns nothing on Peaceful).

## Notification settings

Every event shows a title and plays a sound. Each player can change that for themselves:

- With [Mod Menu](https://modrinth.com/mod/modmenu) installed, open Mods → Chaosaholic → the config button, and
  switch **Event sound**, **Event messages** and **Screen effects**.
- Without Mod Menu, use the client command `/chaosaholic-notify sound off`, `/chaosaholic-notify message off`,
  `/chaosaholic-notify effects off`, or `/chaosaholic-notify status`.

Settings are stored on your computer in `config/chaosaholic.json` and apply on any server that runs Chaosaholic.
Players who join without the mod on their client get the default title and sound, but no camera shake.
Boss bars and **danger warnings are always shown**, whatever you pick.

## Commands (operators)

| Command | What it does |
|---|---|
| `/chaosaholic on\|off\|status` | Turns Chaosaholic Mode on or off for this world, or shows it and the active events |
| `/chaosaholic scope player\|world` | Events hit only the player who levelled up, or every player in the world *(if the scope option ships)* |
| `/chaosaholic events` | Lists all 30 events and whether each is on (hover for a description) |
| `/chaosaholic event <id> on\|off` | Switches a single event on or off for this world |
| `/chaosaholic trigger [<id>] [<player>]` | Starts an event right now (a random one if no id is given) |

In single-player these need cheats: Allow Commands on, or Open to LAN with Allow Cheats on.

## The events

### 🍀 Good

| Event | What happens | Default duration |
|---|---|---|
| Midas Hour | Ores you mine drop double | 60 s |
| Feather Fall | No fall damage | 60 s |
| Loot Piñata | Mobs you kill drop extra random loot | 60 s |
| Speed Demon | Speed III and Haste II | 45 s |
| Sky Chest | A chest full of random loot lands nearby. It's a normal chest and it's yours to keep | instant |
| Double XP | Experience orbs you pick up count double | 60 s |
| Healing Aura | Regeneration II | 30 s |
| Iron Skin | Resistance II | 45 s |
| Moon Jump | Jump Boost III and no fall damage | 45 s |

### 😈 Bad

| Event | What happens | Safety cap (default) | Default duration |
|---|---|---|---|
| TNT Rain | Lit TNT falls around you | 3 s warning. At most 12 TNT per event, landing 4–10 blocks away, never right on you. Blocks only break if `mobGriefing` is on. Hardcore: longer fuses | 20 s |
| Anvil Rain | Anvils crash down on marked spots nearby | 3 s warning, each spot marked with particles 1 s before impact. At most 12 anvils, never on your exact spot. Landed anvils are removed at the end | 20 s |
| Mob Surprise | A wave of hostile mobs shows up | 2 / 3 / 4 mobs on Easy / Normal / Hard (Hardcore = Hard), 8–12 blocks away on safe spots. Nothing on Peaceful. Survivors vanish at the end | 45 s |
| Hunger Games | Hunger III: your food bar drains fast | Vanilla starvation rules: an empty food bar stops at 5 hearts on Easy and half a heart on Normal, but can kill on Hard, so keep food handy | 30 s |
| Butterfingers | Now and then the item in your hand slips out | The item is tossed on the ground like pressing Q, never destroyed | 30 s |
| Eternal Night | Night falls at once | The previous time of day comes back at the end | 60 s |
| Lightning Storm | Lightning strikes around you | 3 s warning. At most 8 bolts, at least 4 blocks away, no fire | 20 s |
| Sluggish | Slowness II and Mining Fatigue I | — | 30 s |
| Bee Swarm | A few angry bees come after you | At most 4 bees (Peaceful: none). Removed at the end | 30 s |
| Blackout | Darkness closes in | Short on purpose | 10 s |

### 🌀 Weird

| Event | What happens | Safety cap (default) | Default duration |
|---|---|---|---|
| Gravity Flip | You float up and drift down by turns | Always ends on slow falling, so it can't drop you to your death | 20 s |
| Chickenpocalypse | Nearby mobs turn into chickens | Radius 16. Bosses, players and tamed pets are left alone. Each chicken turns back into the exact mob it was (if the chicken dies, so does the mob) | 30 s |
| Tiny World | You and everything nearby shrink to half size | Radius 16. Everyone grows back at the end | 45 s |
| Bouncy Floor | Landing bounces you like slime | No fall damage while it lasts | 30 s |
| Upside Down | Nearby mobs flip upside down | Radius 16. Original names come back at the end | 45 s |
| Swap | You swap places with a random nearby mob | Only with a mob within 16 blocks, never into a wall | instant |
| Sheep Disco | Rainbow sheep show up and start a party | At most 5 sheep. They leave at the end (no free wool farm) | 30 s |
| Slippery | Every block feels like ice | — | 30 s |
| Random Teleport | You teleport to a random safe spot nearby | Up to 16 blocks, only onto a safe, solid spot | instant |
| Screen Shake | Your camera wobbles | Client only. Off if you disabled screen effects | 10 s |
| Glow Party | Everything nearby glows, even through walls | Radius 16 | 45 s |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2 or 26.3, and run the game on Java 25.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and `chaosaholic-<version>.jar` in your `mods/` folder.
   Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for the settings screen.
3. Turn the mode on in one of two ways:
   - **New world:** Create World → Game tab → **Chaosaholic Mode** is **ON** by default (switch it off there for a
     normal world).
   - **Existing world or dedicated server:** an operator runs `/chaosaholic on` (`/chaosaholic status` shows the
     current state). Worlds made without the button (dedicated servers, other launchers) start with it off.

The mod is needed on the server. On the client it adds the Create World button, the settings screen and camera
shake; players without it can still join and play.

## Known quirks

- Double XP can level you up, and a level-up rolls another event. That's the point, but XP farms get *very* lively.
- Eternal Night changes the time for the whole world, not just for you. Sleeping during it is allowed; the event
  still ends on time.
- Chickenpocalypse is permanent for a chicken that dies: the original mob is gone with it.
- Tiny World shrinks your reach and step height along with you (that's vanilla's scale attribute).
- TNT Rain follows the `mobGriefing` game rule: with it on, the TNT breaks blocks like normal TNT.
- Events that pick a nearby mob (Swap, Chickenpocalypse, Upside Down) do nothing visible when no mob is around.

## License and source

[MIT](https://github.com/nezo32/chaosaholic/blob/main/LICENSE) © 2026 nezo.
Source code, issues and releases: [github.com/nezo32/chaosaholic](https://github.com/nezo32/chaosaholic).

From the author of [Enchantaholic](https://www.curseforge.com/minecraft/mc-mods/enchanaholic).
