# Feature: history-in-settings

## Objective
A simple History screen, opened from Settings, that shows the totals of past shifts per month and,
inside a month, per week. It works from the data already on the phone, with or without a backup.

## Problem / Why
The Home screen shows only the current period (today, week, month). Drivers have no way to look at
earlier months, even though every shift stays stored on the phone. Phase F3 of the public release
plan (Engram `micuadre/roadmap/public-release-plan`).

## Scope (authorized 2026-10-25)
1. Year screen ("Historial"): year stepper (previous / next, next disabled on the current year),
   hero card with the year gross total, net and shift count, then the months of that year that
   have shifts, newest first. Each month card: name, gross total, a bar relative to the best month,
   "Neto $ X · N turnos". The current month is labelled "en curso". Tapping a month opens it.
2. Month screen: title month + year, hero with gross total and tiles Gastos / Neto / Turnos, then
   the weeks (Monday to Sunday, clipped to the month, e.g. "1 – 7 sep"), each with total, bar and
   "Neto $ X · N turnos". Weeks are not tappable.
3. Empty state for a year without shifts.
4. Entry row "Historial" in Settings, before "Acerca de".
5. Retake `docs/images/shift-expenses.png` with the deduction named "Tarifa de la app" (user
   preference; 1.1.0 shipped it as "Peajes").
Out of scope: Google Sheets backup (F5), any Room schema change, a NavHost.

## Rules (same as the rest of the app)
- A shift belongs entirely to the local date of `sessions.startedAt` (zone injected, the system
  zone in production). Weeks start on Monday.
- Gross = finished trips only (open trips never count). Expenses = the session's deduction
  amounts. Net = gross − expenses, may be negative. Shift count = sessions started in the range,
  including the live one, like `PeriodSummary`.
- Money via `Formatters.moneyNoDecimals` / `Formatters.expenseMoney`; "turno/turnos" via a plurals
  resource.

## Constraints
- Strict TDD (source: global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`; also `assembleDebug`.
- Code, comments and docs in English; UI copy in neutral Spanish (CopyRegisterTest guards it).
- Keep the `AppScreen` enum navigation; no DB change.
- Conventional Commits, no AI attribution trailers. Never commit `.codegraph/` or `.idea/`.
- Branch feat/history-in-settings from main 7ae3d78. Delivery strategy: ask-on-risk.
  Forecast: about 700 authored lines (domain ~250, UI ~450), so review per work-unit commit.

## Tasks
- [x] T1 Domain: pure history aggregation (year → months, month → weeks) plus use cases that
      observe a year and a month from `ShiftRepository` and `DeductionRepository`.
      Route: delegated writer (several non-trivial files).
- [ ] T2 UI: HistoryViewModel, year and month screens, `AppScreen` entries with saved selection,
      Settings row, strings and plurals. Route: delegated writer (several non-trivial files).
- [ ] T3 Device check on the emulator, screenshot retake ("Tarifa de la app"), README/CHANGELOG.
      Route: inline (emulator + docs).

## Acceptance criteria
- Months and weeks add up to the same totals the Home period sheet shows for the current month.
- A night shift crossing midnight or a month boundary counts in its start day / month.
- Open trips never count; the live shift counts with its finished trips.
- A year with no shifts shows the empty state; the next-year button is disabled on the current
  year.
- All unit tests and `assembleDebug` green; no crash on the emulator.

## Progress
- 2026-10-25: feature document created; branch feat/history-in-settings from main 7ae3d78.
- 2026-10-25 T1 done (route: delegated writer), commit 321b5dc. `HistoryAggregator` (year, month,
  barFraction) in `domain/model/History.kt`; `ObserveYearHistoryUseCase` /
  `ObserveMonthHistoryUseCase`; `SessionDao` `MIN(startedAt)` exposed as
  `ShiftRepository.observeEarliestSessionStart()` (no schema change). Weeks: a past month lists
  every Monday–Sunday week clipped to the month, zero weeks included; the current month lists weeks
  up to the one containing today. RED: the focused `*History*` run failed to compile (unresolved
  use cases and aggregator); GREEN: `testDebugUnitTest` 744 tests, 0 failures; `assembleDebug` OK.

## Next step
T2.
