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
      Known gap: ending the shift while OnTrip leaves the open row in the ended session; it is
      never counted nor restored (rehydration only reads the active session).
- [ ] T3 Device QA: start a ride, force-stop or kill the process, reopen and see the ride with its
      original start. Then finish it and check the totals. Also cancel after a restart.

## Acceptance criteria
- Killing the process mid-ride and reopening shows "En carrera" with the original start time.
- Totals and the week/month sheet never include an open ride.
- Existing v4 data migrates without loss.
- All unit tests and `assembleDebug` green.

## Progress
- 2026-10-22: feature document created; branch feat/persist-active-ride from main b437b37.
- 2026-10-22: T1 done (Room v5, MIGRATION_4_5, open-trip DAO/repository, open trips excluded everywhere).
- 2026-10-22: T2 done (open row on start, in-place finish, cancel deletes, rehydrated OnTrip, sticky null-intent restart, T1 advisories folded).

## Next step
T3 device QA (kill the process mid-ride, reopen, finish and cancel).
