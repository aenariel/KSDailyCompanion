# KSCompanion (KSDailyCompanion)

Personal Android companion for Kingshot: reminds you of recurring events for each of your characters,
with reminder times you choose (e.g. 12 h and 2 h before). Everything stays on the phone; no account, no server.

- **Characters**: several per phone, each in an alliance (alliance → kingdom).
- **Legions**: on Swordland / Tri-Alliance registration days the app asks which legion each character is in
  (notification buttons *Legion 1 / Legion 2 / Not playing*), then reminds you before that legion's battle.
  A "usual legion" per character covers the weeks you don't answer.
- **Times are UTC** (game time), always shown with your local time next to them.
- **Alliances** (3-character tags) hold the times: legion hours, Bear Traps…
- **Kingdoms** (#number) hold the dates of their own rotating events (Fishing, Desert Trial / Champion's Way,
  Mystic Divination, Cesares Fury…) and one-off events; every alliance in the kingdom follows them.
- Each character shows as `Name (Tag, #kingdom)` with its own colour. Game-only icons (private-use characters)
  are shown as emoji in the app.
- Optional **daily summary** notification with the day's events.

## Install

[**Add to Obtainium**](http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/aenariel/KSDailyCompanion)
(installs from the GitHub releases and keeps the app updated), or download the APK from the latest
[release](https://github.com/aenariel/KSDailyCompanion/releases/latest).

## Shared data with KSDailyAgenda

Same model as [KSDailyAgenda](https://github.com/aenariel/KSDailyAgenda):

- The fixed 28-day cycle comes from `https://aenariel.github.io/KSDailyAgenda/events.json`
  (checked when the app opens; a bundled copy in `app/src/main/assets/events.json` is used offline).
  Bump `version` there and the app picks it up, keeping each user's on/off choices.
- Rules are the agenda's: `every` (date + every N days), `event` (event id, day from..to, `unless`), `weekday`, `date`.
  `model/Engine.kt` is a direct port of `cycleHit` / `ruleHits`; `EngineTest` checks it against known dates.
- *Settings → Import* reads an agenda backup ("Download settings", "Show everything" or "Show this alliance"):
  its alliances and kingdoms are added; characters, picks and settings stay.

## How reminders work

One inexact alarm is set for the next notification (no exact-alarm permission). When it fires, everything due
since the last firing is posted and the next alarm is set. Boot, app update and time-zone changes re-plan it.
On Xiaomi/HyperOS turn on *Autostart* and set battery to *No restrictions* (buttons in Settings).
*Settings → Log* shows what was planned and fired (also in logcat, tag `KSDC`).

## Build

GitHub Actions builds a signed APK on every push to `main`/`dev` (artifact) and publishes a release for tags `v*`
(install and update with [Obtainium](https://github.com/ImranR98/Obtainium) or by downloading the APK).

Signing secrets (repository → Settings → Secrets and variables → Actions):
`KS_KEYSTORE_BASE64`, `KS_STORE_PASSWORD`, `KS_KEY_PASSWORD`, `KS_KEY_ALIAS`.
Without them the APK is signed with a throwaway debug key. Keep the keystore safe: a different key cannot update
an installed app.

Locally: `./gradlew testDebugUnitTest assembleRelease` (Android SDK required).

## Layout

```
app/src/main/java/io/github/aenariel/ksdailycompanion/
  model/   Model.kt (data), Engine.kt (rules, planning), Format.kt (leads, UTC/local)
  data/    Codec.kt (JSON, agenda import), Store.kt (state file, events fetch), Log.kt
  notify/  Scheduler.kt (alarm + receivers), Notifier.kt (notifications)
  ui/      MainActivity.kt, AgendaScreen, CharactersScreen, AlliancesScreen, KingdomsScreen, SettingsScreen, Widgets
```
