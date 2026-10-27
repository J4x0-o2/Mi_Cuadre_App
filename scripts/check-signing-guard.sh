#!/usr/bin/env bash
# Proves the release-signing guard in app/build.gradle.kts: an incomplete signing config must not
# break debug builds, must fail the real release build (assembleRelease stops at preReleaseBuild,
# before compiling), and no config must stay valid (unsigned).
# Run where local.properties has no MICUADRE_* keys (CI or a clean checkout).
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f local.properties ] && grep -Eq '^\s*MICUADRE_[A-Z_]*\s*[=:]' local.properties; then
  echo "local.properties defines MICUADRE_* keys; run this check on a clean checkout or in CI." >&2
  exit 2
fi

# Must match the *_KEY constants in app/build.gradle.kts.
KEYS=(MICUADRE_KEYSTORE_FILE MICUADRE_KEYSTORE_PASSWORD MICUADRE_KEY_ALIAS MICUADRE_KEY_PASSWORD)
failures=0
runs=0
logdir=$(mktemp -d)
trap 'rm -rf "$logdir"' EXIT

# gradle_with <log> <task> [VAR=value ...]: runs ./gradlew <task> -q with the signing env reset to
# the given values (so the caller's environment does not leak in), output going to <log>.
gradle_with() {
  local log=$1 task=$2
  shift 2
  local unset_args=()
  for key in "${KEYS[@]}"; do unset_args+=(-u "$key"); done
  env "${unset_args[@]}" "$@" ./gradlew "$task" -q >"$log" 2>&1
}

fail() {
  echo "FAIL: $1"
  tail -n 40 "$2" | sed 's/^/    /'
  failures=$((failures + 1))
}

# expect_ok <description> <task> [VAR=value ...]
expect_ok() {
  local desc=$1 task=$2
  shift 2
  runs=$((runs + 1))
  local log="$logdir/$runs.log"
  if gradle_with "$log" "$task" "$@"; then echo "PASS: $desc"; else fail "$desc" "$log"; fi
}

# expect_fail <description> <expected message> <task> [VAR=value ...]: Gradle must exit non-zero
# AND print the message, so unrelated failures (network, syntax, daemon) do not pass.
expect_fail() {
  local desc=$1 message=$2 task=$3
  shift 3
  runs=$((runs + 1))
  local log="$logdir/$runs.log"
  if gradle_with "$log" "$task" "$@"; then
    fail "$desc (build succeeded)" "$log"
  elif grep -Fq -- "$message" "$log"; then
    echo "PASS: $desc"
  else
    fail "$desc (failed without: $message)" "$log"
  fi
}

expect_ok "partial set: configuration succeeds (help)" help MICUADRE_KEY_ALIAS=upload

expect_fail "partial set: assembleRelease fails at the guard" \
  "Incomplete release signing config" assembleRelease MICUADRE_KEY_ALIAS=upload

expect_fail "missing keystore file: validateReleaseSigning fails" \
  "points to a missing file" validateReleaseSigning \
  MICUADRE_KEYSTORE_FILE=does-not-exist.jks MICUADRE_KEYSTORE_PASSWORD=x \
  MICUADRE_KEY_ALIAS=upload MICUADRE_KEY_PASSWORD=x

expect_ok "no values: validateReleaseSigning succeeds (unsigned release)" validateReleaseSigning

if [ "$failures" -ne 0 ]; then
  echo "$failures check(s) failed." >&2
  exit 1
fi
echo "All signing guard checks passed."
