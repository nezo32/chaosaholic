# Chaosaholic — Branding

**Tagline:** *Level up. Unleash chaos. Survive it.*
Alt: *Every level is a coin flip. Sometimes the coin explodes.*

## CurseForge description

> **Chaosaholic** turns every level-up into a dice roll. Each time you gain an experience level, a **random chaos
> event** kicks off: sometimes **good** (Midas Hour, Double XP, Moon Jump), sometimes **bad** (TNT Rain, Anvil Rain,
> Bee Swarm) and sometimes just **weird** (Chickenpocalypse, Sheep Disco, Gravity Flip). A boss bar counts down every
> active event, a title and a sound tell you what just hit you, and dangerous events always give you a 3-second
> warning. Farm XP at your own risk. The mode is a toggle on the world-creation screen, saved with the world, and
> operators can flip it any time with `/chaosaholic on|off`, switch single events off, or trigger one on purpose.
> Each player can mute the event sound, hide the messages or turn off screen effects like camera shake.
> A **Fabric mod for Java 26.2–26.3**.

## Features
- 🎲 **Every level is a roll:** gain a level, get a random event. 30 of them, in three flavours.
- 🍀 **Good, 😈 Bad, 🌀 Weird:** 9 blessings, 10 disasters, 11 things that make no sense at all.
- ⏱️ **Always readable:** a coloured boss bar per active event with the time left, plus a title and a sound on start.
- ⚠️ **Fair warning:** TNT Rain, Anvil Rain and Lightning Storm give a 3-second heads-up, and every hazard has a
  safety cap (TNT and lightning never land right on you, spawned mobs and bees are removed when the event ends).
- 🔁 **Everything goes back:** chickens turn back into the mobs they were, tiny things grow back, night gives the sun
  back, anvils and party sheep are cleaned up.
- ⚙️ **Toggle anywhere:** an ON/OFF button at world creation plus operator commands (`/chaosaholic`) for existing
  worlds and servers: turn the mode on or off, switch single events off, list them, or trigger one yourself.
- 🔕 **Your call on noise:** turn the event sound, the messages or the screen effects off (Mod Menu or
  `/chaosaholic-notify`). Danger warnings always come through.
- 🌍 **English and Russian** out of the box.

## CurseForge project settings (mirror Enchantaholic)

| Setting | Value |
|---|---|
| Project name | Chaosaholic |
| Summary (short description) | Level up. Unleash chaos. Survive it. Every level-up starts a random good, bad or weird event. |
| Logo | `docs/branding/curseforge_logo.png` (400×400) |
| Main category | **Miscellaneous** |
| Extra categories | **Adventure and RPG**, **Mobs**, **Fabric** loader tag |
| Game versions | 26.2, 26.3, Fabric, Java 25, Client, Server (as `CURSEFORGE_GAME_VERSIONS`) |
| Environment | Client and Server (the server does the chaos; the client part adds the Create World button, settings and screen shake) |
| Dependencies | Fabric API (required), Mod Menu (optional) |
| License | MIT |
| Source | https://github.com/nezo32/chaosaholic |
| Issues | https://github.com/nezo32/chaosaholic/issues |
| Description body | `docs/branding/curseforge_description.md` |

## Logo

`curseforge_logo.png` (400×400 RGBA; also the mod icon at `fabric/src/main/resources/assets/chaosaholic/icon.png`)
is drawn on the same 10 px pixel grid as Enchantaholic's: same double purple frame with gold corner studs, same dark
background, lilac sparkles, gold level chevrons and ink outline. The central motif is a lime **experience orb split
by a magenta chaos bolt**, with a die (randomness) in place of Enchantaholic's green "+". No text.

## Color palette
| Role | Hex |
|---|---|
| Background, deep purple | `#26103C` |
| Background, darkest / border | `#140822` |
| Ink / outline | `#180A28` |
| Frame purple | `#783CBE` |
| Frame shade | `#3E1C60` |
| Sparkle lilac | `#D696FF` |
| XP orb highlight | `#F0FFB0` |
| XP orb light | `#C8FF50` |
| XP orb mid (Good green) | `#80FF40` |
| XP orb dark | `#3CB81C` / `#1E6A2A` |
| Chaos bolt light | `#FFB8F4` |
| Chaos bolt mid (Weird magenta) | `#F050D0` |
| Chaos bolt dark | `#A02490` |
| Level gold (warning) | `#FFD640` |
| Level gold shade | `#B07010` |
| Die face / shade | `#F4ECFF` / `#B8A4DC` |

