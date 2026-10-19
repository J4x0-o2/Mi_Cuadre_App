# Feature: custom-platforms

## Objective
Replace the hardcoded platforms (InDriver, Didi, "Otra" + free text) with user-defined platforms
managed from Settings, choosing which ones appear when finishing a trip.

## Problem / Why
`enum Platform { INDRIVER, DIDI, OTHER }` limits the app to two apps; drivers of other apps must
type a free label every time and history fragments by spelling.

## Scope (authorized, user decisions 2026-10-19)
- Platforms are created, renamed, shown/hidden and deleted ONLY from Settings.
- "Otra" + free text is removed from the finish-trip sheet; it shows the visible platforms only.
- Deleting a platform used by trips keeps those trips with their name and totals (soft delete:
  gone from Settings and the sheet, history unchanged).
- Defaults on a fresh install: InDriver, Didi (visible).
- Rename allowed (history shows the new name). Duplicate names (case-insensitive, among
  non-deleted) rejected. Order = creation order.
- Sheet preselects the last used platform if still visible, else the first visible; with no
  visible platforms the sheet tells the user to enable one in Settings.
Out of scope: drag reordering, per-platform colors, Sheets sync, history screen.

## Design
- Room v3: table `platforms(id, name, visible, createdAt, deletedAt?)`; `trips.platformId` FK
  (no cascade) replacing `platform` / `platformOtherLabel` (table rebuild in the migration).
- MIGRATION_2_3: INDRIVER -> "InDriver", DIDI -> "Didi", each distinct trimmed OTHER label
  (case-insensitive, first casing) -> its own visible platform, blank OTHER -> "Otra".
- Service intent extras carry the platform id instead of the enum name.

## Constraints
- Strict TDD (global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- Real Migration + MigrationTestHelper test with existing-data cases; schema 3.json exported.
- UI copy Spanish (voseo, as existing); code/comments English. Conventional Commits, no AI
  attribution. Never commit `.codegraph/` or `.idea/`.

## Tasks
- [x] T1 Data model switch end to end: platforms table + DAO + repository, migration 2->3 with
      tests, Trip/finish flow/use cases/service extras/sheet/trip rows/summary bars on platform id
      + name. Route: delegated writer (many non-trivial files).
      Evidence: a0e8c0f (single commit: layers can't compile separately); RED = 227 compile
      errors; 346 tests green incl. 13 MigrationTest; assembleDebug green. Parent read MIGRATION_2_3.
      Review: medium, granted, reliability APPROVED + acknowledged (review-b257a97fb6216920).
      Advisory -> T2: finishTrip platform check not atomic; intent platform-id parsing untested;
      silent empty default for observeVisiblePlatforms in the interface.
- [x] T2 Settings "Plataformas": list with visible toggle, create, rename, delete (confirm dialog,
      note that past trips are kept), validation messages. Route: delegated writer.
      Evidence: 5645a1d (T1 advisories: atomic INSERT..SELECT WHERE EXISTS, intent id parsing tests,
      abstract observeVisiblePlatforms) + f5ce607 (PlatformRepository, PlatformsScreen, enum nav).
      RED = compile failures on new API (atomic finish had no behavioural RED); 386 tests green;
      assembleDebug green (parent re-ran). Review: medium, granted, reliability APPROVED +
      acknowledged (review-2beb31e08b19dc50). Advisory: SQLite failure path untested; stale
      dialog settle in PlatformsViewModel:104-118 (WARNING).

## Acceptance criteria
- Upgrading a v2 DB keeps every trip, total and name (OTHER labels become platforms).
- Only visible platforms appear in the sheet; deleted ones keep history names and totals.
- `testDebugUnitTest` + `assembleDebug` green; manual check on emulator by the user.

## Checks
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Delivery
- Branch `feat/custom-platforms` from `main` (9e74e78). Forecast ~1200-1600 authored lines.
  Strategy ask-on-risk -> feature-branch-chain (same as previous features); slices = T1, T2.

## Progress
- 2026-10-19: doc created.
- 2026-10-19: T1 done (a0e8c0f), reviewed.
- 2026-10-19: T2 done (5645a1d, f5ce607), reviewed. Next: user checks on emulator (v2->v3
  migration on real data), then merge decision.
