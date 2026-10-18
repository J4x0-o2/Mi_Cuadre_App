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
- [ ] T1 Theme mode domain + resolver + DataStore repository (Hilt) with tests.
      Route: delegated writer.
- [ ] T2 Live wiring: MainActivity/MiCuadreTheme use the resolved mode; automatic mode
      re-evaluates at the next boundary (no per-second polling). Route: delegated writer.
- [ ] T3 Settings screen + navigation from the Home header + strings. Route: delegated writer.

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

## Next step
T1-T3 via one delegated writer.
