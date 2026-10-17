# Feature: screen-redesign

## Objective
Rebuild the app screens to match the approved mockup (claude.ai Design canvas "MiCuadre
Boceto", https://claude.ai/artifact/4UzYHWnu4Joxv84ETtF83d) on top of the premium theme,
prioritizing the most frequent flow: finishing a trip from the background.

## Problem / Why
The app lives mostly in the background. The critical moment is: trip ends -> driver opens the
app (or taps the notification) -> enters the amount -> saves. That flow must take 1-2 taps.
Current screens are functional but do not follow the approved design.

## Scope (authorized)
Finish-trip sheet, ongoing notification (actions), Home while connected, On-trip state,
Shift summary. Uses `MiCuadreTheme` tokens only (no hardcoded colors).
Out of scope: history screen ("Ver todas" link is omitted until it exists), user-defined
platforms (future vision), light-mode-specific layouts (theme already handles light).

## Design spec (from the approved mockup; values map to theme tokens)
Common: screen padding 20dp horizontal; cards = surface + 1dp outlineVariant border,
radius large (28); tiles radius 22; Sora for big numbers (`MiCuadreTheme.typography.money*`,
`timer`), Manrope elsewhere; uppercase 13sp muted section labels (letterSpacing 0.4sp).
Header: 40dp emerald rounded-square logo "M" + "MiCuadre"; status pill on the right
("En turno": primaryContainer/onPrimaryContainer with primary dot; "En carrera": gold
container/onContainer with gold dot).

1. Finish-trip sheet (ModalBottomSheet, top radius 32): title "Cerrar carrera" + subtitle
   "HH:mm – HH:mm · N min"; close icon button 44dp. Amount field: label "VALOR DE LA
   CARRERA", 80dp tall, radius 24, 2dp primary border, "$" prefix, Sora 40sp, autofocus
   with numeric keyboard open, thousands separator formatting. Platform: 3 equal chips
   (InDriver, Didi, Otra) 52dp radius 16, selected = primary border + primaryContainer;
   "Otra" reveals the label field. Last used platform preselected. Payment: segmented
   control (Efectivo / Transferencia) inside a 6dp-padded container, selected = onSurface
   fill with surface text. Primary button "Guardar carrera" 68dp radius 22. Validation
   errors keep showing via existing UiMessage path.
2. Notification: two states. On trip: title "Carrera en curso · <chronometer>", text
   "Iniciada HH:mm · Hoy $X en N carreras", actions "Terminar carrera" (opens the app
   directly into the finish sheet) and "Abrir app". Connected (between trips): title
   "Hoy $X · N carreras", text "Tocá Iniciar cuando subas al pasajero", actions "Iniciar
   carrera" (starts the trip via the service without opening the app) and "Abrir app".
   Use the system chronometer (setUsesChronometer) for live time.
3. Home, connected: total card ("TOTAL DEL DÍA", Sora ~46sp, cash/transfer split tiles),
   two tiles (Conectado h:mm:ss, Carreras N), primary CTA "Iniciar carrera" 68dp radius 22
   with play icon, "Carreras de hoy" list (row: 42dp platform badge, platform + "HH:mm · N
   min", amount + payment), secondary outlined "Terminar turno" 52dp at the bottom.
   Disconnected state: keep a simple welcome + primary "Conectarse" in the same style.
4. On trip: gold "En carrera" pill; centered 264dp ring (10dp track surfaceVariant, gold
   progress arc) with "DURACIÓN", Sora 52sp timer, "Iniciada HH:mm"; tiles Total del día /
   Conectado; primary "Terminar carrera" 76dp radius 24 with check icon; disabled
   "Desconectar" + hint "Terminá la carrera para desconectarte" (business rule: a trip
   must be finished before disconnecting).
5. Shift summary (full screen or large sheet): check badge, "Turno cerrado", "Hoy · HH:mm –
   HH:mm", "GANASTE" total in primary Sora ~50sp, 2x2 metrics (Conectado, Carreras,
   Efectivo, Transferencia), "Por plataforma" bars (relative to max), "Listo" button
   (onSurface fill).

## Constraints
- Strict TDD (source: global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. Test pure logic (formatters,
  summary/bars math, last-used platform, notification content model, intent routing);
  Compose layout itself is not unit-tested.
- Copy: Spanish, voseo register, all in `strings.xml`.
- Conventional Commits, no AI attribution trailers. Untracked `.idea/vcs.xml`: never commit.

## Tasks
- [x] T1 Finish-trip sheet redesign + last-used platform preselection + amount formatting.
      Route: delegated writer.
- [x] T2 Notification redesign + "Terminar carrera" deep link into the sheet + "Iniciar
      carrera" service action. Route: delegated writer.
- [x] T3 Home (connected/disconnected) redesign. Route: delegated writer.
- [x] T4 On-trip state redesign (ring timer, disabled disconnect + hint). Route: delegated writer.
- [x] T4.1 Restore "Desconectar" (pause) on Connected Home next to "Terminar turno"; paused shift keeps totals, can reconnect or end. Route: delegated writer.
- [x] T4.2 Preselect the payment method of the last finished trip (cash default). Route: delegated writer.
- [x] T4.3 Trip rows show start and end time ("HH:mm – HH:mm · N min"). Route: delegated writer.
- [x] T5 Shift summary redesign (metrics + per-platform bars). Route: delegated writer.

## Follow-ups carried from premium-theme review
- Verify system bar icon contrast on a device in both modes.
- Theme wiring test (Compose/Robolectric) if cheap; otherwise keep documented.

## Acceptance criteria
- Screens match the spec using theme tokens; existing behavior and tests preserved.
- `testDebugUnitTest`, `assembleDebug` green.

## Checks
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Delivery
- Branch `feat/screen-redesign` stacked on `feat/premium-theme` (3ff16eb, PR pending).
- Forecast ~1200-1600 authored lines: exceeds the ~400 budget.
- Chain strategy (user, 2026-10-17): feature-branch-chain. Integration branch
  `feat/screen-redesign` (base 2fa066e); slice PRs target it; it merges into `main` at the end.
  - Slice 1 `feat/screen-redesign-finish-flow`: T1, T2 (960d0ea, 370a73b + docs).
  - Slice 2 `feat/screen-redesign-home-trip`: T2.1, T3, T4.
  - Slice 3 `feat/screen-redesign-summary`: T5.

## Progress
- Branch created from 3ff16eb.
- T1 done (960d0ea), route: delegated writer. RED: 14 failing (AmountInput, FinishSheetSubtitle, GetLastUsedPlatform) against stubs; GREEN after implementation. Platform picker step retired (PlatformPickerSheet deleted).
- T2 done (370a73b), route: delegated writer. RED: 9 failing (ShiftNotificationModel, LaunchRequests); GREEN after implementation. testDebugUnitTest and assembleDebug green.

- Review (hook-issued candidate from 647b603, included theme + T1-T2): medium, consent
  granted, lens review-reliability, APPROVED and acknowledged (review-6ac1ef0fb3eb23f7).
  Triage: "no notification re-post on Connected->OnTrip" is a false positive
  (`handleStartTrip` calls `updateNotification`). Real follow-ups -> T2.1.
- [x] T2.1 Hardening from review: (a) every service start command that can arrive via
      `getForegroundService` (notification "Iniciar carrera") must satisfy `startForeground`
      even on early-return paths (e.g. not Connected, restarted service); (b)
      `openFinishSheet` re-entrancy: re-check `finishSheet == null` and OnTrip inside the state
      update after the suspend lookup, and don't reopen if dismissed meanwhile; (c) ViewModel
      wiring test for pending finish request (request + OnTrip -> sheet; expiry -> null).
      Route: delegated writer.
- T2.1 done (e0a3b6f), route: delegated writer. RED: 4 failing FinishSheetCoordinatorTest (stubs) + 2 failing ServiceStartPolicyTest; GREEN after implementation. (a) every delivered start intent calls startForeground first, idle service stops itself (ServiceStartPolicy); (b)/(c) opening logic extracted to FinishSheetCoordinator (atomic re-check in state update, in-flight Job cancelled on dismiss).
- T3 done (d2c0375), route: delegated writer. RED: 15 HomeModelsTest + 2 FormattersTest failing against stubs (day breakdown, trip rows, badge, elapsed, ring, disconnect rule, h:mm:ss, trip timer); GREEN after implementation. Trip rows show start time (HH:mm) and duration; "Ver todas" omitted. Connected Home has no Desconectar (spec); Terminar turno ends the shift.
- T4 done (24bb2f2), route: delegated writer. RED: clockTime test failing against stub; GREEN after implementation. Ring arc is decorative: one step per elapsed minute over a 30 min reference (min 5%, cap 100%). testDebugUnitTest and assembleDebug green.
- T4.1 done (6f0678f), route: delegated writer. RED: 3 failing (HomeActions.secondary x2, paused snapshot) against stubs; GREEN after implementation. Paused shift (Disconnected + open session) now shows its trips/total and "Terminar turno"; service resumes/ends the open session from Room instead of creating a second one or ignoring the end.
- T4.3 done (c63bd0b), route: delegated writer. RED: 2 HomeModelsTest row-detail failures; GREEN reusing FinishSheetSubtitle.
- T4.2 done (526bde0), route: delegated writer. RED: 2 failing GetLastUsedPlatformUseCaseTest (payment); GREEN after implementation. testDebugUnitTest and assembleDebug green.

- Review slice 2 (base 819b2bb, T2.1-T4.3): medium, consent granted, lens review-reliability,
  APPROVED and acknowledged (review-fcbf244403827a1c). Reviewed boundary: ad3f0fe.
  Triage: "unsynchronized service flags" not applicable (service scope is
  `Dispatchers.Main.immediate`, all reads/writes on main). Real follow-ups -> T5.1.
- [x] T5.1 Service tests (Robolectric `ServiceController` or equivalent): reconnect after a
      pause resumes the open Room session (no second session); End Shift on a fresh service
      instance ends the open session; every delivered start intent calls `startForeground`.
      Remove the tautological `requiresForeground(hasIntent = true)` check (call
      `startInForeground` unconditionally). Route: delegated writer (slice 3).
- T5 done (4dccbce), route: delegated writer. RED: 9 of 10 ShiftSummaryModelTest failing against a stub (time range, connected h:mm:ss, cash/transfer split, bars relative to max, Otra grouped by label, empties hidden); GREEN after implementation. DailySummary now carries session start/end and trips; summary is a full-screen Dialog (usePlatformDefaultWidth=false, back dismisses).
- T5.1 done (47862b5), route: delegated writer. Robolectric 4.14.1 + hilt-android-testing, real Hilt-injected ShiftService with an in-memory repository (@BindValue) whose Room lookup can be held in flight, so the fresh-instance race is exercised. Characterization tests; RED shown by mutation (removing the Room fallbacks / startForeground fails 4 of 6). `requiresForeground` removed; startInForeground is unconditional for every delivered intent.

## Next step
Review slice 3, then integrate feat/screen-redesign into main. Device check pending for T1-T4: keyboard autofocus in the sheet, notification chronometer, Terminar carrera deep link, Iniciar carrera without opening the app, Home/on-trip layouts in dark and light.
