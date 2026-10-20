# Feature: shift-safety-and-bubble

## Objective
Fix the app name spelling, protect against accidental shift ends, allow cancelling a ride,
add a floating bubble for quick access over other apps, and simplify the Home header.

## Problem / Why
- "MiCuadré" (accent) is wrong; the name is "MiCuadre".
- "Terminar turno" fires immediately; an accidental tap closes the shift.
- A ride started by mistake can only be finished (with an amount), never discarded.
- Drivers work inside other apps (maps, ride apps); reaching MiCuadre needs the notification
  shade or the launcher.
- The Home header shows a hard-coded "M" badge next to the name; the user wants only the name.

## Scope (authorized, user decisions 2026-10-19)
1. Rename every "MiCuadré" to "MiCuadre" (strings.xml, README).
2. Confirmation AlertDialog before ending a shift (both Home buttons), reusing the
   PlatformsScreen dialog pattern.
3. Cancel ride: discard the in-progress trip (delete the row with endedAt == null, no Room
   migration); state OnTrip -> Connected. Home "Cancelar carrera" action with confirmation.
4. Floating bubble (user chose it over QS tile / launcher shortcut / widget):
   - Overlay window (TYPE_APPLICATION_OVERLAY) owned by ShiftService, shown only while a shift
     is active and SYSTEM_ALERT_WINDOW is granted; draggable; tap expands a compact panel.
   - Panel actions by state: Connected -> Iniciar carrera; OnTrip -> Finalizar (opens the
     existing finish sheet via ACTION_OPEN_FINISH_TRIP) and Cancelar carrera; plus Abrir app.
   - Settings toggle to enable the bubble and request the overlay permission.
5. Home header: remove the "M" badge, keep only the app name.
Out of scope: Google Sheets sync, history screen, README refinement.

## Constraints
- Strict TDD (source: global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- No Room schema change.
- UI copy in Spanish (existing strings.xml); code/comments in English.
- Conventional Commits, no AI attribution trailers. Never commit `.codegraph/` or `.idea/vcs.xml`.
- Delivery strategy: ask-on-risk. Branch: feat/shift-safety-and-bubble.

## Tasks
- [x] T1 Rename MiCuadré -> MiCuadre + Home header without the "M" badge.
      Route: inline (2 mechanical files + README).
- [x] T2 End-shift confirmation dialog (Home). Route: delegated writer.
      Commit 8c370c1. RED: HomeDialogCoordinatorTest failed to compile (HomeDialogCoordinator
      missing). GREEN: 4 new tests; full testDebugUnitTest + assembleDebug pass.
      Dialog state: HomeUiState.dialog via HomeDialogCoordinator, wired in MainViewModel.
- [x] T3 Cancel ride: repository/DAO delete, use case, state machine transition, service
      action, Home button + confirmation. Route: delegated writer (2+ non-trivial files).
      Commit 1d67c72. RED: test sources failed to compile (cancelInProgressTrip, CancelTripUseCase,
      Action.CancelTrip, ACTION_CANCEL_TRIP, HomeDialog.CancelTrip unresolved). GREEN: repository
      (2), use case (1), state machine (2), service (2), dialog (4 more) tests; full suite +
      assembleDebug pass. Note: trip rows are only written at finish time, so the DAO delete is
      defensive; the real effect is the OnTrip -> Connected transition.
      Review (T1..T3 slice, base 3a50813): medium, granted, reliability APPROVED + acknowledged
      (review-4fad99ee052cabad). Advisories -> T3b.
- [ ] T3b Review advisories: stale cancel dialog (clear when leaving OnTrip + pass the trip
      startedAt so the service only cancels that trip), abstract cancelInProgressTrip, test the
      finish-vs-cancel race guard, fix misattached KDoc in TripDao. Route: delegated writer.
- [ ] T4 Floating bubble: overlay controller in ShiftService, Compose panel, permission +
      Settings toggle, manifest. Route: delegated writer (2+ non-trivial files).

## Acceptance criteria
- No "MiCuadré" left in the repo; header shows only the name.
- Ending a shift requires confirming a dialog; dismiss keeps the shift.
- Cancelling a ride (after confirming) removes it and returns to Connected; totals unchanged.
- With the permission granted and a shift active, the bubble appears over other apps, can be
  dragged, and its actions work; it disappears when the shift ends or the toggle is off.

## Progress
- Branch created from main (3a50813). T1 done (4cd39b0), T2 done (8c370c1), T3 done (1d67c72), slice reviewed. Next: T3b, T4.
