# Feature: baseline-fixes

## Objective
Fix the correctness bugs and code smells found in the initial review of MiCuadre so the
redesign (Material 3 premium theme, finish-trip flow from notification, history) builds on a
reliable base.

## Problem / Why
- Persisted timestamps use `SystemClock.elapsedRealtime()` (boot-relative, no calendar date,
  resets on reboot). History, per-day totals and reboot rehydration cannot work.
- `MainViewModel` collects a never-ending trips flow inside the shift-state collector, so
  shift state updates stall.
- Several silent failure paths and dead-code hacks.

## Scope (authorized)
Bug fixes and cleanup only. No visual redesign, no new screens, no dark theme (design phase).

## Constraints
- Kotlin + Compose + Hilt + Room, existing Clean-ish MVVM layering.
- Strict TDD: observed RED -> GREEN -> REFACTOR for behavior changes.
- TDD source: global Claude config ("Strict TDD Mode: enabled").
- Test runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (system default is Java 8).
- Room columns stay `Long`; only their meaning changes to epoch millis. No schema version
  bump, no destructive migration (pre-release app; old dev rows simply keep stale values).
- Conventional Commits, no AI attribution trailers.

## Tasks
- [x] T1 Git baseline: `git init` inside the project, fix `.gitignore` (`build/`, `.kotlin/`),
      baseline commit on `main`, create branch `fix/baseline-fixes`. Route: inline (mechanical).
- [x] T2 Wall-clock timestamps: persist `startedAt`/`endedAt` for sessions and trips as epoch
      millis via an injectable clock; keep `elapsedRealtime` only for on-screen timers;
      rehydration derives elapsed time from wall clock. Route: delegated writer (4+ files).
- [x] T3 ViewModel flow fix: replace nested `collect` in `MainViewModel` with
      `flatMapLatest`/`combine` so state updates never stall. Route: delegated writer.
- [x] T4 Silent failures: `ShiftService` uses the `FinishTripUseCase` result; invalid payload
      surfaces an error to the UI (`pendingError`) instead of being dropped. Route: delegated writer.
- [ ] T5 Notification permission: start the shift service only after the permission result
      (granted or denied handled explicitly). Route: delegated writer.
- [ ] T6 Cleanup: remove unused injected ViewModel params, `touchTrip`/`touchTimeUtils` hacks,
      redundant `Result.Invalid` handling; move hardcoded UI strings to `strings.xml`.
      Route: delegated writer.
- [ ] T7 Room `exportSchema = true` with KSP schema location, schema JSON committed.
      Route: delegated writer.
- [ ] T8 Release build health: verify `assembleRelease` (minify) succeeds; add keep rules only
      if needed. Route: delegated writer.

## Deferred / needs decision
- Disconnect while on a trip is currently impossible (`handleDisconnect` only from
  `Connected`). Product decision pending; not changed here.

## Acceptance criteria
- All unit tests green, new tests cover T2-T5 behavior.
- `assembleDebug` and `assembleRelease` succeed.
- No behavior change outside the listed fixes.

## Checks
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew assembleRelease` (T8)

## Delivery
- Strategy: ask-on-risk. Forecast ~400-600 authored lines (incl. tests).
- Push happens after all tasks, once the user provides the remote.

## Progress
- Baseline: `testDebugUnitTest` green before any change (2026-10-15).

- T1 done: baseline commit c97c317 on `main`, branch `fix/baseline-fixes`. Route: inline.

- T2 done: a214b5e. Injectable `Clock` (SystemWallClock via Hilt) replaces elapsedRealtime everywhere (persisted + on-screen elapsed derived from wall clock; clamped at 0). RED: compile failure on missing Clock/TimeUtils API; GREEN: testDebugUnitTest + assembleDebug. Route: delegated writer.
- T3 done: 4087f99. `homeSnapshots` (flatMapLatest) replaces nested collect; tests prove later state emissions apply with a never-completing trips flow. RED: compile failure on missing homeSnapshots; GREEN: suite. Route: delegated writer.
- T4 done: 57b1c29. `FinishTripHandler` + `ShiftError` via controller `errorEvents` -> `pendingError` -> Snackbar; trip stays OnTrip on rejection. RED: compile failure on missing FinishTripHandler; GREEN: suite + assembleDebug. Route: delegated writer.

## Next step
T5 notification permission flow.
