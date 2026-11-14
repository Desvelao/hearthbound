# AGENTS.md

Guidance for AI coding agents working in this repository. `README.md` is for humans.
Milestones 0 to 7 (the MVP, phase 2 and phase 3) are implemented; milestone 8 is not.

## What this is

A native, fully offline Android habit RPG (Kotlin, Compose, Material 3): habits, dailies, to-dos, a character that
gains XP and gold and loses health, rewards, a gear and pet shop, task value, timed boss quests, classes with
mana and skills, reminders, tags, stats, a dailies home-screen widget, JSON backup and pixel-art avatar sprites.

## Hard constraints (do not break)

1. **No `INTERNET` permission**, and no analytics, ads, remote config or network libraries. Check the merged manifest
   after any dependency change. Libraries add `ACCESS_NETWORK_STATE`, `WAKE_LOCK` and `FOREGROUND_SERVICE`; that is fine.
2. **Docker-only toolchain.** Never ask the user to install Java, Gradle or the SDK. Everything runs through
   `docker compose run --rm dev ...`.
3. **Native only.** No cross-platform framework, no WebView.
4. **Original art and names.** Habitica is inspiration for mechanics only. Sprites are drawn here.
5. **Never commit secrets:** `*.jks`, `keystore.properties` and `.env*` are git-ignored. Check `git status` before
   committing and never `git add -A` blindly (the user keeps local notes such as `claude_session/` in the root).
   `app.properties` (name, id, About link) is tracked on purpose: it holds no secrets and CI needs it. A released
   keystore cannot be regenerated: never delete or overwrite `release.jks`.
6. **Data safety.** Any schema change needs a version bump and a real `Migration` once users have data. Never use
   destructive migration. The exported schema in `core/data/schemas/` is committed.
7. **Changelog.** Every user-visible change adds a line under `[Unreleased]` in `CHANGELOG.md`. When a release is
   tagged, its entries move to a `## [x.y.z] - date` heading and `[Unreleased]` starts empty; the release workflow
   publishes that section as the GitHub Release notes and fails if it is missing or empty.

## Commands

```bash
docker compose build
docker compose run --rm dev ./gradlew :core:domain:test testDebugUnitTest lint assembleDebug   # the standard check
docker compose run --rm dev python3 -I tools/sprites.py --check                                 # validate art only
docker compose run --rm dev ./gradlew installDebug      # needs a device or emulator
```

The dev image is tagged `android-dev:python` in `docker-compose.yml`, not plain `android-dev`: that tag is shared with other
projects, and another project's build once replaced it with an image that has no python (`tools/sprites.py` needs it). Run
`docker compose build` if the image is missing.

The `Makefile` wraps these commands (`make check`, `make sprites`, `make debug`, `make install`, `make release`, ...;
`make help` lists them). `RUN` defaults to the Docker wrapper; `.github/workflows/release.yml` builds on GitHub's runner,
not in Docker, and calls `make sprites RUN=`, `make check RUN=` and `make release RUN=` so CI and local use the same Gradle
commands. Keep the Makefile and the workflow in sync. The Docker-only rule is about the user's host, not the runner.

A change is done when tests pass, `lint` reports **0 errors**, and the release build still builds (use a throwaway
keystore under `build/`, never a real one). Lint catches real mistakes, for example `Locale.getDefault()` in Compose.

## Layout

```
core/domain/    pure Kotlin/JVM: Engine, Rollover, Schedule, ReminderTimes, Catalog (shop items, gear bonuses). No Android imports. JUnit.
core/data/      Room (entities, DAOs, AppDatabase), repositories, DataStore settings, backup. Robolectric tests.
feature/tasks/      habit, daily, to-do lists, the editor (adapts to the type) and the Today view (today/)
feature/character/  header strip, stat bars, Hero page (pixel scene + pet + stats), History, sprites, hair/body/gear pickers, the Shop
feature/rewards/    rewards tab
feature/settings/   day start, pause, theme, hair, tags, backup, notification and battery help
app/            navigation, Hilt wiring, onboarding, WorkManager worker, alarm scheduler, receivers, widget/ (Glance dailies widget)
art/            palette.yaml, STYLE.md, manifest.yaml, sprites/*.sprite, contact sheet (committed)
tools/          sprites.py (validate and render), png2sprite.py (PNG back to a grid)
```

