#!/usr/bin/env bash
# Proves the release-signing guard in app/build.gradle.kts: an incomplete signing config must not
# break debug builds, must fail release validation, and no config must stay valid (unsigned).
# Run where local.properties has no MICUADRE_* keys (CI or a clean checkout).
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f local.properties ] && grep -Eq '^\s*MICUADRE_[A-Z_]*\s*[=:]' local.properties; then
  echo "local.properties defines MICUADRE_* keys; run this check on a clean checkout or in CI." >&2
  exit 2
fi

KEYS=(MICUADRE_KEYSTORE_FILE MICUADRE_KEYSTORE_PASSWORD MICUADRE_KEY_ALIAS MICUADRE_KEY_PASSWORD)
failures=0

# gradle_with <task> [VAR=value ...]: runs ./gradlew <task> -q with the signing env reset to the
# given values, so the caller's environment does not leak in.
gradle_with() {
  local task=$1
  shift
  local unset_args=()
  for key in "${KEYS[@]}"; do unset_args+=(-u "$key"); done
  env "${unset_args[@]}" "$@" ./gradlew "$task" -q >/dev/null 2>&1
}

expect_ok() {
  if "$@"; then echo "PASS: $DESC"; else echo "FAIL: $DESC"; failures=$((failures + 1)); fi
}
expect_fail() {
  if "$@"; then echo "FAIL: $DESC"; failures=$((failures + 1)); else echo "PASS: $DESC"; fi
}

DESC="partial set: configuration succeeds (help)"
expect_ok gradle_with help MICUADRE_KEY_ALIAS=upload

DESC="partial set: validateReleaseSigning fails"
expect_fail gradle_with validateReleaseSigning MICUADRE_KEY_ALIAS=upload

DESC="missing keystore file: validateReleaseSigning fails"
expect_fail gradle_with validateReleaseSigning \
  MICUADRE_KEYSTORE_FILE=does-not-exist.jks MICUADRE_KEYSTORE_PASSWORD=x \
  MICUADRE_KEY_ALIAS=upload MICUADRE_KEY_PASSWORD=x

DESC="no values: validateReleaseSigning succeeds (unsigned release)"
expect_ok gradle_with validateReleaseSigning

if [ "$failures" -ne 0 ]; then
  echo "$failures check(s) failed." >&2
  exit 1
fi
echo "All signing guard checks passed."
