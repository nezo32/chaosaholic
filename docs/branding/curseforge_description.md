<!--
  CurseForge page body for Chaosaholic. Paste into the project description (Markdown editor).
  Numbers marked with a "sync" comment still have to be checked against the final event implementations.
  Remove the comments before pasting.
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
- 🔥 **Chaos stacks:** level up mid-event and another one starts; roll the same one again and it lasts longer.
- ⏱️ **Always readable:** a coloured boss bar for each active event shows the time left, and a title or an actionbar
  line and a sound tell you what just happened.
- ⚠️ **Fair warning:** TNT Rain, Anvil Rain and Lightning Storm give you 3 seconds to run (4 on Hardcore), hazards
  never land right on you, and every event has a safety cap.
- 🚫 **No farming:** only levels above your best since your last death count. Spend levels at the enchanting table,
  earn them back: nothing happens.
- 🔁 **Everything goes back:** chickens turn back into the mobs they were, tiny things grow back, the sun comes back,
  and spawned mobs, bees and party sheep vanish when the event ends, even after a crash.
- 👥 **Solo or everyone:** events hit only the player who levelled up, or every player in the same dimension.
- ⚙️ **Toggle anywhere:** an ON/OFF button at world creation, plus operator commands to turn the mode on or off,
  switch single events off, change how often each one rolls, or trigger one on purpose.
- 🔕 **Your call on noise:** turn the event sound, the messages or the screen effects (camera shake) off, through
  Mod Menu or `/chaosaholic-notify`. Boss bars and danger warnings always come through.
- 🌍 **English and Russian** out of the box.

## What it does

When Chaosaholic Mode is on, each experience level a player gains does the following:

1. **Skipped cases:** players in Creative or Spectator.
2. **Only new levels count:** a level counts only if it's above the highest level you've reached since your last
   death. Levels spent at an enchanting table, an anvil or a grindstone and earned back start nothing. Dying resets
   it: after respawning, your best is the level you respawn with (0 normally, your kept level with `keepInventory`).
3. **One level, one event:** three levels at once start three events, one second apart (at most 10 waiting).
4. **Roll:** one random event from the events that are switched on in this world. Good, Bad and Weird are all in
   the same pool. An event that can't happen right now (Swap with no mob around) is rerolled.
5. **Warning (dangerous events only):** TNT Rain, Anvil Rain and Lightning Storm show a ⚠ warning on the actionbar
   and play a rising ping for 3 seconds before anything falls.
6. **Start:** Bad events show their name as a red title with a short subtitle. Good (green) and Weird (purple) events
   show up on the actionbar. A sound plays.
7. **Countdown:** a boss bar in the category colour shows the event and the time left. Several events can run at
   once (up to 8), each with its own bar. Rolling one you already have extends it instead (180 s at most). Instant
   events (Sky Chest, Swap, Random Teleport) just happen, with no bar.
8. **Cleanup:** when the time is up, the event undoes itself: effects end, spawned mobs, bees and party sheep are
   removed, chickens turn back, the time of day is restored. Leaving, dying, changing dimension or switching to
   Creative ends it for you at once.

Difficulty and Hardcore settings are never changed, and events respect them: Mob Surprise spawns nothing on
Peaceful, and Hardcore gets a longer warning.

## Notification settings

Every event shows a title or an actionbar line and plays a sound. Each player can change that for themselves:

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
| `/chaosaholic [on\|off\|status]` | Turns Chaosaholic Mode on or off for this world, or shows it and the active events |
| `/chaosaholic scope [player\|world]` | Events hit only the player who levelled up, or every player in the same dimension |
| `/chaosaholic events` | Lists all 30 events with their category, on/off and weight (hover for a description) |
| `/chaosaholic event <id> [on\|off\|status]` | Switches a single event on or off for this world |
| `/chaosaholic event <id> weight [0..1000]` | How often an event rolls compared to the others (default 100, 0 = never) |
| `/chaosaholic trigger <id> [players]` | Starts that event right now, even with the mode off |
| `/chaosaholic roll [players]` | Rolls a random event right now, like a level-up |
| `/chaosaholic stop [players]` | Ends every event on those players and cleans up |

In single-player these need cheats: Allow Commands on, or Open to LAN with Allow Cheats on.

## The events

Durations are random within the range. An event never has more than 180 s left, even after it's extended.

### 🍀 Good

| Event | What happens | Duration | Safety cap |
|---|---|---|---|
| Midas Hour | Ores you mine drop double | 60–120 s <!-- sync --> | Ores only. Fortune and Silk Touch work as usual <!-- sync --> |
| Feather Fall | No fall damage | 60–120 s | Only fall damage |
| Loot Piñata | Mobs you kill drop extra random loot | 60–120 s <!-- sync --> | 1–3 extra items per kill <!-- sync --> |
| Speed Demon | Speed III and Haste II | 30–60 s | Ends with the event |
| Sky Chest | A chest full of random loot lands nearby. It's a normal chest and it's yours to keep | instant | Lands on a safe spot, never on you <!-- sync --> |
| Double XP | Experience orbs you pick up count double | 60–120 s <!-- sync --> | The extra XP can level you up, and that rolls another event |
| Healing Aura | Regeneration II | 20–40 s <!-- sync --> | Ends with the event |
| Iron Skin | Resistance II | 30–60 s <!-- sync --> | Ends with the event |
| Moon Jump | Jump Boost III and no fall damage | 30–60 s <!-- sync --> | No fall damage for the whole event <!-- sync --> |

