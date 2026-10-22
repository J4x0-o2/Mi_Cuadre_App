# Feature: persist-active-ride

## Objective
A ride in progress survives the process or the service being killed. When the service comes
back, the driver is still "En carrera" with the original start time.

## Problem / Why
A ride exists only in memory until it is finished. `StartTripUseCase` is a no-op and the `trips`
row is inserted at finish. If Android or the OEM kills the process, or the user swipes the app
away, the ride and its start time are lost and the state rehydrates to Connected.
`RehydrateShiftStateUseCase` already looks for `trips WHERE endedAt IS NULL`, but no such row is
ever written. Losing a ride means losing money records, which is the worst failure for this app.

## Scope (authorized 2026-10-22, phase F1 of the public release plan)
1. Insert an open trip row (`endedAt = NULL`, no amount, payment or platform) when a ride starts.
2. Finishing a ride updates that row; cancelling deletes it.
3. Every total, aggregate and list counts only closed trips (`endedAt IS NOT NULL`).
4. After a kill or restart, rehydration restores OnTrip with the persisted start time, and the
   notification and controller show it.
5. A null-intent restart (START_STICKY) with an active session rehydrates instead of stopping.
Out of scope: copy changes, history, sync.

## Decisions
- Storage: Room v5. `trips.amountMinor`, `paymentMethod` and `platformId` become nullable, with
  a real `MIGRATION_4_5` (table rebuild) and MigrationTestHelper tests (4->5 and the full chain).
  Rejected: a sentinel platform or amount, because it would leak into totals and FKs.
- Closed trips keep the same non-null guarantees at the domain level. Only the open row has nulls.

