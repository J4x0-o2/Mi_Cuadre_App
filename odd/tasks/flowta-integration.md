# FlowTa integration (MiCuadre side)

Counterpart: `FlowTa/odd/tasks/micuadre-integration.md` (FlowTa side, owns the shared contract v1).
Engram mirror: `odd/flowta-integration/tasks`.

## Objective

When enabled, every finished trip is sent to FlowTa as an income, routed by FlowTa to the account
the user assigned to the trip's payment method (CASH / TRANSFER). Nothing is lost if FlowTa is
unavailable: failed sends wait in a local queue and retry.

## Problem / why

The user logs trips in MiCuadre and manages all money in FlowTa; today each income is typed twice.

## Design summary

1. Settings row "Send income to FlowTa" (DataStore boolean, default off), like `BubbleRow`.
2. Enabling it: if FlowTa is not installed (`<queries><package>`), show a dialog with an install link.
   If installed, launch FlowTa's connect screen (`com.j4x.flowta.action.CONNECT_SOURCE`) with the
   ActivityResult API. `RESULT_OK` → toggle stays on; cancel → toggle off.
3. User picks, in FlowTa: income type + one account for CASH + one for TRANSFER. MiCuadre stores
   only "enabled"; it never sees FlowTa ids.
4. After `FinishTripUseCase` returns `Saved`, the trip is written to a Room queue
   `pending_flowta_income` and a unique WorkManager job drains it through
   `ContentResolver.call(authority, "recordIncome", …)` (contract v1, see FlowTa doc).
   - `OK` / `DUPLICATE` → delete the row.
   - `NOT_LINKED` → keep the row, show "Reconnect FlowTa" in Settings.
   - `INVALID` → drop the row and log (data can never succeed).
   - provider missing / SecurityException / exception → keep, retry with backoff.
5. `external_id` = trip id; `amount_minor` sent as-is (pesos x 100); `occurred_at` = `endedAt`;
   `note` = "MiCuadre · <platformName>".

### Facts from exploration (2026-11-02)

- Finishing a trip is an UPDATE of an open row: `ShiftRepositoryImpl.finishOpenTrip`
  (`data/repository/ShiftRepositoryImpl.kt:93`); call chain `ShiftService.handleFinishTrip` →
  `FinishTripHandler.handle` → `FinishTripUseCase`. Hook after `Result.Saved`; `tripId` is known.
- `amountMinor` = pesos x 100 (`AmountInput.toMinor`, `Formatters.money`). COP only.
- Room v5, manual migrations in `data/local/Migrations.kt`, `MigrationTest` with Robolectric
  (may not cover 4 → 5 yet).
- WorkManager is **not** a dependency yet (needs `work-runtime-ktx` + `hilt-work`).
- Debug `applicationId` has `.debug` suffix; FlowTa staging has `.staging`. Target FlowTa package /
  authority must come from `BuildConfig` per build type.
- Release signed with `~/.android-keys/micuadre-upload.jks`; FlowTa must use the same key
  (FlowTa doc, decision D1).

## Scope

In: toggle, detection, connect launch, queue + worker, reconnect state, docs.
Out (v1): sending trips finished before enabling; syncing later edits/deletes; other apps.

## TDD

Mode: strict (on), source: user global config. Runner: `./gradlew testDebugUnitTest`
(JUnit4 + Robolectric + room-testing + hand-written fakes; no MockK).

## Tasks

- [x] **M1 Build wiring** — `BuildConfig.FLOWTA_PACKAGE` / `FLOWTA_AUTHORITY` per build type
      (debug → `com.j4x.flowta`, release → `com.j4x.flowta`), `<queries><package>` for both, uses-permission
      `com.j4x.flowta.permission.RECORD_INCOME`. ALSO declare the identical `<permission>` (signature) in
      MiCuadre's manifest: if MiCuadre is installed before FlowTa, a permission defined only by FlowTa may not
      be granted; both apps share the signing key, so declaring it in both is allowed. Treat a
      `SecurityException` from `call()` as retryable + "reconnect" hint. [route: inline]
      Evidence 2026-11-02: fields live in `defaultConfig` (FlowTa debug has no suffix, so every build
      type targets `com.j4x.flowta`; a staging override can be added later). `assembleDebug` green;
      merged manifest has the permission, uses-permission and `<queries>`. Emulator: installing
      MiCuadre debug next to FlowTa debug (both debug key, both declaring the permission) succeeds and
      `dumpsys package` shows `RECORD_INCOME: granted=true`. No unit test: build/manifest only.
