# Feature: home-aggregates

## Objective
Show the right summary on the Home screen (current shift when connected, today's summary when
disconnected) and add week / month earnings with a detail sheet.

## Problem / Why
Every aggregate today is per session. A driver who ends a shift sees nothing about the day,
and there is no way to see weekly or monthly earnings.

## Scope (authorized, user decisions 2026-10-18)
- A shift counts for the local day it STARTED (night shifts are not split at midnight).
- Week = calendar week Monday..today; month = 1st..today (device time zone).
- Connected / on trip: card "TOTAL DEL TURNO" (current session) with "Desde HH:mm".
- Disconnected without an open shift: "RESUMEN DE HOY" across every session started today:
  total, cash/transfer, trips, connected time, per-hour earnings, number of shifts.
- Disconnected with a paused open shift: same "RESUMEN DE HOY" (it includes the open shift),
  keeping the paused hint and "Terminar turno".
- Two buttons "Esta semana" / "Este mes" showing the amount; they open a bottom sheet with
  Semana/Mes tabs: range, total, cash/transfer, bars (per day for week, per calendar week for
  month), trips, days worked, average per worked day.
- Sketches approved: https://claude.ai/artifact/Srm9f4rES8jP9FXqTxGxfy
Out of scope: versioning, user-defined platforms, app icon (separate features), history screen.

## Constraints
- Strict TDD (source: global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- No Room schema change expected (queries only). If one is needed: version bump + real
  Migration + MigrationTestHelper test.
- UI copy in Spanish (existing strings.xml); code/comments in English.
- Conventional Commits, no AI attribution trailers. Never commit `.codegraph/` or `.idea/vcs.xml`.

## Tasks
- [x] T1 Domain + data: period aggregation (day/week/month ranges by session start, totals,
      cash/transfer, trips, connected seconds, worked days, buckets) with DAO queries, repository
      and use case, injected Clock/ZoneId. Route: delegated writer (2+ non-trivial files).
      Evidence: 92959eb; RED = compile failures on new API; 19 new tests green;
      testDebugUnitTest + assembleDebug green. Gaps for T2: re-invoke at midnight,
      ticker for open interval, ZoneId read once.
      Review: medium, granted, 1 lens (reliability) APPROVED + acknowledged
      (lineage review-9b3d10f034865df7). Advisory: stale range across midnight, re-emit test
      not observing, DST untested, non-atomic combine -> first two folded into T2.
- [x] T2 Home: TOTAL DEL TURNO when connected, RESUMEN DE HOY when disconnected, week/month
      buttons with amounts; midnight rollover + live connected ticker; fix re-emit test.
      Route: delegated writer. Evidence: b1a3376; RED = compile failures on new API;
      testDebugUnitTest + assembleDebug green (parent re-ran tests: green).
      Review: medium, granted, reliability APPROVED + acknowledged (review-e4bdc41fb30b1610).
      Advisory -> T3: sheet state unconsumed, paused night shift shows zeros today,
      clock/zone jumps not detected until midnight.
- [ ] T3 Period bottom sheet (Semana/Mes tabs, bars, stats) + T2 advisories: paused shift
      started yesterday shows its own shift totals; re-arm date ticker on time/zone change.
      Route: delegated writer.

## Acceptance criteria
- Night shift 20:00-02:00 counts entirely for its start day.
- Week starts Monday; month starts on day 1; both end today.
- Home matches the sketches; `testDebugUnitTest` and `assembleDebug` green.

## Checks
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Delivery
- Branch `feat/home-aggregates` from `main`. Strategy: ask-on-risk -> feature-branch-chain
  (user choice 2026-10-18). Forecast ~800-1000 authored lines; slices = T1, T2, T3.

## Progress
- 2026-10-18: doc created; branch created.
- 2026-10-18: T1 done (92959eb), reviewed boundary = docs commit after it.
- 2026-10-18: T2 done (b1a3376), reviewed. Next: T3.
