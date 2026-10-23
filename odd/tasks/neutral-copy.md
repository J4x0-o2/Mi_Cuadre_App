# Feature: neutral-copy

## Objective
All user-facing copy uses neutral Spanish (tú forms or impersonal), so the app reads naturally
for drivers anywhere, not only in Argentina. Close the small deduction follow-ups left in 1.0.0.

## Problem / Why
The app targets public release for any app-based driver. About 20 strings still use voseo
("Tocá", "Intentá de nuevo", "Seguís en turno. Conectate...", "No tenés plataformas"), mixed
with newer neutral copy ("Elige", "Toca para volver a MiCuadre."). The register is inconsistent
and regional.

## Scope (authorized 2026-10-23, phase F2 of the public release plan)
1. Rewrite every voseo string in `app/src/main/res/values/strings.xml` to neutral tú forms
   (Tocá -> Toca, Intentá -> Intenta, Seguís -> Sigues, Conectate -> Conéctate, Terminá -> Termina,
   tenés -> tienes, Activá/creá/Agregá -> Activa/crea/Agrega, Ingresá -> Ingresa, Revisá -> Revisa,
   Permití -> Permite, Elegí -> Elige, Escribí -> Escribe, Usá -> Usa, ...). Keep meaning and length.
   Also hardcoded Kotlin UI literals, if any.
2. Guard test: a unit test that fails if strings.xml contains a known voseo form.
3. Deductions follow-ups from 1.0.0:
   - the show/hide toggle of a hidden deduction gets a content description (a11y);
   - the expenses/period total uses the same minus glyph as the rest of the app ("−", U+2212),
     not "-".
4. README and CHANGELOG copy in the same neutral register where they quote app text.
Out of scope: retaking the screenshots (done in the 1.1.0 release step, on the emulator).

## Constraints
- Strict TDD (source: global config). Runner: `./gradlew testDebugUnitTest` with
  `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`; also `assembleDebug`.
- Code, comments and docs in English; UI copy in neutral Spanish.
- Conventional Commits, no AI attribution trailers. Never commit `.codegraph/` or `.idea/`.
- Branch feat/neutral-copy from main bd7ab00. Delivery strategy: ask-on-risk.

## Tasks
- [x] T1 Neutral copy in strings.xml (+ Kotlin literals, README/CHANGELOG quotes) with the voseo
      guard test. Route: delegated writer (several files).
- [ ] T2 Deduction follow-ups: toggle content description and the "−" glyph in the total.
      Route: delegated writer (same writer, separate commit).

## Acceptance criteria
- No voseo form remains in user-facing copy; the guard test passes and would fail on regression.
- The hidden-deduction toggle is announced by TalkBack.
- Every negative amount in the app uses "−".
- All unit tests and `assembleDebug` green.

## Progress
- 2026-10-23: feature document created; branch feat/neutral-copy from main bd7ab00 (F1 merged).

- 2026-10-23 T1 done (route: delegated writer). RED: `CopyRegisterTest` failed on 14 keys (paused_hint,
  disconnect_hint, no_active_platforms, notif_connected_text, error_start_trip, error_finish_trip,
  amount_required, settings_save_error, settings_bubble_permission_unavailable, platforms_hint,
  platforms_empty, platforms_error_blank, platforms_error_too_long, platforms_save_error).
  GREEN after rewrite: `./gradlew testDebugUnitTest` 717 tests, 0 failures; `assembleDebug` OK.
  No Kotlin literals, other `values-*` locales, README or docs needed changes. CHANGELOG
  `[Unreleased]` entry added (neutral copy + F1 fix).

## Next step
T2.
