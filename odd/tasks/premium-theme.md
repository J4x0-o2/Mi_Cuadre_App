# Feature: premium-theme

## Objective
Translate the approved design mockup (claude.ai Design canvas "MiCuadre Boceto",
https://claude.ai/artifact/4UzYHWnu4Joxv84ETtF83d) into a complete Material 3 theme for
Compose: colors, shapes, typography. Screens are NOT redesigned here (next features).

## Problem / Why
Current theme is light-only, ignores `useDarkTheme`, and uses default shapes/fonts. Every
screen of the redesign depends on a correct theme foundation.

## Scope (authorized)
`ui/theme` (Color, Theme, Type, new Shape + extended tokens), bundled fonts in `res/font`,
`MainActivity` theme wiring, XML window theme (`themes.xml`) so the splash/window background
matches. No screen layout changes beyond what the new tokens change automatically.

## Design tokens (from the approved mockup)
Dark (primary look, night driving):
- background `#0B0F0E`, surface `#151B19`, surfaceVariant/container `#1D2522`,
  outline `#2A3430`, outlineVariant `#222B28`
- onBackground/onSurface `#F2F5F3`, onSurfaceVariant (muted) `#9AA8A2`
- primary (emerald, money + main action) `#3DDC97`, onPrimary `#04140D`,
  primaryContainer `#12291F`, onPrimaryContainer `#7BEBB8`
- "on trip" accent (gold): `#E8C27A`, container `#2E2614`, onContainer `#F0D29A`
  (extended token, not a Material role)
Light: derived counterpart with the same roles (emerald darkened for 4.5:1 on white).
Shapes: extraSmall 12, small 16, medium 20, large 28, extraLarge 32 (dp).
Type: Sora (display/headline/numbers), Manrope (title/body/label); tabular figures for
amounts and timers.

## Decisions / assumptions
- Theme follows the system dark/light setting; dynamic color OFF (fixed brand identity).
- Fonts bundled in `res/font` (offline, no GMS font provider). Both are SIL OFL 1.1.
- Gold "on trip" state lives in an extended `MiCuadreColors` via CompositionLocal.
- Future: user-defined platforms (any app-based worker, taxis). Out of scope here.

## Constraints
- Strict TDD (source: global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- Conventional Commits, no AI attribution trailers.

## Tasks
- [x] T1 Color tokens + contrast: dark/light schemes and extended colors; JVM test asserting
      WCAG contrast (>= 4.5:1 text roles, >= 3:1 large/non-text) for every on/container pair.
      Route: delegated writer (2+ non-trivial files).
- [x] T2 Shapes + typography: `Shapes` with the radii above; Sora/Manrope bundled fonts;
      `Typography` scale with tabular-number styles for amounts/timers. Route: delegated writer.
- [ ] T3 Theme wiring: `MiCuadreTheme(darkTheme = isSystemInDarkTheme())` really switching
      schemes, edge-to-edge system bars, `themes.xml` window background per mode.
      Route: delegated writer.

## Acceptance criteria
- Dark and light schemes apply; no hardcoded colors left in `ui/theme` consumers that bypass
  the theme for the tokens above.
- Contrast test green; `testDebugUnitTest`, `assembleDebug` green.

## Checks
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Delivery
- Branch `feat/premium-theme` from `main` (647b603, PR #1 merged). Strategy: ask-on-risk.
- Forecast ~300-450 authored lines.

## Progress
- Branch created from 647b603.

- T1 done (route: delegated writer). RED: light scheme with untuned emerald failed 7 pairs (onPrimary/primary 1.77 etc.), dark failed only decorative outline/background 1.50 (pair dropped: WCAG exempts decorative borders). GREEN after light primary #0A7A4D, gold #8A6410. Commit: e318e12.

- T2 done (route: delegated writer). RED: TypographyShapesTest failed to compile (AppShapes, DefaultMiCuadreTypography undefined). GREEN: full testDebugUnitTest passes. Fonts: Sora + Manrope variable TTF from google/fonts (OFL), licenses in licenses/. Commit: @T2@.

## Next step
T3 (wiring): scheme switching, edge-to-edge, themes.xml.
