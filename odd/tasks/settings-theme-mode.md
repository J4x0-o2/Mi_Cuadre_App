# Feature: settings-theme-mode

## Objective
Add a Settings screen where the driver chooses the app theme mode, including an automatic
night schedule for driving at night.

## Problem / Why
Today the theme only follows the system dark/light setting. Drivers want the app dark at
night without changing the whole phone, or forced light/dark.

## Scope (authorized, user decisions 2026-10-17)
- Settings screen reachable from an icon in the Home header (top-right, 44dp, where the
  mockup had the history icon).
- Theme mode options (single choice, Spanish copy, voseo register):
  1. "Según el sistema" — DEFAULT on install (current behavior).
  2. "Claro" — always light.
  3. "Oscuro" — always dark.
  4. "Automático por horario" — dark from 18:00 to 06:00, light otherwise; switches live
     at the boundary even while the app is open. Fixed hours for now (customizable hours
     are a possible future extension).
- Persist with Jetpack DataStore (Preferences). No Room changes.
Out of scope: Google Sheets sync (future version, saved in Engram), history screen,
user-defined platforms.

## Design
Same visual language as the other screens (MiCuadreTheme tokens, cards radius 28, Manrope,
Sora headings). Settings: top bar with back, title "Ajustes", section "APARIENCIA" with a
card holding 4 selectable rows (radio + label + short description; selected row uses
primaryContainer). Back via BackHandler / top bar. Insets via Scaffold.

## Constraints
- Strict TDD (source: global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. Pure logic first: resolving the
  effective dark flag from (mode, system dark, local time) including the 18:00/06:00
  boundaries; next switch instant for the live ticker; preference mapping (unknown stored
  value -> SYSTEM).
- Conventional Commits, no AI attribution trailers. Untracked `.idea/vcs.xml`: never commit.

## Tasks
- [x] T1 Theme mode domain + resolver + DataStore repository (Hilt) with tests.
      Route: delegated writer.
- [x] T2 Live wiring: MainActivity/MiCuadreTheme use the resolved mode; automatic mode
      re-evaluates at the next boundary (no per-second polling). Route: delegated writer.
- [x] T3 Settings screen + navigation from the Home header + strings. Route: delegated writer.
- [x] T4 Review fixes: robust Room-schema test setup (debug assets + release unit tests disabled),
      settings write-failure handling, read-failure fallback test, re-arm schedule on time zone /
      clock change. Route: delegated writer.

## Acceptance criteria
- Default is "Según el sistema"; choice survives app restart; automatic mode flips at 18:00
  and 06:00 while open; `testDebugUnitTest`, `assembleDebug` green.

## Checks
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Delivery
- Branch `feat/settings-theme-mode` stacked on `feat/screen-redesign-summary-fixes`
  (screen-redesign not yet in main). Strategy: ask-on-risk; forecast ~400-600 lines.

## Progress
- Branch created.
- T1 done (3f7ca01, delegated writer): ThemeMode, ThemeSchedule resolver, DataStore repo + Hilt; RED 14/14 failing (stubs) -> GREEN; repo tests over temp-file DataStore; 226 tests green.
- T2 done (1d1d09b, delegated writer): themeStates ticker (flatMapLatest + delay to next 06:00/18:00, no polling, re-armed on resume via WhileSubscribed(0)), ThemeViewModel, edge-to-edge bars + window background follow resolved mode; RED 3/3 -> GREEN; 229 green.
- T3 done (ef579e3, delegated writer): SettingsScreen, SettingsViewModel, header gear (44dp), strings; navigation = saved boolean in MainActivity (smaller than navigation-compose); RED 3/3 -> GREEN; 232 green, assembleDebug green.
- T4 done (delegated writer): build 6dde412 (debug-assets schemas, release unit test disabled, copy/delete hack removed; release APK has no schemas); settings write failure a474fa3 (RED 2/6 -> GREEN, UiMessage snackbar, read-failure tests added as characterization, already green); clock change TIME_CHANGED/TIMEZONE_CHANGED re-arm (RED 2/5 -> GREEN, 770849a).

## Next step
Review, push, device check.
