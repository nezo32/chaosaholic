# Contributing to Chaosaholic

Chaosaholic is a Java Edition Fabric mod in `fabric/`. It is built, tested and released by GitHub Actions from this
repository.

## Branch flow

`main` is always releasable, and nobody commits to it directly.

```
main ──●──────────●────────────●──── tag v1.3.0 → release
        \        / squash     /
         feature/anvil-rain  fix/night-clock-restore
```

### `main` is protected

- Changes land only through a pull request.
- These checks must pass: `branch-name`, `actionlint`, `scripts`, `mod / build`, `mod-26_2 / build`.
- PRs are **squash-merged**, so history stays linear: one commit per PR.
- No force-pushes to `main`.

### Branch names

Name your branch `<type>/<kebab-name>`, for example `feature/anvil-rain` or `fix/night-clock-restore`. The
`branch-name` check rejects other names.

| Type | Use for | PR label → release-notes section |
|---|---|---|
| `feature/`, `feat/` | new functionality | `enhancement` → New features |
| `fix/`, `hotfix/` | bug fixes | `bug` → Bug fixes |
| `docs/` | documentation only | `documentation` → Documentation |
| `chore/`, `ci/`, `build/`, `refactor/`, `perf/`, `test/` | everything else | `chore` → Maintenance |
| `release/` | release preparation | (none) → Other changes |

The prefix sets the PR label automatically, and the label decides where the PR appears in the release notes. PRs that
touch `fabric/` also get a `fabric` label.

### PR titles

The PR title becomes a line in the release notes, and from there in the CurseForge changelog. Write it as an imperative
sentence that makes sense to players: "Add the Anvil Rain event", not "anvil wip".

- Add the `breaking` label to a PR that breaks worlds, configs or compatibility. It gets its own section at the top.
- Add `skip-changelog` to leave a PR out of the notes (for example a typo fix in CI).

### Releases

- Only maintainers create releases.
- A release is an annotated tag on a commit of `main`: `vMAJOR.MINOR.PATCH`, optionally with `-alpha.N`, `-beta.N` or
  `-rc.N` (no `+build` suffix). A tag on any other branch is rejected.
- Everything after the tag is automated: the jar is built and attached to a GitHub release, then uploaded to
  CurseForge.
- The version lives **only in the tag**. Never edit `mod_version` in `fabric/gradle.properties` by hand.

Details: [docs/ci/RELEASING.md](docs/ci/RELEASING.md).

## Local checks before opening a PR

Run the same checks as CI (Java 25):

```bash
(cd fabric && ./gradlew build)                   # 26.3: compiles, JUnit, server GameTests, builds the jar
(cd fabric && ./gradlew clean build -Pmc=26.2)   # the same against 26.2
```

If you changed `.github/` or `scripts/`:

```bash
shellcheck scripts/*.sh scripts/test/*.sh
bash scripts/test/curseforge-upload.test.sh
bash scripts/check-inlined-script.sh
actionlint                                                                # https://github.com/rhysd/actionlint
```

`scripts/curseforge-upload.sh` has a byte-identical copy inside
`.github/workflows/reusable-publish-curseforge.yml`. Edit the script, then paste it into the workflow's heredoc;
`check-inlined-script.sh` tells you when they differ.

## Adding a new event

An event is one class. The framework does the rest: rolling, the boss bar, announcements, stacking, caps and
cleanup. All paths are under `fabric/src/`.

1. **The class:** `main/java/dev/chaosaholic/event/impl/<Name>.java`, extending `ChaosEvent` with an id, a category
   and a duration range in seconds (max 180, `INSTANT, INSTANT` for instant events). Implement only the hooks you
   need (`onPlayerAdded`, `onTick`, `onStop`, `allowDamage`, `afterBlockBreak`, `afterKill`, …) and make every
   temporary change through the instance trackers (`ev.effects()`, `ev.modifiers()`, `ev.entities()`, `ev.names()`,
   `ev.blocks()`), so it is undone on every end path. Event-specific numbers go in `public static final` constants;
   shared caps are in `core/ChaosLimits`. Bad events with a hazard start it through `Warning.thenRun(ev, …)`.
   Start with the reference events: `SpeedDemon` (effects), `FeatherFall` (routed damage hook), `TinyWorld` (area +
   attribute modifiers).
2. **Registration:** one line in `main/java/dev/chaosaholic/event/ChaosEvents.java`.
3. **Lang entries:** `chaosaholic.event.<id>` (name, 20 characters at most: it sits in the boss bar), `.desc`,
   `.announce`, and `.warning` if the event overrides `hasWarning()`, in **both**
   `main/resources/assets/chaosaholic/lang/en_us.json` and `ru_ru.json`. Never put player-facing text in Java: use
   `Texts.tr(key, args)`.
4. **GameTests:** `gametest/java/dev/chaosaholic/test/events/<Name>GameTests.java`, listed in
   `gametest/resources/fabric.mod.json`. Test at least the start, that the end (expiry and a forced stop) reverts
   everything, logout or Creative for per-player parts, the caps, and the `canStart` refusals.

`LangFileTest` checks both lang files (same keys, same placeholders, name/desc/announce for every event, warning keys
exactly for warning events), and `SourceTextTest` rejects hardcoded player text. The full event authoring guide
(hooks, helpers, cleanup and Hardcore rules) is in the javadoc of `event/ChaosEvent.java` and the helper classes in
`event/helper/`.

## CI and release pipeline

- `.github/workflows/ci.yml` runs on every PR and on `main`.
- `.github/workflows/release.yml` runs on version tags.
- The `reusable-*.yml` workflows are shared with other projects; changes there affect them too. See
  [docs/ci/REUSABLE_RELEASE_PIPELINE.md](docs/ci/REUSABLE_RELEASE_PIPELINE.md).
