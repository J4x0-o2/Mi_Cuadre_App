# Feature: release-base

## Objective
A release build that can be signed, shrunk with R8 and verified on a device, a CI pipeline that
checks every push, and explicit backup and device-transfer rules. Phase F4 of the public release
plan (Engram `micuadre/roadmap/public-release-plan`).

## Problem / Why
Every release so far was a debug build. Play Store (F6) needs a signed release bundle, the Google
Sheets backup (F5) needs the SHA-1 of the signing key for its OAuth client, and nothing checks the
build or the 769 unit tests outside this machine. The manifest says `allowBackup="false"` and the
generated `backup_rules.xml` / `data_extraction_rules.xml` are unreferenced templates, so what a
phone migration copies is left to platform defaults.

## Scope (authorized 2026-10-25)
1. Release signing: an upload keystore stored outside the repo; `signingConfig` read from
   `local.properties` or environment variables. Without those values the release build still
   assembles, unsigned, so CI and other clones keep working.
2. R8 release smoke test: `assembleRelease` with minify + shrinkResources, installed on the
   emulator; start a shift, log a ride, end the shift with expenses, open History. Fix keep rules if
   anything breaks.
3. GitHub Actions CI: on push and pull request, run `testDebugUnitTest` and `assembleDebug` (and the
   release build without signing).
4. Backup and device-transfer rules: reference `data_extraction_rules.xml` (API 31+) and
   `backup_rules.xml` (API 29–30) from the manifest with an explicit policy.
Out of scope: Google Sheets backup (F5), Play Console setup and store listing (F6), a version bump.

## Decisions
- 2026-10-25 Backup policy (user): enable Google cloud auto backup AND device-to-device transfer of
  the Room database and the DataStore settings. The device check must confirm a restored database
  with an open shift or ride is handled.
- Keystore: `%USERPROFILE%/.android-keys/micuadre-upload.jks`, alias `upload`, random password kept
  only in the git-ignored `local.properties`. The user must back up both (Play App Signing can reset
  a lost upload key, but only through support).

## TDD
Strict TDD enabled (session config). Runner: `./gradlew testDebugUnitTest` with `JAVA_HOME` =
Android Studio jbr. Gradle build-script and CI changes have no unit-test seam; they are verified by
the builds and the device smoke test instead, recorded per task.

## Tasks
- [x] T1 Release signing config (local.properties / env, unsigned fallback) + keystore outside repo
- [x] T2 R8 release smoke test on emulator Pixel_6, keep-rule fixes if needed
- [x] T3 GitHub Actions CI workflow
- [x] T4 Backup and device-transfer rules in the manifest
- [x] T5 Docs: README build/release section, CHANGELOG `[Unreleased]`

## Acceptance criteria
- `assembleRelease` produces a signed APK when the signing values exist, and an unsigned one
  otherwise; `apksigner verify` passes on the signed one.
- The release APK runs the smoke flow without crashes.
- The CI workflow is valid and runs the unit tests and builds.
- The manifest references both rule files and they match the chosen policy.
- 769+ unit tests green, `assembleDebug` OK.

## Checks
`./gradlew testDebugUnitTest assembleDebug assembleRelease`; emulator smoke flow; `apksigner verify`.

## Progress
- 2026-10-25 Keystore generated (PKCS12, RSA 4096, alias `upload`, 10000 days) at
  `~/.android-keys/micuadre-upload.jks`; signing values appended to `local.properties`. Upload cert
  SHA-1 `71:54:82:13:3D:05:21:F1:37:E8:1B:46:74:A2:12:3E:65:86:6D:4A`. A credentials file was left on
  the user's Desktop at their request, for them to back up and delete.
- 2026-10-25 T1 (route: delegated writer, trigger: 2+ non-trivial files across T1/T3/T4) `8c48608`:
  values from local.properties, env fallback; partial set or missing keystore fails the build.
  `apksigner verify --print-certs` on app-release.apk: SHA-1 715482133d...866d4a (matches).
  Unsigned fallback verified by the parent in a clean sibling worktree without local.properties:
  `assembleRelease` OK, output `app-release-unsigned.apk`. Build script, no unit-test seam.
- 2026-10-25 T3 (delegated) `e616e83`: `.github/workflows/ci.yml` (checkout/setup-java 17/
  setup-gradle v4, `testDebugUnitTest assembleDebug assembleRelease`, test reports on failure,
  concurrency cancel); `gradlew` made executable in git. Not yet run on GitHub (branch not pushed).
- 2026-10-25 T4 (delegated) `ab3c03c`: allowBackup true, fullBackupContent + dataExtractionRules,
  includes `database:.` and `file:datastore/` for cloud backup and device transfer. RED: 4 new
  `BackupRulesTest` tests failed (empty includes, allowBackup false, missing device-transfer); GREEN:
  773 tests, 0 failures.
- 2026-10-25 T2 (route: inline) Emulator Pixel_6, signed release APK (R8 + shrinkResources): start
  shift, ride $15.000 Didi efectivo, end shift, summary, Historial year + October month, create the
  Combustible deduction, second shift with a ride open. Backup with `bmgr backupnow` (local
  transport): Success. Uninstall + reinstall: the ride in progress was restored with its original
  start 22:40 and the week shows $35.000 (both shifts). Finished it ($20.000 InDriver), expenses
  sheet with Combustible − $5.000, History: $35.000, Neto $30.000, 2 turnos. Crash buffer empty the
  whole time. No keep-rule change needed. Transport switched back to GMS afterwards.
- 2026-10-25 T5 (route: inline) README "Build de release" section + CHANGELOG `[Unreleased]`.
- 2026-10-26 Review (base e66ea24, through 48da9f2, 10 files, 293 lines): high (gradlew mode,
  CI shell), granted by the user, 4 lenses APPROVED and acknowledged (lineage
  review-203146a14258b87a). Non-blocking follow-ups, not applied:
  - WARNING R4: signing `require()` runs at configuration time, so a partial signing set or a moved
    keystore also breaks `assembleDebug` / `testDebugUnitTest`; scope the check to release tasks.
  - WARNING R2: `BackupRulesTest.includes()` KDoc says direct children but uses
    `getElementsByTagName` (all descendants); fix the comment or iterate child nodes.
  - SUGGESTION R1: add `permissions: contents: read` to ci.yml; optionally pin actions to SHAs.
  - SUGGESTION R3: do not trim the passwords, only the path and alias.
  - SUGGESTION R3: no automated check of the partial-config / missing-keystore / signed paths.
  - SUGGESTION R2: `assertEquals` instead of `assertTrue(tagName == ...)`.

## Next step
Push the branch so CI runs once on GitHub; merge and release (1.3.0 or a 1.2.1) when the user decides.
