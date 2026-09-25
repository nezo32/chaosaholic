# Chaosaholic for Fabric (Java Edition)

This is the Fabric mod for Minecraft Java **26.3** (the default) and **26.2**. For what the mod does and how to install it, see the [root README](../README.md).

## Requirements

- JDK 21 or newer to run Gradle. The build itself compiles with a **Java 25 toolchain**, which Gradle downloads automatically through the foojay resolver. To use a JDK you already have, pass `-Porg.gradle.java.installations.paths=/path/to/jdk-25`.
- Everything else comes from the Gradle wrapper (Gradle 9.5.1, Fabric Loom 1.17, Loader 0.19.5, Fabric API 0.161.0).
- Mod Menu (optional, 21.0.0 for 26.3 / 20.0.3 for 26.2) to open the settings screen. The build only compiles against it (`clientCompileOnly`, from the TerraformersMC maven); it is not bundled and not on the dev/gametest runtime classpath. To try it in `runClient`, drop the matching `modmenu-*.jar` into `run/mods`.

## Build and test

```bash
./gradlew build                          # 26.3: compile + JUnit + server gametests
./gradlew build -Pmod_version=1.2.3      # set the version (default 0.0.0)
./gradlew clean build -Pmc=26.2          # build and test against 26.2 instead
./gradlew test                           # JUnit only (pure core logic, lang files, source text)
./gradlew runGameTest                    # server gametests only (headless)
./gradlew runClient                      # dev client
```

`build/libs/` gets `chaosaholic-<version>.jar`, the jar you ship, and `chaosaholic-<version>-sources.jar`.

The client gametest opens a real client and needs a display. It is not part of `build`:

```bash
timeout 600 xvfb-run -a env LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1 ./gradlew runClientGameTest   # needs libegl1 libegl-mesa0
```

## Layout

