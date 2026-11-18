# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). Add every user-visible change under `Unreleased`; the release
workflow publishes the matching section as the GitHub Release notes.

## [Unreleased]

### Added
- A "Paused" banner shows above the tabs and on the Hero page while pause mode is on; tap it to resume the game.
- Add several tasks at once from pasted text (the small pencil button on the Tasks tab): one task per line, each line
  gets a guessed type (habit, daily or to-do) that you can change, and a button opens the full editor for it first.
  An info button explains the words and `h:`, `d:`, `t:` prefixes the guess understands.

## [0.1.0] - 2026-11-14

First release.

### Added
- Habits, dailies and to-dos with a Today view, repeat schedules, tags and optional reminders.
- A character that gains XP and gold, loses health when dailies are missed, and levels up; task value that
  changes the reward by how often a habit or to-do is done.
- Rewards you define yourself, plus a shop with gear and pets.
- Timed boss quests that your completed tasks damage, and classes with mana and skills from level 10.
- Stats and history with streaks, built from the event log, and a dailies home-screen widget.
- Pixel-art avatar with body types, hair and gear, and a Hero page with the scene and pet.
- Optional pause mode, day-start hour, and switches for gear bonuses, quests and classes.
- JSON backup export and import.
- English, Spanish, German, French and Italian, plus a System default that follows the phone.
- A flame-and-check-mark app icon, with a themed (monochrome) version and a matching notification icon.
- Fully offline: no account, no server and no `INTERNET` permission.
- Releases publish the APK, the AAB, the R8 mapping and a `SHA256SUMS` file so a download can be verified.

[Unreleased]: https://github.com/Desvelao/hearthbound/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Desvelao/hearthbound/releases/tag/v0.1.0