The engine is one function, `Engine.reduce(state, event, clock)`. The UI, the rollover worker and the tests all call
it through `GameRepository.dispatch`, which is the only write path. Screens never call the engine directly.

## Navigation

The bottom bar has four places: **Today**, **Tasks** (Habits, Dailies and To-dos as sub-tabs), **Rewards** and **Hero**.
The character strip (bars and gold) sits above every tab except Hero, which shows the whole character. Settings is the
gear on the Hero scene, and History opens from Hero. **Rewards** has two sub-tabs, Rewards and Shop (like Tasks has three). `HomeScreen.kt` owns this; pushed routes are `editor`, `settings`
and `history`.

## Rules that are easy to get wrong

- **History numbers come from the event log through `HistoryMath`** (pure, in `core/data`). A day's count is plus taps
  and ticks net of undos; a streak is consecutive game days with at least one; today not being over never breaks it.
  Undoing a *minus* has no xp or gold, which is how `completionDelta` tells it from undoing a plus.
- **Today's rules live in `TodayBuilder`** (pure): dailies due today, to-dos with no date or due by today, habits always
  listed but not counted in the ring. Kotlin cannot smart-cast a property from another module, so copy `dueDate` to a
  local first.
- **Do not use `LocalDate.EPOCH`** (Android 14+). Lint flags it; unit tests on a newer JVM do not.

- **Rollover must never score a day twice or skip one.** It processes the days between `lastRolloverDate` and today,
  then stores today. **The stored date only ever moves forward** (clock set back, time-zone travel and a changed
  day-start hour all used to double-score a day). More than two ended days, or pause mode, means no damage.
- **Time comes from the injected `Clock`.** In production that is `SystemZoneClock`, which follows the phone's zone;
  `Clock.systemDefaultZone()` freezes the zone at creation. Tests use a fake clock.
- **Room `@Insert(onConflict = REPLACE)` deletes the row first** and cascades to its children. Use `@Upsert` to update.
- **Undo:** the snackbar uses an in-memory `Change` snapshot. Un-ticking a checkbox uses `GameRepository.uncomplete`,
  which reverses the latest tick from the event log, so it works after a restart.
- **Backup entities are the file format.** The format is version 1 (the development versions were reset before the first release). Gear rows from a file are cleaned (unknown ids, unowned or wrong-slot rows dropped). A renamed column changes the file. `BackupTest` has a fixed v1 file; if it
  stops parsing, bump `BackupFile.CURRENT_VERSION` and keep reading the old version. Treat a backup as untrusted input.
- **The new-day result is produced before any screen listens.** `PlayFeedback.rollover` holds it, and
  `rememberPlayFeedback` shows it from a separate coroutine. Consuming it inside the effect that shows it cancels
  that effect (this dropped the message once; `PlayFeedbackHostTest` pins it).
- **Reminders:** one alarm per reminder for its next occurrence; the receiver schedules the one after. Alarms are
  rebuilt from the database on app start, boot, update, and clock, zone or exact-alarm changes. Exact alarms can be
  denied (the default on new installs), so there is an inexact fallback.
- **Catalog ids and prices must equal `art/manifest.yaml`** (`GearTest` checks it), and every id needs a sprite in
  `ItemSprites` (`ShopTest` checks it). Pets are 16x16 and drawn beside the hero in the Hero scene only; gear layers are
  32x32 and stack body, outfit, armour, hair, headgear, weapon. Armour is an overlay on the outfit, one grid for all bodies.
- **Gear lives in `owned_items` and `equipped`, not on the character row**: `GameRepository.persist` rewrites that row
  from the domain `Character` and would wipe extra columns. Bonuses reach the engine as `GameState.bonuses`, built in
  `GameRepository.loadState` (and `observeBonuses` for the Today preview), and are zero while the Settings switch
  "Gear bonuses" is off. The look never depends on that switch. Armour never takes damage below 1.
