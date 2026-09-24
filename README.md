<p align="center">
  <img src="docs/branding/curseforge_logo.png" alt="Chaosaholic logo" width="256">
</p>

<h1 align="center">Chaosaholic</h1>

<p align="center"><em>Level up. Unleash chaos. Survive it.</em></p>

Chaosaholic is a Minecraft mode in which each experience level you gain starts a random chaos event: sometimes good,
sometimes bad and sometimes just weird. There are 30 events. Farm XP at your own risk.

It is a Fabric mod for **Minecraft Java Edition 26.2–26.3** (in [`fabric/`](fabric/)).

## What it does

When the mode is on, each experience level a player gains does the following:

1. **Skipped cases:** players in Creative or Spectator. Their level changes never start events.
2. **Only new levels count:** a level counts only if it is above the highest level the player has reached since
   their last death. Levels you spend at an enchanting table, an anvil or a grindstone and then earn back start
   nothing (see [Anti-farming](#anti-farming)).
3. **One level, one event:** three levels at once start three events, one second apart. At most 10 are waiting at a
   time; extra levels beyond that are dropped (so `/xp add @s 1000 levels` doesn't queue 1000 events).
4. **Roll:** one random event from the events that are switched on in this world, weighted by each event's weight.
   Good, Bad and Weird events share one pool. An event that can't start right now (Swap with no mob around, Mob
   Surprise on Peaceful) is rerolled.
5. **Warning (dangerous events only):** TNT Rain, Anvil Rain and Lightning Storm show a ⚠ warning on the actionbar
   and play a rising ping for 3 seconds (4 on Hardcore) before the first hazard.
6. **Start:** Bad events show their name as a title in red with a short subtitle. Good (green) and Weird (purple)
   events show `✦ Name · subtitle` on the actionbar. A sound plays.
7. **Countdown:** a boss bar in the category colour shows the event and the time left (`Speed Demon — 42 s`,
   `m:ss` from one minute). Instant events (Sky Chest, Swap, Random Teleport) just happen, with no bar.
8. **Cleanup:** when the time is up, the event undoes itself (see [Cleanup guarantees](#cleanup-guarantees)).

Levelling up **during** an event rolls another one. Chaos stacks:

- **Different events** run side by side, each with its own boss bar, up to **8** timed events per player. Further
  level-ups wait in the queue until one ends.
- **The same event** rolled again for a player who already has it doesn't start a copy. It **extends** the running
  one by a fresh roll of its duration, announces it again, and never goes past **180 s** remaining.

Difficulty and Hardcore settings are never changed, and events respect them (see
[Hardcore and difficulty](#hardcore-and-difficulty)).

## Notification settings

Every event shows a title or an actionbar line and plays a sound. Each player can turn off either one, and also
screen effects such as the camera wobble of Screen Shake:

- With [Mod Menu](https://modrinth.com/mod/modmenu) installed, open Mods → Chaosaholic → the config button, and
  switch **Event sound**, **Event messages** and **Screen effects**. Every click is saved right away.
- Without Mod Menu, use the client command `/chaosaholic-notify sound|message|effects [on|off]` (without `on|off` it
  flips the setting) or `/chaosaholic-notify status`.

Settings are stored on your computer in `config/chaosaholic.json` (`notifySound`, `notifyMessage`, `screenEffects`;
a missing key means on) and apply on any server that runs Chaosaholic. Players who join without the mod on their
client always get the default title/actionbar and sound, and no camera wobble.

**Boss bars and danger warnings always show and play**, whatever you pick. They are safety information.

## The events

Durations are picked at random from the range each time. "Nearby" means around each affected player, never more
than 24 blocks away. A player can be under at most 8 timed events at once, and an event never has more than 180 s
left, even after extensions.

### 🍀 Good

| Event | What happens | Duration | Safety cap |
|---|---|---|---|
| Midas Hour | Ores you mine drop double | 60–120 s <!-- sync --> | Only blocks in the `c:ores` tag. One extra drop roll per ore: Fortune applies, Silk Touch gives the ore block <!-- sync --> |
| Feather Fall | You take no fall damage | 60–120 s | Only fall damage is cancelled, nothing else |
| Loot Piñata | Mobs you kill drop extra random loot | 60–120 s <!-- sync --> | 1–3 extra items per kill, none when the `doMobLoot` game rule is off <!-- sync --> |
| Speed Demon | Speed III and Haste II | 30–60 s | Both effects end with the event |
| Sky Chest | A chest full of random loot lands nearby. It's a normal chest and it's yours to keep | instant | Only on a safe, solid spot near you, never on you <!-- sync --> |
| Double XP | Experience orbs you pick up count double | 60–120 s <!-- sync --> | Only orbs you pick up yourself. The extra XP can level you up, and that rolls another event |
| Healing Aura | Regeneration II | 20–40 s <!-- sync --> | Ends with the event |
| Iron Skin | Resistance II | 30–60 s <!-- sync --> | Ends with the event |
| Moon Jump | Jump Boost III and no fall damage | 30–60 s <!-- sync --> | Fall damage is off for the whole event, so a big jump can't kill you <!-- sync --> |

### 😈 Bad

| Event | What happens | Duration | Safety cap |
|---|---|---|---|
| TNT Rain | Lit TNT falls around you | 15–20 s <!-- sync --> | 3 s warning. At most 12 TNT, landing 4–10 blocks away, never on you. Fuse at least 3 s (4 s on Hardcore). Blocks break only when `mobGriefing` is on <!-- sync --> |
| Anvil Rain | Anvils crash down on marked spots nearby | 15–20 s <!-- sync --> | 3 s warning. Each spot is marked with red particles at least 1 s before impact. Anvils never become blocks: nothing to clean up, nothing to farm <!-- sync --> |
| Mob Surprise | A wave of hostile mobs shows up | 60–90 s <!-- sync --> | 2 / 3 / 4 mobs on Easy / Normal / Hard (Hardcore counts as Hard), 8–12 blocks away on safe spots. Never on Peaceful. Survivors vanish at the end <!-- sync --> |
| Hunger Games | Hunger III: your food bar drains fast | 30–45 s <!-- sync --> | Vanilla starvation rules apply: keep food handy on Hard <!-- sync --> |
| Butterfingers | Every few seconds the item in your hand may slip out | 30–45 s <!-- sync --> | The item is tossed on the ground like pressing Q, never destroyed <!-- sync --> |
| Eternal Night | Night falls at once | 60–120 s <!-- sync --> | Overworld-like dimensions only. The clock goes back to where it would have been at the end, even on server stop <!-- sync --> |
| Lightning Storm | Lightning strikes around you | 15–20 s <!-- sync --> | 3 s warning, a spark marks each spot first. At least 4 blocks from any player, no damage, no fire <!-- sync --> |
| Sluggish | Slowness II and Mining Fatigue I | 20–40 s <!-- sync --> | Ends with the event |
| Bee Swarm | A few angry bees come after you | 30–45 s <!-- sync --> | 3–5 bees. Removed at the end <!-- sync --> |
| Blackout | Darkness closes in | 5–10 s <!-- sync --> | Short on purpose. Darkness, not Blindness |

### 🌀 Weird

| Event | What happens | Duration | Safety cap |
|---|---|---|---|
| Gravity Flip | You float up and drift down by turns | 20–30 s <!-- sync --> | Always ends on slow falling, so it can't drop you to your death <!-- sync --> |
| Chickenpocalypse | Nearby mobs turn into chickens | 30–60 s <!-- sync --> | At most 16 mobs <!-- sync -->. Bosses, players, pets and named mobs are left alone. Each chicken turns back into the exact mob it was, even after a crash. If the chicken dies, so does the mob |
| Tiny World | You and the mobs around you shrink to half size | 30–60 s | Radius 12 blocks, at most 64 mobs. Bosses, pets, named mobs and riders are left alone. Everyone grows back at the end (the change is never saved, so it can't stick) |
| Bouncy Floor | Landing bounces you like slime | 30–60 s <!-- sync --> | No fall damage while it lasts. Sneak to land without bouncing |
| Upside Down | Nearby mobs flip upside down | 30–60 s <!-- sync --> | Bosses, pets and named mobs are left alone. Original names come back at the end, even after a crash <!-- sync --> |
| Swap | You swap places with a random nearby mob | instant | Only with a mob within 16 blocks. No mob, no swap: another event is rolled <!-- sync --> |
| Sheep Disco | Rainbow sheep show up and start a party | 30–45 s <!-- sync --> | 3–5 sheep. They leave at the end (no free wool farm) <!-- sync --> |
| Slippery | Every block feels like ice | 30–45 s <!-- sync --> | Ends with the event |
| Random Teleport | You teleport to a random safe spot nearby | instant | 8–24 blocks away, only onto a safe, solid spot (works in caves) <!-- sync --> |
| Screen Shake | Your camera wobbles | 10–15 s <!-- sync --> | Client only, small and smooth. Off if you turned screen effects off |
| Glow Party | Everything nearby glows, even through walls | 30–60 s <!-- sync --> | Radius 16 blocks <!-- sync -->. Ends with the event |

**Fair play:** mobs, TNT, anvils, bees and sheep that events spawn are never saved with the world: when the event
ends, the chunk unloads or the server crashes, they're gone. Their drops can't be farmed. Effects given by an event
come back if you drink milk, until the event ends.

## Rules of the chaos

### Anti-farming

Each player has a **mark**: the highest experience level they have reached since their last death. It is saved with
the player (`chaosaholic:level_mark`). Only levels **above the mark** start events:

- You reach level 30 (30 events so far). You spend 20 levels enchanting and earn them back: levels 11–30 start
  nothing. Level 31 starts the next event. The same goes for anvils and the grindstone.
- Losing levels never starts anything and never lowers the mark.
- Levels gained while the mode is off, or in Creative or Spectator, still raise the mark. Turning the mode on later
  doesn't release a backlog.
- The first time the mod sees a player (a world from before the mod, a new player joining), the mark is set to their
  current level. Nothing floods.
- **Death resets the mark.** The respawned player starts a new mark at their level after respawning. Normally that
  is 0, so every level you earn back counts again: dying is the price. With the `keepInventory` game rule on you keep
  your levels, so the new mark is the level you respawn with. Levels you had spent before dying (say you reached 30,
  spent down to 10, then died) count again when you earn them back.
- Double XP can level you up, and those levels count like any other. That's the point.

### Scope

`/chaosaholic scope player|world` decides who an event hits. It is saved per world.

- **player** (default): only the player who levelled up.
- **world**: every eligible player (Survival or Adventure, alive) **in the same dimension** as the player who
  levelled up, at the moment the event starts. Players in other dimensions aren't affected, and players who arrive
  later don't join it. Everyone who already has that event gets it extended; everyone who already has 8 events is
  skipped.

### Who is affected

Only players in Survival or Adventure mode who are online and alive. A player drops out of an event, and every
change it made to them is undone right away, when they:

- switch to Creative or Spectator,
- die,
- log out,
- change dimension.

The event keeps running for everyone else and ends once no one is left. A player's pending level-ups are cleared on
death, when they switch to Creative or Spectator, and when the mode is switched off.

### Hardcore and difficulty

Bad events never make a death unavoidable:

- every hazard (TNT, anvils, lightning) comes after a 3-second warning, 4 seconds on Hardcore;
- hazards land around you, never on your exact spot;
- movement events end in a safe state (Gravity Flip ends on slow falling);
- mob events spawn nothing on Peaceful. Difficulty sets the wave size, and Hardcore counts as Hard;
- `mobGriefing` decides whether explosions break blocks.

### Cleanup guarantees

Every temporary change goes through the framework, never through an event's own cleanup code, so nothing is left
behind:

| When | What happens |
|---|---|
| The timer runs out | The event ends and undoes everything |
| `/chaosaholic off` or `/chaosaholic stop` | Every running event (or the targets' events) ends at once, with full cleanup |
| The server stops | Every event ends before the final save |
| A player logs out, dies, changes dimension or goes Creative/Spectator | That player's effects, size and other changes are removed |
| A chunk unloads | Spawned mobs, TNT and sheep vanish (they're never saved). Size changes are never saved either |
| The server crashes | On the next start: spawned entities are gone, chickens turn back into their original mobs, renamed mobs get their names back, and temporary blocks are put back to what they were |
| An event throws an error | It is logged once, and the event is stopped and cleaned up like any other |

Effects are removed at the end unless you had a longer one of your own. Rewards are real, though: Sky Chest's chest,
Midas Hour's ores, Loot Piñata's drops and Double XP's experience are yours to keep.

## Commands

`/chaosaholic` is for operators (permission level 2, like `/gamerule`). In single-player it needs cheats: Allow
Commands on, or Open to LAN with Allow Cheats on.

| Command | What it does |
|---|---|
| `/chaosaholic [on\|off\|status]` | Turns Chaosaholic Mode on or off for this world. Plain `/chaosaholic` or `status` shows the mode and the active events. `off` also ends every running event |
| `/chaosaholic scope [player\|world]` | Sets who an event hits (see [Scope](#scope)). Without an argument, shows the current scope |
| `/chaosaholic events` | Lists all 30 events with their category, on/off and weight. Hover for a description, click to fill in `/chaosaholic event <id> ` |
| `/chaosaholic event <id> [on\|off\|status]` | Switches one event on or off for this world, or shows it |
| `/chaosaholic event <id> weight [0..1000]` | Sets how often an event is rolled compared to the others (default 100, 0 = never). Without a number, shows the weight |
| `/chaosaholic trigger <id> [targets]` | Starts that event now, even if the mode or the event is off. Still refuses players in Creative/Spectator, players with 8 events, and events that can't start there |
| `/chaosaholic roll [targets]` | Rolls a random event now, as a level-up would (switches and weights apply), even if the mode is off |
| `/chaosaholic stop [targets]` | Ends every event affecting the targets, with full cleanup |

Targets default to yourself. Command results (for command blocks): `on`/`off`/`status` return 1 for ON and 0 for
OFF; `trigger`, `roll` and `stop` return how many events they started or stopped.

Client command, for any player with the mod on their client: `/chaosaholic-notify status` and
`/chaosaholic-notify sound|message|effects [on|off]` (see [Notification settings](#notification-settings)).

Settings are stored per world in `data/chaosaholic/settings.dat`: the mode, the scope, and each event's on/off and
weight.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2 or 26.3, and run the game on Java 25.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and `chaosaholic-<version>.jar` in your `mods/` folder. Get
   the jar from [GitHub releases](https://github.com/nezo32/chaosaholic/releases) or CurseForge.
   Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for the settings screen.
3. Turn the mode on in one of two ways:
   - **New world:** Create World → Game tab → **Chaosaholic Mode** (right below Difficulty) is **ON** by default
     (switch it off there for a normal world).
   - **Existing world or dedicated server:** an operator runs `/chaosaholic on` (`/chaosaholic status` shows the
     current state). Worlds made without the button (dedicated servers, other launchers, worlds from before the mod)
     start with it off.

The mod is needed on the server. On the client it adds the Create World button, the settings screen and the camera
wobble; players without it can still join and play.

## Known quirks

- Double XP can level you up, and a level-up rolls another event. XP farms get *very* lively.
- Eternal Night moves the clock of the whole dimension, so every player there gets the night, not only the affected
  ones. <!-- sync -->
- Chickenpocalypse is permanent for a chicken that dies: the original mob is gone with it.
- Tiny World uses vanilla's scale attribute, so your reach and step height shrink with you.
- TNT Rain follows the `mobGriefing` game rule: with it on, the TNT breaks blocks like normal TNT.
- Milk only helps for a second: an event's effects come back until the event ends.
- A player with 8 running events keeps their level-ups in the queue (up to 10); the queue lives in memory, so pending
  level-ups are lost when the server stops.

## Repository layout

| Path | Contents |
|---|---|
| `fabric/` | Java Edition Fabric mod (Gradle), see [fabric/README.md](fabric/README.md) |
| `.github/workflows/` | CI (`ci.yml`), release (`release.yml`) and the reusable `reusable-*.yml` workflows |
| `scripts/` | CurseForge upload script and its tests |
| `docs/ci/` | Release runbook and reusable pipeline docs |
| `docs/branding/` | Logo, palette, player-facing strings, CurseForge page |

## Development

Needs JDK 25. Gradle can also run on Java 21 and download a JDK 25 toolchain.

```bash
cd fabric
./gradlew build                   # Minecraft 26.3: compile, JUnit, server GameTests; jars in build/libs/
./gradlew clean build -Pmc=26.2   # the same against 26.2
```

One jar runs on both 26.2 and 26.3. The Create World button is covered by a client GameTest (`./gradlew
runClientGameTest`). It needs a display (for example Xvfb), so `build` and CI don't run it.

Adding an event is one class, one registration line, lang entries and a GameTest: see
[CONTRIBUTING.md → Adding a new event](CONTRIBUTING.md#adding-a-new-event). Branch names, PR rules and the full list
of local checks are in [CONTRIBUTING.md](CONTRIBUTING.md).

## Releasing

To release, push an annotated `vX.Y.Z` tag on a commit of `main` (pre-releases use `-alpha.N`, `-beta.N` or `-rc.N`).
`release.yml` then does the rest:

1. Builds and tests the jar, stamping the tag's version into it.
2. Creates the GitHub release with notes generated from PR titles and labels.
3. Uploads the jar to CurseForge.

Don't edit the version in `fabric/gradle.properties` by hand. The tag sets the version.

- Maintainer runbook: [docs/ci/RELEASING.md](docs/ci/RELEASING.md)
- How the reusable pipeline works and how other projects can use it:
  [docs/ci/REUSABLE_RELEASE_PIPELINE.md](docs/ci/REUSABLE_RELEASE_PIPELINE.md)

## License

[MIT](LICENSE) © 2026 nezo