In-game text: category colours **Good `§a` (green)**, **Bad `§c` (red)**, **Weird `§d` (light purple)**; warnings
`§6` (gold) with a ⚠; secondary text (announce lines, times) `§7` (gray). Boss bars: Good `GREEN`, Bad `RED`, Weird
`PURPLE`, overlay `NOTCHED_10`.

## Player-facing strings (en_us)

Final key list is owned by the architect; this is the wording (Russian lives in `ru_ru.json`, same keys).

| Key | Text |
|---|---|
| `chaosaholic.category.good` | Good |
| `chaosaholic.category.bad` | Bad |
| `chaosaholic.category.weird` | Weird |
| `chaosaholic.bossbar.format` | %1$s — %2$s |
| `chaosaholic.time.seconds` | %s s |
| `chaosaholic.time.minutes` | %1$s:%2$s |
| `chaosaholic.announce.title` | %s |
| `chaosaholic.announce.category` | %s event |
| `chaosaholic.createWorld.toggle` | Chaosaholic Mode |
| `chaosaholic.createWorld.toggle.tooltip` | Every time you gain an experience level, a random chaos event starts: good, bad or just weird. Saved with this world. Operators can change it later with /chaosaholic on\|off. |
| `chaosaholic.createWorld.scope` | Chaos Scope |
| `chaosaholic.createWorld.scope.player` | Just Me |
| `chaosaholic.createWorld.scope.world` | Everyone |
| `chaosaholic.createWorld.scope.tooltip` | Just Me: an event hits only the player who levelled up. Everyone: it hits every player in the world. Operators can change it later with /chaosaholic scope player\|world. |
| `chaosaholic.command.value.on` | ON |
| `chaosaholic.command.value.off` | OFF |
| `chaosaholic.command.on` | Chaosaholic Mode is now ON for this world |
| `chaosaholic.command.off` | Chaosaholic Mode is now OFF for this world |
| `chaosaholic.command.status.on` | Chaosaholic Mode is ON in this world |
| `chaosaholic.command.status.off` | Chaosaholic Mode is OFF in this world |
| `chaosaholic.command.status.active` | Active events: %s |
| `chaosaholic.command.status.none` | No active events |
| `chaosaholic.command.scope.set` | Chaos scope is now: %s |
| `chaosaholic.command.scope.status` | Chaos scope: %s |
| `chaosaholic.command.scope.player` | only the player who levelled up |
| `chaosaholic.command.scope.world` | every player in this world |
| `chaosaholic.command.event.on` | %s can happen again in this world |
| `chaosaholic.command.event.off` | %s will no longer happen in this world |
| `chaosaholic.command.event.status` | %1$s: %2$s |
| `chaosaholic.command.events.header` | Chaos events (%1$s of %2$s enabled): |
| `chaosaholic.command.events.line` | %1$s · %2$s · %3$s |
| `chaosaholic.command.events.hover` | %1$s<br>/chaosaholic event %2$s on\|off |
| `chaosaholic.command.trigger` | Started %1$s for %2$s |
| `chaosaholic.command.trigger.random` | Rolled %1$s for %2$s |
| `chaosaholic.command.stop` | Stopped active events: %s |
| `chaosaholic.command.error.unknownEvent` | Unknown event: %s. See /chaosaholic events |
| `chaosaholic.command.error.noTarget` | %s has no valid target right now |
| `chaosaholic.command.error.disabled` | %s is switched off in this world |
| `chaosaholic.command.error.modeOff` | Chaosaholic Mode is OFF in this world. Turn it on with /chaosaholic on |
| `chaosaholic.command.error.noEvents` | Every event is switched off, so nothing can roll |
| `chaosaholic.settings.title` | Chaosaholic Settings |
| `chaosaholic.settings.notifySound` | Event sound |
| `chaosaholic.settings.notifySound.tooltip` | Play a sound when a chaos event starts. Danger warnings (TNT, anvils, lightning) always play. |
| `chaosaholic.settings.notifyMessage` | Event messages |
| `chaosaholic.settings.notifyMessage.tooltip` | Show the title and the actionbar line when a chaos event starts. Boss bars and danger warnings always show. |
| `chaosaholic.settings.screenEffects` | Screen effects |
| `chaosaholic.settings.screenEffects.tooltip` | Allow camera shake and other screen effects. Turn this off if motion bothers you. |
| `chaosaholic.command.notify.sound` | Event sound: %s |
| `chaosaholic.command.notify.message` | Event messages: %s |
| `chaosaholic.command.notify.effects` | Screen effects: %s |
**Announcement:** when an event starts, the player sees its name as a title in the category colour, with the
`.announce` line as a gray subtitle (Bad events), or `✦ <name> · <announce>` on the actionbar (Good and Weird events).
Build it with `Component.translatable(...)`, never by concatenating strings. The three dangerous events show their
`.warning` line on the actionbar (`⚠`, gold) for 3 seconds before the first hazard, even when messages are off.

