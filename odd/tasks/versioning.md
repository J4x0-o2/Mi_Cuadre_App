# Feature: versioning

## Objective
Give MiCuadre a single source of truth for its version, a changelog, and show the version in
Settings, starting at 0.2.0.

## Problem / Why
`versionCode = 1` / `versionName = "0.1.0"` are hardcoded in `app/build.gradle.kts`, there are no
tags or changelog, and the user cannot see which build is installed.

## Scope (authorized, user decisions 2026-10-18)
- Semantic Versioning. Current release = 0.2.0 (0.1.0 = first redesign; 0.2.0 = home summaries).
- Single source: `version.properties` at repo root (`VERSION_NAME=0.2.0`); `versionCode` derived as
  major*10000 + minor*100 + patch (0.2.0 -> 200), validated (fails the build on a bad value).
- Settings: new "ACERCA DE" section showing "Versión 0.2.0" (debug builds also show the build
  type) read from BuildConfig.
- `CHANGELOG.md` (Keep a Changelog) with 0.1.0 and 0.2.0 entries from git history.
- Tag `v0.2.0` on the merge commit (push of the tag = user decision).
Out of scope: release signing, Play Store, CI.

## Constraints
- Strict TDD (global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. Pure logic tested: version parsing /
  versionCode derivation (kept as a small pure Kotlin function usable from the build script or
  duplicated test-covered logic), Settings label formatting.
- Conventional Commits, no AI attribution. Never commit `.codegraph/` or `.idea/`.

## Tasks
- [ ] T1 Version source + derived versionCode + BuildConfig + Settings "ACERCA DE" + CHANGELOG.
      Route: delegated writer (2+ non-trivial files).

## Acceptance criteria
- Changing only `version.properties` changes versionName/versionCode; Settings shows "Versión 0.2.0";
  `testDebugUnitTest` + `assembleDebug` green.

## Checks
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Delivery
- Branch `feat/versioning` from `main` (84d84a0). Forecast ~150-250 lines -> single PR/merge.

## Progress
- 2026-10-18: doc created. Next: T1.
