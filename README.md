# MiCuadré

**Know exactly what you earned in every shift.** MiCuadré is an Android app for ride-hailing and
taxi drivers (InDriver, DiDi or any other platform). You log each ride in a couple of taps and the
app instantly shows how much you made in the current shift, today, this week and this month,
split into cash and bank transfers.

![MiCuadré: shift total, weekly and monthly earnings](docs/images/cover.png)

## Why it exists

Drivers who work across several apps usually "do the math" (*el cuadre*) at the end of the day
from memory, screenshots or a notebook. That is slow and error-prone, and it hides the numbers
that matter: how much you earn per hour, which days pay off, and how much cash you should have in
your pocket.

MiCuadré replaces that with a record you build while you drive. It works offline and keeps every
number on your phone.

| Goal | How the app gets there |
|------|------------------------|
| Logging must never slow you down | Start and finish rides from the persistent notification, without opening the app |
| Always know your numbers | Live totals for the shift, the day, the week and the month |
| Know where the money is | Every ride records cash or transfer; totals show both |
| Fit any driver | You define your own platforms in Settings |

## What it does

- **Shifts.** Connect when you start working. Pause (disconnect) and resume as needed. End the
  shift to see its summary. Connected time excludes pauses.
- **Rides.** Start a ride, then finish it with the platform, the amount and the payment method.
  The last platform and payment method you used are preselected.
- **Home summary.**
  - *While connected:* the shift total, connected time, ride count and the rides of the shift.
  - *While disconnected:* today's summary across all shifts, including earnings per hour.
- **Week and month.** Two buttons open a sheet with the total, cash vs. transfer, a bar chart (per
  day for the week, per week for the month), worked days and the average per worked day.
- **Your platforms.** In *Settings › Plataformas* you create, rename, show or hide and delete
  platforms. Deleting one never changes your history: past rides keep its name and totals.
- **Theme.** System, light, dark, or automatic by schedule (dark from 18:00 to 06:00).

## How the numbers are counted

| Rule | Why |
|------|-----|
| A shift counts entirely for the day it **started** | A night shift from 20:00 to 02:00 is one working day, not two |
| The week runs Monday to today; the month runs from the 1st to today | Matches calendars, bills and goals |
| "Per hour" appears only after one full connected minute | Avoids absurd rates extrapolated from a few seconds |
| A ride must be finished before you disconnect | No ride is ever left half-recorded |

## Screenshots

| Today's summary | On shift | Week |
|---|---|---|
| ![Today](docs/images/today.png) | ![On shift](docs/images/shift.png) | ![Week](docs/images/week.png) |

The screenshots use demo data.

## Quick start (developers)

**Requirements:** Android Studio (recent stable), JDK 17 or newer (the JDK bundled with Android
Studio works), and a device or emulator on Android 10 (API 29) or newer.

1. Clone the repository and open the project folder in Android Studio.
2. Run the `app` configuration on a device or emulator.
3. Run the tests:

```bash
./gradlew testDebugUnitTest   # unit, Room and Robolectric tests
./gradlew assembleDebug       # debug APK in app/build/outputs/apk/debug/
```

The debug build installs as a separate app (`com.micuadre.app.debug`), so it never touches the
data of a release install.

## Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose with Material 3 (custom theme, Sora and Manrope fonts) |
| Dependency injection | Hilt |
| Storage | Room (shifts, rides, connected intervals, platforms) and DataStore (preferences) |
| Background work | A foreground service with notification actions to start and finish rides |
| Async | Kotlin Coroutines and Flow |
| Tests | JUnit 4, Robolectric, Room `MigrationTestHelper` (about 390 tests) |
| Build | Gradle Kotlin DSL with a version catalog; `minSdk 29`, `targetSdk 35` |

## Architecture

The code follows a layered (clean) architecture: the UI depends on the domain, and the domain
knows nothing about Android storage.

```text
app/src/main/kotlin/com/micuadre/app/
├── domain/        Business rules: models, repository interfaces, use cases
├── data/          Room entities, DAOs, migrations and repository implementations
├── presentation/  ViewModels, coordinators, UI models and Compose screens
├── service/       Foreground shift service, notification and its state machine
├── ui/theme/      Colors, typography and theme
├── di/            Hilt modules
└── util/          Formatting, version and time helpers
```

Key decisions:

- **Database migrations are real and tested.** Each schema version (now v3) ships with a
  `Migration` and a `MigrationTestHelper` test that runs it on existing data.
- **Time is injected.** A `Clock` and the time zone are provided by Hilt, so day, week and month
  boundaries, midnight rollover and time-zone changes are unit-tested.
- **Logic lives outside composables.** Screens only render pure UI models, which are covered by
  plain JVM tests.

## Versioning

The app follows [Semantic Versioning](https://semver.org). The version lives in a single file,
`version.properties`, and the Android `versionCode` is derived from it as
`major × 10000 + minor × 100 + patch`. The installed version is shown in *Settings › Acerca de*.
Every release is tagged in git (`v0.3.0`), and changes are listed in [CHANGELOG.md](CHANGELOG.md).

## Roadmap

- Optional sync of rides to Google Sheets.
- A history screen to browse past shifts and rides.

## Project status

MiCuadré is in active development (current version **0.3.0**) and is used first by its author as
a daily driver tool, with the goal of opening it to any app-based driver.
