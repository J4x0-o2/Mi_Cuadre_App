# Changelog

All notable changes to MiCuadre are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The version lives in
`version.properties`; the Android `versionCode` is derived from it as `major*10000 + minor*100 + patch`.

## [0.2.0] - 2026-10-18

### Added
- Home shows the shift total and a summary of today, where "today" starts at the shift start day.
- Week and month earnings on Home, with a detail sheet and per-day / per-week bars.
- Single version source (`version.properties`) with a derived `versionCode`, and an "Acerca de"
  section in Settings that shows the installed version (debug builds also show the build type).

### Fixed
- Summaries roll over correctly at midnight and when the time zone or clock changes.

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