## Constraints
- Strict TDD (source: global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`; also `assembleDebug`.
- The first build after a schema version bump must run twice so 5.json exists.
- Code, comments and docs in English. Conventional Commits, no AI attribution trailers.
  Never commit `.codegraph/` or `.idea/vcs.xml`.
- One work-unit commit (or more) per task on feat/persist-active-ride.
- Delivery strategy: ask-on-risk. Base: main b437b37.

## Tasks
- [x] T1 Room v5: nullable trip columns, MIGRATION_4_5, schema 5.json, DatabaseModule, migration
      tests (4->5 with existing rows preserved, 1->5 chain). DAO: insert open trip, finish open
      trip (update), and `endedAt IS NOT NULL` on every aggregate or list query.
      Route: delegated writer (several non-trivial files).
      RED: test compile failed on unresolved MIGRATION_4_5, insertOpenIfNone, finishOpen, openTrip.
      GREEN: `./gradlew testDebugUnitTest` 665 tests, 0 failures (baseline 641, +24);
      `./gradlew assembleDebug` BUILD SUCCESSFUL. REFACTOR: none needed.
      Data API for T2: `ShiftRepository.openTrip(sessionId, startedAt): Long` (idempotent),
      `finishOpenTrip(tripId, platformId, endedAt, amountMinor, paymentMethod): Boolean`; cancel
      reuses `cancelInProgressTrip`. DAO: `insertOpenIfNone`, `finishOpen`. Open trips map to a
      domain Trip with platformId 0 and empty platformName; all list/aggregate queries and the
      @Relation activity mapping exclude them. One old test (summary counting an unfinished trip)
      was inverted to the new rule.
- [x] T2 Domain + service: StartTripUseCase inserts the open row; FinishTripHandler updates it;
      cancel deletes it; rehydration restores OnTrip with the persisted start; null-intent
      restart with an active session rehydrates. Route: delegated writer (several non-trivial files).
      RED: unit-test compile failed on the new API (StartTripUseCase.Started, tripId on
      FinishTripUseCase/FinishTripHandler/Rehydrate result, TripDao.openIfNone,
      ShiftError.StartTripFailed). GREEN: `./gradlew testDebugUnitTest` 682 tests, 0 failures
      (665 before, +17 net); `./gradlew assembleDebug` BUILD SUCCESSFUL. REFACTOR: none needed.
      Behavior: start persists via `openTrip` first; an insert failure keeps the shift Connected and
      publishes `ShiftError.StartTripFailed` (new `error_start_trip` string), so there is no
      in-memory-only ride. The service keeps `activeTripId` and the persisted start; a rejected
      `finishOpenTrip` keeps the ride open (`InvalidFinishTrip`). Finish and the notification/bubble
      flow all go through the service, so they close the open row in place. `finishTrip` was removed
      (interface, impl, DAO insert, use-case path); tests use the `recordFinishedTrip` fixture.
      `insertTrip` remains for tests/legacy and now rejects closed trips without a platform.
      Cancel deletes the open row (covered after a restart too). `RehydrateShiftStateUseCase.Result`
      carries `tripId`. A null-intent restart returns START_STICKY: an active session (even
      mid-ride) is rehydrated into the foreground, no session stops the idle service.
      `onTaskRemoved` left alone: the service has no stopWithTask, so a swipe does not stop it and
      OEM process kills are covered by START_STICKY plus the persisted row.
      Folded T1 advisories: R3-insert-trip-null-platform (insertTrip rejects closed trips without a
      platform; DAO lists/total/count all require a platform, tests assert total and count too),
      R3-interface-default-throws (`openTrip`/`finishOpenTrip` abstract, all fakes updated),
      R3-opentrip-race-throw (`TripDao` is now an abstract class with a @Transaction `openIfNone`;
      stress test of openTrip racing cancel).
      (The known gap about an open row left by ending the shift is fixed in T2b.)
- [x] T2b Accepted from the T2 review and the branch-level review (non-blocking findings).
      Route: delegated writer (several non-trivial files).
      1. A sticky restart refused by the system (`ForegroundServiceStartNotAllowedException` on
         API 31+, or SecurityException) no longer crashes: `ForegroundStarter` seam (Hilt module,
         `PlatformForegroundStarter`), `startInForeground` catches RuntimeException (the API 31 class
         is never named, minSdk 29), logs, posts a plain "Tu turno sigue guardado" notice (own
         channel, tap opens MainActivity, skipped when notifications are not allowed) and stops. The
         open row stays in Room. Opening the app restores the ride: `MainViewModel` checks for an
         active session and calls `controller.restore()` (ACTION_RESTORE) only if the controller is
         Disconnected, so a cold launch with no shift shows nothing. A later successful foreground
         start cancels the notice.
      2. Ending the shift deletes the open trip row (best effort, like cancel); finished trips and
         totals are untouched.
      3, 4. ShiftRepositoryImplTest: raw row counts for the rejected closed trip and for the
         openTrip-vs-cancel stress test (at most one open row, consistent after the race).
      5. Branch review: ShiftServiceTest cases for a pause during the openTrip insert (no open row,
         not OnTrip) and a duplicate start while the ride is live (row and start time untouched);
         the fake repository got an `insertedGate`.
      RED: 6 new tests failed behaviorally (refused restart/intent, notification, end shift mid-ride,
      app-open policy) after adding only the seam and stubs. GREEN: `./gradlew testDebugUnitTest`
      695 tests, 0 failures (682 before, +13); `./gradlew assembleDebug` BUILD SUCCESSFUL.
      The race and restore-after-refusal tests passed on first run (they pin existing behavior).
      REFACTOR: none needed.
- [x] T2c Accepted from the T2b review (all four lenses approved; non-blocking warnings).
      Route: delegated writer (several non-trivial files).
      1. A refused foreground start resets the service state and publishes Disconnected to the
         singleton controller (`resetAfterRefusedForeground`), so the app-open gate sees nothing
         published and calls `restore()`. Chosen over publish-after-start because it also covers the
         delivered-intent paths, where state was published before the refusal. Room is untouched.
      2. `notificationFactory.build` now runs before the try: only the platform call through the
         seam is guarded, KDoc and log updated. A build failure propagates (our bug, deterministic,
         nothing is lost since the state is in Room; hiding it as a "refusal" would mask it).
      3. The restore wiring moved into `PersistedShiftRestorer` (lambdas, JVM-testable, like
         ConnectCoordinator) with tests: restores with a session, not without one, not over a
         published state, a failing lookup is reported and not restored, cancellation is rethrown
         first. MainViewModel only wires it.
      4. Copy register left for F2.
      RED: 5 new tests failed on behavior (restorer stub, stale published state, build failure
      swallowed as a refusal). GREEN: `./gradlew testDebugUnitTest` 702 tests, 0 failures (695
      before, +7); `./gradlew assembleDebug` BUILD SUCCESSFUL. REFACTOR: none needed.
- [ ] T3 Device QA: start a ride, force-stop or kill the process, reopen and see the ride with its
      original start. Then finish it and check the totals. Also cancel after a restart.
      Also the background sticky restart: with a ride in progress, put the app in the background,
      turn the screen off or lock it, then `adb shell am kill com.micuadre.app.debug`. Expect either
      the foreground notification back or the "Tu turno sigue guardado" notification, never a crash
      (check `adb logcat -b crash`). Then open the app: the ride must show "En carrera" with its
      original start. Also end the shift mid-ride and confirm no open ride is left.

## Acceptance criteria
- Killing the process mid-ride and reopening shows "En carrera" with the original start time.
- Totals and the week/month sheet never include an open ride.
- Existing v4 data migrates without loss.
- All unit tests and `assembleDebug` green.

## Progress
- 2026-10-22: feature document created; branch feat/persist-active-ride from main b437b37.
- 2026-10-22: T1 done (Room v5, MIGRATION_4_5, open-trip DAO/repository, open trips excluded everywhere).
- 2026-10-22: T2 done (open row on start, in-place finish, cancel deletes, rehydrated OnTrip, sticky null-intent restart, T1 advisories folded).
- 2026-10-22: T2b done (refused foreground restart handled, end shift drops the open row, test hardening, start-trip race tests).
- 2026-10-22: T2c done (refused start resets published state, narrower catch, restorer tests).

## Next step
T3 device QA (kill the process mid-ride, reopen, finish and cancel; background sticky-restart case).