- **Task value** (`Task.value`) applies to habits and to-dos only, never dailies (their streak bonus is their curve). A plus
  tap is paid at the value before the tap, then value goes up 1; minus goes down 1. Rollover lowers idle habits by 1 and open
  to-dos by 0.25, only on days that count (no pause, not a long absence). The multiplier is `1 - 0.05 x value`, kept
  between 0.5 and 1.5. Every number is in `Constants.kt`. Backup clamps value on import.
- **The widget runs the rollover first**, both when drawing and when ticking (`WidgetActions`), so a widget left over
  midnight never shows or un-ticks yesterday's list. Its list comes from `TodayBuilder`, the same rule as the Today tab.
  It is redrawn from `HabitApp` (while the process is alive), `RolloverWorker` and its own taps.
  Its content also **watches** the data and the language (`WidgetActions.observe()` inside `provideContent`): Glance keeps a session
  alive for a while and an `update` sent to it does not rebuild what `provideGlance` captured, so a captured language or list
  would stay stale (a language change showed only after the session ended). Keep anything the widget draws reactive.
- **Quests (milestone 7)** are timed bosses (`QuestCatalog`, `Quest` in `GameState.quest`, one at a time). A plus tap, a daily tick or
  a to-do hurts the boss by `BOSS_DAMAGE_BASE x difficulty` (plus class bonus); the damage is stored in `Change.bossDamage` and the
  `task_events.bossDamage` column. The killing blow puts the boss reward **inside that same change**, so `Undo` and the log-based
  `Revert` reverse it with no special case. A finished quest (WON or ESCAPED) stays in state until `DismissQuest`, which writes a
  `quest_history` row (`GameRepository.persist` diffs `quest`). Rollover escapes a boss at the end of its deadline day; on days that
  do not count (pause, long absence, quests off) the deadline moves forward instead, so a break never costs a boss.
- **Classes (milestone 7)** unlock at level 10 (`Classes`, fields on `Character`: `classId`, `mana`, `statMight/Mind/Vigor`). Free stat
  points are `level - 10 - spent` and spent points are not refunded when a level is lost. Class perk and stats are added to
  `GameState.bonuses` in `loadState` (gear + class) and are zero when the switch is off. Mana regens per ended day at rollover, also
  during pause. Skills (`Event.CastSkill`) are instant, not undoable, and do nothing (no mana spent) when they cannot work.
- **Feature switches:** `Settings.quests` and `Settings.classes` (default on) reach the engine as `GameState.features`. Off means the
  rules do nothing and the data is kept. A new switch goes in `Settings`, the DataStore key, the flow mapping, `restore`,
  `BackupSettings` and `toSettings()`. Onboarding (`OnboardingScreen`, third of four pages) lets the player set the quests, classes and gear
  switches before the first game; a new switch should get a row there too, and `OnboardingViewModel.finish` saves them.
- **Backup quests and classes:** `quest`, `questHistory`, the class fields and the two switches are all defaulted, so older files parse.
  Import cleans them as untrusted: unknown boss or class ids dropped, hp clamped, stats reset if over the allowance, mana clamped.
- Boss ids in `QuestCatalog` must equal `art/manifest.yaml` (`QuestTest` checks it) and each needs a `BossSprites` entry.
- **Languages (English, Spanish, German, French, Italian)** and a "System default" that follows the phone. `Settings.language`
  (`AppLanguage.tag`, default `system`) is applied at the root by `ProvideAppLanguage` (`app/.../LanguageProvider.kt`): it
  overrides `LocalContext`, `LocalConfiguration` and `LocalResources` and sets the JVM default locale, so strings, plurals and
  dates change at once with no restart. Code outside Compose (reminder notifications, the Glance widget, the notification
  channel, starter tasks) calls `context.withLanguage(AppLanguage.fromTag(settings.language))`. Read the locale in Compose from
  `LocalConfiguration.current.locales[0]`, never from `LocalLocale` (it does not follow our override). The picker is
  `LanguageRow` in `feature/settings`, used in Settings and on the first onboarding page. The language is in backups.