- [x] **M2 Room v6 queue** — `pending_flowta_income(tripId PK, amountMinor, paymentMethod, endedAt,
      platformName, attempts, lastError, createdAt)`, `MIGRATION_5_6`, DAO, `MigrationTest` 5 → 6
      (and 4 → 5 if missing). [route: delegated]
      Evidence 2026-11-02: entity + `PendingFlowTaIncomeDao` (upsert, oldest(limit), delete, markFailed,
      count, observeCount), `MIGRATION_5_6`, `6.json`. No FK: a queued row survives trip deletion.
      `paymentMethod` stored as the enum name String, like `TripEntity`. RED: `Unresolved reference
      'MIGRATION_5_6'`. GREEN: testDebugUnitTest 783 tests, 0 failures; assembleDebug. Parent re-ran
      MigrationTest (29) + PendingFlowTaIncomeDaoTest (6): green. 4 → 5 was already covered.
      Gotcha: the first test run after adding a version can fail until `6.json` is generated.
- [ ] **M3 Setting + detection** — `FlowTaSyncPreferenceRepository` (`flowta_sync_enabled`),
      `FlowTaAvailability` (installed?), Settings row + install dialog, ViewModel tests. [route: delegated]
- [ ] **M4 Connect launch** — ActivityResult contract for `CONNECT_SOURCE`; OK → enabled, cancel →
      disabled; "Reconnect" entry when FlowTa reports `NOT_LINKED`. [route: delegated]
- [ ] **M5 Enqueue + send** — after `Saved` and only if enabled: insert into queue, enqueue unique
      `FlowTaSyncWorker` (network not required, exponential backoff). Client maps contract statuses as in
      the design. Tests: client mapping with a fake caller, worker drain logic, no enqueue when disabled.
      [route: delegated]
- [ ] **M6 Docs** — CLAUDE.md / README: feature, contract reference, Room v6. [route: inline]

## Acceptance criteria

- With the toggle off nothing is sent and nothing is queued.
- With FlowTa uninstalled, enabling shows the install dialog and leaves the toggle off.
- A finished trip appears in FlowTa within seconds; if FlowTa is force-stopped or uninstalled
  afterwards, the trip waits in the queue and is delivered once it is back.
- Existing users upgrade Room 5 → 6 without data loss.

## Progress

- 2026-11-02: plan written; waits on the FlowTa side (provider + connect screen) and decision D1.
- 2026-11-02: FlowTa side F1–F7 done on FlowTa branch `feat/micuadre-integration` (unpushed). Branch
  `feat/flowta-integration` created here; M1 done.

- 2026-11-02: M2 done (24a6349). Review (M1+M2, reliability, medium, slice budget): granted → approved,
  acknowledged (lineage review-e16b5007e3df0498); reviewed boundary = 24a6349. Findings:
  - WARNING (kept, documented): declaring the permission in both apps makes Android reject installing
    the second app with `INSTALL_FAILED_DUPLICATE_PERMISSION` when the two are signed with DIFFERENT keys
    (e.g. debug MiCuadre next to release FlowTa). Accepted tradeoff: real installs (both release, same key)
    and dev installs (both debug) are fine, and it removes the install-order grant problem. Never mix a
    debug build of one app with a release build of the other. → M6 docs.
  - Folded into M5: enqueue must not reset `attempts`/`lastError`/`createdAt` of a row already queued
    (insert-ignore for enqueue, keep `upsert` only if needed); test the `oldest()` tripId tie-break;
    `markFailed` on a missing row is a no-op (document/test).

## Next step

M3 (setting + detection).
