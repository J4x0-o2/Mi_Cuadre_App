# Changelog

All notable changes to MiCuadre are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The version lives in
`version.properties`; the Android `versionCode` is derived from it as `major*10000 + minor*100 + patch`.

## [0.4.0] - 2026-10-20

### Added
- Floating bubble over other apps while a shift is active: draggable, snaps to the edge, and opens
  a panel with Start trip, Finish trip, Cancel trip and Open app. Enabled from Settings; requires
  the "Display over other apps" permission (with a fallback when the system page is missing).
- Cancel an in-progress trip, with confirmation; it is discarded and never counted.
- Confirmation dialog before ending a shift.

### Changed
- README translated to Spanish and updated with the new features and screenshots.
- The app name is spelled "MiCuadre" everywhere, and the Home header shows only the name.

### Fixed
- A database failure while cancelling a trip no longer crashes the app.
- A cancel received while the service is restoring its state is no longer dropped.

## [0.3.0] - 2026-10-19

### Added
- User-defined platforms managed from Settings: create, rename, show or hide in the finish-trip
  sheet, and delete (past trips keep their platform name and totals).

### Changed
- New app icon: a white wallet with coins on black, as an adaptive vector icon with a monochrome
  layer for themed icons (Android 13+). Legacy per-density bitmaps removed (minSdk 29).
- Database v3: platforms live in their own table; free-text "Otra" labels were migrated into
  platforms and removed from the finish-trip sheet.

## [0.2.0] - 2026-10-18

### Added
- Home shows the shift total and a summary of today, where "today" starts at the shift start day.
- Week and month earnings on Home, with a detail sheet and per-day / per-week bars.
- Single version source (`version.properties`) with a derived `versionCode`, and an "Acerca de"
  section in Settings that shows the installed version (debug builds also show the build type).

### Fixed
- Summaries roll over correctly at midnight and when the time zone or clock changes.
- "Por hora" no longer extrapolates a huge rate from less than one connected minute.

## [0.1.0] - 2026-10-17

### Added
- Premium Material 3 theme: light and dark color schemes, shapes, and Sora / Manrope typography.
- Redesigned Home, on-trip, finish-trip and shift summary screens.
- Settings screen with theme mode selection (system, light, dark, scheduled).
- Shift summary counts only real connected time, using persisted intervals.

### Fixed
- Shift timestamps are persisted as wall-clock time through an injectable clock.
- Invalid finish-trip input is surfaced and keeps the trip open.
- The shift service starts only after the notification permission result.
- Shift summary renders inside the activity and handles shifts across midnight.