- **Every user-visible word is a string resource, in all five languages.** English goes in `values/strings.xml` of the module
  that shows it, with `values-es`, `-de`, `-fr` and `-it` beside it. Use positional placeholders (`%1$s`, `%2$d`), `<plurals>`
  for counts (Spanish, French and Italian also need `many`), and never build a sentence from pieces. **No English text in
  `core/domain` or `core/data`**: the domain keeps ids (`Catalog`, `QuestCatalog`, `Classes`), and `Names` in `core/data` maps
  each id to a string resource (like `ItemSprites` for art); view models hold typed values (`SettingsMessage`, `ImportError`,
  `RowNote`), not text. Lint's `MissingTranslation` is an error and `TranslationsTest` checks keys, placeholders and plurals.
  The in-app language choice needs every language in every install, so `bundle { language { enableSplit = false } }` stays.
  A literal `%` in a string that takes arguments is written `%%`. The app name stays untranslated (brand parameter).
- **Hair ids** in `HairStyle` and **body ids** in `BodyType` (Male, Female, Neutral; neutral is `body_default`) must match `art/manifest.yaml` (`AccessibilityTest` checks it).
- AGP 9 has built-in Kotlin: do not apply `org.jetbrains.kotlin.android`. `compileSdk`/`targetSdk` are 37, `minSdk` 26.
- Keep `surfaceVariant` different from the surface containers in `ui/Theme.kt`, or text on cards turns muted.
- The launcher icon folder must stay `mipmap-anydpi-v26`.
- The debug and release builds use different signatures: uninstall one before installing the other.

## Testing notes

- **Migrations:** none yet. The database and the backup format were reset to version 1 before the first release, so `core/data/schemas/.../1.json` is the baseline.
  The first schema change after release bumps the version, adds a `Migration` and brings back a `MigrationTest` (the exported schemas are already test assets). KSP writes a
  new `N.json` during the build, so after bumping the version build once, then run the tests (the first run misses the file).

- Robolectric 4.17 cannot run SDK 37 on Java 17, so tests are pinned with `sdk=35` in each module's
  `src/test/resources/robolectric.properties`. The container user has no passwd entry, so the root `build.gradle.kts`
  points test JVMs at `$HOME` (`/gradle`).
- A view model's `viewModelScope` runs on Main, and under Robolectric the test thread is Main. Do not block it with a
  latch; `setMain(UnconfinedTestDispatcher())` first. Room and DataStore use real threads, so await a result
  (`flow.first { ... }`) instead of `advanceUntilIdle()`.
- Compose UI tests run on the JVM (`createComposeRule` with Robolectric); there is no emulator in CI. Robolectric's default
  window is tiny: lazy lists then skip rows below the fold and clicks miss, so give such tests
  `@Config(qualifiers = "w411dp-h1600dp-xxhdpi")`.
- **Emulator checks** (the user owns the emulator): `adb root` works on userdebug images, so you can move the clock
  with `adb shell date MMDDhhmmYYYY.ss`. Set `settings put global auto_time 0` first and **restore `auto_time 1` and
  `font_scale 1.0` afterwards.** `uiautomator dump` plus `input tap` drives the UI. Clearing app data deletes the
  user's local history, so say so first.

## Art pipeline

Sprites are text grids in `art/sprites/`. `tools/sprites.py` validates them (canvas size per layer type, palette
characters only, inside the anchor box), renders PNGs into `feature/character/src/main/res/drawable-nodpi/`, and writes
`art/contact_sheet.png` and `art/avatar_preview.png`. After changing a sprite, open the contact sheet and check it
against `art/STYLE.md`. Hair colours are palette swaps in the manifest, not new grids. Layers draw in the order body,
outfit, hair.

## Brand parameters

`APP_NAME`, `APP_ID`, `APP_VERSION_NAME`, `APP_VERSION_CODE`, `APP_ABOUT_URL` (the Settings > About link, default
`https://example.com`, blank hides it) come from a `-P` property, an environment variable, the tracked
`app.properties` (holds `APP_NAME`, `APP_ID`, `APP_ABOUT_URL` only; the versions are set by the release workflow), then
the default (`io.github.desvelao.hearthbound`, "Hearthbound"). Signing uses
`SIGNING_KEYSTORE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` or `keystore.properties`.
