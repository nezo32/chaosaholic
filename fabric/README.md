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
./gradlew test                           # JUnit only (pure core logic, lang files, no hardcoded text)
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
| `src/main/java/dev/chaosaholic/core/` | Pure logic: anti-farming level mark (`LevelMark`), weighted picker with rerolls, stacking/extension, per-player trigger queue, time format, notification settings, and every cap in `ChaosLimits`. It must not import Minecraft or Fabric classes (`CorePurityTest`). |
| `src/main/java/dev/chaosaholic/mode/` | Per-world settings (`ChaosSettings`, `data/chaosaholic/settings.dat`: mode, scope, per-event switch and weight), the `/chaosaholic` command, the Create World handoff (`PendingWorldMode`, `ModeBootstrap`). |
| `src/main/java/dev/chaosaholic/event/` | The event framework: `ChaosEvent` (one class per event), `ActiveEvent` (one running instance), `EventManager` (level watch, queues, rolls, ticking, boss bars, sync, every cleanup path), `EventRegistry` + `ChaosEvents` (registration list), `Announcer`. |
| `src/main/java/dev/chaosaholic/event/helper/` | Reusable, capped helpers: tracked effects / attribute modifiers / names, owned entities, temporary blocks (+ crash-safe store), area queries, safe spots, warnings, sounds, motion. |
| `src/main/java/dev/chaosaholic/event/impl/` | The 30 events, one class each. |
| `src/main/java/dev/chaosaholic/net/` | `chaosaholic:event_start` (announce on modded clients) and `chaosaholic:active_events` (client sync) payloads. |
| `src/main/java/dev/chaosaholic/mixin/` | The storage-access duck for the Create World handoff, a `MinecraftServer` accessor, and `EntityMixin` (owned temporary entities are never saved). |
| `src/client/` | The Create World toggle (`GameTabMixin`, right below Difficulty; `CreateWorldScreenMixin`), notification settings (`NotifyConfig`, `NotifyClient`, `NotifySettingsScreen`, `ModMenuIntegration`, `/chaosaholic-notify`), and `ClientEventState` for client-side event effects. |
| `src/main/resources/assets/chaosaholic/lang/` | `en_us.json` and `ru_ru.json` (same keys). |
| `src/test/` | JUnit tests. |
| `src/gametest/` | Server and client gametests, a separate test mod (`chaosaholic-gametest`) that is never packaged. |

The event framework and the event authoring guide are described in `ARCHITECTURE.md`.