## Event names

Keys: `chaosaholic.event.<id>` (name, ≤ 20 characters because it sits in the boss bar), `.desc` (one line, shown in
`/chaosaholic events` hovers and the README), `.announce` (start subtitle) and `.warning` (only `tnt_rain`,
`anvil_rain`, `lightning_storm`).

| id | Category | Name | Name (ru_ru) | `.desc` |
|---|---|---|---|---|
| `midas_hour` | Good | Midas Hour | Час Мидаса | Ores you mine drop double. |
| `feather_fall` | Good | Feather Fall | Пёрышко | You take no fall damage. |
| `loot_pinata` | Good | Loot Piñata | Пиньята | Mobs you kill drop extra random loot. |
| `speed_demon` | Good | Speed Demon | Демон скорости | Speed III and Haste II. |
| `chest_from_sky` | Good | Sky Chest | Сундук с неба | A chest full of random loot lands nearby. It's yours to keep. |
| `double_xp` | Good | Double XP | Двойной опыт | Experience orbs you pick up count double. |
| `healing_aura` | Good | Healing Aura | Аура исцеления | Regeneration II. |
| `iron_skin` | Good | Iron Skin | Железная кожа | Resistance II. |
| `moon_jump` | Good | Moon Jump | Лунный прыжок | Jump Boost III and no fall damage. |
| `tnt_rain` | Bad | TNT Rain | Динамитный дождь | Lit TNT falls around you, never right on top of you. |
| `anvil_rain` | Bad | Anvil Rain | Наковальнепад | Anvils crash down on marked spots nearby. |
| `mob_surprise` | Bad | Mob Surprise | Мобы в гости | A wave of hostile mobs shows up nearby. |
| `hunger_games` | Bad | Hunger Games | Голодные игры | Hunger III: your food bar drains fast. |
| `butterfingers` | Bad | Butterfingers | Дырявые руки | Every few seconds you may drop the item in your hand. |
| `eternal_night` | Bad | Eternal Night | Вечная ночь | Night falls at once and lasts until the event ends. |
| `lightning_storm` | Bad | Lightning Storm | Гроза | Lightning strikes around you, never on you and without fire. |
| `sluggish` | Bad | Sluggish | Сонная муха | Slowness II and Mining Fatigue I. |
| `bee_swarm` | Bad | Bee Swarm | Пчелиный рой | A few angry bees come after you. |
| `blackout` | Bad | Blackout | Затмение | Darkness closes in for a few seconds. |
| `gravity_flip` | Weird | Gravity Flip | Кувырок гравитации | You float up and drift down by turns. It always ends softly. |
| `chicken_apocalypse` | Weird | Chickenpocalypse | Курапокалипсис | Nearby mobs turn into chickens until the event ends. |
| `tiny_world` | Weird | Tiny World | Мини-мир | You and everything nearby shrink to half size. |
| `bouncy_floor` | Weird | Bouncy Floor | Пол-батут | Landing bounces you like slime, with no fall damage. |
| `upside_down` | Weird | Upside Down | Вверх ногами | Nearby mobs flip upside down. |
| `swap` | Weird | Swap | Рокировка | You swap places with a random nearby mob. |
| `sheep_disco` | Weird | Sheep Disco | Овечье диско | Rainbow sheep show up and start a party. |
| `slippery` | Weird | Slippery | Гололёд | Every block feels like ice. |
| `random_teleport` | Weird | Random Teleport | Телепорт наугад | You teleport to a random safe spot nearby. |
| `screen_shake` | Weird | Screen Shake | Тряска экрана | Your camera wobbles. You can turn screen effects off in the settings. |
| `glow_party` | Weird | Glow Party | Сияющая тусовка | Everything nearby glows, even through walls. |