### 😈 Bad

| Event | What happens | Duration | Safety cap |
|---|---|---|---|
| TNT Rain | Lit TNT falls around you | 15–20 s <!-- sync --> | 3 s warning. At most 12 TNT, landing 4–10 blocks away, never right on you. Blocks only break if `mobGriefing` is on. Hardcore: longer fuses <!-- sync --> |
| Anvil Rain | Anvils crash down on marked spots nearby | 15–20 s <!-- sync --> | 3 s warning, each spot marked with particles 1 s before impact. Anvils never stay as blocks <!-- sync --> |
| Mob Surprise | A wave of hostile mobs shows up | 60–90 s <!-- sync --> | 2 / 3 / 4 mobs on Easy / Normal / Hard (Hardcore = Hard), 8–12 blocks away on safe spots. Nothing on Peaceful. Survivors vanish at the end <!-- sync --> |
| Hunger Games | Hunger III: your food bar drains fast | 30–45 s <!-- sync --> | Vanilla starvation rules, so keep food handy on Hard <!-- sync --> |
| Butterfingers | Now and then the item in your hand slips out | 30–45 s <!-- sync --> | The item is tossed on the ground like pressing Q, never destroyed |
| Eternal Night | Night falls at once | 60–120 s <!-- sync --> | The day comes back at the end <!-- sync --> |
| Lightning Storm | Lightning strikes around you | 15–20 s <!-- sync --> | 3 s warning. At least 4 blocks away, no damage, no fire <!-- sync --> |
| Sluggish | Slowness II and Mining Fatigue I | 20–40 s <!-- sync --> | Ends with the event |
| Bee Swarm | A few angry bees come after you | 30–45 s <!-- sync --> | 3–5 bees, removed at the end <!-- sync --> |
| Blackout | Darkness closes in | 5–10 s <!-- sync --> | Short on purpose |

### 🌀 Weird

| Event | What happens | Duration | Safety cap |
|---|---|---|---|
| Gravity Flip | You float up and drift down by turns | 20–30 s <!-- sync --> | Always ends on slow falling, so it can't drop you to your death |
| Chickenpocalypse | Nearby mobs turn into chickens | 30–60 s <!-- sync --> | At most 16 mobs <!-- sync -->. Bosses, pets and named mobs are left alone. Each chicken turns back into the exact mob it was (if the chicken dies, so does the mob) |
| Tiny World | You and the mobs around you shrink to half size | 30–60 s | Radius 12. Bosses, pets and named mobs are left alone. Everyone grows back at the end |
| Bouncy Floor | Landing bounces you like slime | 30–60 s <!-- sync --> | No fall damage while it lasts |
| Upside Down | Nearby mobs flip upside down | 30–60 s <!-- sync --> | Original names come back at the end |
| Swap | You swap places with a random nearby mob | instant | Only with a mob within 16 blocks <!-- sync --> |
| Sheep Disco | Rainbow sheep show up and start a party | 30–45 s <!-- sync --> | 3–5 sheep. They leave at the end (no free wool farm) <!-- sync --> |
| Slippery | Every block feels like ice | 30–45 s <!-- sync --> | Ends with the event |
| Random Teleport | You teleport to a random safe spot nearby | instant | 8–24 blocks away, only onto a safe, solid spot <!-- sync --> |
| Screen Shake | Your camera wobbles | 10–15 s <!-- sync --> | Client only. Off if you disabled screen effects |
| Glow Party | Everything nearby glows, even through walls | 30–60 s <!-- sync --> | Radius 16 <!-- sync --> |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2 or 26.3, and run the game on Java 25.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and `chaosaholic-<version>.jar` in your `mods/` folder.
   Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for the settings screen.
3. Turn the mode on in one of two ways:
   - **New world:** Create World → Game tab → **Chaosaholic Mode** (right below Difficulty) is **ON** by default
     (switch it off there for a normal world).
   - **Existing world or dedicated server:** an operator runs `/chaosaholic on` (`/chaosaholic status` shows the
     current state). Worlds made without the button (dedicated servers, other launchers) start with it off.

The mod is needed on the server. On the client it adds the Create World button, the settings screen and camera
shake; players without it can still join and play.

## Known quirks

- Double XP can level you up, and a level-up rolls another event. That's the point, but XP farms get *very* lively.
- Eternal Night changes the time for the whole dimension, not just for you. <!-- sync -->
- Chickenpocalypse is permanent for a chicken that dies: the original mob is gone with it.
- Tiny World shrinks your reach and step height along with you (that's vanilla's scale attribute).
- TNT Rain follows the `mobGriefing` game rule: with it on, the TNT breaks blocks like normal TNT.
- Milk only helps for a second: an event's effects come back until the event ends.

## License and source

[MIT](https://github.com/nezo32/chaosaholic/blob/main/LICENSE) © 2026 nezo.
Source code, issues and releases: [github.com/nezo32/chaosaholic](https://github.com/nezo32/chaosaholic).

From the author of [Enchantaholic](https://www.curseforge.com/minecraft/mc-mods/enchanaholic).
