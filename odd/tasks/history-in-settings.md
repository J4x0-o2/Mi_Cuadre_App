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
- [x] T2 UI: HistoryViewModel, year and month screens, `AppScreen` entries with saved selection,
      Settings row, strings and plurals; T1 review follow-ups (day-change refresh, stronger
      integration test). Route: delegated writer (several non-trivial files).
- [x] T3 Device check on the emulator, screenshot retake ("Tarifa de la app"), README/CHANGELOG.
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

- 2026-10-25 T1 review (base 7ae3d78, through d7bb69b): medium, granted, reliability APPROVED +
  acknowledged. Follow-ups accepted into T2: (a) WARNING the use cases read "today" only when the
  repository flows emit, so "en curso", the next-year button and the week cutoff go stale across
  midnight/month/year while the screen stays open — re-evaluate on local day changes (reuse
  `PeriodSummaryCoordinator.localDays`); (b) SUGGESTION the integration test's open trip has no
  amount and shiftCount is not asserted — strengthen it.

- 2026-10-25 T2 done (route: delegated writer). Commits 262b736 (day-change refresh via
  `switchOnDayChange` over `localDays`, stronger `HistoryUseCaseTest` with a priced open trip and
  `shiftCount == 2`) and 3cadbb7 (`HistoryViewModel` with `SavedStateHandle` year/month,
  `YearHistoryModel`/`MonthHistoryModel`, `HistoryScreen` + `HistoryMonthScreen`, `AppScreen`
  History/HistoryMonth, Settings "Historial" section before "Acerca de", strings + plurals). RED:
  compile failures (`switchOnDayChange`, `YearHistoryModel` unresolved); GREEN:
  `testDebugUnitTest` 767 tests, 0 failures; `assembleDebug` OK.

- 2026-10-25 T2 review (base d7bb69b, through 15672cc): medium, granted, reliability APPROVED +
  acknowledged. Follow-ups fixed in 73e418d (delegated writer): a new month selection clears the
  previous month (`onStart { emit(null) }`), and the zone is read through `ZoneId::systemDefault`
  on every day/clock change. RED: the month test failed on `assertNull`; the zone test failed to
  compile. GREEN: 769 tests. Not applied to `year` (would blank the stepper and drop rapid taps).
- 2026-10-25 T3 (route: inline). Emulator Pixel_6, debug build: Settings › Historial opens; the year
  shows $ 222.500, Neto $ 195.000, 7 turnos, matching Home "Este mes"; October lists 1–4, 5–11,
  12–18, 19–25 oct whose totals add up to the month (19–25 equals Home "Esta semana" $ 214.500);
  back goes month → year → Settings; no crash in the crash buffer. Found on device: the Gastos tile
  cut "− $ 27.5…"; fixed in 85d2879 (amount shrinks to fit, Turnos tile takes its natural width),
  re-checked on the emulator. Compose layout only, so no unit RED was possible; full suite 769
  green, `assembleDebug` OK. Screenshot `docs/images/shift-expenses.png` retaken with "Tarifa de la
  app" (Gboard disabled during typing to avoid autocapitalization); README feature + roadmap and
  CHANGELOG `[Unreleased]` updated in 8c052c9. Emulator DB restored from
  `Documents/MiCuadre-backups/emulator-db-1.1.0-2026-10-25`.
  Not checked on device: the empty state of a year without shifts and the disabled previous-year
  button (covered by unit tests only).

## Next step
Review the 73e418d..8c052c9 slice if due, then merge and release 1.2.0 when the user decides.