| Path | What it holds |
|---|---|
| `src/main/java/dev/chaosaholic/core/` | Pure logic: `ChaosLimits` (every cap and timing constant), `LevelMark` (anti-farming), `WeightedPicker` (weighted roll with rerolls), `Stacking` (extensions, per-player room), `TriggerQueue` (1 s spacing, cap), `TimeFormat`, `EventIds`, `NotifySettings`. It must not import Minecraft or Fabric classes, and `CorePurityTest` checks this. |
| `src/main/java/dev/chaosaholic/mode/` | The per-world settings (`ChaosSettings`, SavedData), `Scope`, the `/chaosaholic` command, and the Create World handoff (`ModeBootstrap`, `PendingWorldMode`). |
| `src/main/java/dev/chaosaholic/event/` | The engine: `ChaosEvent` (the base class every event extends), `EventManager` (level watch, queues, rolls, tick, boss bars, sync, cleanup), `ActiveEvent` (one running instance), `EventContext`, `EventRegistry`, `ChaosEvents` (registration list), `Category`, `Announcer`, `PlayerLevels` (the `chaosaholic:level_mark` attachment). |
| `src/main/java/dev/chaosaholic/event/helper/` | What events build on, all capped and cleaned up by the framework: `TrackedEffects`, `TrackedModifiers`, `OwnedEntities`, `TrackedNames`, `TempBlocks` / `TempBlockStore`, `Area`, `Spots`, `Warning`, `Sounds`, `Motion`, `Marks`. |
| `src/main/java/dev/chaosaholic/event/impl/` | The 30 events, one class each. |
| `src/main/java/dev/chaosaholic/net/` | `chaosaholic:event_start` (start announcement for modded clients) and `chaosaholic:active_events` (timed events affecting the player, for client-side effects). |
| `src/main/java/dev/chaosaholic/mixin/` | A duck on `LevelStorageSource.LevelStorageAccess` that carries the Create World choice to the integrated server, a `MinecraftServer` accessor, and `EntityMixin` (entities owned by an event are never saved). |
| `src/client/` | `GameTabMixin` adds the **Chaosaholic Mode** button right below Difficulty, `CreateWorldScreenMixin` hands its value to the new world. Notifications: `NotifyConfig` (`config/chaosaholic.json` via the pure `core/NotifySettings`), `NotifyClient` (payload receiver), `ClientEventState` (synced active events, screen effects), `NotifySettingsScreen`, `ModMenuIntegration` (Mod Menu entrypoint only) and `NotifyCommand` (`/chaosaholic-notify`). |
| `src/main/resources/assets/chaosaholic/lang/` | `en_us.json` and `ru_ru.json` (same keys; see [Languages](#languages)). |
| `src/test/` | JUnit tests. |
| `src/gametest/` | Server and client gametests, including one `events/<Name>GameTests` class per event. This is a separate test mod, `chaosaholic-gametest`, and it is never packaged. |

## Behavior summary

- Chaosaholic Mode is stored per world in `data/chaosaholic/settings.dat`, together with the scope (`player` or `world`) and per-event `{enabled, weight}` overrides (unknown ids are kept). The Create World → Game button starts ON; worlds created elsewhere (dedicated servers, worlds from before the mod) start OFF. Set it with that button or `/chaosaholic on|off|status` (op level 2; result 1 = ON, 0 = OFF). There is no game rule.
- `EventManager` polls each player's experience level every server tick (no mixin, so every source counts, `/xp` included). Levels above the player's mark (the highest level since the last death, attachment `chaosaholic:level_mark`, persistent, not copied on death) queue one trigger each; the first observation of a player only initializes the mark. Mark updates happen even while the mode is off or the player is in Creative, so no backlog builds up.
- A player's queue holds at most 10 triggers (`QUEUE_CAP`) and starts one every 20 ticks (`START_INTERVAL_TICKS`). A roll picks from all events with their per-world weight (0 when switched off, 0..1000, default 100) and rerolls while `canStart` refuses. A trigger that finds nothing to start is dropped.
- Stacking: the same event on the same player extends the running instance by a new duration roll, capped at 180 s remaining (`MAX_REMAINING_TICKS`); different events stack up to 8 timed events per player (`MAX_ACTIVE_PER_PLAYER`), further triggers wait. Instant events never extend and don't count.
- Scope `world` = every eligible player in the trigger player's dimension at start. Eligible = online, alive, Survival or Adventure, not a fake player (`EventManager.isEligible`). Ineligible players (Creative/Spectator, dead, logged out, other dimension) are removed from their events the next tick, with per-player revert.
- Every temporary change is made through the trackers and reverted on every stop reason (expired, forced, mode off, no players left, server stopping, error). Entities spawned by events are never saved; replaced mobs (chickens) keep their original's full NBT and come back at the end or on the next load; renamed mobs get their names back; temporary blocks are listed in `data/chaosaholic/temp_blocks.dat` and restored after a crash. A hook that throws is logged once per event id and the instance is stopped and cleaned up.
- Caps live in `core/ChaosLimits`: area queries at most 24 blocks and 64 entities, 32 owned entities per event and 256 overall, 256 temporary blocks per event, 3 s warning before any hazard (4 s on Hardcore). Player-facing table: [root README](../README.md#the-events).

### Languages

- English (`en_us`) and Russian (`ru_ru`), in `src/main/resources/assets/chaosaholic/lang/`. Every player-facing string (Create World button and tooltip, `/chaosaholic` and `/chaosaholic-notify` feedback, the settings screen, event names, descriptions, announcements and warnings, boss bars, the Mod Menu summary) is a translation key. Vanilla terms (ON/OFF on buttons, Done) come from the game's own translation.
- Server-side strings go through `Texts.tr`, which uses `translatableWithFallback` with the English text from the jar, so players on vanilla clients (no mod lang files) read English.
- `LangFileTest` checks that `ru_ru.json` has exactly the keys of `en_us.json`, the same `%s`/`%1$s` placeholders per key, no empty values, a name/desc/announce for every event, `.warning` keys exactly for events that override `hasWarning()`, and translated Russian event names. `SourceTextTest` rejects `Component.literal("text")`, `translatableWithFallback` outside `Texts`, and literal keys missing from `en_us.json`. Add a key to both files.

### Notification settings

- Each event start shows a title (Bad) or an actionbar line (Good, Weird) and plays a sound. Each player can turn off the sound, the messages and the screen effects. The settings are client-side and per player, stored in `config/chaosaholic.json`: `{"notifySound": true, "notifyMessage": true, "screenEffects": true}`. A missing key means on.
- With [Mod Menu](https://modrinth.com/mod/modmenu) installed (optional, `suggests`), Mods → Chaosaholic → the config button opens the settings screen (**Event sound** / **Event messages** / **Screen effects**, saved on every click).
- Without Mod Menu, use the client command `/chaosaholic-notify status`, `/chaosaholic-notify sound|message|effects` (switches it) or `/chaosaholic-notify sound|message|effects on|off`. The root is `chaosaholic-notify`, not `chaosaholic notify`, so it cannot clash with the server's `/chaosaholic` op command.
- When the client has the mod, the server sends only the `chaosaholic:event_start` payload and the client shows/plays according to its settings. Players who join without the mod (vanilla client) always get the default title/actionbar + sound from the server, and no screen effects.
- Boss bars and danger warnings (`⚠` actionbar + rising ping, 3 s before a hazard) are always vanilla packets and ignore these settings.
