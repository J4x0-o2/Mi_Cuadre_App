# Feature: release-0.4.0

## Objective
Ship feat/shift-safety-and-bubble as MiCuadre 0.4.0: Spanish README with the new features,
a screenshot suite of every feature, and a LinkedIn launch post.

## Scope (user request 2026-10-20)
- Save and push to GitHub (precedent: merge into main with --no-ff, tag, push).
- Version 0.4.0 (new features, no breaking changes) + CHANGELOG entry.
- README rewritten in Spanish, documenting the new features.
- Image suite covering every feature (real emulator screenshots + LinkedIn-ready composites).
- LinkedIn post draft (delivered in chat).

## Constraints
- Docs only plus version bump; no source changes. Functional check: assembleDebug after the bump.
- Conventional Commits, no AI attribution trailers. Never commit `.codegraph/` or `.idea/vcs.xml`.
- Push with `git -c credential.helper=manager push` (no gh CLI).

## Tasks
- [x] T1 Screenshot suite from the emulator into docs/images + LinkedIn composites outside the
      repo (Documents/MiCuadre-marketing/0.4.0). Route: delegated (preparation + many files).
      9 new screenshots; today/shift/week replaced (old ones said "MiCuadré"); cover.png is the
      carousel cover slide. 10 slides (1080x1350) + carousel PDF, checked visually.
- [x] T2 Version 0.4.0, CHANGELOG, Spanish README. Route: inline (docs; README is one file).
      assembleDebug passes after the bump.
- [ ] T3 Merge into main, tag v0.4.0, push main, branch and tag. Route: inline (git state).
- [ ] T4 LinkedIn post draft. Route: inline (chat).

## Progress
- Doc created.
